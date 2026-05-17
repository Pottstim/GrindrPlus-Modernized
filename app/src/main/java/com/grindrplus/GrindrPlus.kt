package com.grindrplus

import com.grindrplus.utils.HookManager
import com.grindrplus.utils.Logger
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class GrindrPlus : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.grindrapp.android") return

        try {
            Logger.log("GrindrPlus v2.0 loading for ${lpparam.packageName}")
            val hookManager = HookManager()
            hookManager.registerDefaults()
            val results = hookManager.initAll(lpparam)
            results.forEach { (name, ok) ->
                Logger.log("  ${if (ok) "✓" else "✗"} $name")
            }
        } catch (e: Throwable) {
            Logger.error("Fatal error during initialization", e)
            XposedBridge.log("GrindrPlus fatal: ${e.message}")
        }
    }
}
