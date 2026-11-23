"""Refuse a Switch 2 ELF that could write to the console's own storage, or that runs without the guard.

Under kaiHEN a homebrew program may open any system service and the system NAND, so nothing on
the console stops a program from writing there: only the program's own code does. Three checks:

1. Names. Every function the ELF defines in a vetted family (file systems, settings, content and
   installation, the security processor, power, applets, the service manager, reports, kaiHEN's
   own API, ...) must be in ALLOW, read-only or SD-card-only uses checked by reading libounce's
   source. A new library function in one of those families fails the check until someone has
   looked at it.
2. The guard (src/platform/switch2/platform_guard.cpp) is linked: every wrapped function has its
   `__wrap_` twin. It refuses at run time what the names cannot show: a service asked for by
   hand, a mount other than the SD card, a write outside /switch/warsmash*/.
3. The guard is in the path: the libraries' own callers (the SD card's stdio layer, the service
   initialisers) branch to the `__wrap_` functions, never straight to the real ones.
4. The game has its own crash handler (platform_crash.cpp), so a fault is not filed by the system.

    python scripts/check_switch2_safety.py build-switch2/warsmash.elf
    python scripts/check_switch2_safety.py --probe build-probe/ws_probe.elf
    python scripts/check_switch2_safety.py --self-test
"""
from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys

# Service families whose functions are vetted one by one.
# psc: libounce's pscm*/pscPm* only; a bare "psc" also caught FreeType's PostScript cmaps module
# (pscmaps_interface, pscmaps_services), 2026-11-21.
FAMILIES = re.compile(
    r"^(fs[A-Z]|fsdev|set[A-Z]|setsys|setcal|ncm|ns[A-Z]|nsam|es[A-Z]|spl[A-Z]|bpc|pcv|clkrst|apm|"
    r"lr[A-Z]|pm[A-Z]|pmshell|pmdmnt|ldr[A-Z]|fatal|psc[A-Z]|pscm[A-Z]|capsa|capssu|caps[A-Z]|"
    r"account|acc[A-Z]|friends|nifm|sslc|i2c|gpio|pinmux|tc[A-Z]|ts[A-Z]|fan|"
    r"applet|libApplet|error[A-Z]|swkbd|web[A-Z]|mii[A-Z]|sm[A-Z]|time[A-Z]|psm|spsm|lbl|audctl|"
    r"hidsys|hiddbg|hidbus|hidSetNmouseButtonConfig|nfc|nfp|btm|usb[A-Z]|wlan|ldn|pctl|erpt|eupld|prepo|bcat|olsc|"
    r"kaihen|jit[A-Z]|ro[A-Z]|vi2Set|viSet|"
    r"svcCallSecureMonitor|svcMapPhysicalMemory|svcSetProcessMemory|svcWriteDebugProcessMemory|"
    r"svcCreateProcess|svcStartProcess|svcMapProcessMemory|svcKernelDebug|svcChangeKernelTraceState|"
    r"svcMapDeviceAddressSpace|svcSetHardwareBreakPoint|svcUnknown)")

