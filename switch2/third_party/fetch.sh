#!/bin/bash
# Third-party code the console build uses, none of it part of this repository: downloaded, checked
# against the SHA-256 below, then unpacked or built here (everything lands in ignored folders).
#   jars/            libGDX, Gson, ANTLR runtime, RegExodus (Maven Central)
#   dl/              libffi source (GitHub) and devkitPro's switch-zziplib package (through pacman)
#   pkg-zziplib/     the zziplib package unpacked (not installed into devkitPro)
#   prefix-switch2/  libffi built for the Switch 2 (build-libffi.sh)
# Run in devkitPro's MSYS2 shell (pacman, zstd and the Switch 2 toolchain):
#   C:/devkitPro/msys2/usr/bin/bash.exe -lc "bash /c/path/to/switch2/third_party/fetch.sh"
# Files already present with the right checksum are kept.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"
mkdir -p jars dl

MAVEN=https://repo1.maven.org/maven2
JARS=(
	"com/badlogicgames/gdx/gdx/1.13.1/gdx-1.13.1.jar d5860eaf5787a4083e14183ae47da99d3e6908029ca99691947bbd0852b49264"
	"com/badlogicgames/gdx/gdx-box2d/1.13.1/gdx-box2d-1.13.1.jar f403cac91b93316c56d1667d990d935538d1d87683c5a5c504ce7736c017f479"
	"com/badlogicgames/gdx/gdx-freetype/1.13.1/gdx-freetype-1.13.1.jar fe396dcc5bd573ffeb4d292c0db7d2fa12ed1da4bdcad2862982b74ea94ab7f5"
	"com/badlogicgames/gdx-controllers/gdx-controllers-core/2.2.1/gdx-controllers-core-2.2.1.jar 81f9bfd9faeea2daa2d73f63d4366f7be549ad240e314612fddc8088a711ffb3"
	"com/badlogicgames/gdx/gdx-jnigen-loader/2.5.2/gdx-jnigen-loader-2.5.2.jar df81f23cfd6785c52d35e108eeaa3930f559d4d2770a6102e75ca7bfa6f9f27a"
	"org/antlr/antlr4-runtime/4.7/antlr4-runtime-4.7.jar 2a61943f803bbd1d0e02dffd19b92a418f83340c994346809e3b51e2231aa6c0"
	"com/google/code/gson/gson/2.10/gson-2.10.jar 0cdd163ce3598a20fc04eee71b140b24f6f2a3b35f0a499dbbdd9852e83fbfaf"
	"com/github/tommyettinger/regexodus/0.1.15/regexodus-0.1.15.jar 2227c0e54ac81710bdf64dca255340d47cf9e73291d2d001f40c68dc4322788f"
)
LIBFFI_URL=https://github.com/libffi/libffi/releases/download/v3.4.6/libffi-3.4.6.tar.gz
LIBFFI_SHA=b0dea9df23c863a7a50e825440f3ebffabd65df1497108e5d437747843895a4e
ZZIP_PKG=switch-zziplib-0.13.78-1-any.pkg.tar.zst
ZZIP_SHA=fc95fcd4cbd45b846de0ac789ee787f8ec70d2e59e051569ea010641105223e8

ok() { [ -f "$1" ] && [ "$(sha256sum "$1" | cut -c1-64)" = "$2" ]; }

check() {
	ok "$1" "$2" || { echo "checksum mismatch: $1 (expected $2)" >&2; rm -f "$1"; exit 1; }
}

for entry in "${JARS[@]}"; do
	path=${entry% *}; sha=${entry#* }; file=jars/$(basename "$path")
	ok "$file" "$sha" && continue
	echo "downloading $(basename "$path")"
	curl -fsSL -o "$file" "$MAVEN/$path"
	check "$file" "$sha"
done

if ! ok dl/libffi-3.4.6.tar.gz $LIBFFI_SHA; then
	echo "downloading libffi-3.4.6.tar.gz"
	curl -fsSL -o dl/libffi-3.4.6.tar.gz $LIBFFI_URL
	check dl/libffi-3.4.6.tar.gz $LIBFFI_SHA
fi

# devkitPro's server only serves its package manager: download-only (-Sw) into dl/, nothing installed.
if ! ok dl/$ZZIP_PKG $ZZIP_SHA; then
	echo "downloading $ZZIP_PKG"
	pacman -Sw --noconfirm --cachedir "$HERE/dl" switch-zziplib > /dev/null
	check dl/$ZZIP_PKG $ZZIP_SHA
fi
if [ ! -f pkg-zziplib/.PKGINFO ]; then
	rm -rf pkg-zziplib && mkdir pkg-zziplib
	tar --zstd -xf dl/$ZZIP_PKG -C pkg-zziplib
fi

if [ ! -f prefix-switch2/lib/libffi.a ]; then
	bash "$HERE/build-libffi.sh"
fi
echo "third_party: ready"
