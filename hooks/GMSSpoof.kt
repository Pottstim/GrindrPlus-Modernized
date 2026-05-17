package com.grindrplus.hooks

import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import android.content.pm.PackageInfo
import android.content.pm.Signature

/**
 * GMSSpoof — Spoofs Google Mobile Services responses to bypass Play Integrity checks.
 *
 * Grindr 26.x+ validates:
 * 1. PackageManager.getPackageInfo() signatures match Play Store
 * 2. GoogleApiAvailability.isGooglePlayServicesAvailable() returns SUCCESS
 * 3. SafetyNet/Play Integrity API attestation
 */
class GMSSpoof : Hook("GMS Spoof", "Fakes Google Play Services responses") {

    // Known Grindr Play Store signing certificate hash
    private val PLAY_STORE_SIGNATURE_HASH = "your_signature_hash_here"

    override fun init() {
        spoofPackageManager(lpparam)
        spoofGoogleApiAvailability(lpparam)
        spoofPlayIntegrity(lpparam)
    }

    /**
     * Spoof PackageManager.getPackageInfo() to return valid Play Store signatures.
     */
    private fun spoofPackageManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager",
                lpparam.classLoader,
                "getPackageInfo",
                String::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val pkgName = param.args[0] as? String ?: return
                        if (pkgName == "com.grindrapp.android" || pkgName == "com.android.vending") {
                            try {
                                val packageInfo = param.result as? PackageInfo ?: return
                                // Replace with a valid signature array
                                packageInfo.signatures = arrayOf(
                                    Signature(PLAY_STORE_SIGNATURE_HASH.toByteArray())
                                )
                                Logger.log("GMS: Spoofed signature for $pkgName")
                            } catch (e: Throwable) {
                                // Ignore — signature field may be inaccessible
                            }
                        }
                    }
                }
            )
            Logger.log("GMS: PackageManager hook installed")
        } catch (e: Throwable) {
            Logger.error("GMS: PackageManager hook failed", e)
        }
    }

    /**
     * Spoof GoogleApiAvailability.isGooglePlayServicesAvailable() to return SUCCESS (0).
     */
    private fun spoofGoogleApiAvailability(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Try multiple possible class names for different GMS versions
            val gmsClasses = listOf(
                "com.google.android.gms.common.GoogleApiAvailability",
                "com.google.android.gms.common.GooglePlayServicesUtil"
            )

            gmsClasses.forEach { className ->
                try {
                    val clazz = XposedHelpers.findClass(className, lpparam.classLoader)

                    // Hook isGooglePlayServicesAvailable
                    XposedHelpers.findAndHookMethod(
                        clazz,
                        "isGooglePlayServicesAvailable",
                        android.content.Context::class.java,
                        XC_MethodReplacement.returnConstant(0) // 0 = SUCCESS
                    )

                    // Hook isUserResolvableError
                    try {
                        XposedHelpers.findAndHookMethod(
                            clazz,
                            "isUserResolvableError",
                            Int::class.javaPrimitiveType,
                            XC_MethodReplacement.returnConstant(false)
                        )
                    } catch (_: Throwable) { }

                    Logger.log("GMS: Spoofed $className")
                } catch (_: Throwable) { }
            }
        } catch (e: Throwable) {
            Logger.error("GMS: GoogleApiAvailability hook failed", e)
        }
    }

    /**
     * Spoof Play Integrity API responses.
     */
    private fun spoofPlayIntegrity(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook Play Integrity token generation
            val integrityClasses = listOf(
                "com.google.android.play.core.integrity.IntegrityManager",
                "com.android.vending.integrity.IntegrityService"
            )

            integrityClasses.forEach { className ->
                try {
                    val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                    XposedBridge.hookAllMethods(clazz, "requestIntegrityToken",
                        XC_MethodReplacement.returnConstant(null))
                    Logger.log("GMS: Blocked Play Integrity via $className")
                } catch (_: Throwable) { }
            }
        } catch (e: Throwable) {
            Logger.error("GMS: Play Integrity hook failed", e)
        }
    }
}
