#!/bin/sh
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
mkdir -p out
find src/main/java -name "*.java" > /tmp/skillswap-sources.txt
javac -encoding UTF-8 -d out @/tmp/skillswap-sources.txt
echo "Compiled to $ROOT/out"
