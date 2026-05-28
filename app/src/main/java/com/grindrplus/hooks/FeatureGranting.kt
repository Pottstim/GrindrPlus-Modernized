package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.Executors

/**
 * Fix #4: Dynamic Dex scanning moved to a background thread.
 *
 * The original implementation ran the full Dex class enumeration on the main
 * thread during package load, which could iterate thousands of class names and
 * trigger an ANR on slower devices. The fix:
 *
 *  - Known/stable class names are hooked immediately on the calling thread (fast path).
 *  - The expensive Dex enumeration scan is dispatched to a single-thread executor
 *    so it never blocks the main thread.
 *  - Hooks discovered by the background scan are applied via XposedBridge from the
 *    background thread, which is safe — Xposed allows hook registration from any thread.
 */
class FeatureGranting : Hook("Feature Granting", "Unlocks all premium features via flag + subscription spoofing") {

    private val scanExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "GrindrPlus-FeatureScan").also { it.isDaemon = true }
    }

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

        // Fast path: hook well-known stable class names immediately (no scanning needed)
        hookSubscriptionStatus()
        hookFeatureConfig()
        hookKnownFeatureClasses()

        // Slow path: background Dex scan for obfuscated / dynamic class names
        hookFeatureFlagsAsync()

        Logger.log("FeatureGranting: Fast-path hooks active; background scan started")
    }

    /** Hook known, stable class names immediately on the calling thread. */
    private fun hookKnownFeatureClasses() {
        val knownTargets = listOf(
            "com.grindrapp.android.flag.FeatureFlag",
            "com.grindrapp.android.flag.FeatureFlagManager",
            "com.grindrapp.android.feature.FeatureManager",
            "com.grindrapp.android.data.repository.FeatureRepository"
        )
        knownTargets.forEach { className ->
            try { hookFeatureClass(className) } catch (_: Throwable) { }
        }
    }

    /**
     * Fix #4: Dex enumeration dispatched to a background thread.
     * XposedBridge.hookAllMethods / hookMethod are thread-safe and can be called
     * from any thread at any point during the app's lifetime.
     */
    private fun hookFeatureFlagsAsync() {
        scanExecutor.execute {
            try {
                val classLoader = lpparam.classLoader
                val pathField = classLoader.javaClass.getDeclaredField("pathList")
                pathField.isAccessible = true
                val pathList = pathField.get(classLoader)
                val dexElementsField = pathList.javaClass.getDeclaredField("dexElements")
                dexElementsField.isAccessible = true
                val dexElements = dexElementsField.get(pathList) as Array<*>

                var discovered = 0
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
                                try {
                                    hookFeatureClass(className)
                                    discovered++
                                } catch (_: Throwable) { }
                            }
                        }
                    } catch (_: Throwable) { }
                }
                Logger.log("FeatureGranting: Background scan complete — $discovered additional classes hooked")
            } catch (e: Throwable) {
                Logger.error("hookFeatureFlagsAsync:dexScan", e)
            }
        }
    }

    private fun hookFeatureClass(className: String) {
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
                XposedBridge.hookAllMethods(clazz, "getSubscriptionStatus",
                    XC_MethodReplacement.returnConstant("ACTIVE"))
                XposedBridge.hookAllMethods(clazz, "isSubscriptionActive",
                    XC_MethodReplacement.returnConstant(true))
                XposedBridge.hookAllMethods(clazz, "isValid",
                    XC_MethodReplacement.returnConstant(true))
                XposedBridge.hookAllMethods(clazz, "isExpired",
                    XC_MethodReplacement.returnConstant(false))
                XposedBridge.hookAllMethods(clazz, "isCancelled",
                    XC_MethodReplacement.returnConstant(false))
                Logger.log("FeatureGranting: Subscription spoofed for $className")
            } catch (_: Throwable) { }
        }
    }

    private fun hookFeatureConfig() {
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
