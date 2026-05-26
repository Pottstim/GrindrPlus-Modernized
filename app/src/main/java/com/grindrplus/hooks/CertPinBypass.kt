package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers

/**
 * Issue #13: Certificate pinning bypass
 * Hooks OkHttp CertificatePinner, TrustManager, and WebView SSL error handler
 * to allow inspection/interception of HTTPS traffic.
 */
class CertPinBypass : Hook("Cert Pin Bypass", "Bypasses certificate pinning for HTTPS interception") {

    override fun init() {
        Config.registerHook(name, description, false) // Disabled by default — enable only if needed
        if (!Config.isHookEnabled(name)) return
        bypassOkHttpPinning()
        bypassTrustManager()
        bypassWebViewSSL()
        Logger.log("CertPinBypass: Active")
    }

    private fun bypassOkHttpPinning() {
        try {
            val certificatePinner = XposedHelpers.findClass(
                "okhttp3.CertificatePinner", lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(certificatePinner, "check",
                String::class.java, List::class.java,
                XC_MethodReplacement.returnConstant(null))
            Logger.log("CertPin: OkHttp3 CertificatePinner.check() bypassed")
        } catch (e: Throwable) { Logger.error("bypassOkHttpPinning", e) }
    }

    private fun bypassTrustManager() {
        try {
            val x509TrustManager = XposedHelpers.findClass(
                "javax.net.ssl.X509TrustManager", lpparam.classLoader
            )

            val trustAllManager = object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            }

            XposedHelpers.findAndHookMethod(
                javax.net.ssl.SSLContext::class.java, "init",
                Array<javax.net.ssl.KeyManager>::class.java,
                Array<javax.net.ssl.TrustManager>::class.java,
                java.security.SecureRandom::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[1] = arrayOf(trustAllManager)
                    }
                })
            Logger.log("CertPin: TrustManager bypass installed")
        } catch (e: Throwable) { Logger.error("bypassTrustManager", e) }
    }

    private fun bypassWebViewSSL() {
        try {
            val webViewClient = XposedHelpers.findClass(
                "android.webkit.WebViewClient", lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(webViewClient, "onReceivedSslError",
                XposedHelpers.findClass("android.webkit.WebView", lpparam.classLoader),
                XposedHelpers.findClass("android.webkit.SslErrorHandler", lpparam.classLoader),
                XposedHelpers.findClass("android.net.http.SslError", lpparam.classLoader),
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // Tell WebView to proceed despite SSL error
                        XposedHelpers.callMethod(param.args[1], "proceed")
                    }
                })
            Logger.log("CertPin: WebView SSL error handler bypassed")
        } catch (_: Throwable) { }
    }
}
