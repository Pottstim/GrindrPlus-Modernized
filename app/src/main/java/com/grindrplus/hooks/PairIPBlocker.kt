package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Issue #6 fix: Safer ClassLoader swap with whitelist for Android framework classes
 * Issue #11 fix: Native libpairip.so hooking via System.loadLibrary interception
 */
class PairIPBlocker : Hook("PairIP Blocker", "Blocks PairIP license checks — Java layer only") {

    private val pairipPackages = setOf(
        "com.pairip.licensecheck",
        "com.pairipcore"
    )

    // Whitelist: classes that must always be loadable
    private val classWhitelist = setOf(
        "android.", "java.", "javax.", "dalvik.", "kotlin.", "kotlinx.",
        "org.xml.", "org.json.", "sun.", "com.android.", "libcore.",
        "com.grindrapp."
    )

    override fun init() {
        Config.registerHook(name, description, true)
        blockPairIPActivities()
        blockPairIPContentProvider()
        blockDynamicPairIP()
        // NOTE: Native lib blocking removed — blocking System.loadLibrary causes
        // the app to crash when it tries to call JNI methods from the loaded lib.
        // Java-level class blocking is sufficient to neutralize PairIP.
        Logger.log("PairIP: Java-level block active (Activities + ContentProvider + ClassLoader)")
    }

    private fun blockPairIPActivities() {
        val activities = listOf(
            "com.pairip.licensecheck.LicenseContentProvider",
            "com.pairip.licensecheck.LicenseActivity"
        )
        activities.forEach { name ->
            try {
                val clazz = XposedHelpers.findClass(name, lpparam.classLoader)
                XposedBridge.hookAllMethods(clazz, "onCreate", XC_MethodReplacement.returnConstant(true))
                Logger.log("PairIP: Blocked $name")
            } catch (e: Throwable) { Logger.error("blockPairIPActivities:$name", e) }
        }
    }

    private fun blockPairIPContentProvider() {
        try {
            XposedHelpers.findAndHookMethod(
                "android.content.ContentResolver", lpparam.classLoader,
                "call", android.net.Uri::class.java, String::class.java, String::class.java, android.os.Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val uri = param.args[0] as? android.net.Uri ?: return
                        if (uri.authority?.contains("pairip") == true) {
                            param.result = null
                            Logger.log("PairIP: Blocked ContentResolver call to $uri")
                        }
                    }
                }
            )
        } catch (e: Throwable) { Logger.error("blockPairIPContentProvider", e) }

        try {
            XposedHelpers.findAndHookMethod(
                "android.content.ContentResolver", lpparam.classLoader,
                "query",
                android.net.Uri::class.java, Array<String>::class.java, String::class.java,
                Array<String>::class.java, String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val uri = param.args[0] as? android.net.Uri ?: return
                        if (uri.authority?.contains("pairip") == true) {
                            param.result = null
                        }
                    }
                }
            )
        } catch (e: Throwable) { Logger.error("blockPairIPContentProvider:query", e) }
    }

    /**
     * Blocks PairIP classes from being loaded dynamically.
     * Uses a ThreadLocal re-entrancy guard to prevent ClassNotFoundException loops.
     */
    private fun blockDynamicPairIP() {
        val inHook = ThreadLocal.withInitial { false }
        try {
            XposedHelpers.findAndHookMethod(
                ClassLoader::class.java, "loadClass",
                String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (inHook.get()) return
                        val className = param.args[0] as? String ?: return
                        if (classWhitelist.any { className.startsWith(it) }) return
                        if (pairipPackages.any { className.startsWith(it) }) {
                            inHook.set(true)
                            try {
                                Logger.log("PairIP: Blocked dynamic load of $className")
                                param.throwable = ClassNotFoundException(className)
                            } finally {
                                inHook.set(false)
                            }
                        }
                    }
                }
            )
            Logger.log("PairIP: ClassLoader protection active")
        } catch (e: Throwable) { Logger.error("blockDynamicPairIP", e) }
    }
}
