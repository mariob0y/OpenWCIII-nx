#!/bin/sh
# Compares core's AWT-free image decoders with the old AWT/ImageIO path, pixel by pixel.
# Old side: blp-iio-plugin + net.nikr.dds + TgaFile as of upstream f9e0aeed (git HEAD of master).
# Usage (Git Bash): sh run.sh [wc3data folder] [random count]
set -e
here=$(cd "$(dirname "$0")" && pwd)
ws=/c/VSCode_projects/warsmash
J=$ws/tools/jdk-17.0.21+9/bin
E=$ws/WarsmashModEngine
CP_LIBS="C:/VSCode_projects/warsmash/WarsmashModEngine/jars/blp-iio-plugin.jar;$(cygpath -m ~/.gradle/caches/modules-2/files-2.1/net.nikr/dds/1.0.0/3501b2cab0803fa2d3a01f24382737cc7f8115e0/dds-1.0.0.jar)"
cd "$here"
rm -rf classes old && mkdir -p classes old/com/etheller/warsmash/viewer5/handlers/tga
git -C "$E" show f9e0aeed:core/src/com/etheller/warsmash/viewer5/handlers/tga/TgaFile.java > old/com/etheller/warsmash/viewer5/handlers/tga/TgaFile.java
git -C "$E" show f9e0aeed:core/src/com/etheller/warsmash/viewer5/handlers/tga/ImageUtils.java > old/com/etheller/warsmash/viewer5/handlers/tga/ImageUtils.java
"$J/javac" -nowarn -d classes -cp "$CP_LIBS" ImageCheck.java old/com/etheller/warsmash/viewer5/handlers/tga/*.java "$E"/core/src/com/etheller/warsmash/util/image/*.java
"$J/java" -Xmx4g -cp "classes;$CP_LIBS" ImageCheck "${1:-C:/VSCode_projects/warsmash/wc3data}" "${2:-2000}"
