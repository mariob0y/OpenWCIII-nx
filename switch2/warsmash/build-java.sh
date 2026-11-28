#!/bin/bash
# warsmash-nx W2, Java half: compile Warsmash's console modules, SwitchGDX's Java side and our
# launcher to Java 8 bytecode against what the console has, then transpile everything to C++.
#   console JDK = ../jdk-overlay + Clearwing's runtime jar (+ ../javac-stubs: lambda symbols only)
#   build/switchgdx.jar  SwitchGDX's classes + its switchgdx.clearwing.json (the transpiler merges
#                        any *clearwing.json it finds in an input jar)
#   build/classes-backend backend/java: our replacements for SwitchGDX classes on the Switch 2
#                        (GL30, graphics, input with the Joy-Con mouse, audio) and their inline
#                        natives (hence also a --source); after switchgdx.jar, so they win
#   build/classes        Warsmash (core, shared, jassparser, fdfparser), shims, launcher
#   build/gen            the C++ (build/gen/src)
# Inputs to the transpiler: later ones win, so switchgdx.jar comes after gdx (it replaces a few
# gdx classes: AsyncExecutor, Timer, ReflectionPool, SharedLibraryLoader...).
#
#   bash warsmash/build-java.sh            compile + transpile
#   bash warsmash/build-java.sh --javac    compile only
set -euo pipefail
cd "$(dirname "$0")"
J="${JAVA_HOME:?set JAVA_HOME to a JDK 17}/bin"
# Clearwing and SwitchGDX: pinned submodules (git submodule update --init); Clearwing with
# ../patches/clearwing applied (scripts/apply_patches.py) and its jars built.
python -I ../scripts/apply_patches.py
CW="$(cd ../external/clearwing-vm && pwd -W)"
RT=$CW/runtime/build/libs/runtime-3.1.2.jar
TRANSPILER=$CW/transpiler/build/libs/transpiler-3.1.2.jar
if [ ! -f "$RT" ] || [ ! -f "$TRANSPILER" ]; then
	# UTF-8: the runtime's sources have non-ASCII literals; javac's default on Windows is the ANSI code page.
	(cd ../external/clearwing-vm && JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 ./gradlew --offline -q :runtime:jar :transpiler:shadowJar)
fi
SGDX=../external/switch-gdx/switch-gdx
if [ ! -f ../third_party/jars/gson-2.10.jar ]; then
	echo "third-party jars missing: run third_party/fetch.sh in devkitPro's MSYS2 shell" >&2
	exit 1
fi
W=../..
JAVAC=("$J/javac" -encoding UTF-8 -nowarn -proc:none -source 8 -target 8 -Xlint:-options -Xmaxerrs 10000)
# Warsmash's own sources: OpenWCIII is written in Java 17 (var, instanceof patterns, switch rules).
# ../scripts/Javac8Plus.java is javac with those syntax-only features allowed at -source 8; they
# lower to plain Java 8 bytecode, so the upstream sources stay as they are (records excepted).
JAVAC_W=("$J/java" --add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED ../scripts/Javac8Plus.java "${JAVAC[@]:1}")

if [ "$RT" -nt "$TRANSPILER" ]; then
	echo "runtime jar is newer than the transpiler jar: run ./gradlew --offline :runtime:jar :transpiler:shadowJar" >&2
	exit 1
fi

rm -rf build/overlay-classes build/classes-stubs build/classes-switchgdx build/classes-backend build/classes build/switchgdx.jar
mkdir -p build/overlay-classes build/classes-stubs build/classes-switchgdx build/classes-backend build/classes
"${JAVAC[@]}" -bootclasspath "$RT" -d build/overlay-classes $(find ../jdk-overlay -name "*.java")
"${JAVAC[@]}" -bootclasspath "$RT" -d build/classes-stubs $(find ../javac-stubs -name "*.java")
BOOT="$(pwd -W)/build/overlay-classes;$(pwd -W)/build/classes-stubs;$RT"
LIBS=(../third_party/jars/gdx-1.13.1.jar ../third_party/jars/gdx-freetype-1.13.1.jar
	../third_party/jars/gdx-controllers-core-2.2.1.jar ../third_party/jars/regexodus-0.1.15.jar
	../third_party/jars/gdx-jnigen-loader-2.5.2.jar ../third_party/jars/antlr4-runtime-4.7.jar ../third_party/jars/gson-2.10.jar)
CP="$(printf '%s;' "${LIBS[@]}")"

"${JAVAC[@]}" -bootclasspath "$BOOT" -cp "$CP" -d build/classes-switchgdx $(find $SGDX/src -name "*.java") \
	> build/javac-switchgdx.log 2>&1 || { cat build/javac-switchgdx.log; exit 1; }
