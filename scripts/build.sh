#!/bin/bash
# GrindrPlus Build Script (Fixed)
# Builds the Xposed module APK directly from the modernized project structure.
#
# Fix: The original script tried to copy sources from root-level paths
# (GrindrPlus.kt, hooks/*.kt, etc.) which no longer exist after the modernization
# refactor moved all sources into app/src/main/java/com/grindrplus/.
# This script now builds the project in-place using the existing Gradle wrapper.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== GrindrPlus v2.0 Build ==="
echo "Project: $PROJECT_DIR"

# Check for Java
if ! command -v java &>/dev/null; then
    echo "ERROR: Java not found. Install JDK 17+."
    echo "  Ubuntu/Debian: sudo apt install openjdk-17-jdk"
    echo "  Termux:        pkg install openjdk-17"
    echo "  Windows:       https://adoptium.net"
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
if [ "$JAVA_VER" -lt 17 ] 2>/dev/null; then
    echo "WARNING: JDK 17+ recommended (found $JAVA_VER). Build may fail."
fi

# Check for Android SDK
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "ERROR: Neither ANDROID_HOME nor ANDROID_SDK_ROOT is set."
    echo "Set it to your Android SDK path, e.g.:"
    echo "  export ANDROID_HOME=\$HOME/Android/Sdk"
    exit 1
fi

SDK_DIR="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
echo "Android SDK: $SDK_DIR"

# Write local.properties if missing
if [ ! -f "$PROJECT_DIR/local.properties" ]; then
    echo "sdk.dir=$SDK_DIR" > "$PROJECT_DIR/local.properties"
    echo "Created local.properties"
fi

# Build
cd "$PROJECT_DIR"
echo ""
echo "Running: ./gradlew assembleDebug"
./gradlew assembleDebug --no-daemon

APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
    echo ""
    echo "=== Build successful ==="
    echo "Module APK: $APK"
    echo ""
    echo "Install options:"
    echo "  LSPosed (root):  adb install $APK"
    echo "  LSPatch (no-root): lspatch --manager-mode Grindr_v26.x.apk --module $APK"
else
    echo "ERROR: Build completed but APK not found at expected path."
    exit 1
fi
