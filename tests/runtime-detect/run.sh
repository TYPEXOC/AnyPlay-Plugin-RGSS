#!/bin/sh
# Host test for RuntimeDetector.java. Needs a JDK (javac). Run from anywhere.
set -e
D=$(cd "$(dirname "$0")" && pwd)
SRC="$D/../../app/src/main/java/com/hatkid/mkxpz/RuntimeDetector.java"
OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
# --release 8: the Android build compiles with Java 8 language level
javac --release 8 -d "$OUT" "$SRC"
javac -cp "$OUT" -d "$OUT" "$D/SelfTest.java"
java -cp "$OUT" SelfTest
