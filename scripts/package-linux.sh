#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
"$(dirname "$0")/build.sh"
mkdir -p build/native
if [[ ! -x build/runtime-min/bin/java ]]; then
  jlink --add-modules java.desktop --strip-debug --no-header-files --no-man-pages --compress=2 --output build/runtime-min
fi
if [[ -e build/native/BattleShipReborn ]]; then
  echo "Existing generated app image will be replaced: build/native/BattleShipReborn" >&2
  rm -rf build/native/BattleShipReborn
fi
jpackage --type app-image \
  --name BattleShipReborn \
  --app-version 1.0.0 \
  --vendor 'BattleShip Reborn' \
  --runtime-image build/runtime-min \
  --input build/distribution \
  --main-jar BattleShip-Reborn.jar \
  --dest build/native \
  --java-options '-Dfile.encoding=UTF-8'
printf 'Linux application: %s\n' "$(pwd)/build/native/BattleShipReborn/bin/BattleShipReborn"
