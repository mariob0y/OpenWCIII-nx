"""Unity (jumbo) build of the transpiled C++, so the console build runs ~250 compiler processes
instead of ~7,000 (process start-up dominates a build of thousands of small files on Windows).

  python -I unity.py rename  build/gen/src              per-file names for the class metadata
  python -I unity.py batches build/gen/src build/unity  write the batch files CMake compiles

rename: every transpiled class file defines the same file-local statics (vtableEntries, methods,
fields, interfaces, innerClasses, initAnnotations), which clash once several files share one
translation unit. Each gets a suffix made from the file's path, so the output is the same on
every run (build-java.sh compares the new transpile with the old one and gives identical files
their old timestamps back; run rename before that comparison). Only the declaration and the
`= (intptr_t) name,` reference of the class record are renamed: the same words also occur as
locals, struct fields, string literals and include paths, all of which are left alone.

batches: build/unity/<package>__<n>.cpp, each #including about BATCH_BYTES of one package's
transpiled class files, in name order (a new file only reshuffles its own package). Files that
are not transpiled classes (the runtime's hand-written C++ at the top of build/gen/src, asmjit)
and files bigger than BATCH_BYTES get a batch of their own. A batch file is only rewritten when
its text changes, and stale ones are removed.
"""
import hashlib
import os
import re
import sys

BATCH_BYTES = 1_500_000
NAMES = ("vtableEntries", "methods", "fields", "interfaces", "innerClasses", "initAnnotations")
DECL = re.compile(r"^(static (?:VtableEntry|FieldMetadata|MethodMetadata|jclass) )(vtableEntries|methods|fields|interfaces|innerClasses)(\[\])"
                  r"|^(static void )(initAnnotations)(\(jcontext ctx\))", re.M)
REF = re.compile(r"(= \(intptr_t\) )(" + "|".join(NAMES) + r")(,)")
MARK = "\nstatic bool registered_"


def transpiled_class(root, rel, text=None):
    """A transpiled class file (batchable), as opposed to the runtime's hand-written C++."""
    top = rel.split("/", 1)[0]
    if "/" not in rel or top == "asmjit":
        return False
    if text is None:
        with open(os.path.join(root, rel), encoding="utf-8", errors="replace") as f:
            text = f.read()
    return MARK in text


def cpp_files(root):
    for d, _, files in os.walk(root):
        for f in files:
            if f.endswith(".cpp"):
                yield os.path.relpath(os.path.join(d, f), root).replace("\\", "/")


def rename(root):
    done = skipped = 0
    for rel in cpp_files(root):
        path = os.path.join(root, rel)
        with open(path, encoding="utf-8", newline="") as f:
            text = f.read()
        if not transpiled_class(root, rel, text):
            continue
        suffix = "_u" + hashlib.md5(rel.encode()).hexdigest()[:10]
        if suffix in text:
            skipped += 1  # already renamed (rename run twice on the same output)
            continue
        declared = set()

        def decl(m):
            if m.group(2):
                declared.add(m.group(2))
                return m.group(1) + m.group(2) + suffix + m.group(3)
            declared.add(m.group(5))
            return m.group(4) + m.group(5) + suffix + m.group(6)

        text, nd = DECL.subn(decl, text)
        referenced = set()

        def ref(m):
            if m.group(2) not in declared:
                return m.group(0)  # not this file's static (cannot happen today; leave it)
            referenced.add(m.group(2))
            return m.group(1) + m.group(2) + suffix + m.group(3)

        text, nr = REF.subn(ref, text)
        if declared != referenced:
            sys.exit(f"unity.py rename: {rel}: declared {sorted(declared)} but referenced {sorted(referenced)}")
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(text)
        done += 1
    print(f"unity.py rename: {done} class files renamed" + (f", {skipped} already were" if skipped else ""))


def batches(root, out):
    by_package = {}
    solo = []
    for rel in sorted(cpp_files(root)):
        size = os.path.getsize(os.path.join(root, rel))
        if transpiled_class(root, rel) and size < BATCH_BYTES:
            by_package.setdefault(rel.rsplit("/", 1)[0], []).append((rel, size))
        else:
            solo.append(rel)
    wanted = {}
    for package, files in by_package.items():
        stem = package.replace("/", "_")
        group, total, n = [], 0, 0
        for rel, size in files:
            group.append(rel)
            total += size
            if total >= BATCH_BYTES:
                wanted[f"{stem}__{n}.cpp"] = group
                group, total, n = [], 0, n + 1
        if group:
            wanted[f"{stem}__{n}.cpp"] = group
    for rel in solo:
        wanted["solo__" + rel[:-4].replace("/", "_") + ".cpp"] = [rel]
    os.makedirs(out, exist_ok=True)
    written = 0
    for name, group in wanted.items():
        text = "// generated by warsmash/unity.py\n" + "".join(f'#include "{rel}"\n' for rel in group)
        path = os.path.join(out, name)
        try:
            with open(path, encoding="utf-8") as f:
                if f.read() == text:
                    continue
        except FileNotFoundError:
            pass
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        written += 1
    stale = [f for f in os.listdir(out) if f.endswith(".cpp") and f not in wanted]
    for f in stale:
        os.remove(os.path.join(out, f))
    print(f"unity.py batches: {len(wanted)} translation units ({len(wanted) - len(solo)} batches, {len(solo)} single files); "
          f"{written} written, {len(stale)} removed")


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "rename":
        rename(sys.argv[2])
    elif len(sys.argv) == 4 and sys.argv[1] == "batches":
        batches(sys.argv[2], sys.argv[3])
    else:
        sys.exit(__doc__)
