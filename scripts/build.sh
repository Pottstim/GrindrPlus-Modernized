#!/bin/bash
# GrindrPlus Build Script
# Builds the Xposed module APK for LSPosed/LSPatch

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_DIR/build"
OUTPUT_DIR="$PROJECT_DIR/output"

echo "=== GrindrPlus Build ==="
echo "Project: $PROJECT_DIR"

# Clean previous build
rm -rf "$BUILD_DIR" "$OUTPUT_DIR"
mkdir -p "$BUILD_DIR" "$OUTPUT_DIR"

# Check for Android SDK
if [ -z "$ANDROID_HOME" ]; then
    echo "ERROR: ANDROID_HOME not set"
    echo "Set it to your Android SDK path, e.g.:"
    echo "  export ANDROID_HOME=/opt/android-sdk"
    exit 1
fi

echo "Android SDK: $ANDROID_HOME"

# Create minimal Android project structure
echo "Creating project structure..."
mkdir -p "$BUILD_DIR/app/src/main/java/com/grindrplus/"{hooks,utils,core}
mkdir -p "$BUILD_DIR/app/src/main/res/values"
mkdir -p "$BUILD_DIR/gradle/wrapper"

# Copy source files
echo "Copying source files..."
cp "$PROJECT_DIR/GrindrPlus.kt" "$BUILD_DIR/app/src/main/java/com/grindrplus/"
cp "$PROJECT_DIR/hooks/"*.kt "$BUILD_DIR/app/src/main/java/com/grindrplus/hooks/"
cp "$PROJECT_DIR/utils/"*.kt "$BUILD_DIR/app/src/main/java/com/grindrplus/utils/"
cp "$PROJECT_DIR/core/"*.kt "$BUILD_DIR/app/src/main/java/com/grindrplus/core/"

# Create AndroidManifest.xml
cat > "$BUILD_DIR/app/src/main/AndroidManifest.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.grindrplus">

    <application
        android:label="GrindrPlus"
        android:allowBackup="false">

        <!-- Xposed module metadata -->
        <meta-data
            android:name="xposedmodule"
            android:value="true" />
        <meta-data
            android:name="xposeddescription"
            android:value="GrindrPlus v2.0 — Premium unlock + feature enhancements for Grindr 26.x+" />
        <meta-data
            android:name="xposedminversion"
            android:value="93" />

    </application>
</manifest>
EOF

# Create strings.xml
cat > "$BUILD_DIR/app/src/main/res/values/strings.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">GrindrPlus</string>
</resources>
EOF

# Create build.gradle for app module
cat > "$BUILD_DIR/app/build.gradle" << 'EOF'
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.grindrplus'
    compileSdk 34

    defaultConfig {
        applicationId "com.grindrplus"
        minSdk 26
        targetSdk 34
        versionCode 200
        versionName "2.0.0"
    }

    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
        debug {
            minifyEnabled false
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    // Xposed API (compile-only, not bundled)
    compileOnly 'de.robv.android.xposed:api:82'
    compileOnly 'de.robv.android.xposed:api:82:sources'

    // Kotlin coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // JSON
    implementation 'org.json:json:20231013'
}
EOF

# Create settings.gradle
cat > "$BUILD_DIR/settings.gradle" << 'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "GrindrPlus"
include ':app'
EOF

# Create root build.gradle
cat > "$BUILD_DIR/build.gradle" << 'EOF'
plugins {
    id 'com.android.application' version '8.2.0' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.20' apply false
}
EOF

# Create gradle wrapper
cat > "$BUILD_DIR/gradle/wrapper/gradle-wrapper.properties" << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

# Create gradle.properties
cat > "$BUILD_DIR/gradle.properties" << 'EOF'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
EOF

# Create proguard rules
cat > "$BUILD_DIR/app/proguard-rules.pro" << 'EOF'
# Keep Xposed entry point
-keep class com.grindrplus.GrindrPlus {
    public void handleLoadPackage(...);
}

# Keep hook classes
-keep class com.grindrplus.hooks.** { *; }
-keep class com.grindrplus.utils.** { *; }
-keep class com.grindrplus.core.** { *; }

# Keep Xposed API
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**
EOF

# Create local.properties
cat > "$BUILD_DIR/local.properties" << EOF
sdk.dir=$ANDROID_HOME
EOF

echo ""
echo "=== Project structure created ==="
echo "Build directory: $BUILD_DIR"
echo ""
echo "To build:"
echo "  cd $BUILD_DIR"
echo "  ./gradlew assembleDebug"
echo ""
echo "Output APK will be at:"
echo "  $BUILD_DIR/app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "For LSPatch integration:"
echo "  1. Build the APK"
echo "  2. Use: lspatch --manager-mode --embed-grindrplus Grindr_v26.x.apk"