# Read-only, SD-card-only or runtime-only uses, checked by reading libounce's source.
ALLOW = {
    # The SD card, through stdio (the guard limits the writes to /switch/warsmash*/).
    "fsInitialize", "fsExit", "fsOpenSdCardFileSystem",
    "fsFsClose", "fsFsCreateDirectory", "fsFsCreateFile", "fsFsDeleteDirectory", "fsFsDeleteFile",
    "fsFsGetEntryType", "fsFsGetFileTimeStampRaw", "fsFsGetFreeSpace", "fsFsGetTotalSpace",
    "fsFsOpenDirectory", "fsFsOpenFile", "fsFsRenameDirectory", "fsFsRenameFile",
    "fsFsCommit", "fsFsCleanDirectoryRecursively", "fsFsDeleteDirectoryRecursively",
    "fsFsSetConcatenationFileAttribute",
    "fsFileClose", "fsFileFlush", "fsFileGetSize", "fsFileRead", "fsFileSetSize", "fsFileWrite",
    "fsDirClose", "fsDirRead", "fsDirGetEntryCount",
    # The program's own RomFS (read-only).
    "fsOpenDataStorageByCurrentProcess", "fsStorageClose", "fsStorageRead", "fsStorageGetSize",
    "fsdevMountDevice", "fsdevMountSdmc", "fsdevTranslatePath", "fsdevUnmountAll",
    "fsdevUnmountDevice", "fsdevGetDeviceFileSystem", "fsdevCommitDevice", "fsdevSetConcatenationFileAttribute",
    # Reads: firmware version, clock rates (opt-in by an SD flag), language.
    "setInitialize", "setExit", "setGetSystemLanguage", "setMakeLanguageCode", "setGetLanguageCode",
    "setGetAvailableLanguageCodeCount", "setGetAvailableLanguageCodes",
    # newlib's stdio device table, not a service.
    "setDefaultDevice",
    "setsysInitialize", "setsysExit", "setsysGetFirmwareVersion",
    "pcvInitialize", "pcvExit", "pcvGetClockRate",
    "clkrstInitialize", "clkrstExit", "clkrstOpenSession", "clkrstCloseSession", "clkrstGetClockRate",
    # Runtime performance mode; libnx's applet start-up sets it and its exit puts it back.
    "apmInitialize", "apmExit", "apmSetPerformanceConfiguration", "apmGetPerformanceConfiguration",
    "apmGetPerformanceMode",
    # The application's own applet session: messages, focus, the loading boost (reset at exit).
    "appletInitialize", "appletExit", "appletMainLoop", "appletProcessMessage", "appletGetMessage",
    "appletNotifyRunning", "appletGetAppletResourceUserId", "appletGetDefaultDisplayResolution",
    "appletGetGraphicsSupervisor", "appletGetOperationMode", "appletGetThemeColorType",
    "appletSetFocusHandlingMode", "appletSetGamePlayRecordingState",
    "appletSetCpuBoostMode", "appletCancelCpuBoostMode",
    # ICommonStateGetter 61: an event that fires when the TV's output resolution changes, the
    # read-only twin of appletGetDefaultDisplayResolution. Linked by nwindowSetModeDimensions, which
    # calls it only for a 0x0 ("follow the TV") size; the game passes sizes for both modes.
    "appletGetDefaultDisplayResolutionChangeEvent",
    # The software keyboard (character names), Nintendo's library applet, without its dictionary.
    "swkbdCreate", "swkbdClose", "swkbdShow", "swkbdConfigMakePresetDefault",
    "swkbdConfigSetHeaderText", "swkbdConfigSetGuideText", "swkbdConfigSetInitialText",
    "appletCreateLibraryApplet", "appletGetLibraryAppletInfo",
    "appletHolderClose", "appletHolderGetExitReason", "appletHolderGetLibraryAppletInfo",
    "appletHolderGetPopInteractiveOutDataEvent", "appletHolderJoin", "appletHolderPopInteractiveOutData",
    "appletHolderPopOutData", "appletHolderPushInData", "appletHolderPushInteractiveInData",
    "appletHolderStart", "appletHolderWaitInteractiveOut",
    "appletCreateStorage", "appletCreateTransferMemoryStorage", "appletCreateHandleStorage",
    "appletCreateHandleStorageTmem", "appletStorageClose", "appletStorageCloseTmem",
    "appletStorageGetSize", "appletStorageRead", "appletStorageWrite",
    # The service manager, as a client: asking for services (through the guard), never registering.
    "smInitialize", "smExit", "smGetServiceWrapper", "smGetServiceOriginal", "smGetServiceOverride",
    "smAddOverrideHandle",
    # The clock, read.
    "timeInitialize", "timeExit", "timeGetCurrentTime", "timeGetServiceSession_SystemClock",
    "timeToCalendarTimeWithMyRule", "timeToPosixTimeWithMyRule",
    # The default window's 120 Hz and VRR requests, withdrawn as the window closes.
    "vi2SetDisplay120HzCapability", "vi2SetDisplayVrrCapability",
    # SVC 0x80: a memory attribute on the program's own GPU memory (libounce's gsv, cpu_window).
    "svcUnknown80",
}
LOCAL_SUFFIX = re.compile(r"\.(localalias|part\.\d+|constprop\.\d+|isra\.\d+|cold)(\.\d+)?$")
FSDEV_INTERNAL = re.compile(r"^(fsdev_[a-z_]+|fsdevFindDevice)$")

