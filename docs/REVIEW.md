# GrindrPlus Modernization — Document Review & Fixes

## Critical Issues Found in Original Document

### 1. DynamicHookResolver — Broken `findClass("*")` Pattern
**Severity: CRITICAL** — Won't compile or run.

`XposedHelpers.findClass()` requires an exact class name. The wildcard `"com.pairipcore.*"` is invalid. The "scan all loaded classes" approach needs `XposedHelpers.findClassIfExists()` with explicit class names discovered via JADX, or hook `ClassLoader.loadClass()` to intercept by name pattern.

**Fix:** Implemented `DynamicHookResolver` that hooks `ClassLoader.loadClass()` and matches class names against regex patterns at load time. Also provides `findClassByPattern()` using reflection on ClassLoader's internal class cache.

### 2. PairIP `onCreate` Hook — Insufficient Alone
**Severity: HIGH** — App still crashes or shows license dialog.

The original only hooks `onCreate` for `LicenseActivity`. But PairIP also:
- Uses `LicenseContentProvider` for CRUD-based license validation
- Loads native `libpairpcore.so` via `System.loadLibrary`
- Makes network calls to validate licenses server-side
- Uses `Class.forName()` reflection to detect hooks

**Fix:** `PairIPBlocker` now blocks all four layers:
1. `LicenseActivity.onCreate()` → returns null
2. `LicenseContentProvider` CRUD → returns null
3. `Runtime.loadLibrary0()` → blocks pairip native libs
4. `URL.openConnection()` → blocks pairip network calls
5. `Class.forName()` → blocks pairip reflection detection

### 3. `loadLibrary` Hook — Too Broad
**Severity: MEDIUM** — May block legitimate native libs.

The original hooks `ClassLoader.loadClass` but native libs are loaded via `System.loadLibrary()` → `Runtime.loadLibrary0()`. Different code path.

**Fix:** Now hooks both `ClassLoader.loadClass` AND `Runtime.loadLibrary0`.

### 4. No GMS/Play Integrity Spoofing
**Severity: HIGH** — Grindr 26.x+ validates Play Integrity.

The original document mentions GMS signature validation but provides no implementation. Grindr now checks:
- `GoogleApiAvailability.isGooglePlayServicesAvailable()` must return 0 (SUCCESS)
- `PackageManager.getPackageInfo()` signatures must match Play Store
- Play Integrity API attestation

**Fix:** New `GMSSpoof` hook that:
- Returns SUCCESS (0) for all GMS availability checks
- Spoofs package signatures via `PackageManager` hook
- Blocks Play Integrity token requests

### 5. No Anti-Detection
**Severity: CRITICAL** — Grindr detects LSPosed/LSPatch and refuses to run.

The original has no anti-detection measures. Grindr 26.x+ checks:
- Root binaries via `File.exists()`
- Xposed frames in stack traces
- Debugger attachment via `Debug.isDebuggerConnected()`
- Emulator via `SystemProperties`
- Modified APK signatures

**Fix:** New `AntiDetection` hook that:
- Hides root files via `File.exists()` hook
- Filters Xposed frames from stack traces
- Returns false for debugger checks
- Spoofs Build properties for real device
- Strips signature-check flags from PackageManager calls

### 6. FeatureGranting — No Obfuscation Handling
**Severity: HIGH** — Obfuscated class names change between versions.

The original uses hardcoded class names that break when Grindr obfuscates.

**Fix:** `FeatureGranting` now uses `DynamicHookResolver` with regex patterns to match obfuscated class names at runtime. Falls back to known class names for older versions.

### 7. No Ban Prevention
**Severity: MEDIUM** — Account can still be banned.

The original has no ban prevention. Grindr can ban accounts server-side.

**Fix:** New `BanManagement` hook that:
- Intercepts client-side ban checks (always returns "not banned")
- Monitors API responses for ban-related data
- Hides shadow-ban indicators

### 8. No Structured Logging
**Severity: LOW** — Hard to debug hook failures.

**Fix:** New `Logger` utility with:
- XposedBridge logging
- File-based logging with timestamps
- Error/warn/info levels
- Per-hook result reporting

### 9. No Config Management
**Severity: MEDIUM** — Can't toggle hooks at runtime.

**Fix:** New `Config` system with:
- JSON-based config file
- Per-hook enable/disable
- Debug mode toggle
- Runtime persistence

### 10. HookManager — No Error Isolation
**Severity: MEDIUM** — One failing hook crashes all others.

**Fix:** `HookManager.initAll()` now wraps each hook in try/catch with per-hook error reporting.

## Architecture Improvements

### Original Architecture
```
XposedInit → Direct hook calls (no isolation)
             ↓
         Hardcoded class names (breaks on obfuscation)
             ↓
         No error recovery
```

### New Architecture
```
GrindrPlus (entry point)
    ↓
Config (load settings)
    ↓
HookManager (registry + lifecycle)
    ↓
    ├── AntiDetection (hide root/LSPosed/debugger)
    ├── PairIPBlocker (bypass license checks)
    ├── GMSSpoof (fake GMS responses)
    ├── FeatureGranting (unlock premium)
    ├── EnableUnlimited (remove limits)
    ├── BanManagement (prevent bans)
    └── TimberLogging (debug only)
    
Each hook uses:
    ├── DynamicHookResolver (obfuscation-resistant)
    ├── Logger (structured logging)
    └── Config (runtime toggle)
```

## Build & Deployment

### LSPosed (Root)
```bash
./gradlew assembleDebug
# Install APK, enable in LSPosed, select Grindr
```

### LSPatch (No-Root)
```bash
# Use LSPatch to patch Grindr APK with GrindrPlus module
lspatch --manager-mode --embed-grindrplus Grindr_v26.x.apk
```

### Testing
```bash
# Clear app data after every hook change
adb shell pm clear com.grindrapp.android

# Monitor logs
adb logcat | grep -E "(GrindrPlus|pairip|xposed)"

# Disable individual features in GrindrPlus UI to isolate crashes
```

### Rollback
1. Disable GrindrPlus in LSPosed/LSPatch
2. Clear Grindr app data
3. Reinstall clean Grindr APK from Play Store
