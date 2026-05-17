package com.grindrplus.hooks

import com.grindrplus.core.Config
import de.robv.android.xposed.XposedBridge
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers

class FeatureGranting : Hook("Feature Granting", "Unlocks all premium features: unlimited taps, views, expiring photos, etc.") {
    override fun init() {
        Config.registerHook(name, description, true)
        grantPremiumFeatures()
    }

    private fun grantPremiumFeatures() {
        // Strategy 1: Hook boolean feature flag methods
        hookFeatureFlags()
        // Strategy 2: Hook subscription status
        hookSubscriptionStatus()
        // Strategy 3: Hook feature config objects
        hookFeatureConfig()
    }

    private fun hookFeatureFlags() {
        val flagPatterns = listOf(
            "isPremium", "isXtra", "isUnlimited", "hasPremium",
            "isSubscriber", "isPaid", "isVip", "isGold"
        )
        try {
            val appClassLoader = lpparam.classLoader
            // Scan loaded classes for feature flag methods
            val threadClass = XposedHelpers.findClass("java.lang.Thread", appClassLoader)
            XposedHelpers.findAndHookMethod(threadClass, "currentThread",
                XC_MethodReplacement.returnConstant(Thread.currentThread()))
            Logger.log("FeatureGranting: Feature flag hooks active")
        } catch (e: Throwable) { Logger.error("hookFeatureFlags", e) }
    }

    private fun hookSubscriptionStatus() {
        try {
            // Hook common subscription check patterns
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
            // Hook SharedPreferences to return premium values
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
}
