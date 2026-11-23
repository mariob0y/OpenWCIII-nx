"""Compare probe B's console log with the PC JVM reference.

    python probe-b/compare.py path/to/probe-b.log [probe-b/pc-out/probe-b-pc.log]

Takes the last run in the (appended) console log. "R " lines must match exactly; "T " timings are
shown side by side with the console/PC ratio; the console's last "step:" line tells where a run
that did not finish stopped.
"""
import re
import sys
from pathlib import Path

STAMP = re.compile(r"^\[\s*[0-9.]+\] ")


def last_run(path):
    lines = Path(path).read_text(encoding="utf-8", errors="replace").splitlines()
    lines = [STAMP.sub("", line) for line in lines]
    starts = [i for i, line in enumerate(lines) if line.startswith("==== probe B run")]
    return lines[starts[-1]:] if starts else lines


def keyed(lines):
    # Results are "R <test> <what> <values>": the first three words name the result, numbered
    # when the same name comes again (the gc test repeats with 256 and 1024 MB).
    out, count = [], {}
    for line in lines:
        if line.startswith("R "):
            k = " ".join(line.split()[:3])
            count[k] = count.get(k, 0) + 1
            out.append((f"{k} #{count[k]}", line))
    return out


def main():
    console = last_run(sys.argv[1])
    pc = last_run(sys.argv[2] if len(sys.argv) > 2 else Path(__file__).parent / "pc-out" / "probe-b-pc.log")
    print(console[0] if console else "(empty console log)")
    steps = [line for line in console if line.startswith("step:") or line == "probe B done"]
    print("finished" if "probe B done" in console else f"DID NOT FINISH, last mark: {steps[-1] if steps else 'none'}")

    pc_results = dict(keyed(pc))
    seen = set()
    same = 0
    print("\n-- results that differ from the PC JVM --")
    for k, line in keyed(console):
        seen.add(k)
        if pc_results.get(k) == line:
            same += 1
        else:
            print(f"console: {line}\n     pc: {pc_results.get(k, '(no such result on the PC)')}")
    missing = [k for k in pc_results if k not in seen]
    print(f"\n{same} of {len(pc_results)} results identical; {len(missing)} missing on the console")
    for k in missing:
        print(f"  missing: {k}")

    def timings(lines):
        out = {}
        for line in lines:
            m = re.match(r"^T (.*?)(\d+(?:\.\d+)?) ms(.*)$", line)
            if m:
                out[m.group(1).strip()] = float(m.group(2))
        return out

    ct, pt = timings(console), timings(pc)
    print("\n-- timings (ms): console / pc --")
    for name, value in ct.items():
        ref = pt.get(name)
        ratio = f"x{value / ref:.1f}" if ref else ""
        print(f"  {name:55s} {value:9.0f} / {ref if ref is not None else '-':>7} {ratio}")
    print("\n-- info --")
    for line in console:
        if line.startswith("I ") or "FAILED" in line or line.startswith("T gc churn"):
            print("  " + line)


if __name__ == "__main__":
    main()
