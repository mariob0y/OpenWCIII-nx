#!/usr/bin/env python3
"""Apply switch2/patches/<name>/*.patch to the pinned submodules under switch2/external/.

The submodules are never committed to: every change to them is a numbered patch here. The patches
form a series (a later one may change lines an earlier one added), so the series is checked as a
whole. A submodule's working copy is in one of three states:

- pinned:   exactly the pinned commit, nothing applied;
- applied:  the pinned commit with every patch applied, in order;
- modified: anything else (changes that are in no patch yet, or a partly applied series).

Usage:
    python -I switch2/scripts/apply_patches.py            # apply (nothing to do when applied)
    python -I switch2/scripts/apply_patches.py --check    # report the state, change nothing
    python -I switch2/scripts/apply_patches.py --reset    # back to the pinned commit (drops changes)
    python -I switch2/scripts/apply_patches.py --new clearwing "fix: runtime - ..."
        # the working copy's changes beyond the series become the next numbered patch

After the Clearwing patches change, rebuild its jars (build-java.sh does it when they are missing):
    cd switch2/external/clearwing-vm && ./gradlew --offline -q :runtime:jar :transpiler:shadowJar
"""
import argparse
import os
import pathlib
import subprocess
import sys
import tempfile

SWITCH2 = pathlib.Path(__file__).resolve().parent.parent

# patches/<name> -> external/<submodule>
TREES = {
    "clearwing": "clearwing-vm",
}


def git(tree, *args, index=None, check=True):
    env = dict(os.environ)
    if index is not None:
        env["GIT_INDEX_FILE"] = str(index)
    result = subprocess.run(["git", *args], cwd=tree, env=env, capture_output=True)
    if check and result.returncode != 0:
        sys.exit(f"git {' '.join(args)} failed in {tree}:\n{result.stderr.decode(errors='replace')}")
    return result


def out(tree, *args, index=None):
    return git(tree, *args, index=index).stdout.decode().strip()


class Tree:
    def __init__(self, name):
        self.name = name
        self.path = SWITCH2 / "external" / TREES[name]
        self.patches = sorted((SWITCH2 / "patches" / name).glob("*.patch"))
        self.tmp = tempfile.TemporaryDirectory()
        self.index = pathlib.Path(self.tmp.name) / "index"

    def present(self):
        return (self.path / ".git").exists()

    def pinned_tree(self):
        return out(self.path, "rev-parse", "HEAD^{tree}")

    def live_tree(self):
        """The working copy as a tree (line endings normalized as git stores them)."""
        git(self.path, "read-tree", "HEAD", index=self.index)
        git(self.path, "add", "-A", index=self.index)
        return out(self.path, "write-tree", index=self.index)

    def patched_tree(self):
        """The pinned commit with the whole series applied."""
        git(self.path, "read-tree", "HEAD", index=self.index)
        for patch in self.patches:
            result = git(self.path, "apply", "--cached", str(patch), index=self.index, check=False)
            if result.returncode != 0:
                sys.exit(f"{patch.name} does not apply after the patches before it:\n"
                         f"{result.stderr.decode(errors='replace')}")
        return out(self.path, "write-tree", index=self.index)

    def state(self):
        live = self.live_tree()
        if live == self.pinned_tree():
            return "pinned"
        if live == self.patched_tree():
            return "applied"
        return "modified"

    def apply(self):
        state = self.state()
        if state == "applied":
            print(f"external/{TREES[self.name]}: {len(self.patches)} patches already applied")
            return 0
        if state == "modified":
            print(f"external/{TREES[self.name]}: the working copy has changes that are in no patch.\n"
                  f"  Save them:  --new {self.name} \"type: summary\"   or drop them:  --reset")
            return 1
        target = self.patched_tree()
        # Write the patched tree into the working copy through git (which converts line endings the
        # way the checkout did), then unstage: the submodule's index stays at the pinned commit.
        git(self.path, "read-tree", target)
        git(self.path, "checkout-index", "-a", "-f")
        for deleted in out(self.path, "diff", "--name-only", "--diff-filter=D", "HEAD", target).split():
            (self.path / deleted).unlink(missing_ok=True)
        git(self.path, "read-tree", "HEAD")
        print(f"external/{TREES[self.name]}: applied {len(self.patches)} patches")
        return 0

    def reset(self):
        git(self.path, "reset", "-q", "--hard")
        git(self.path, "clean", "-fdq")
        print(f"external/{TREES[self.name]}: back to the pinned commit")
        return 0

    def new(self, subject):
        live, target = self.live_tree(), self.patched_tree()
        if live == target:
            print("nothing to save: the working copy is the series applied")
            return 0
        # The patch's author is this repository's configured identity, never one git would guess
        # from the machine (account name and network domain).
        identity = {}
        for key, variables in (("user.name", ("GIT_AUTHOR_NAME", "GIT_COMMITTER_NAME")),
                               ("user.email", ("GIT_AUTHOR_EMAIL", "GIT_COMMITTER_EMAIL"))):
            value = git(SWITCH2.parent, "config", key, check=False).stdout.decode().strip()
            if not value:
                sys.exit(f"set {key} in this repository first (git config {key} ...)")
            identity.update(dict.fromkeys(variables, value))
        os.environ.update(identity)
        head = out(self.path, "rev-parse", "HEAD")
        base = out(self.path, "commit-tree", target, "-p", head, "-m", "series")
        tip = out(self.path, "commit-tree", live, "-p", base, "-m", subject)
        number = len(self.patches) + 1
        directory = SWITCH2 / "patches" / self.name
        written = out(self.path, "format-patch", "-q", "--no-signature", "--zero-commit",
                      "--start-number", str(number), "-o", str(directory), f"{base}..{tip}")
        print(f"wrote {pathlib.Path(written).name if written else 'patch ' + str(number)}")
        return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--reset", action="store_true")
    ap.add_argument("--new", nargs=2, metavar=("NAME", "SUBJECT"))
    args = ap.parse_args()

    if args.new:
        return Tree(args.new[0]).new(args.new[1])

    failures = 0
    for name, submodule in TREES.items():
        tree = Tree(name)
        if not tree.present():
            print(f"external/{submodule} is not checked out - run:\n"
                  f"    git submodule update --init switch2/external/{submodule}")
            failures += 1
            continue
        if args.check:
            print(f"external/{submodule}: {tree.state()} ({len(tree.patches)} patches)")
        elif args.reset:
            failures += tree.reset()
        else:
            failures += tree.apply()
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
