#!/bin/bash
# fix-native-libs.sh — Recompress an APK so native .so libs AND resources.arsc
# are stored uncompressed. Fixes INSTALL_FAILED_INVALID_APK (res=-2) and
# installPackageLI -124 on Android 11+.
#
# Usage: ./scripts/fix-native-libs.sh input.apk output.apk
#
# Requires: zip, zipalign, apksigner (Android SDK build-tools)

set -euo pipefail

INPUT="${1:?Usage: $0 <input.apk> <output.apk>}"
OUTPUT="${2:?Usage: $0 <input.apk> <output.apk>}"

TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

echo "[1/5] Extracting APK..."
unzip -q "$INPUT" -d "$TMPDIR"

echo "[2/5] Removing old signature..."
rm -rf "$TMPDIR"/META-INF/*

echo "[3/5] Repacking with uncompressed native libs and resources.arsc..."
cd "$TMPDIR"
zip -r "$TMPDIR/unsigned.apk" . -x "*.DS_Store" >/dev/null

# Store resources.arsc uncompressed (required for Android 11+/API 30+)
zip -0 "$TMPDIR/unsigned.apk" resources.arsc >/dev/null

# Store all lib/*.so files uncompressed (required when extractNativeLibs="false")
find lib -name "*.so" -exec zip -0 "$TMPDIR/unsigned.apk" {} \; >/dev/null

echo "[4/5] Zipaligning..."
zipalign -p -v 4 "$TMPDIR/unsigned.apk" "$TMPDIR/aligned.apk"

echo "[5/5] Signing..."
KEYSTORE="${KEYSTORE:-$HOME/.android/debug.keystore}"
KEYSTORE_PASS="${KEYSTORE_PASS:-android}"
KEY_ALIAS="${KEY_ALIAS:-androiddebugkey}"

apksigner sign \
  --ks "$KEYSTORE" \
  --ks-pass "pass:$KEYSTORE_PASS" \
  --key-pass "pass:$KEYSTORE_PASS" \
  --ks-key-alias "$KEY_ALIAS" \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$OUTPUT" \
  "$TMPDIR/aligned.apk"

apksigner verify --verbose "$OUTPUT"

echo ""
echo "Done: $OUTPUT"
ls -lh "$OUTPUT"
