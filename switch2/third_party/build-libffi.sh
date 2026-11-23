#!/bin/bash
# libffi for the Switch 2 (Clearwing's Reflection.cpp calls methods through it). Builds into
# third_party/prefix-switch2, leaving the devkitPro install alone. Run in devkitPro's MSYS2:
# (fetch.sh runs it after downloading the source into dl/)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
PREFIX=$HERE/prefix-switch2
source /opt/devkitpro/switch2vars.sh
CPU=$(sed -n 's/^SWITCH2_CPU[ \t]*:=[ \t]*//p' /opt/devkitpro/libounce/switch2_cpu.mk)
rm -rf "$HERE/build-libffi"; mkdir -p "$HERE/build-libffi"; cd "$HERE/build-libffi"
tar -xzf "$HERE/dl/libffi-3.4.6.tar.gz"
cd libffi-3.4.6
CFLAGS="-O2 $CPU -mtp=soft -ftls-model=local-exec -fPIE -D__SWITCH__ -D__SWITCH2__" \
	./configure --host=aarch64-none-elf --prefix="$PREFIX" --disable-shared --enable-static \
	--disable-docs --disable-multi-os-directory --disable-exec-static-tramp > ../configure.log 2>&1
make -j8 > ../make.log 2>&1
make install > ../install.log 2>&1
echo "libffi installed: $(ls "$PREFIX"/lib/libffi.a)"
