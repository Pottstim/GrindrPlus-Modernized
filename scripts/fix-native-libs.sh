#!/bin/bash
# fix-native-libs.sh — Recompress an APK so native .so libs are stored uncompressed.
# This fixes INSTALL_FAILED_INVALID_APK (res=-2) on devices with extractNativeLibs="false".
#
# Usage: ./scripts/fix-native-libs.sh input.apk [output.apk]

set -e

INPUT="$1"
OUTPUT="${2:-${1%.apk}-fixed.apk}"

if [ ! -f "$INPUT" ]; then
    echo "ERROR: Input APK not found: $INPUT"
    exit 1
fi

TMPDIR=$(mktemp -d)
trap "rm -rf $TMPDIR" EXIT

echo "=== Fixing native library compression ==="
echo "Input:  $INPUT"
echo "Output: $OUTPUT"

# Unzip
echo "Extracting APK..."
unzip -q -o "$INPUT" -d "$TMPDIR"

# Remove old signature
rm -rf "$TMPDIR/META-INF"

# Re-zip with compression (default)
echo "Repacking APK..."
cd "$TMPDIR"
zip -q -r "$TMPDIR/repacked.apk" . -x "*.DS_Store"

# Re-store .so files uncompressed
echo "Storing native libs uncompressed..."
find lib -name "*.so" -exec zip -q -0 "$TMPDIR/repacked.apk" {} \; 2>/dev/null || true

# Zipalign
echo "Zipaligning..."
zipalign -p -f 4 "$TMPDIR/repacked.apk" "$TMPDIR/aligned.apk"

# Sign
echo "Signing..."
apksigner sign \
    --ks "${ANDROID_DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$OUTPUT" \
    "$TMPDIR/aligned.apk"

# Verify
echo "Verifying..."
apksigner verify "$OUTPUT"

# Report
SO_COUNT=$(unzip -Z "$OUTPUT" | grep "^.*lib/.*\.so$" | grep "stor" | wc -l)
SO_COMPRESSED=$(unzip -Z "$OUTPUT" | grep "^.*lib/.*\.so$" | grep -v "stor" | wc -l)
echo ""
echo "=== Done ==="
echo "Native libs stored uncompressed: $SO_COUNT"
echo "Native libs still compressed:    $SO_COMPRESSED (should be 0)"
echo "Output: $OUTPUT"
ls -lh "$OUTPUT"
