package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.core.RemoteConfig
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class PairIPBlocker : Hook("PairIP Blocker", "Blocks PairIP license verification at runtime") {

    override fun init() {
        Config.registerHook(name, description, true)

        // Merge remote blocked classes with local
        val remoteBlocked = RemoteConfig.getBlockedClasses()
        if (remoteBlocked.isNotEmpty()) {
            Logger.log("PairIP: ${remoteBlocked.size} remote blocked classes loaded")
        }

        blockLicenseActivity()
        blockLicenseProvider()
        blockDynamicPairIP(remoteBlocked)
    }

    private fun blockLicenseActivity() {
        try {
            val targets = listOf(
                "com.pairip.licensecheck.LicenseActivity",
                "com.pairip.licensecheck.LicenseContentProvider"
            )
            targets.forEach { className ->
                try {
                    val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                    XposedBridge.hookAllMethods(clazz, "onCreate", XC_MethodReplacement.returnConstant(null))
                    Logger.log("PairIP: Blocked $className.onCreate")
                } catch (_: Throwable) { }
            }
        } catch (e: Throwable) { Logger.error("blockLicenseActivity", e) }
    }

    private fun blockLicenseProvider() {
        try {
            val resolver = lpparam.classLoader.loadClass("android.content.ContentResolver")
            XposedHelpers.findAndHookMethod(resolver, "call",
                android.net.Uri::class.java, String::class.java, String::class.java, android.os.Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val uri = param.args[0] as? android.net.Uri ?: return
                        if (uri.authority?.contains("pairip") == true) {
                            param.result = null
                            Logger.d("PairIP: Blocked ContentResolver.call for $uri")
                        }
                    }
                })
        } catch (e: Throwable) { Logger.error("blockLicenseProvider", e) }
    }

    private fun blockDynamicPairIP(remoteBlocked: List<String>) {
        try {
            val classLoader = lpparam.classLoader

            // Block local pairipcore classes
            val localPatterns = listOf("com.pairipcore.", "com.pairip.licensecheck.")

            // Merge with remote patterns
            val allPatterns = localPatterns + remoteBlocked

            // Hook ClassLoader to intercept pairip class loading
            val threadClass = XposedHelpers.findClass("java.lang.Thread", classLoader)
            XposedHelpers.findAndHookMethod(threadClass, "getContextClassLoader",
                XC_MethodReplacement.returnConstant(object : ClassLoader(classLoader) {
                    override fun loadClass(name: String): Class<*> {
                        if (allPatterns.any { name.contains(it) }) {
                            Logger.log("PairIP: Intercepted class load: $name")
                            throw ClassNotFoundException(name)
                        }
                        return super.loadClass(name)
                    }
                }))

            // Also try to directly hook any already-loaded pairipcore classes
            try {
                val loadedClass = XposedHelpers.findClass("com.pairipcore.PairIPCore", classLoader)
                XposedBridge.hookAllMethods(loadedClass, "verify", XC_MethodReplacement.returnConstant(true))
                XposedBridge.hookAllMethods(loadedClass, "check", XC_MethodReplacement.returnConstant(true))
                Logger.log("PairIP: Directly hooked PairIPCore")
            } catch (_: Throwable) { }

            Logger.log("PairIP: Dynamic blocking active (${allPatterns.size} patterns)")
        } catch (e: Throwable) { Logger.error("blockDynamicPairIP", e) }
    }
}
