#!/bin/bash
# LSPatch Integration Script
# Embeds GrindrPlus into Grindr APK for no-root usage
#
# Prerequisites:
#   - LSPatch installed (https://github.com/LSPosed/LSPatch)
#   - Grindr APK (v26.x+) downloaded
#   - GrindrPlus module APK built
#
# Usage:
#   ./lspatch-build.sh <grindr-apk-path> <grindrplus-apk-path>

set -e

GRINDR_APK="${1:?Usage: $0 <grindr-apk> <grindrplus-apk>}"
GP_APK="${2:?Usage: $0 <grindr-apk> <grindrplus-apk>}"
OUTPUT_DIR="$(dirname "$GRINDR_APK")"
OUTPUT_APK="${OUTPUT_DIR}/Grindr_Plus_v26.x_patched.apk"

echo "=== GrindrPlus LSPatch Builder ==="
echo "Grindr APK: $GRINDR_APK"
echo "GrindrPlus APK: $GP_APK"
echo "Output: $OUTPUT_APK"
echo ""

# Verify inputs exist
if [ ! -f "$GRINDR_APK" ]; then
    echo "ERROR: Grindr APK not found: $GRINDR_APK"
    exit 1
fi

if [ ! -f "$GP_APK" ]; then
    echo "ERROR: GrindrPlus APK not found: $GP_APK"
    echo "Build it first with: ./scripts/build.sh"
    exit 1
fi

# Check for LSPatch
if ! command -v lspatch &> /dev/null; then
    echo "ERROR: lspatch not found in PATH"
    echo "Install from: https://github.com/LSPosed/LSPatch"
    exit 1
fi

echo "Step 1: Running PKiller to strip PairIP from Grindr APK..."
if command -v pkiller &> /dev/null; then
    pkiller "$GRINDR_APK"
    echo "PKiller: Done"
else
    echo "PKiller: Not found (optional — PairIP will be blocked at runtime)"
    echo "Install from: https://github.com/Anon4You/PKiller"
fi

echo ""
echo "Step 2: Patching with LSPatch..."
lspatch \
    --manager-mode \
    --embed-grindrplus \
    --apk "$GRINDR_APK" \
    --module "$GP_APK" \
    --output "$OUTPUT_APK"

echo ""
echo "=== Build Complete ==="
echo "Patched APK: $OUTPUT_APK"
echo ""
echo "Install on device:"
echo "  adb install \"$OUTPUT_APK\""
echo ""
echo "After install:"
echo "  1. Open LSPatch app"
echo "  2. Enable GrindrPlus module for Grindr"
echo "  3. Clear Grindr app data: adb shell pm clear com.grindrapp.android"
echo "  4. Launch Grindr and log in"
echo ""
echo "Debug:"
echo "  adb logcat | grep -E '(GrindrPlus|pairip|xposed)'"
