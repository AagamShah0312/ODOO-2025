#!/bin/sh
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
if [ ! -d out/com/skillswap ]; then
  ./compile.sh
fi
export SKILLSWAP_STATIC="${SKILLSWAP_STATIC:-$ROOT/src/main/resources/static}"
export PORT="${PORT:-8080}"
exec java -cp out com.skillswap.SkillSwapApp
