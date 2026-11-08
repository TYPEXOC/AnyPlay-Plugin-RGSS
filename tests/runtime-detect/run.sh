#!/bin/sh
# Host tests for RuntimeDetector.java (RGSS generation) and RuntimeProbe.java (legacy vs modern).
# Needs a JDK (javac). Run from anywhere.
set -e
D=$(cd "$(dirname "$0")" && pwd)
SRC="$D/../../app/src/main/java/com/hatkid/mkxpz"
OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
# --release 8: the Android build compiles with Java 8 language level
javac --release 8 -d "$OUT" "$SRC/RuntimeDetector.java" "$SRC/RuntimeProbe.java"
javac -cp "$OUT" -d "$OUT" "$D/SelfTest.java"
javac -cp "$OUT" -d "$OUT" "$D/ProbeTest.java"
java -cp "$OUT" SelfTest
java -cp "$OUT" com.hatkid.mkxpz.ProbeTest