# What the guard wraps (cmake/switch2_guard.cmake): each needs its __wrap_ twin.
WRAPPED = ("smGetServiceWrapper", "smGetServiceOriginal", "fsdevMountDevice",
           "fsFsCreateFile", "fsFsDeleteFile", "fsFsCreateDirectory", "fsFsDeleteDirectory",
           "fsFsDeleteDirectoryRecursively", "fsFsCleanDirectoryRecursively",
           "fsFsRenameFile", "fsFsRenameDirectory", "fsFsOpenFile", "fsFsSetConcatenationFileAttribute")

# Library callers whose branches must reach the guard: the SD card's stdio layer and the service
# initialisers. Those absent from an ELF are skipped; at least the stdio ones must be there.
CALLERS = ("fsdev_open", "fsdev_unlink", "fsdev_mkdir", "fsdev_rmdir", "fsdev_rename",
           "fsInitialize", "hidInitialize", "appletInitialize", "setsysInitialize", "timeInitialize",
           "audoutInitialize", "nvInitialize", "vi2Initialize", "gsvInitialize", "socketInitialize")
REQUIRED_CALLERS = ("fsdev_open", "fsInitialize")

# Functions a NAND installer or dumper calls (sphaira, an installer, 2026-11), each of which must
# be refused; and functions an ordinary game calls, each of which must pass. For --self-test.
MUST_REFUSE = ("fsOpenBisStorage", "fsOpenBisFileSystem", "fsOpenContentStorageFileSystem",
               "fsCreateSaveDataFileSystem", "fsOpen_SystemSaveData", "fsdevMountSystemSaveData",
               "ncmContentStorageWritePlaceHolder", "ncmContentStorageRegister", "nsInstallApplication",
               "esImportTicket", "splSetConfig", "bpcRebootSystem", "setsysSetRegionCode",
               "smRegisterService", "errorApplicationShow", "timeSetCurrentTime", "kaihenJitMap",
               "roLoadNro", "pctlSetPinCode", "fatalThrow", "erptCreateReport", "psmGetChargerType",
               "lblSetCurrentBrightnessSetting", "hidsysSetNotificationLedPattern",
               "appletRequestExitToSelf", "svcCallSecureMonitor", "svcUnknown81", "pscmGetPmModule",
               # Joy-Con mouse button remapping: whether the system keeps it is not established,
               # so the port never calls it (it reads the pad's own buttons for clicks instead).
               "hidSetNmouseButtonConfigJoyLeftJcms", "hidSetNmouseButtonConfigJoyRightJcms",
               "hidSetNmouseButtonConfigStandardMouse")
MUST_PASS = ("fsFsOpenFile", "fsdevMountSdmc", "appletMainLoop", "smGetServiceWrapper",
             "swkbdShow", "clkrstGetClockRate", "romfsMountSelf", "hidInitialize", "nvIoctl",
             "hidInitializeNmouse", "hidGetNmouseStatesWithDelta", "padPointerUpdate",
             "hidInitializeMouse", "hidGetMouseStates", "pscmaps_interface")


def find_tool(name: str, env: str) -> str:
    for candidate in (os.environ.get(env), f"aarch64-none-elf-{name}",
                      f"/opt/devkitpro/devkitA64/bin/aarch64-none-elf-{name}",
                      rf"C:\devkitPro\devkitA64\bin\aarch64-none-elf-{name}.exe"):
        if candidate and (shutil.which(candidate) or os.path.exists(candidate)):
            return candidate
    sys.exit(f"check_switch2_safety: aarch64-none-elf-{name} not found (set {env})")


def refused(names) -> list:
    vetted = sorted(n for n in names if FAMILIES.match(n) and not FSDEV_INTERNAL.match(n))
    return [n for n in vetted if n not in ALLOW], len(vetted)


