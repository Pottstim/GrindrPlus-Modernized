package com.grindrplus.hooks

import com.grindrplus.core.Config
import de.robv.android.xposed.XposedBridge
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers

class EnableUnlimited : Hook("Unlimited Everything", "Removes all limits: cascades, taps, blocks, favorites, views") {
    override fun init() {
        Config.registerHook(name, description, true)
        removeLimits()
    }

    private fun removeLimits() {
        removeXtraLimits()
        removeTapLimits()
        removeBlockLimits()
        removeFavoriteLimits()
        removeViewLimits()
    }

    private fun removeXtraLimits() {
        try {
            val patterns = listOf(
                "com.grindrapp.android.model.XtraTier",
                "com.grindrapp.android.data.model.XtraConfig",
                "com.grindrapp.android.utils.LimitedCounter"
            )
            patterns.forEach { pattern ->
                try {
                    val clazz = lpparam.classLoader.loadClass(pattern)
                    // Hook all int-returning methods to return max value
                    clazz.declaredMethods.forEach { method ->
                        if (method.returnType == Int::class.javaPrimitiveType) {
                            XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(Int.MAX_VALUE))
                        }
                        if (method.returnType == Long::class.javaPrimitiveType) {
                            XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(Long.MAX_VALUE))
                        }
                    }
                    Logger.log("Unlimited: Hooked $pattern")
                } catch (_: Throwable) { }
            }
        } catch (e: Throwable) { Logger.error("removeXtraLimits", e) }
    }

    private fun removeTapLimits() {
        try {
            val patterns = listOf(
                "com.grindrapp.android.tap",
                "com.grindrapp.android.data.model.TapCounter"
            )
            patterns.forEach { pattern ->
                try {
                    val clazz = lpparam.classLoader.loadClass(pattern)
                    XposedBridge.hookAllMethods(clazz, "getRemaining", XC_MethodReplacement.returnConstant(Int.MAX_VALUE))
                    XposedBridge.hookAllMethods(clazz, "canSend", XC_MethodReplacement.returnConstant(true))
                    XposedBridge.hookAllMethods(clazz, "isLimited", XC_MethodReplacement.returnConstant(false))
                    Logger.log("Unlimited: Tap limits removed for $pattern")
                } catch (_: Throwable) { }
            }
        } catch (e: Throwable) { Logger.error("removeTapLimits", e) }
    }

    private fun removeBlockLimits() {
        XposedHelpers.findAndHookMethod("android.content.SharedPreferences",
            lpparam.classLoader, "getInt",
            String::class.java, Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as? String ?: return
                    if (key.contains("block", ignoreCase = true) ||
                        key.contains("ban_count", ignoreCase = true)) {
                        param.result = 0
                    }
                }
            })
        Logger.log("Unlimited: Block limits removed")
    }

    private fun removeFavoriteLimits() {
        try {
            val patterns = listOf(
                "com.grindrapp.android.favorite",
                "com.grindrapp.android.data.model.Favorite"
            )
            patterns.forEach { pattern ->
                try {
                    val clazz = lpparam.classLoader.loadClass(pattern)
                    XposedBridge.hookAllMethods(clazz, "getMaxFavorites", XC_MethodReplacement.returnConstant(Int.MAX_VALUE))
                    XposedBridge.hookAllMethods(clazz, "isFavoriteLimitReached", XC_MethodReplacement.returnConstant(false))
                    Logger.log("Unlimited: Favorite limits removed for $pattern")
                } catch (_: Throwable) { }
            }
        } catch (e: Throwable) { Logger.error("removeFavoriteLimits", e) }
    }

    private fun removeViewLimits() {
        try {
            val patterns = listOf(
                "com.grindrapp.android.profile.view",
                "com.grindrapp.android.data.model.ProfileView"
            )
            patterns.forEach { pattern ->
                try {
                    val clazz = lpparam.classLoader.loadClass(pattern)
                    XposedBridge.hookAllMethods(clazz, "getViewCount", XC_MethodReplacement.returnConstant(0))
                    XposedBridge.hookAllMethods(clazz, "isViewLimited", XC_MethodReplacement.returnConstant(false))
                    Logger.log("Unlimited: View limits removed for $pattern")
                } catch (_: Throwable) { }
            }
        } catch (e: Throwable) { Logger.error("removeViewLimits", e) }
    }
}
