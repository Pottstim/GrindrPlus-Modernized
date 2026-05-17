package com.grindrplus

import com.grindrplus.core.Config
import com.grindrplus.utils.HookManager
import com.grindrplus.utils.Logger
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * GrindrPlus — Main Xposed entry point.
 *
 * Targets: com.grindrapp.android (Grindr official)
 * Supports: LSPosed (root) and LSPatch (no-root)
 *
 * Architecture:
 * 1. PairIPBlocker — Strips PairIP license validation (must run first)
 * 2. GMSSpoof — Fakes Google Play Services responses
 * 3. AntiDetection — Hides root/LSPosed/debugger
 * 4. FeatureGranting — Unlocks all premium features
 * 5. EnableUnlimited — Removes all usage limits
 * 6. BanManagement — Prevents bans
 * 7. TimberLogging — Debug logging (debug builds only)
 */
class GrindrPlus : IXposedHookLoadPackage {

    companion object {
        const val TARGET_PACKAGE = "com.grindrapp.android"
        const val VERSION = "2.0.0"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        Logger.log("=== GrindrPlus v$VERSION loading ===")
        Logger.log("Process: ${lpparam.processName}")
        Logger.log("ClassLoader: ${lpparam.classLoader}")

        try {
            Config.init()
            val hookManager = HookManager()

            // Phase 1: Anti-detection (must run before anything else)
            hookManager.register(AntiDetection())

            // Phase 2: PairIP bypass (must run before Grindr initializes)
            hookManager.register(PairIPBlocker())

            // Phase 3: GMS spoofing
            hookManager.register(GMSSpoof())

            // Phase 4: Feature unlocking
            hookManager.register(FeatureGranting())

            // Phase 5: Unlimited usage
            hookManager.register(EnableUnlimited())

            // Phase 6: Ban prevention
            hookManager.register(BanManagement())

            // Phase 7: Debug logging (only in debug builds)
            if (Config.isDebugMode()) {
                hookManager.register(TimberLogging())
            }

            // Initialize all hooks
            val results = hookManager.initAll(lpparam)

            // Report results
            val success = results.count { it.value }
            val total = results.size
            Logger.log("=== GrindrPlus: $success/$total hooks loaded ===")

            results.forEach { (name, ok) ->
                Logger.log("  ${if (ok) "✓" else "✗"} $name")
            }

        } catch (e: Throwable) {
            Logger.error("Fatal error during initialization", e)
            XposedBridge.log("GrindrPlus fatal: ${e.message}")
        }
    }
}