def self_test() -> int:
    bad, _ = refused(MUST_REFUSE)
    leak = [n for n in MUST_REFUSE if n not in bad]
    noisy, _ = refused(MUST_PASS)
    for name in leak:
        print(f"self-test: {name} is not refused")
    for name in noisy:
        print(f"self-test: {name} is refused")
    print("check_switch2_safety: self-test " + ("FAILED" if leak or noisy else "OK"))
    return 1 if leak or noisy else 0


def unguarded_branches(elf: str, defined: set) -> tuple:
    """Branches from the library callers straight to a wrapped function, and the callers read."""
    objdump = find_tool("objdump", "WS_OBJDUMP")
    branch = re.compile(r"\sb(?:l)?\s+[0-9a-f]+ <([^>+]+)")
    bypass, read = [], []
    for caller in CALLERS:
        if caller not in defined:
            continue
        out = subprocess.run([objdump, "-d", "--no-show-raw-insn", f"--disassemble={caller}", elf],
                             capture_output=True, text=True, check=True).stdout
        read.append(caller)
        for target in branch.findall(out):
            if target in WRAPPED:
                bypass.append(f"{caller} -> {target}")
    return bypass, read


def main() -> int:
    args = sys.argv[1:]
    if args == ["--self-test"]:
        return self_test()
    probe = "--probe" in args
    args = [a for a in args if a != "--probe"]
    if len(args) != 1:
        print(__doc__)
        return 2
    elf = args[0]
    out = subprocess.run([find_tool("nm", "WS_NM"), "--defined-only", elf], capture_output=True, text=True,
                         check=True).stdout
    names = {LOCAL_SUFFIX.sub("", line.split()[-1]) for line in out.splitlines() if len(line.split()) >= 3}
    kinds = {line.split()[-1]: line.split()[-2] for line in out.splitlines() if len(line.split()) >= 3}
    failures = []

    bad, vetted = refused(names)
    if bad:
        failures.append("functions not vetted for the Switch 2:\n" + "\n".join(f"  {n}" for n in bad))

    missing = [f"__wrap_{n}" for n in WRAPPED if f"__wrap_{n}" not in names]
    if missing:
        failures.append("the guard is not linked (cmake/switch2_guard.cmake), missing:\n" +
                        "\n".join(f"  {n}" for n in missing))

    # The program's own crash handler (platform_crash.cpp), so a fault never reaches the system's
    # crash reporter. Required of probes too: probe A's first console run crashed without one
    # (2026-11-19), so the system filed the report this rule exists to avoid.
    if "__libnx_exception_handler" not in names:
        failures.append("no crash handler (__libnx_exception_handler, platform_crash.cpp): a crash would "
                        "be filed by the system")
    # libounce's diagAbortWithResult is weak and ends in svcBreak(Panic), i.e. the system's crash
    # reporting; platform_crash.cpp replaces it. A weak ("W") definition is libounce's own. Probe A
    # died through it twice (2026-11-19) when its text console came back after EGL.
    if kinds.get("diagAbortWithResult") != "T":
        failures.append("diagAbortWithResult is libounce's (svcBreak) and not platform_crash.cpp's: an abort "
                        "would be filed by the system")
    # newlib's abort() ends the process without a word (std::terminate, assert, a corrupt heap);
    # platform_crash.cpp's __wrap_abort (-Wl,--wrap=abort) writes crash.txt first. Probe B died
    # through it with no trace (2026-11-20).
    if "__wrap_abort" not in names:
        failures.append("abort() is not wrapped (__wrap_abort, cmake/switch2_guard.cmake): an uncaught C++ "
                        "exception or corrupt heap would end the program without a report")

    bypass, read = unguarded_branches(elf, names)
    absent = [c for c in REQUIRED_CALLERS if c not in read]
    if absent:
        failures.append("callers to verify are not in the ELF: " + ", ".join(absent))
    if bypass:
        failures.append("calls that go around the guard:\n" + "\n".join(f"  {b}" for b in bypass))

    if failures:
        print(f"check_switch2_safety: REFUSED {elf}:")
        for failure in failures:
            print(failure)
        return 1
    print(f"check_switch2_safety: OK, {vetted} service functions vetted, guard linked and in the path "
          f"of {len(read)} callers: {elf}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
