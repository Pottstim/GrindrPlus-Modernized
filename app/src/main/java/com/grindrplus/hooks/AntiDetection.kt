package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File

class AntiDetection : Hook("Anti-Detection", "Hides root, LSPosed, debugger, emulator, and modified APK") {
    override fun init() {
        Config.registerHook(name, description, true)
        hideRoot()
        hideXposed()
        hideDebugger()
        hideEmulator()
    }

    private fun hideRoot() {
        try {
            val fileClass = XposedHelpers.findClass("java.io.File", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(fileClass, "exists", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val file = param.thisObject as? File ?: return
                    val path = file.absolutePath
                    if (path.contains("/su") || path.contains("/magisk") ||
                        path.contains("supersu") || path.contains("Superuser")) {
                        param.result = false
                    }
                }
            })
            Logger.log("AntiDetection: Root hiding active")
        } catch (e: Throwable) { Logger.error("hideRoot", e) }
    }

    private fun hideXposed() {
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.Class", lpparam.classLoader, "forName",
                String::class.java, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val name = param.args[0] as? String ?: return
                        if (name.contains("de.robv.android.xposed") ||
                            name.contains("org.lposed") ||
                            name.contains("io.github.lsposed")) {
                            param.throwable = ClassNotFoundException(name)
                        }
                    }
                })
            Logger.log("AntiDetection: Xposed hiding active")
        } catch (e: Throwable) { Logger.error("hideXposed", e) }
    }

    private fun hideDebugger() {
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.Debug", lpparam.classLoader,
                "isDebuggerConnected", XC_MethodReplacement.returnConstant(false))
            Logger.log("AntiDetection: Debugger hiding active")
        } catch (e: Throwable) { Logger.error("hideDebugger", e) }
    }

    private fun hideEmulator() {
        try {
            val buildClass = XposedHelpers.findClass("android.os.Build", lpparam.classLoader)
            XposedHelpers.setStaticObjectField(buildClass, "FINGERPRINT", "google/redfin/redfin:13/TP1A.221005.002/9012097:user/release-keys")
            XposedHelpers.setStaticObjectField(buildClass, "MODEL", "Pixel 5")
            XposedHelpers.setStaticObjectField(buildClass, "MANUFACTURER", "Google")
            XposedHelpers.setStaticObjectField(buildClass, "BRAND", "google")
            XposedHelpers.setStaticObjectField(buildClass, "PRODUCT", "redfin")
            XposedHelpers.setStaticObjectField(buildClass, "DEVICE", "redfin")
            Logger.log("AntiDetection: Emulator hiding active")
        } catch (e: Throwable) { Logger.error("hideEmulator", e) }
    }
}
