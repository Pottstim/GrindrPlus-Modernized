package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class AntiDetection : Hook("Anti-Detection", "Bypasses root, Xposed, debugger, emulator, Frida, and modified APK detection") {

    override fun init() {
        Config.registerHook(name, description, true)
        bypassRootDetection()
        bypassXposedDetection()
        bypassDebuggerDetection()
        bypassEmulatorDetection()
        bypassFridaDetection()
        bypassModifiedApkDetection()
        Logger.log("Anti-Detection: All layers active")
    }

    private fun bypassRootDetection() {
        // Hook common root check methods
        val rootCheckClasses = listOf(
            "com.scottyab.rootbeer.RootBeer",
            "com.topjohnwu.magisk.utils.DenylistDetector",
            "com.topjohnwu.magisk.utils.IS denial"
        )
        rootCheckClasses.forEach { className ->
            try {
                val clazz = lpparam.classLoader.loadClass(className)
                XposedBridge.hookAllMethods(clazz, "isRooted", XC_MethodReplacement.returnConstant(false))
                XposedBridge.hookAllMethods(clazz, "detectRootManagementApps", XC_MethodReplacement.returnConstant(false))
                XposedBridge.hookAllMethods(clazz, "detectPotentiallyDangerousApps", XC_MethodReplacement.returnConstant(false))
                XposedBridge.hookAllMethods(clazz, "detectSuBinary", XC_MethodReplacement.returnConstant(false))
                Logger.log("AntiDetect: Root check bypassed for $className")
            } catch (_: Throwable) { }
        }

        // Block ProcessBuilder / exec for su, magisk, etc.
        try {
            XposedHelpers.findAndHookMethod(
                ProcessBuilder::class.java, "start",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val pb = param.thisObject as ProcessBuilder
                            val command = pb.command().joinToString(" ")
                            if (command.contains("su") || command.contains("magisk") ||
                                command.contains("busybox")) {
                                Logger.log("AntiDetect: Blocked command: $command")
                                pb.command(listOf("echo", ""))
                            }
                        } catch (_: Throwable) {}
                    }
                }
            )
        } catch (e: Throwable) { Logger.error("bypassRootDetection:ProcessBuilder", e) }

        // Hide Magisk / SU files
        val dangerousPaths = listOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/sdcard/magisk", "/data/adb/magisk", "/cache/magisk.log"
        )
        dangerousPaths.forEach { path ->
            try {
                val fileClass = File::class.java
                XposedHelpers.findAndHookConstructor(fileClass, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val file = param.thisObject as File
                                if (file.absolutePath == path) {
                                    val field = File::class.java.getDeclaredField("path")
                                    field.isAccessible = true
                                    field.set(param.thisObject, "/dev/null/nonexistent")
                                }
                            } catch (_: Throwable) {}
                        }
                    })
            } catch (_: Throwable) { }
        }
    }

    private fun bypassXposedDetection() {
        // Clear stack trace of Xposed classes
        try {
            XposedHelpers.findAndHookMethod(
                Throwable::class.java, "getStackTrace",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam): Array<StackTraceElement> {
                        try {
                            val original = Throwable().stackTrace
                            return original.filter { element ->
                                !element.className.contains("de.robv.android.xposed") &&
                                !element.className.contains("com.grindrplus") &&
                                !element.methodName.contains("handleHookedMethod") &&
                                !element.methodName.contains("invoke")
                            }.toTypedArray()
                        } catch (_: Throwable) { return emptyArray() }
                    }
                }
            )
        } catch (e: Throwable) { Logger.error("bypassXposedDetection:getStackTrace", e) }

        // Hook ClassLoader to hide Xposed classes
        try {
            XposedHelpers.findAndHookMethod(
                ClassLoader::class.java, "loadClass",
                String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val className = param.args[0] as? String ?: return
                        if (className.contains("de.robv.android.xposed") ||
                            className.contains("com.grindrplus")) {
                            param.throwable = ClassNotFoundException(className)
                        }
                    }
                }
            )
        } catch (e: Throwable) { Logger.error("bypassXposedDetection:loadClass", e) }

        // Hook SystemProperties to hide Xposed
        try {
            val systemProperties = XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(systemProperties, "get", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        if (key.contains("vbox") || key.contains("goldfish") || key.contains("xposed")) {
                            param.result = ""
                        }
                    }
                })
        } catch (_: Throwable) { }
    }

    private fun bypassDebuggerDetection() {
        try {
            val debugClass = Class.forName("android.os.Debug")
            XposedBridge.hookAllMethods(debugClass, "isDebuggerConnected",
                XC_MethodReplacement.returnConstant(false)
            )
        } catch (e: Throwable) { Logger.error("bypassDebuggerDetection", e) }
    }

    private fun bypassEmulatorDetection() {
        try {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", lpparam.classLoader,
                "getDeviceId", XC_MethodReplacement.returnConstant("000000000000000")
            )
        } catch (e: Throwable) { Logger.error("bypassDebuggerDetection", e) }

        try {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", lpparam.classLoader,
                "getNetworkOperatorName", XC_MethodReplacement.returnConstant("T-Mobile")
            )
        } catch (_: Throwable) { }
    }

    /**
     * Issue #7 fix: Read /proc/net/tcp instead of running netstat command
     * Running netstat from the app process is itself a detection vector
     */
    private fun bypassFridaDetection() {
        // Hook java.lang.Runtime.exec to block Frida detection commands
        val runtimeClass = XposedHelpers.findClass(
            "java.lang.Runtime", lpparam.classLoader
        )
        XposedBridge.hookAllMethods(runtimeClass, "exec",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val cmd = try {
                        when (param.args.size) {
                            1 -> param.args[0] as? String ?: ""
                            else -> (param.args[0] as? Array<*>)?.joinToString(" ") ?: ""
                        }
                    } catch (_: Throwable) { "" }
                    if (cmd.contains("netstat") || cmd.contains("frida") || cmd.contains("xposed")) {
                        Logger.log("AntiDetect: Blocked exec: $cmd")
                        param.args[0] = "echo"
                    }
                }
            }
        )

        // Check /proc/net/tcp for Frida's default port (27042) — read-only, no exec
        try {
            val fridaPorts = setOf("69A2", "69A3") // 27042, 27043 in hex
            val tcpFile = File("/proc/net/tcp")
            if (tcpFile.exists()) {
                val content = tcpFile.readText()
                for (port in fridaPorts) {
                    if (content.contains(port)) {
                        Logger.warn("AntiDetect: Frida port detected in /proc/net/tcp!")
                        // Don't modify — just log. Modifying /proc is not possible.
                        // The real fix is to not have Frida running simultaneously.
                    }
                }
            }
        } catch (_: Throwable) { }

        // Hook File.list to hide Frida-related files
        try {
            XposedHelpers.findAndHookMethod(
                File::class.java, "list",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val file = param.thisObject as File
                        val result = param.result as? Array<String> ?: return
                        if (result.any { it.contains("frida") || it.contains("xposed") }) {
                            param.result = result.filter {
                                !it.contains("frida") && !it.contains("xposed")
                            }.toTypedArray()
                        }
                    }
                }
            )
        } catch (_: Throwable) { }
    }

    private fun bypassModifiedApkDetection() {
        // Hook PackageManager to report original package info
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager", lpparam.classLoader,
                "getInstallerPackageName", String::class.java,
                XC_MethodReplacement.returnConstant("com.android.vending")
            )
        } catch (e: Throwable) { Logger.error("bypassModifiedApkDetection", e) }

        // Spoof source dir to look like original install
        try {
            XposedHelpers.findAndHookMethod(
                "android.content.ContextWrapper", lpparam.classLoader,
                "getPackageResourcePath",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val originalPath = param.result as? String ?: return
                            if (originalPath.contains("lspatch") || originalPath.contains("xposed")) {
                                param.result = originalPath.replace(Regex("lspatch[^/]*"), "")
                            }
                        } catch (_: Throwable) {}
                    }
                }
            )
        } catch (_: Throwable) { }
    }
}
