#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to Android SDK}"
TOOLS="$ANDROID_HOME/build-tools/35.0.0"
JAR="$ANDROID_HOME/platforms/android-35/android.jar"
BUILD=build
mkdir -p "$BUILD/generated" "$BUILD/classes" "$BUILD/dex"
"$TOOLS/aapt2" compile --dir app/src/main/res -o "$BUILD/resources.zip"
"$TOOLS/aapt2" link -o "$BUILD/base.apk" -I "$JAR" --manifest app/src/main/AndroidManifest.xml --java "$BUILD/generated" --min-sdk-version 26 --target-sdk-version 35 --version-code 4 --version-name 0.4.0-preview "$BUILD/resources.zip"
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -classpath "$JAR" -d "$BUILD/classes" $(find app/src/main/java "$BUILD/generated" -name '*.java')
java -m jdk.jartool/sun.tools.jar.Main --create --file "$BUILD/classes.jar" -C "$BUILD/classes" .
"$TOOLS/d8" --lib "$JAR" --min-api 26 --output "$BUILD/dex" "$BUILD/classes.jar"
cp "$BUILD/base.apk" "$BUILD/un-aligned.apk"
(cd "$BUILD/dex" && zip -q ../un-aligned.apk classes.dex)
"$TOOLS/zipalign" -f 4 "$BUILD/un-aligned.apk" "$BUILD/SafeBridge-unsigned.apk"
