package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File

/**
 * Fix #3: AntiDetection global hook side effects resolved.
 *
 * Issues addressed:
 *  a) ClassLoader.loadClass hook — added a ThreadLocal re-entrancy guard to prevent
 *     infinite ClassNotFoundException loops when the hook itself triggers class loading.
 *  b) Throwable.getStackTrace hook — added a ThreadLocal re-entrancy guard so that
 *     exceptions thrown inside the filter lambda don't recursively invoke the hook.
 *  c) File constructor hook — replaced per-path constructor hooks (which registered
 *     N identical hooks for N paths) with a single constructor hook that checks all
 *     dangerous paths in one pass.
 *  d) bypassModifiedApkDetection: getPackageResourcePath used beforeHookedMethod to
 *     read param.result (which is always null before the original runs). Fixed to use
 *     afterHookedMethod instead.
 *  e) bypassEmulatorDetection error log tag was incorrectly labelled "bypassDebuggerDetection".
 *     Fixed to "bypassEmulatorDetection".
 */
class AntiDetection : Hook("Anti-Detection", "Bypasses root, Xposed, debugger, emulator, Frida, and modified APK detection") {

    // Re-entrancy guards — prevent hooks from recursively triggering themselves
    private val inClassLoaderHook = ThreadLocal.withInitial { false }
    private val inStackTraceHook  = ThreadLocal.withInitial { false }

    private val dangerousPaths = setOf(
        "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
        "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
        "/sdcard/magisk", "/data/adb/magisk", "/cache/magisk.log"
    )

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
        // Hook common root-check library methods
        val rootCheckClasses = listOf(
            "com.scottyab.rootbeer.RootBeer",
            "com.topjohnwu.magisk.utils.DenylistDetector"
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

        // Block ProcessBuilder / exec for su, magisk, busybox
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

        // Fix #3c: Single File constructor hook that checks all dangerous paths in one pass
        try {
            XposedHelpers.findAndHookConstructor(
                File::class.java, String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val file = param.thisObject as File
                            if (file.absolutePath in dangerousPaths) {
                                val field = File::class.java.getDeclaredField("path")
                                field.isAccessible = true
                                field.set(param.thisObject, "/dev/null/nonexistent")
                            }
                        } catch (_: Throwable) {}
                    }
                }
            )
        } catch (_: Throwable) { }
    }

    private fun bypassXposedDetection() {
        // Fix #3b: Re-entrancy guard on Throwable.getStackTrace to prevent infinite loops
        try {
            XposedHelpers.findAndHookMethod(
                Throwable::class.java, "getStackTrace",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Array<StackTraceElement> {
                        if (inStackTraceHook.get()) {
                            // Re-entrant call — return empty to break the loop
                            return emptyArray()
                        }
                        inStackTraceHook.set(true)
                        return try {
                            val original = Throwable().stackTrace
                            original.filter { element ->
                                !element.className.contains("de.robv.android.xposed") &&
                                !element.className.contains("com.grindrplus") &&
                                !element.methodName.contains("handleHookedMethod") &&
                                !element.methodName.contains("invoke")
                            }.toTypedArray()
                        } catch (_: Throwable) { emptyArray() }
                        finally { inStackTraceHook.set(false) }
                    }
                }
            )
        } catch (e: Throwable) { Logger.error("bypassXposedDetection:getStackTrace", e) }

        // Fix #3a: Re-entrancy guard on ClassLoader.loadClass to prevent ClassNotFoundException loops
        try {
            XposedHelpers.findAndHookMethod(
                ClassLoader::class.java, "loadClass",
                String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (inClassLoaderHook.get()) return  // Re-entrant — skip
                        val className = param.args[0] as? String ?: return
                        if (className.startsWith("de.robv.android.xposed") ||
                            className.startsWith("com.grindrplus")) {
                            inClassLoaderHook.set(true)
                            try {
                                param.throwable = ClassNotFoundException(className)
                            } finally {
                                inClassLoaderHook.set(false)
                            }
                        }
                    }
                }
            )
        } catch (e: Throwable) { Logger.error("bypassXposedDetection:loadClass", e) }

        // Hook SystemProperties to hide Xposed / emulator markers
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
        // Fix #3e: Corrected error log tag from "bypassDebuggerDetection" → "bypassEmulatorDetection"
        try {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", lpparam.classLoader,
                "getDeviceId", XC_MethodReplacement.returnConstant("000000000000000")
            )
        } catch (e: Throwable) { Logger.error("bypassEmulatorDetection:getDeviceId", e) }

        try {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", lpparam.classLoader,
                "getNetworkOperatorName", XC_MethodReplacement.returnConstant("T-Mobile")
            )
        } catch (_: Throwable) { }
    }

    private fun bypassFridaDetection() {
        // Hook java.lang.Runtime.exec to block Frida detection commands
        try {
            val runtimeClass = XposedHelpers.findClass("java.lang.Runtime", lpparam.classLoader)
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
        } catch (e: Throwable) { Logger.error("bypassFridaDetection:exec", e) }

        // Check /proc/net/tcp for Frida's default port (27042) — read-only, no exec
        try {
            val fridaPorts = setOf("69A2", "69A3") // 27042, 27043 in hex
            val tcpFile = File("/proc/net/tcp")
            if (tcpFile.exists()) {
                val content = tcpFile.readText()
                for (port in fridaPorts) {
                    if (content.contains(port)) {
                        Logger.warn("AntiDetect: Frida port detected in /proc/net/tcp!")
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
        // Hook PackageManager to report original installer
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager", lpparam.classLoader,
                "getInstallerPackageName", String::class.java,
                XC_MethodReplacement.returnConstant("com.android.vending")
            )
        } catch (e: Throwable) { Logger.error("bypassModifiedApkDetection:getInstallerPackageName", e) }

        // Fix #3d: Changed beforeHookedMethod → afterHookedMethod so param.result is populated
        try {
            XposedHelpers.findAndHookMethod(
                "android.content.ContextWrapper", lpparam.classLoader,
                "getPackageResourcePath",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
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
