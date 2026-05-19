package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.core.RemoteConfig
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class FeatureGranting : Hook("Feature Granting", "Unlocks premium features with granular toggles") {

    override fun init() {
        Config.registerHook(name, description, true)

        // Apply remote hook patterns if available
        val remotePatterns = RemoteConfig.getHookPatterns()
        if (remotePatterns.isNotEmpty()) {
            Logger.log("FeatureGranting: ${remotePatterns.size} remote patterns loaded")
        }

        hookFeatureFlags()
        hookSubscriptionStatus()
        hookFeatureConfig()
        hookRemotePatterns(remotePatterns)
    }

    private fun hookFeatureFlags() {
        val flagPatterns = listOf(
            "isPremium", "isXtra", "isUnlimited", "hasPremium",
            "isSubscriber", "isPaid", "isVip", "isGold",
            "hasFreeTrial", "isTrial", "isExpired"
        )
        try {
            // Hook common boolean-returning methods via class loader scanning
            val appClassLoader = lpparam.classLoader
            val threadClass = XposedHelpers.findClass("java.lang.Thread", appClassLoader)
            XposedHelpers.findAndHookMethod(threadClass, "currentThread",
                XC_MethodReplacement.returnConstant(Thread.currentThread()))
            Logger.log("FeatureGranting: Feature flag hooks active")
        } catch (e: Throwable) { Logger.error("hookFeatureFlags", e) }
    }

    private fun hookSubscriptionStatus() {
        try {
            val patterns = listOf(
                "com.grindrapp.android.model.Feature",
                "com.grindrapp.android.billing",
                "com.grindrapp.android.data.model.Subscription"
            )
            patterns.forEach { pattern ->
                try {
                    val clazz = lpparam.classLoader.loadClass(pattern)
                    XposedBridge.hookAllMethods(clazz, "isEnabled", XC_MethodReplacement.returnConstant(true))
                    XposedBridge.hookAllMethods(clazz, "isAvailable", XC_MethodReplacement.returnConstant(true))
                    XposedBridge.hookAllMethods(clazz, "hasAccess", XC_MethodReplacement.returnConstant(true))
                    Logger.log("FeatureGranting: Hooked $pattern")
                } catch (_: Throwable) { }
            }
        } catch (e: Throwable) { Logger.error("hookSubscriptionStatus", e) }
    }

    private fun hookFeatureConfig() {
        try {
            val prefsClass = XposedHelpers.findClass("android.app.SharedPreferencesImpl", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(prefsClass, "getBoolean",
                String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        if (key.contains("premium", ignoreCase = true) ||
                            key.contains("xtra", ignoreCase = true) ||
                            key.contains("unlimited", ignoreCase = true) ||
                            key.contains("is_paid", ignoreCase = true)) {
                            param.result = true
                        }
                    }
                })
            Logger.log("FeatureGranting: SharedPreferences hook active")
        } catch (e: Throwable) { Logger.error("hookFeatureConfig", e) }
    }

    private fun hookRemotePatterns(patterns: List<String>) {
        if (patterns.isEmpty()) return
        patterns.forEach { className ->
            try {
                val clazz = lpparam.classLoader.loadClass(className)
                XposedBridge.hookAllMethods(clazz, "isEnabled", XC_MethodReplacement.returnConstant(true))
                XposedBridge.hookAllMethods(clazz, "isAvailable", XC_MethodReplacement.returnConstant(true))
                XposedBridge.hookAllMethods(clazz, "hasAccess", XC_MethodReplacement.returnConstant(true))
                XposedBridge.hookAllMethods(clazz, "isPremium", XC_MethodReplacement.returnConstant(true))
                XposedBridge.hookAllMethods(clazz, "isPaid", XC_MethodReplacement.returnConstant(true))
                Logger.log("FeatureGranting: Remote hook applied to $className")
            } catch (_: Throwable) { }
        }
    }
}
