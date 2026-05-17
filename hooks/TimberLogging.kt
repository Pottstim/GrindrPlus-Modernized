package com.grindrplus.hooks

import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * TimberLogging — Intercepts Grindr's Timber logging to capture internal state.
 *
 * Useful for debugging hook targets and understanding obfuscated class behavior.
 * Only active in debug mode.
 */
class TimberLogging : Hook("Timber Logging", "Captures Grindr internal logs for debugging") {

    override fun init() {
        if (!com.grindrplus.core.Config.isDebugMode()) return

        hookTimber(lpparam)
        hookLogcat(lpparam)
    }

    private fun hookTimber(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val timberClass = XposedHelpers.findClass("timber.log.Timber", lpparam.classLoader)
            val logMethods = listOf("d", "i", "w", "e", "v", "wtf")

            logMethods.forEach { method ->
                try {
                    XposedBridge.hookAllMethods(timberClass, method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val message = param.args.getOrNull(0)?.toString() ?: return
                            val tag = try {
                                val tagField = timberClass.getDeclaredField("tree")
                                tagField.isAccessible = true
                                tagField.get(null)?.toString() ?: "Timber"
                            } catch (_: Throwable) { "Timber" }

                            // Log to our own logger with Timber prefix
                            Logger.log("[$tag] $message")

                            // Also capture any throwable
                            param.args.getOrNull(1)?.let { throwable ->
                                if (throwable is Throwable) {
                                    Logger.log("  Exception: ${throwable.javaClass.name}: ${throwable.message}")
                                }
                            }
                        }
                    })
                } catch (_: Throwable) { }
            }

            Logger.log("Timber: Logging hooks installed")
        } catch (e: Throwable) {
            Logger.log("Timber: Not found in this Grindr version (may use custom logging)")
        }
    }

    private fun hookLogcat(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                android.util.Log::class.java,
                "d", String::class.java, String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val tag = param.args[0] as? String ?: return
                        val msg = param.args[1] as? String ?: return
                        if (tag.contains("grindr", ignoreCase = true) ||
                            msg.contains("pairip", ignoreCase = true) ||
                            msg.contains("license", ignoreCase = true)
                        ) {
                            Logger.log("Logcat[$tag] $msg")
                        }
                    }
                }
            )
        } catch (_: Throwable) { }
    }
}
