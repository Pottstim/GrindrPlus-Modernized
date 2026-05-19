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
import java.io.InputStreamReader

class AntiDetection : Hook("Anti-Detection", "Hides root, LSPosed, debugger, emulator, Frida, and modified APK") {

    override fun init() {
        Config.registerHook(name, description, true)

        // Check for Frida — crash silently if detected
        if (isFridaDetected()) {
            Logger.warn("Frida detected! Entering clean mode.")
            return
        }

        hideRoot()
        hideXposed()
        hideDebugger()
        hideEmulator()
        hideFrida()
        spoofBuildProperties()
        hideModifiedApk()
    }

    private fun isFridaDetected(): Boolean {
        return try {
            // Check for Frida server port
            val process = Runtime.getRuntime().exec("netstat -tlnp")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line?.contains("27042") == true || line?.contains("27043") == true) {
                    reader.close()
                    return true
                }
            }
            reader.close()

            // Check /proc/self/maps for frida
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                val content = mapsFile.readText()
                if (content.contains("frida") || content.contains("gadget") || content.contains("linjector")) {
                    return true
                }
            }

            // Check for Frida-specific files
            val fridaFiles = listOf(
                "/data/local/tmp/frida-server",
                "/data/local/tmp/frida-gadget",
                "/data/local/tmp/re.frida.server"
            )
            fridaFiles.any { File(it).exists() }
        } catch (_: Throwable) { false }
    }

    private fun hideRoot() {
        try {
            val fileClass = XposedHelpers.findClass("java.io.File", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(fileClass, "exists", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val file = param.thisObject as? File ?: return
                    val path = file.absolutePath
                    if (path.contains("/su") || path.contains("/magisk") ||
                        path.contains("supersu") || path.contains("Superuser") ||
                        path.contains("/sbin/.magisk") || path.contains("/system/xbin/su") ||
                        path.contains("/system/bin/su") || path.contains("/data/adb/magisk")) {
                        param.result = false
                    }
                }
            })

            // Hook ProcessBuilder to block su commands
            val processBuilder = XposedHelpers.findClass("java.lang.ProcessBuilder", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(processBuilder, "start", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val command = param.thisObject as? ProcessBuilder ?: return
                    val cmdList = try {
                        val field = ProcessBuilder::class.java.getDeclaredField("command")
                        field.isAccessible = true
                        field.get(command) as? List<*>
                    } catch (_: Throwable) { null }
                    val cmd = cmdList?.joinToString(" ") ?: ""
                    if (cmd.contains("su") || cmd.contains("magisk") || cmd.contains("which")) {
                        param.throwable = java.io.IOException("Permission denied")
                    }
                }
            })

            Logger.log("AntiDetection: Root hiding active")
        } catch (e: Throwable) { Logger.error("hideRoot", e) }
    }

    private fun hideXposed() {
        try {
            // Block Class.forName for Xposed classes
            XposedHelpers.findAndHookMethod(
                "java.lang.Class", lpparam.classLoader, "forName",
                String::class.java, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val name = param.args[0] as? String ?: return
                        if (name.contains("de.robv.android.xposed") ||
                            name.contains("org.lsposed") ||
                            name.contains("io.github.lsposed") ||
                            name.contains("com.topjohnwu.magisk") ||
                            name.contains("me.weishu.exp")) {
                            param.throwable = ClassNotFoundException(name)
                        }
                    }
                })

            // Block ClassLoader.loadClass for Xposed
            XposedHelpers.findAndHookMethod(
                "java.lang.ClassLoader", lpparam.classLoader, "loadClass",
                String::class.java, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val name = param.args[0] as? String ?: return
                        if (name.contains("de.robv.android.xposed") ||
                            name.contains("org.lsposed") ||
                            name.contains("io.github.lsposed")) {
                            param.throwable = ClassNotFoundException(name)
                        }
                    }
                })

            // Hook PackageManager to hide LSPatch/GrindrPlus
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager", lpparam.classLoader,
                "getPackageInfo", String::class.java, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args[0] as? String ?: return
                        if (pkg.contains("org.lsposed") ||
                            pkg.contains("io.github.lsposed") ||
                            pkg.contains("com.grindrplus")) {
                            param.throwable = android.content.pm.PackageManager.NameNotFoundException(pkg)
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

            // Also hook via SystemProperties
            XposedHelpers.findAndHookMethod(
                "android.os.SystemProperties", lpparam.classLoader,
                "get", String::class.java, String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        if (key.contains("debug") || key.contains("adb")) {
                            param.result = param.args[1] // return default value
                        }
                    }
                })

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
            XposedHelpers.setStaticObjectField(buildClass, "HARDWARE", "redfin")
            XposedHelpers.setStaticObjectField(buildClass, "BOARD", "redfin")
            XposedHelpers.setStaticObjectField(buildClass, "BOOTLOADER", "unknown")
            XposedHelpers.setStaticObjectField(buildClass, "RADIO", "unknown")
            XposedHelpers.setStaticObjectField(buildClass, "SERIAL", "unknown")
            Logger.log("AntiDetection: Emulator hiding active")
        } catch (e: Throwable) { Logger.error("hideEmulator", e) }
    }

    private fun hideFrida() {
        try {
            // Hook File.list to hide Frida files
            XposedHelpers.findAndHookMethod("java.io.File", lpparam.classLoader,
                "list", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val result = param.result as? Array<*> ?: return
                        val filtered = result.filter {
                            val s = it?.toString() ?: return@filter true
                            !s.contains("frida") && !s.contains("gadget") && !s.contains("linjector")
                        }.toTypedArray()
                        if (filtered.size != result.size) {
                            param.result = filtered
                        }
                    }
                })

            // Hook Runtime.exec to block Frida detection commands
            XposedHelpers.findAndHookMethod("java.lang.Runtime", lpparam.classLoader,
                "exec", String::class.java, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val cmd = param.args[0] as? String ?: return
                        if (cmd.contains("frida") || cmd.contains("gadget") || cmd.contains("linjector")) {
                            param.throwable = java.io.IOException("No such file or directory")
                        }
                    }
                })

            Logger.log("AntiDetection: Frida hiding active")
        } catch (e: Throwable) { Logger.error("hideFrida", e) }
    }

    private fun spoofBuildProperties() {
        try {
            // Hook SystemProperties.get for common detection keys
            val sysProps = XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(sysProps, "get",
                String::class.java, String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        when {
                            key == "ro.build.tags" -> param.result = "release-keys"
                            key == "ro.build.type" -> param.result = "user"
                            key == "ro.debuggable" -> param.result = "0"
                            key == "ro.secure" -> param.result = "1"
                            key == "ro.build.selinux" -> param.result = "1"
                            key == "ro.product.model" -> param.result = "Pixel 5"
                            key == "ro.product.manufacturer" -> param.result = "Google"
                            key == "ro.product.brand" -> param.result = "google"
                        }
                    }
                })
            Logger.log("AntiDetection: Build property spoofing active")
        } catch (e: Throwable) { Logger.error("spoofBuildProperties", e) }
    }

    private fun hideModifiedApk() {
        try {
            // Hook PackageManager.getApplicationInfo to hide modified flags
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager", lpparam.classLoader,
                "getApplicationInfo", String::class.java, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val info = param.result as? android.content.pm.ApplicationInfo ?: return
                        // Clear flags that indicate modification
                        info.flags = info.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE.inv()
                    }
                })
            Logger.log("AntiDetection: Modified APK hiding active")
        } catch (e: Throwable) { Logger.error("hideModifiedApk", e) }
    }
}
