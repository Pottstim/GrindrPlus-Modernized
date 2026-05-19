package com.grindrplus

import com.grindrplus.core.Config
import com.grindrplus.core.HookStateStore
import com.grindrplus.core.RemoteConfig
import com.grindrplus.utils.HookManager
import com.grindrplus.utils.Logger
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class GrindrPlus : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.grindrapp.android") return

        try {
            // Initialize HookStateStore with app context
            val appContext = try {
                val appClazz = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
                val currentApp = XposedHelpers.callStaticMethod(appClazz, "currentApplication")
                XposedHelpers.callMethod(currentApp, "getApplicationContext")
            } catch (_: Throwable) { null }

            if (appContext is android.content.Context) {
                HookStateStore.init(appContext)
            }

            HookStateStore.incrementInitCount()
            val initCount = HookStateStore.getInitCount()
            Logger.log("GrindrPlus v2.0 loading (init #$initCount)")

            // Fetch remote config
            if (Config.isRemoteConfigEnabled()) {
                RemoteConfig.fetch()
            }

            // Initialize all hooks
            val hookManager = HookManager()
            hookManager.registerDefaults()
            val results = hookManager.initAll(lpparam)

            // Log summary
            val success = results.count { it.value }
            val total = results.size
            Logger.log("GrindrPlus: $success/$total hooks active")

        } catch (e: Throwable) {
            Logger.error("Fatal error during initialization", e)
            XposedBridge.log("GrindrPlus fatal: ${e.message}")
        }
    }
}
