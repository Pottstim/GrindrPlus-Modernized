package com.grindrplus.hooks

import com.grindrplus.utils.DynamicHookResolver
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.net.URL

/**
 * PairIPBlocker — Comprehensive PairIP license check bypass for Grindr 26.x+
 *
 * PairIP protection layers:
 * 1. LicenseActivity.onCreate() — Shows "Get from Play Store" dialog
 * 2. LicenseContentProvider — Validates license via ContentProvider CRUD
 * 3. Native libpairpcore.so — JNI-based integrity verification
 * 4. Network validation — Server-side license check
 *
 * This hook blocks ALL four layers.
 */
class PairIPBlocker : Hook("PairIP Blocker", "Bypasses all PairIP license checks") {

    override fun init() {
        blockLicenseActivity(lpparam)
        blockLicenseContentProvider(lpparam)
        blockNativeLibraryLoad(lpparam)
        blockNetworkValidation(lpparam)
        blockPairIPReflection(lpparam)
    }

    /**
     * Block LicenseActivity from showing the "Get from Play Store" dialog.
     * FIXED: Uses exact class names, not wildcards.
     */
    private fun blockLicenseActivity(lpparam: XC_LoadPackage.LoadPackageParam) {
        val targets = listOf(
            "com.pairip.licensecheck.LicenseActivity",
            "com.pairip.licensecheck.LicenseContentProvider"
        )

        targets.forEach { className ->
            try {
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                XposedBridge.hookAllMethods(clazz, "onCreate", XC_MethodReplacement.returnConstant(null))
                Logger.log("PairIP: Blocked $className.onCreate()")
            } catch (e: Throwable) {
                // Class may be obfuscated in this version — try dynamic resolution
                Logger.log("PairIP: $className not found directly, trying dynamic scan...")
                tryDynamicScan(lpparam, className)
            }
        }
    }

    /**
     * Block all LicenseContentProvider CRUD operations.
     */
    private fun blockLicenseContentProvider(lpparam: XC_LoadPackage.LoadPackageParam) {
        val providerMethods = listOf("query", "insert", "update", "delete", "getType", "openFile")

        try {
            val clazz = XposedHelpers.findClass(
                "com.pairip.licensecheck.LicenseContentProvider",
                lpparam.classLoader
            )
            providerMethods.forEach { method ->
                try {
                    XposedBridge.hookAllMethods(clazz, method, XC_MethodReplacement.returnConstant(null))
                } catch (_: Throwable) { }
            }
            Logger.log("PairIP: Blocked LicenseContentProvider CRUD")
        } catch (e: Throwable) {
            Logger.log("PairIP: LicenseContentProvider not found (may be obfuscated)")
        }
    }

    /**
     * Block native library loading for libpairpcore.so.
     * FIXED: Also hooks System.loadLibrary, not just ClassLoader.
     */
    private fun blockNativeLibraryLoad(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pairipLibs = listOf("pairipcore", "pairip", "libpairipcore")

        // Hook ClassLoader.loadClass (for System.loadLibrary path)
        try {
            XposedHelpers.findAndHookMethod(
                ClassLoader::class.java,
                "loadClass",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val name = param.args[0] as? String ?: return
                        if (pairipLibs.any { name.contains(it, ignoreCase = true) }) {
                            Logger.log("PairIP: Blocked class load for $name")
                            param.throwable = ClassNotFoundException(name)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            Logger.error("PairIP: ClassLoader hook failed", e)
        }

        // Hook Runtime.loadLibrary0 for native .so loading
        try {
            XposedHelpers.findAndHookMethod(
                Runtime::class.java,
                "loadLibrary0",
                Class::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val libName = param.args[1] as? String ?: return
                        if (pairipLibs.any { libName.contains(it, ignoreCase = true) }) {
                            Logger.log("PairIP: Blocked native load for $libName")
                            param.result = void
                        }
                    }
                }
            )
            Logger.log("PairIP: Blocked native library loading")
        } catch (e: Throwable) {
            Logger.error("PairIP: Runtime.loadLibrary0 hook failed", e)
        }
    }

    /**
     * Block PairIP network validation calls.
     */
    private fun blockNetworkValidation(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                URL::class.java,
                "openConnection",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val url = (param.thisObject as? URL)?.toString() ?: return
                        if (url.contains("pairip", ignoreCase = true) ||
                            url.contains("license", ignoreCase = true)
                        ) {
                            Logger.log("PairIP: Blocked network call to $url")
                            param.throwable = java.io.IOException("Blocked by GrindrPlus")
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            Logger.error("PairIP: Network block hook failed", e)
        }
    }

    /**
     * Block PairIP reflection-based detection.
     */
    private fun blockPairIPReflection(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                Class::class.java,
                "forName",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val name = param.args[0] as? String ?: return
                        if (name.startsWith("com.pairip")) {
                            Logger.log("PairIP: Blocked Class.forName($name)")
                            param.throwable = ClassNotFoundException(name)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            Logger.error("PairIP: Reflection block hook failed", e)
        }
    }

    /**
     * Fallback: scan loaded classes by pattern when exact name fails.
     */
    private fun tryDynamicScan(lpparam: XC_LoadPackage.LoadPackageParam, targetName: String) {
        try {
            val resolver = DynamicHookResolver(lpparam)
            val pattern = Regex(".*pairip.*", RegexOption.IGNORE_CASE)
            val clazz = resolver.findClassByPattern(pattern)
            if (clazz != null) {
                XposedBridge.hookAllMethods(clazz, "onCreate", XC_MethodReplacement.returnConstant(null))
                Logger.log("PairIP: Dynamically blocked ${clazz.name}")
            }
        } catch (e: Throwable) {
            Logger.error("PairIP: Dynamic scan failed for $targetName", e)
        }
    }
}
