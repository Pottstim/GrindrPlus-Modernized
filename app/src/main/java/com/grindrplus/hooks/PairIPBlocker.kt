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
class PairIPBlocker : Hook("PairIP Blocker", "Blocks PairIP license checks — Java + native layer") {

    private val pairipPackages = setOf(
        "com.pairip.licensecheck",
        "com.pairipcore"
    )

    // Whitelist: classes that must always be loadable even when PairIP is blocked
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
        blockNativePairIP()
        Logger.log("PairIP: Full block active (Java + native)")
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

        // Block query() on PairIP URIs
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
     * Issue #6 fix: Safer ClassLoader with whitelist
     * Only blocks PairIP classes, never Android framework or app classes
     */
    private fun blockDynamicPairIP() {
        try {
            XposedHelpers.findAndHookMethod(
                ClassLoader::class.java, "loadClass",
                String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val className = param.args[0] as? String ?: return

                        // Check whitelist first — never block framework/app classes
                        if (classWhitelist.any { className.startsWith(it) }) return

                        // Only block PairIP packages
                        if (pairipPackages.any { className.startsWith(it) }) {
                            Logger.log("PairIP: Blocked dynamic load of $className")
                            param.throwable = ClassNotFoundException(className)
                        }
                    }
                }
            )
            Logger.log("PairIP: ClassLoader protection active")
        } catch (e: Throwable) { Logger.error("blockDynamicPairIP", e) }
    }

    /**
     * Issue #11 fix: Native libpairip.so blocking
     * Hooks System.loadLibrary and Runtime.load to prevent native PairIP init
     */
    private fun blockNativePairIP() {
        val nativeLibs = setOf("pairip", "pairipcore", "libpairip")

        // Hook System.loadLibrary
        try {
            XposedHelpers.findAndHookMethod(
                System::class.java, "loadLibrary", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val libName = param.args[0] as? String ?: return
                        if (nativeLibs.any { libName.contains(it, ignoreCase = true) }) {
                            Logger.log("PairIP: Blocked native loadLibrary($libName)")
                            // Don't call original — just return silently
                            param.result = null
                        }
                    }
                }
            )
        } catch (e: Throwable) { Logger.error("blockNativePairIP:loadLibrary", e) }

        // Hook System.load (full path version)
        try {
            XposedHelpers.findAndHookMethod(
                System::class.java, "load", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val path = param.args[0] as? String ?: return
                        if (nativeLibs.any { path.contains(it, ignoreCase = true) }) {
                            Logger.log("PairIP: Blocked native load($path)")
                            param.result = null
                        }
                    }
                }
            )
        } catch (e: Throwable) { Logger.error("blockNativePairIP:load", e) }

        // Hook Runtime.loadLibrary0 (deeper hook for apps that bypass System.loadLibrary)
        try {
            XposedHelpers.findAndHookMethod(
                Runtime::class.java, "loadLibrary0",
                ClassLoader::class.java, String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val libName = param.args[1] as? String ?: return
                        if (nativeLibs.any { libName.contains(it, ignoreCase = true) }) {
                            Logger.log("PairIP: Blocked Runtime.loadLibrary0($libName)")
                            param.result = null
                        }
                    }
                }
            )
        } catch (e: Throwable) { Logger.error("blockNativePairIP:loadLibrary0", e) }
    }
}
