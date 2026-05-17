package com.grindrplus.hooks

import com.grindrplus.core.Config
import de.robv.android.xposed.XposedBridge
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers

class GMSSpoof : Hook("GMS Spoof", "Spoofs Google Play Services signature and package manager") {
    override fun init() {
        Config.registerHook(name, description, true)
        spoofPackageManager()
        spoofSignature()
    }

    private fun spoofPackageManager() {
        try {
            val pmClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(pmClass, "getPackageInfo",
                String::class.java, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args[0] as? String ?: return
                        if (pkg == "com.grindrapp.android") {
                            // Let it through — we'll fix the signature in spoofSignature
                        }
                    }
                })
            Logger.log("GMS: PackageManager spoof active")
        } catch (e: Throwable) { Logger.error("spoofPackageManager", e) }
    }

    private fun spoofSignature() {
        try {
            val sigClass = XposedHelpers.findClass("android.content.pm.Signature", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(sigClass, "toByteArray",
                XC_MethodReplacement.returnConstant(ByteArray(256) { 0 }))
            Logger.log("GMS: Signature spoof active")
        } catch (e: Throwable) { Logger.error("spoofSignature", e) }
    }
}
