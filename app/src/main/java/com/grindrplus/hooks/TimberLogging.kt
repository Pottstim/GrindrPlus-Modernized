package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

class TimberLogging : Hook("Debug Logging", "Logs all Timber debug output for reverse engineering") {
    override fun init() {
        Config.registerHook(name, description, false)
        if (!Config.isDebugMode()) return
        hookTimber()
    }

    private fun hookTimber() {
        try {
            val timberClass = lpparam.classLoader.loadClass("timber.log.Timber")
            val treeClass = lpparam.classLoader.loadClass("timber.log.Timber\$Tree")
            XposedBridge.hookAllMethods(treeClass, "d", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val msg = param.args.getOrNull(0)?.toString() ?: return
                    Logger.log("Timber.D: $msg")
                }
            })
            XposedBridge.hookAllMethods(treeClass, "e", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val msg = param.args.getOrNull(0)?.toString() ?: return
                    Logger.log("Timber.E: $msg")
                }
            })
            Logger.log("Timber: Debug logging active")
        } catch (e: Throwable) { Logger.error("hookTimber", e) }
    }
}
