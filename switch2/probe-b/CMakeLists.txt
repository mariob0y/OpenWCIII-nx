# Warsmash Switch 2 probe B: Java (wsnx.probeb.ProbeB) converted to C++ by Clearwing VM 3.1.2.
#   ./build-java.sh                                  compile, PC reference run, transpile into gen/src
#   cmake -S probe-b -B build-probe-b -G "Unix Makefiles" -DCMAKE_TOOLCHAIN_FILE=/opt/devkitpro/libounce/cmake/Switch2.cmake
#   make -C build-probe-b -j8
# Before it goes onto a console:
#   python scripts/check_switch2_safety.py --probe build-probe-b/ws_probe_b.elf
cmake_minimum_required(VERSION 3.13)
project(ws_switch2_probe_b C CXX)
# ~700 objects: the link line is longer than Windows allows. The Makefile generator can pass just
# the objects in a response file (they are relative paths; MSYS does not translate /c/... paths
# inside one for the Windows-native compiler, so everything else stays on the command line).
set(CMAKE_CXX_USE_RESPONSE_FILE_FOR_OBJECTS 1)
set(CMAKE_CXX_USE_RESPONSE_FILE_FOR_LIBRARIES 0)

if(NOT NINTENDO_SWITCH2)
    message(FATAL_ERROR "Probe B is for the Switch 2 toolchain only (libounce/cmake/Switch2.cmake)")
endif()

set(GEN "${CMAKE_CURRENT_SOURCE_DIR}/gen/src")
if(NOT EXISTS "${GEN}/main.cpp")
    message(FATAL_ERROR "No transpiled sources: run probe-b/build-java.sh first")
endif()

# libffi (built by third_party/build-libffi.sh) and zziplib (devkitPro's switch-zziplib package,
# unpacked, not installed) live in the project, not in the devkitPro install.
set(TP "${CMAKE_CURRENT_SOURCE_DIR}/../third_party")
set(ZZIP_ROOT "${TP}/pkg-zziplib/opt/devkitpro/portlibs/switch")

file(GLOB_RECURSE GEN_SOURCES CONFIGURE_DEPENDS "${GEN}/*.cpp")
add_executable(ws_probe_b ${GEN_SOURCES})
set_target_properties(ws_probe_b PROPERTIES CXX_STANDARD 20 CXX_STANDARD_REQUIRED ON)
target_include_directories(ws_probe_b PRIVATE "${GEN}" "${TP}/prefix-switch2/include" "${ZZIP_ROOT}/include"
    "${DEVKITPRO}/portlibs/switch/include")
# Proxy.cpp's asmjit: no JIT on the console (no executable memory), so java.lang.reflect.Proxy is
# unavailable; everything else of asmjit is only compiled.
target_compile_definitions(ws_probe_b PRIVATE ASMJIT_STATIC ASMJIT_NO_JIT)
# Java semantics the C++ compiler would otherwise not keep: int arithmetic wraps (-fwrapv), and
# a*b+c is two roundings, never one fused multiply-add (-ffp-contract=off; GCC fuses by default,
# which changed probe B's float simulation loop on 2026-11-19).
target_compile_options(ws_probe_b PRIVATE -O2 -fwrapv -ffp-contract=off -Wno-volatile -Wno-return-type -Wno-attributes)
target_link_libraries(ws_probe_b PRIVATE
    "${TP}/prefix-switch2/lib/libffi.a"
    "${ZZIP_ROOT}/lib/libzzip.a"
    "${DEVKITPRO}/portlibs/switch/lib/libz.a")

# The guard: no service, mount or SD-card write outside /switch/warsmash*/; and the crash handler.
include(${CMAKE_CURRENT_LIST_DIR}/../cmake/switch2_guard.cmake)
ws_switch2_guard(ws_probe_b ADD_SOURCE)

nx_generate_nacp(ws_probe_b.nacp NAME "Warsmash probe B" AUTHOR "warsmash-nx" VERSION "0.1")
nx_create_nro(ws_probe_b NACP ws_probe_b.nacp)
