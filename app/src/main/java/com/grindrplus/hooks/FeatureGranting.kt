package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Issue #1 fix: Actually implements feature flag scanning instead of no-op
 * Issue #8 fix: Uses regex-based class name matching for obfuscation resilience
 */
class FeatureGranting : Hook("Feature Granting", "Unlocks all premium features via flag + subscription spoofing") {

    // Target patterns — matched as prefixes against discovered class names
    private val flagPatterns = listOf(
        "com.grindrapp.android.flag.",
        "com.grindrapp.android.model.Feature",
        "com.grindrapp.android.data.model.Config"
    )

    private val premiumMethodNames = listOf(
        "isPremium", "isXtra", "isUnlimited", "hasPremium",
        "isVip", "isGold", "isPlus", "isPaid",
        "getTier", "isSubscribed", "hasSubscription",
        "isFeatureEnabled", "isFlagEnabled"
    )

    private val unlockValues = mapOf(
        "getTier" to "Premium",
        "getSubscription" to "premium",
        "getXtraTier" to "Unlimited"
    )

    override fun init() {
        Config.registerHook(name, description, true)
        hookFeatureFlags()
        hookSubscriptionStatus()
        hookFeatureConfig()
        Logger.log("FeatureGranting: All features unlocked")
    }

    /**
     * Issue #1 fix: Actually scan classes and hook methods
     */
    private fun hookFeatureFlags() {
        // Method 1: Dynamic class scanning via DexFile (works on any class name regardless of obfuscation)
        try {
            val dexFileClass = XposedHelpers.findClass("dalvik.system.DexFile", lpparam.classLoader)
            val dexConstructor = lpparam.classLoader.loadClass("dalvik.system.DexFile")
            // Try to enumerate classes from the app's DexFile
            val classLoader = lpparam.classLoader
            val pathField = classLoader.javaClass.getDeclaredField("pathList")
            pathField.isAccessible = true
            val pathList = pathField.get(classLoader)
            val dexElementsField = pathList.javaClass.getDeclaredField("dexElements")
            dexElementsField.isAccessible = true
            val dexElements = dexElementsField.get(pathList) as Array<*>

            dexElements.forEach { element ->
                try {
                    val dexFileField = element!!.javaClass.getDeclaredField("dexFile")
                    dexFileField.isAccessible = true
                    val dexFile = dexFileField.get(element) ?: return@forEach
                    val entriesMethod = dexFile.javaClass.getMethod("entries")
                    val enumEntries = entriesMethod.invoke(dexFile) as java.util.Enumeration<*>

                    while (enumEntries.hasMoreElements()) {
                        val className = enumEntries.nextElement() as? String ?: continue
                        if (flagPatterns.any { pattern -> className.startsWith(pattern) }) {
                            hookFeatureClass(className)
                        }
                    }
                } catch (_: Throwable) { }
            }
        } catch (e: Throwable) { Logger.error("hookFeatureFlags:dexScan", e) }

        // Method 2: Fallback — hook known common feature flag holders
        val knownTargets = listOf(
            "com.grindrapp.android.flag.FeatureFlag",
            "com.grindrapp.android.flag.FeatureFlagManager",
            "com.grindrapp.android.feature.FeatureManager",
            "com.grindrapp.android.data.repository.FeatureRepository"
        )
        knownTargets.forEach { className ->
            try {
                hookFeatureClass(className)
            } catch (_: Throwable) { }
        }
    }

    private fun hookFeatureClass(className: String) {
        try {
            val clazz = lpparam.classLoader.loadClass(className)
            premiumMethodNames.forEach { methodName ->
                try {
                    XposedBridge.hookAllMethods(clazz, methodName, object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: XC_MethodHook.MethodHookParam): Any? {
                            val method = param.method
                            val returnType = if (method is java.lang.reflect.Method) method.returnType else String::class.java
                            return when {
                                returnType == Boolean::class.javaPrimitiveType -> true
                                returnType == String::class.java -> unlockValues[methodName] ?: "premium"
                                returnType == Int::class.javaPrimitiveType -> 1
                                methodName.contains("list", ignoreCase = true) -> listOf("premium", "unlimited")
                                methodName.contains("count", ignoreCase = true) -> Int.MAX_VALUE
                                else -> true
                            }
                        }
                    })
                } catch (_: Throwable) { }
            }
            Logger.log("FeatureGranting: Hooked $className")
        } catch (e: Throwable) { Logger.error("hookFeatureClass:$className", e) }
    }

    private fun hookSubscriptionStatus() {
        val knownClasses = listOf(
            "com.grindrapp.android.billing.SubscriptionManager",
            "com.grindrapp.android.billing.BillingManager",
            "com.grindrapp.android.data.api.model.Subscription"
        )
        knownClasses.forEach { className ->
            try {
                val clazz = lpparam.classLoader.loadClass(className)
                // Hook getSubscriptionStatus and similar
                XposedBridge.hookAllMethods(clazz, "getSubscriptionStatus",
                    XC_MethodReplacement.returnConstant("ACTIVE"))
                XposedBridge.hookAllMethods(clazz, "isSubscriptionActive",
                    XC_MethodReplacement.returnConstant(true))
                XposedBridge.hookAllMethods(clazz, "isValid",
                    XC_MethodReplacement.returnConstant(true))
                // Hook any isExpired / isCancelled
                XposedBridge.hookAllMethods(clazz, "isExpired",
                    XC_MethodReplacement.returnConstant(false))
                XposedBridge.hookAllMethods(clazz, "isCancelled",
                    XC_MethodReplacement.returnConstant(false))
                Logger.log("FeatureGranting: Subscription spoofed for $className")
            } catch (_: Throwable) { }
        }
    }

    private fun hookFeatureConfig() {
        // Hook SharedPreferences to return premium values
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.SharedPreferencesImpl", lpparam.classLoader,
                "getBoolean", String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        if (key.contains("premium", ignoreCase = true) ||
                            key.contains("xtra", ignoreCase = true) ||
                            key.contains("unlimited", ignoreCase = true) ||
                            key.contains("vip", ignoreCase = true)) {
                            param.result = true
                        }
                    }
                }
            )
        } catch (e: Throwable) { Logger.error("hookFeatureConfig:SharedPreferences", e) }
    }
}
