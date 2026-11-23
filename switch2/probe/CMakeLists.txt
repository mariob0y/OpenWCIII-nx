# Warsmash Switch 2 probe A, built with the Switch 2 toolchain (devkitPro's MSYS2 shell):
#   cmake -S probe -B build-probe -G Ninja -DCMAKE_TOOLCHAIN_FILE=/opt/devkitpro/libounce/cmake/Switch2.cmake
#   ninja -C build-probe
# Before it goes onto a console:
#   python scripts/check_switch2_safety.py --probe build-probe/ws_probe.elf
cmake_minimum_required(VERSION 3.13)
project(ws_switch2_probe CXX)

if(NOT NINTENDO_SWITCH2)
    message(FATAL_ERROR "The probe is for the Switch 2 toolchain only (libounce/cmake/Switch2.cmake)")
endif()

add_executable(ws_probe probe.cpp)
target_compile_options(ws_probe PRIVATE -Wall -Wextra -O2)
target_link_libraries(ws_probe PRIVATE "${DEVKITPRO}/portlibs/switch2/lib/libEGL.a")
# The guard: no service, mount or SD-card write outside /switch/warsmash*/.
include(${CMAKE_CURRENT_LIST_DIR}/../cmake/switch2_guard.cmake)
ws_switch2_guard(ws_probe ADD_SOURCE)

nx_generate_nacp(ws_probe.nacp NAME "Warsmash probe A" AUTHOR "warsmash-nx" VERSION "0.1")
nx_create_nro(ws_probe NACP ws_probe.nacp)
