#!/bin/bash
# Probe B, Java half: compile, run the PC JVM reference, transpile to C++ with Clearwing.
#   ../jdk-overlay/  our replacements for Clearwing runtime classes (compiled against Clearwing's runtime)
#   java/     the probe (+ Gson 2.10 and Warsmash's RuntimeTypeAdapterFactory for the w2 step). The PC reference compiles it for Java 17 against the JDK; the console build
#             compiles it to Java 8 bytecode (as Clearwing expects) against what the console has:
#             jdk-overlay + Clearwing's runtime jar (+ ../javac-stubs: lambda symbols, never transpiled)
# Output: classes-pc/ + pc-out/probe-b-pc.log (reference), classes-cw/, gen/src (C++)
set -euo pipefail
cd "$(dirname "$0")"
J="${JAVA_HOME:?set JAVA_HOME to a JDK 17}/bin"
CW="$(cd ../external/clearwing-vm && pwd -W)"  # patched: ../scripts/apply_patches.py
RT=$CW/runtime/build/libs/runtime-3.1.2.jar
TRANSPILER=$CW/transpiler/build/libs/transpiler-3.1.2.jar
GSON=../third_party/jars/gson-2.10.jar
RTAF=../../core/src/com/google/gson/typeadapters/RuntimeTypeAdapterFactory.java
# The transpiler jar carries its own copy of the runtime classes: a runtime rebuilt without
# :transpiler:shadowJar compiles the probe against new classes and transpiles the old ones.
if [ "$RT" -nt "$TRANSPILER" ]; then
	echo "runtime jar is newer than the transpiler jar: run ./gradlew --offline :runtime:jar :transpiler:shadowJar" >&2
	exit 1
fi

rm -rf classes-pc classes-cw classes-stubs gen
mkdir -p classes-pc classes-cw classes-stubs
"$J/javac" --release 17 -nowarn -encoding UTF-8 -cp "$GSON" -d classes-pc $(find java -name "*.java") "$RTAF"
if [ "${1:-}" != "--no-pc" ]; then
	rm -f pc-out/probe-b-pc.log
	# US locale: on this PC String.format would print 3,142 (the console prints 3.142).
	"$J/java" -Xmx4g -Duser.language=en -Duser.country=US -Dprobeb.dir=pc-out -cp "classes-pc;$GSON" wsnx.probeb.ProbeB > /dev/null
	echo "PC reference: pc-out/probe-b-pc.log ($(grep -c '^R ' pc-out/probe-b-pc.log) result lines)"
fi
"$J/javac" -source 8 -target 8 -Xlint:-options -nowarn -encoding UTF-8 \
	-bootclasspath "$RT" -d classes-cw $(find ../jdk-overlay -name "*.java")
"$J/javac" -source 8 -target 8 -Xlint:-options -nowarn -encoding UTF-8 \
	-bootclasspath "$RT" -d classes-stubs $(find ../javac-stubs -name "*.java")
"$J/javac" -source 8 -target 8 -Xlint:-options -nowarn -encoding UTF-8 \
	-bootclasspath "$(pwd -W)/classes-cw;$(pwd -W)/classes-stubs;$RT" -cp "$GSON" -d classes-cw $(find java -name "*.java") "$RTAF"
# -Duser.*: the transpiler prints float constants with the default locale (a comma on this PC).
"$J/java" -Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US -jar "$TRANSPILER" --input classes-cw "$GSON" --source java ../jdk-overlay \
	--output gen --config config.json --main wsnx.probeb.ProbeB
echo "C++: $(find gen/src -name '*.cpp' | wc -l) files"
