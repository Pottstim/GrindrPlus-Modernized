package com.grindrplus.hooks

import com.grindrplus.core.Config
import de.robv.android.xposed.XposedBridge
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers

class BanManagement : Hook("Ban Management", "Prevents ban checks, shadowbans, and account restrictions") {
    override fun init() {
        Config.registerHook(name, description, true)
        interceptBanChecks()
        preventShadowban()
        spoofBanStatus()
    }

    private fun interceptBanChecks() {
        try {
            val patterns = listOf(
                "com.grindrapp.android.ban",
                "com.grindrapp.android.data.model.BanStatus",
                "com.grindrapp.android.api.model.Restriction"
            )
            patterns.forEach { pattern ->
                try {
                    val clazz = lpparam.classLoader.loadClass(pattern)
                    XposedBridge.hookAllMethods(clazz, "isBanned", XC_MethodReplacement.returnConstant(false))
                    XposedBridge.hookAllMethods(clazz, "isShadowbanned", XC_MethodReplacement.returnConstant(false))
                    XposedBridge.hookAllMethods(clazz, "isRestricted", XC_MethodReplacement.returnConstant(false))
                    XposedBridge.hookAllMethods(clazz, "isSuspended", XC_MethodReplacement.returnConstant(false))
                    Logger.log("BanMgmt: Intercepted $pattern")
                } catch (_: Throwable) { }
            }
        } catch (e: Throwable) { Logger.error("interceptBanChecks", e) }
    }

    private fun preventShadowban() {
        try {
            val patterns = listOf(
                "com.grindrapp.android.feed",
                "com.grindrapp.android.data.model.Visibility"
            )
            patterns.forEach { pattern ->
                try {
                    val clazz = lpparam.classLoader.loadClass(pattern)
                    XposedBridge.hookAllMethods(clazz, "isVisible", XC_MethodReplacement.returnConstant(true))
                    XposedBridge.hookAllMethods(clazz, "isShown", XC_MethodReplacement.returnConstant(true))
                    Logger.log("BanMgmt: Shadowban prevention for $pattern")
                } catch (_: Throwable) { }
            }
        } catch (e: Throwable) { Logger.error("preventShadowban", e) }
    }

    private fun spoofBanStatus() {
        XposedHelpers.findAndHookMethod("android.content.SharedPreferences",
            lpparam.classLoader, "getBoolean",
            String::class.java, Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as? String ?: return
                    if (key.contains("ban", ignoreCase = true) ||
                        key.contains("restrict", ignoreCase = true) ||
                        key.contains("suspend", ignoreCase = true) ||
                        key.contains("shadow", ignoreCase = true)) {
                        param.result = false
                    }
                }
            })
        Logger.log("BanMgmt: SharedPreferences ban spoofing active")
    }
}
