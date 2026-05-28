package com.grindrplus

import android.content.Context
import com.grindrplus.core.Analytics
import com.grindrplus.core.Config
import com.grindrplus.core.HookStateStore
import com.grindrplus.core.RemoteConfig
import com.grindrplus.utils.HookManager
import com.grindrplus.utils.Logger
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class GrindrPlus : IXposedHookLoadPackage {

    companion object {
        private val hookManager = HookManager()
        @Volatile private var hooksInitialized = false
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.grindrapp.android") return

        try {
            // Register hooks once (lightweight — no class loading yet)
            if (!hooksInitialized) {
                hookManager.registerDefaults()
            }

            // Defer full initialization until Application.attachBaseContext fires,
            // so we have a valid Context to pass to HookStateStore (Fix #1).
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "attachBaseContext",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (hooksInitialized) return
                        hooksInitialized = true

                        val context = param.args[0] as? Context ?: return

                        try {
                            // Now we have a real Context — initialize persistent state
                            HookStateStore.init(context)

                            // Fetch remote config async (no main-thread blocking)
                            RemoteConfig.fetchAsync()

                            // Initialize all hooks with the load package param
                            val results = hookManager.initAll(lpparam)

                            val success = results.count { it.value }
                            val total = results.size
                            Logger.log("GrindrPlus v2.0: $success/$total hooks active for ${lpparam.packageName}")

                            // Report anonymous hook survival telemetry (Fix #4 — wired)
                            val grindrVersion = try {
                                context.packageManager
                                    .getPackageInfo("com.grindrapp.android", 0)
                                    .versionName ?: "unknown"
                            } catch (_: Throwable) { "unknown" }
                            Analytics.reportHookResults(results, grindrVersion)

                            // Show remote notice if available
                            val notice = RemoteConfig.getNoticeMessage()
                            if (notice.isNotEmpty()) {
                                Logger.log("Notice: $notice")
                            }

                        } catch (e: Throwable) {
                            Logger.error("GrindrPlus: Fatal init error", e)
                        }
                    }
                }
            )

        } catch (e: Throwable) {
            Logger.error("GrindrPlus: Fatal error in handleLoadPackage", e)
        }
    }
}
