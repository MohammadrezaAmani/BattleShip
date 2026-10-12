#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/classes build/distribution
find src/main/java -name '*.java' -print0 | xargs -0 javac --release 21 -encoding UTF-8 -d build/classes
jar --create --file build/distribution/BattleShip-Reborn.jar --main-class com.battleship.Main -C build/classes com/battleship
printf 'Built: %s\n' "$(pwd)/build/distribution/BattleShip-Reborn.jar"
