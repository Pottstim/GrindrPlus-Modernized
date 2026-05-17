# GrindrPlus v2.0 — Modernized

Premium unlock + feature enhancements for Grindr 26.x+ with PairIP bypass, GMS spoofing, and anti-detection.

## What's New vs Original

| Issue | Original (v1.0) | Fixed (v2.0) |
|-------|-----------------|--------------|
| `findClass("com.pairipcore.*")` | Broken wildcard | `DynamicHookResolver` with regex patterns |
| PairIP bypass | Only `onCreate` | All 4 layers: Activity, ContentProvider, native, network |
| GMS validation | Not implemented | Full spoof: GMS availability, signatures, Play Integrity |
| Anti-detection | Not implemented | Root hide, Xposed hide, debugger hide, emulator hide |
| Obfuscation | Hardcoded class names | Dynamic pattern matching at runtime |
| Ban prevention | Not implemented | Client-side ban check interception |
| Error isolation | None | Per-hook try/catch with result reporting |
| Config | None | JSON config with per-hook toggle |
| Logging | `print()` | Structured Logger with file + XposedBridge |

## Two Ways to Use

### Option A: LSPatch Module (Recommended for No-Root)

This is the **module APK** — you install it through LSPatch alongside Grindr.

**What you need:**
- Android phone (no root required)
- [LSPatch](https://github.com/LSPosed/LSPatch) installed
- Original Grindr APK (v26.x+) from Play Store or APKMirror

**Steps:**
1. Install LSPatch on your phone
2. Download `app-debug.apk` from this release
3. Open LSPatch → "Manage" → "+" → Select Grindr APK
4. Choose "Local" mode (no internet needed for patching)
5. Select GrindrPlus module from the list
6. Tap "Patch" → Wait for completion
7. Install the patched APK (uninstall original Grindr first)
8. Open patched Grindr → Log in

**Google Login workaround (LSPatch only):**
1. Uninstall patched Grindr
2. Install original Grindr from Play Store
3. Reboot phone
4. Log in with Google on original app
5. Uninstall original Grindr
6. Reinstall patched GrindrPlus
7. Open and log in with Google within 10 minutes

### Option B: LSPosed Module (Root — Most Stable)

**What you need:**
- Rooted Android phone
- [LSPosed](https://github.com/LSPosed/LSPosed) (JingMatrix fork recommended)
- Grindr installed from Play Store

**Steps:**
1. Install `app-debug.apk` on your phone
2. Open LSPosed → "Modules" → Enable GrindrPlus
3. Set scope to only `com.grindrapp.android`
4. Reboot
5. Open Grindr — all features unlocked

## Architecture

```
GrindrPlus.kt (Xposed entry point)
    │
    ├── Config (load/save settings)
    ├── HookManager (registry + lifecycle)
    │       │
    │       ├── 1. AntiDetection ← hides root/LSPosed/debugger
    │       ├── 2. PairIPBlocker ← strips PairIP (ALL layers)
    │       ├── 3. GMSSpoof ← fakes GMS + Play Integrity
    │       ├── 4. FeatureGranting ← unlocks premium
    │       ├── 5. EnableUnlimited ← removes limits
    │       ├── 6. BanManagement ← prevents bans
    │       └── 7. TimberLogging ← debug only
    │
    ├── DynamicHookResolver (obfuscation-resistant class resolution)
    └── Logger (structured logging)
```

## Build from Source

### Prerequisites
- Android SDK (API 34)
- Kotlin 1.9+
- Gradle 8.4+

```bash
git clone https://github.com/Pottstim/GrindrPlus-Modernized.git
cd GrindrPlus-Modernized
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

## File Structure

```
GrindrPlus-Modernized/
├── GrindrPlus.kt              # Main Xposed entry point
├── hooks/
│   ├── AntiDetection.kt       # Hide root/LSPosed/debugger
│   ├── PairIPBlocker.kt       # Bypass PairIP (all layers)
│   ├── GMSSpoof.kt            # Fake GMS responses
│   ├── FeatureGranting.kt     # Unlock premium features
│   ├── EnableUnlimited.kt     # Remove usage limits
│   ├── BanManagement.kt       # Prevent bans
│   ├── TimberLogging.kt       # Debug logging
│   └── hooks.json             # Hook metadata for UI
├── utils/
│   ├── DynamicHookResolver.kt # Obfuscation-resistant resolver
│   ├── HookManager.kt         # Hook registry + lifecycle
│   ├── Hook.kt                # Base hook class
│   └── Logger.kt              # Structured logging
├── core/
│   └── Config.kt              # Runtime configuration
├── scripts/
│   ├── build.sh               # Gradle build setup
│   └── lspatch-build.sh       # LSPatch integration
├── docs/
│   └── REVIEW.md              # Full review of original doc
└── README.md                  # This file
```

## Testing

```bash
# Clear app data after every hook change
adb shell pm clear com.grindrapp.android

# Monitor all GrindrPlus activity
adb logcat | grep -E "(GrindrPlus|pairip|xposed)"

# Isolate crashes: disable individual hooks in config
adb shell cat /data/data/com.grindrapp.android/shared_prefs/gp_config.json
```

## Rollback

1. Disable GrindrPlus in LSPosed/LSPatch
2. `adb shell pm clear com.grindrapp.android`
3. Reinstall clean Grindr from Play Store

## Credits

- Original: [R0rt1z2/GrindrPlus](https://github.com/R0rt1z2/GrindrPlus)
- PairIP research: [ahmedmani/pairipfix](https://github.com/ahmedmani/pairipfix)
- PKiller: [Anon4You/PKiller](https://github.com/Anon4You/PKiller)
- OpenGrind: [open-grind/open-grind](https://git.opengrind.org/open-grind/open-grind)
