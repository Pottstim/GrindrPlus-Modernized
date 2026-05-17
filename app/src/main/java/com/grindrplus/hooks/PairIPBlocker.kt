package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class PairIPBlocker : Hook("PairIP Blocker", "Blocks PairIP license verification at runtime") {
    override fun init() {
        Config.registerHook(name, description, true)
        blockLicenseActivity()
        blockLicenseProvider()
        blockDynamicPairIP()
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
                            Logger.log("PairIP: Blocked ContentResolver.call for $uri")
                        }
                    }
                })
        } catch (e: Throwable) { Logger.error("blockLicenseProvider", e) }
    }

    private fun blockDynamicPairIP() {
        try {
            val classLoader = lpparam.classLoader
            val loadedClasses = mutableSetOf<String>()
            val originalLoadClass = classLoader::class.java.getMethod("loadClass", String::class.java)
            // Scan for pairipcore classes via class loader interception
            val threadClass = XposedHelpers.findClass("java.lang.Thread", classLoader)
            XposedHelpers.findAndHookMethod(threadClass, "getContextClassLoader",
                XC_MethodReplacement.returnConstant(object : ClassLoader(classLoader) {
                    override fun loadClass(name: String): Class<*> {
                        if (name.contains("pairipcore") || name.contains("pairip.licensecheck")) {
                            Logger.log("PairIP: Intercepted class load: $name")
                            throw ClassNotFoundException(name)
                        }
                        return super.loadClass(name)
                    }
                }))
        } catch (e: Throwable) { Logger.error("blockDynamicPairIP", e) }
    }
}
