package com.grindrplus

import com.grindrplus.core.Config
import com.grindrplus.core.HookStateStore
import com.grindrplus.core.RemoteConfig
import com.grindrplus.utils.HookManager
import com.grindrplus.utils.Logger
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class GrindrPlus : IXposedHookLoadPackage {

    companion object {
        private val hookManager = HookManager()
        private var initialized = false
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.grindrapp.android") return

        try {
            if (!initialized) {
                // Register hooks once
                hookManager.registerDefaults()

                // Fetch remote config async (Issue #9 fix — no main thread blocking)
                RemoteConfig.fetchAsync()

                initialized = true
            }

            // Issue #15: Hook init timeout watchdog
            // Each hook's init() should complete within 15 seconds
            val results = hookManager.initAll(lpparam)

            // Log results
            val success = results.count { it.value }
            val total = results.size
            Logger.log("GrindrPlus v2.0: $success/$total hooks active for ${lpparam.packageName}")

            // Show remote notice if available
            val notice = RemoteConfig.getNoticeMessage()
            if (notice.isNotEmpty()) {
                Logger.log("Notice: $notice")
            }

        } catch (e: Throwable) {
            Logger.error("GrindrPlus: Fatal error", e)
        }
    }
}
