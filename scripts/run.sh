#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/classes
find src/main/java -name '*.java' -print0 | xargs -0 javac --release 21 -encoding UTF-8 -d build/classes
java -cp build/classes com.battleship.Main "$@"
