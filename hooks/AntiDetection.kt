package com.grindrplus.hooks

import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

/**
 * AntiDetection — Hides root, LSPosed/LSPatch, and debugger presence from Grindr.
 *
 * Grindr checks for:
 * 1. Root binaries (su, magisk, etc.)
 * 2. Xposed/LSPosed framework presence
 * 3. Debugger attachment
 * 4. Emulator detection
 * 5. Modified APK signature
 */
class AntiDetection : Hook("Anti-Detection", "Hides root, LSPosed, and debugger") {

    override fun init() {
        hideRootBinaries(lpparam)
        hideXposedFramework(lpparam)
        hideDebugger(lpparam)
        hideEmulator(lpparam)
        hideModifiedApk(lpparam)
    }

    private fun hideRootBinaries(lpparam: XC_LoadPackage.LoadPackageParam) {
        val rootPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/app/Superuser.apk", "/system/app/SuperSU.apk",
            "/data/adb/magisk", "/sbin/.magisk",
            "/system/bin/magisk", "/data/adb/magisk.img",
            "/data/adb/magisk.db", "/cache/.disable_magisk"
        )

        // Hook File.exists() to hide root files
        try {
            XposedHelpers.findAndHookMethod(
                File::class.java, "exists",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val file = param.thisObject as? File ?: return
                        if (rootPaths.any { file.absolutePath.startsWith(it) }) {
                            param.result = false
                        }
                    }
                }
            )
            Logger.log("AntiDetect: File.exists() hooked")
        } catch (e: Throwable) {
            Logger.error("AntiDetect: File.exists() hook failed", e)
        }

        // Hook Runtime.exec() to hide root commands
        try {
            XposedHelpers.findAndHookMethod(
                Runtime::class.java, "exec",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val cmd = param.args[0] as? String ?: return
                        if (cmd.contains("su") || cmd.contains("magisk") ||
                            cmd.contains("which") || cmd.contains("busybox")
                        ) {
                            param.throwable = java.io.IOException("Permission denied")
                        }
                    }
                }
            )
            Logger.log("AntiDetect: Runtime.exec() hooked")
        } catch (e: Throwable) {
            Logger.error("AntiDetect: Runtime.exec() hook failed", e)
        }
    }

    private fun hideXposedFramework(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook StackTraceElement to remove Xposed frames
        try {
            XposedHelpers.findAndHookMethod(
                Throwable::class.java, "getStackTrace",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val stack = param.result as? Array<*> ?: return
                        @Suppress("UNCHECKED_CAST")
                        val filtered = (stack as Array<StackTraceElement>).filter {
                            !it.className.contains("de.robv.android.xposed") &&
                            !it.className.contains("com.saurik.substrate") &&
                            !it.className.contains("com.topjohnwu.magisk") &&
                            !it.className.contains("org.lsposed") &&
                            !it.className.contains("io.github.lsposed")
                        }.toTypedArray()
                        param.result = filtered
                    }
                }
            )
            Logger.log("AntiDetect: StackTrace hooked")
        } catch (e: Throwable) {
            Logger.error("AntiDetect: StackTrace hook failed", e)
        }

        // Hook ClassLoader to hide Xposed classes
        try {
            XposedHelpers.findAndHookMethod(
                Class::class.java, "forName",
                String::class.java, Boolean::class.javaPrimitiveType, ClassLoader::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val name = param.args[0] as? String ?: return
                        if (name.contains("de.robv.android.xposed") ||
                            name.contains("com.saurik.substrate") ||
                            name.contains("org.lsposed")
                        ) {
                            param.throwable = ClassNotFoundException(name)
                        }
                    }
                }
            )
            Logger.log("AntiDetect: Class.forName hooked")
        } catch (e: Throwable) {
            Logger.error("AntiDetect: Class.forName hook failed", e)
        }
    }

    private fun hideDebugger(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook Debug.isDebuggerConnected()
        try {
            XposedHelpers.findAndHookMethod(
                android.os.Debug::class.java, "isDebuggerConnected",
                XC_MethodReplacement.returnConstant(false)
            )
            Logger.log("AntiDetect: Debug.isDebuggerConnected hooked")
        } catch (e: Throwable) {
            Logger.error("AntiDetect: Debug hook failed", e)
        }

        // Hook ApplicationInfo flags to remove DEBUGGABLE
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager",
                lpparam.classLoader,
                "getApplicationInfo",
                String::class.java, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val info = param.result as? android.content.pm.ApplicationInfo ?: return
                        info.flags = info.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE.inv()
                    }
                }
            )
            Logger.log("AntiDetect: ApplicationInfo flags hooked")
        } catch (e: Throwable) {
            Logger.error("AntiDetect: ApplicationInfo hook failed", e)
        }
    }

    private fun hideEmulator(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook Build properties to return real device values
        val buildFakes = mapOf(
            "ro.build.tags" to "release-keys",
            "ro.build.type" to "user",
            "ro.debuggable" to "0",
            "ro.secure" to "1",
            "ro.product.model" to "SM-S928B", // Galaxy S24 Ultra
            "ro.product.brand" to "samsung",
            "ro.product.name" to "dm3q",
            "ro.product.device" to "dm3q",
            "ro.hardware" to "qcom",
            "ro.boot.hardware" to "qcom",
            "ro.kernel.qemu" to ""
        )

        try {
            XposedHelpers.findAndHookMethod(
                android.os.SystemProperties::class.java, "get",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        buildFakes[key]?.let { param.result = it }
                    }
                }
            )
            Logger.log("AntiDetect: SystemProperties hooked")
        } catch (e: Throwable) {
            Logger.error("AntiDetect: SystemProperties hook failed", e)
        }
    }

    private fun hideModifiedApk(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook PackageManager to hide signature modifications
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager",
                lpparam.classLoader,
                "getPackageInfo",
                String::class.java, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val flags = param.args[1] as? Int ?: return
                        // Remove GET_SIGNATURES flag to prevent signature checks
                        if (flags and android.content.pm.PackageManager.GET_SIGNATURES != 0) {
                            param.args[1] = flags and android.content.pm.PackageManager.GET_SIGNATURES.inv()
                        }
                        // Remove GET_SIGNING_CERTIFICATES flag (API 28+)
                        if (flags and 0x8000000 != 0) { // GET_SIGNING_CERTIFICATES
                            param.args[1] = flags and 0x8000000.inv()
                        }
                    }
                }
            )
            Logger.log("AntiDetect: PackageManager signature check hooked")
        } catch (e: Throwable) {
            Logger.error("AntiDetect: PackageManager hook failed", e)
        }
    }
}