"$J/jar" cf build/switchgdx.jar -C build/classes-switchgdx . -C $SGDX/res switchgdx.clearwing.json

"${JAVAC[@]}" -bootclasspath "$BOOT" -cp "${CP}build/switchgdx.jar" -d build/classes-backend $(find backend/java -name "*.java") \
	> build/javac-backend.log 2>&1 || { cat build/javac-backend.log; exit 1; }

find $W/core/src $W/shared/src $W/jassparser/src $W/jassparser/build/generated-src $W/fdfparser/src \
	$W/fdfparser/build/generated-src shims/src launcher/src -name "*.java" \
	| grep -v -E "/core/src/com/etheller/warsmash/viewer5/handlers/w3x/ui/(CampaignPlaythroughProbe|Human02PlaythroughProbe|HumanCampaignPlaythroughProbe|CampaignPersistenceProbe|MissionResumeProbe|MissionBattleProbe|MissionEconomyProbe|(FrozenThrone|HumanExpansion|NightElf|Orc|Rexxar|Undead)[A-Za-z0-9]*Probe|MovieAudioPlayback)\.java$" \
	| grep -v -E "/core/src/com/etheller/warsmash/viewer5/handlers/w3x/simulation/sound/(CMIDISound|MidiSoundbankReader)\.java$" \
	| grep -v -E "/core/src/com/etheller/warsmash/(viewer5/handlers/w3x/simulation/save/EngineBinaryDigest|util/FileSync)\.java$" \
	> build/sources.txt
# ^ PC-only OpenWCIII code, replaced by stand-ins in shims/src: the QA probes (Java 16 desktop audits),
#   CMIDISound/MidiSoundbankReader (javax.sound.midi), MovieAudioPlayback (ffmpeg ProcessBuilder),
#   EngineBinaryDigest (hashes the engine's class files) and FileSync (FileOutputStream.getFD().sync())
"${JAVAC_W[@]}" -bootclasspath "$BOOT" -cp "${CP}build/classes-backend;build/switchgdx.jar" -d build/classes @build/sources.txt \
	> build/javac-warsmash.log 2>&1 || { tail -40 build/javac-warsmash.log; exit 1; }
echo "classes: switchgdx $(find build/classes-switchgdx -name '*.class' | wc -l), backend $(find build/classes-backend -name '*.class' | wc -l), warsmash $(find build/classes -name '*.class' | wc -l)"
[ "${1:-}" = "--javac" ] && exit 0

# The previous output is kept aside so files that come out identical get their old timestamps back
# (make then recompiles only what changed: a full build takes ~15 min).
rm -rf build/gen-prev
[ -d build/gen ] && mv build/gen build/gen-prev
# -Duser.*: the transpiler prints float constants with the default locale (a comma on this PC).
time "$J/java" -Xmx8g -Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US -jar "$TRANSPILER" \
	--input "${LIBS[@]}" build/switchgdx.jar build/classes-backend build/overlay-classes build/classes \
	--source ../jdk-overlay launcher/src backend/java \
	--output build/gen --config config.json --main wsnx.warsmash.SwitchLauncher 2>&1 | tee build/transpile.log | tail -40
# The headers backend/java's inline natives include, next to them (build/gen/src is on the object
# library's include path; a per-file include directory would change flags.make, see CMakeLists.txt).
cp -p backend/native/nx_audio.h backend/native/nxgl.h build/gen/src/
echo "C++: $(find build/gen/src -name '*.cpp' | wc -l) files, $(du -sh build/gen/src | cut -f1)"
# Unity build (unity.py): per-file names for the class metadata first, so the
# comparison below sees the same text as last time.
python -I unity.py rename build/gen/src
if [ -d build/gen-prev ]; then
	python -I - build/gen build/gen-prev <<'EOF'
import filecmp, os, sys
new, old = sys.argv[1], sys.argv[2]
same = changed = 0
for d, _, files in os.walk(new):
    for f in files:
        n = os.path.join(d, f)
        o = os.path.join(old, os.path.relpath(n, new))
        if os.path.exists(o) and filecmp.cmp(n, o, shallow=False):
            st = os.stat(o)
            os.utime(n, ns=(st.st_atime_ns, st.st_mtime_ns))
            same += 1
        else:
            changed += 1
print(f"unchanged since the last transpile: {same}, new or changed: {changed}")
EOF
	rm -rf build/gen-prev
fi
# The translation units CMake compiles: batches of build/gen/src's files (outside build/gen, so their
# timestamps survive the next transpile).
python -I unity.py batches build/gen/src build/unity
