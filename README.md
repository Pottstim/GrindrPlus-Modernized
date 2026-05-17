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

## Architecture

```
GrindrPlus.kt (entry point)
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

## Build

### Prerequisites
- Android SDK (API 34)
- Kotlin 1.9+
- Gradle 8.4+

### LSPosed (Root)
```bash
cd build/
./gradlew assembleDebug
# Install APK → Enable in LSPosed → Select Grindr scope
```

### LSPatch (No-Root)
```bash
# Build module APK first, then:
./scripts/lspatch-build.sh Grindr_v26.x.apk app-debug.apk
# Install patched APK → Enable in LSPatch
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
