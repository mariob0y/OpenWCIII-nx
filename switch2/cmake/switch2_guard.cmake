# Switch 2: route every service request, mount and SD-card write of an executable through
# src/platform/switch2/platform_guard.cpp, which refuses what a homebrew must never do on the
# console (its NAND, settings, installs, reports). --require-defined makes the link fail if the
# guard is not in the executable, so a build cannot quietly go without it.
#
#
# The crash handler (platform_crash.cpp) comes with it: libounce refers to
# __libnx_exception_handler only weakly, so --require-defined makes a build without it fail too.
#
#   ws_switch2_guard(<target>)                 guard and crash handler are already in a linked library
#   ws_switch2_guard(<target> ADD_SOURCE)      compile both into the target itself
set(WS_SWITCH2_GUARD_SOURCE "${CMAKE_CURRENT_LIST_DIR}/../src/platform/switch2/platform_guard.cpp")
set(WS_SWITCH2_CRASH_SOURCE "${CMAKE_CURRENT_LIST_DIR}/../src/platform/switch2/platform_crash.cpp")
set(WS_SWITCH2_GUARD_WRAPPED
    smGetServiceWrapper smGetServiceOriginal fsdevMountDevice
    fsFsCreateFile fsFsDeleteFile fsFsCreateDirectory fsFsDeleteDirectory
    fsFsDeleteDirectoryRecursively fsFsCleanDirectoryRecursively
    fsFsRenameFile fsFsRenameDirectory fsFsOpenFile fsFsSetConcatenationFileAttribute)

function(ws_switch2_guard target)
    if(NOT NINTENDO_SWITCH2)
        return()
    endif()
    if("ADD_SOURCE" IN_LIST ARGN)
        target_sources(${target} PRIVATE "${WS_SWITCH2_GUARD_SOURCE}" "${WS_SWITCH2_CRASH_SOURCE}")
    endif()
    target_link_options(${target} PRIVATE "-Wl,--require-defined=__libnx_exception_handler")
    # abort() (newlib's ends the process quietly: an uncaught C++ exception via std::terminate, a
    # failed assert, malloc finding its heap corrupt) goes to platform_crash.cpp's __wrap_abort,
    # which writes crash.txt first. probe B died that way without a trace (2026-11-20).
    target_link_options(${target} PRIVATE "-Wl,--wrap=abort" "-Wl,--require-defined=__wrap_abort")
    foreach(_ws_fn IN LISTS WS_SWITCH2_GUARD_WRAPPED)
        target_link_options(${target} PRIVATE "-Wl,--wrap=${_ws_fn}" "-Wl,--require-defined=__wrap_${_ws_fn}")
    endforeach()
endfunction()
