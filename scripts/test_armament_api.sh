#!/usr/bin/env bash
# Isolated fallback check, NOT a replacement for the full Fabric Gradle build.
# Supply Java 17 and jars matching existing dependencies: Gson 2.10 / JUnit 5.10.3.
set -euo pipefail
armament_root=$(cd "$(dirname "$0")/.." && pwd)
cd "$armament_root"
armament_jdk=${ARMAMENT_JAVA_HOME:-${JAVA_HOME:-}}
armament_lib=${ARMAMENT_TEST_LIB_DIR:?Set ARMAMENT_TEST_LIB_DIR to verified test jars}
: "${armament_jdk:?Set JAVA_HOME or ARMAMENT_JAVA_HOME to JDK 17}"
armament_out=${ARMAMENT_TEST_OUTPUT:-"$armament_root/build/armament-isolated"}
armament_gson="$armament_lib/gson-2.10.jar"
armament_junit="$armament_lib/junit-platform-console-standalone-1.10.3.jar"
test -f "$armament_gson"
test -f "$armament_junit"
"$armament_jdk/bin/java" -version
"$armament_jdk/bin/javac" -version 2>&1 | rg '^javac 17\.'
mkdir -p "$armament_out"
armament_out=$(mktemp -d "$armament_out/run-XXXXXXXX")
printf 'Isolated check outputs: %s\n' "$armament_out"
mkdir -p "$armament_out/common" "$armament_out/main" "$armament_out/test" "$armament_out/reports"
find src/main/java/com/bobincraft/armament -name '*.java' ! -path '*/client/*' | sort > "$armament_out/common-sources.txt"
# This target deliberately remains the dependency-free stage-2 API check.
# The actual JglTF loader and Minecraft backend have separate validation targets.
find src/main/java/com/bobincraft/armament -name '*.java' \
    ! -path '*/client/model/glb/*' ! -path '*/client/render/fabric/*' \
    ! -path '*/client/resource/fabric/*' ! -path '*/client/debug/*' \
    ! -path '*/client/animation/gltf/*' \
    ! -path '*/client/presentation/*' ! -path '*/client/mixin/*' \
    ! -name 'ArmamentGlbClient.java' | sort > "$armament_out/main-sources.txt"
find src/test/java/com/bobincraft/armament -name '*.java' ! -name 'Glb*Test.java' ! -name 'Gltf*Test.java' ! -name 'FirstPerson*Test.java' ! -name 'PlayerHand*Test.java' | sort > "$armament_out/test-sources.txt"
# Common is compiled separately with no Minecraft/Fabric/client classes available.
"$armament_jdk/bin/javac" --release 17 -encoding UTF-8 -cp "$armament_gson" -d "$armament_out/common" @"$armament_out/common-sources.txt"
"$armament_jdk/bin/javac" --release 17 -encoding UTF-8 -cp "$armament_gson" -d "$armament_out/main" @"$armament_out/main-sources.txt"
"$armament_jdk/bin/javac" --release 17 -encoding UTF-8 -cp "$armament_out/main:$armament_gson:$armament_junit" -d "$armament_out/test" @"$armament_out/test-sources.txt"
"$armament_jdk/bin/java" -jar "$armament_junit" execute \
    --class-path "$armament_out/main:$armament_out/test:$armament_gson:$armament_root/src/test/resources" \
    --scan-class-path --include-package com.bobincraft.armament --fail-if-no-tests \
    --reports-dir "$armament_out/reports" --details summary --disable-ansi-colors
