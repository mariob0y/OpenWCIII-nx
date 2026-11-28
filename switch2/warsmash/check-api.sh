#!/bin/bash
# warsmash-nx: does Warsmash compile against what the console has? Compiles the console modules
# (core, shared, jassparser, fdfparser + shims) with Clearwing's runtime jar and our jdk-overlay as
# the whole JDK (javac -source 8 -bootclasspath), so every JDK class or method the console lacks is
# a compile error. javac stops after flow analysis (no class files: lambdas would need
# java.lang.invoke, which Clearwing replaces). Log: build/cw-check.log; summary on stdout.
#
#   bash warsmash/check-api.sh
set -euo pipefail
cd "$(dirname "$0")"
J="${JAVA_HOME:?set JAVA_HOME to a JDK 17}/bin"
CW="$(cd ../external/clearwing-vm && pwd -W)"  # patched: ../scripts/apply_patches.py
RT=$CW/runtime/build/libs/runtime-3.1.2.jar
W=../..

rm -rf build/overlay-classes build/classes-cw-check
mkdir -p build/overlay-classes build/classes-cw-check
"$J/javac" -source 8 -target 8 -Xlint:-options -nowarn -encoding UTF-8 -bootclasspath "$RT" \
	-d build/overlay-classes $(find ../jdk-overlay -name "*.java")

find $W/core/src $W/shared/src $W/jassparser/src $W/jassparser/build/generated-src $W/fdfparser/src \
	$W/fdfparser/build/generated-src shims/src -name "*.java" > build/sources.txt
CP="$(ls ../third_party/jars/*.jar | tr '\n' ';')"
set +e
# Javac8Plus: javac taking OpenWCIII's Java 17 syntax at -source 8 (see build-java.sh).
"$J/java" --add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED ../scripts/Javac8Plus.java \
	-encoding UTF-8 -nowarn -proc:none -source 8 -target 8 -Xlint:-options -Xmaxerrs 10000 \
	-XDshould-stop.ifNoError=FLOW -XDshould-stop.ifError=FLOW \
	-bootclasspath "$(pwd -W)/build/overlay-classes;$RT" -cp "$CP" -d build/classes-cw-check @build/sources.txt \
	> build/cw-check.log 2>&1
set -e
echo "sources: $(wc -l < build/sources.txt); errors: $(grep -c 'error:' build/cw-check.log || true)"
python - <<'EOF'
import re, collections
lines = open("build/cw-check.log", encoding="utf-8", errors="replace").read().splitlines()
miss = collections.Counter(); where = collections.defaultdict(set)
for i, l in enumerate(lines):
    m = re.search(r"([^\\/]+\.java):\d+: error: (.*)", l)
    if not m:
        continue
    f, msg = m.groups()
    if msg == "cannot find symbol":
        sym = loc = ""
        for j in range(i + 1, min(i + 7, len(lines))):
            s = lines[j].strip()
            if s.startswith("symbol:"):
                sym = s[7:].strip()
            if s.startswith("location:"):
                loc = re.sub(r"variable \w+ of type ", "", s[9:].strip())
                break
        msg = sym if sym.startswith("class") else f"{re.sub(r'[(].*[)]', '(..)', sym)} @ {loc}"
    miss[msg] += 1
    where[msg].add(f)
for k, n in miss.most_common():
    files = sorted(where[k])
    print(f"{n:4} {k}   [{', '.join(files[:3])}{'...' if len(files) > 3 else ''}]")
EOF
