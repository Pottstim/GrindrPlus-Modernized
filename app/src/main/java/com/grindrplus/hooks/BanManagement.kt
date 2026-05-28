package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Fix #2: OkHttp response stream consumption bug resolved.
 *
 * The original implementation called ResponseBody.string() which drains and closes
 * the OkHttp response stream. Any non-ban response would then crash Grindr's
 * networking layer with "IllegalStateException: closed" when it tried to parse
 * the already-consumed body.
 *
 * Fix: We now buffer the Okio source via source.request(Long.MAX_VALUE) and then
 * clone the internal buffer for inspection, leaving the original source intact
 * for Grindr's own parsers to consume normally.
 */
class BanManagement : Hook("Ban Management", "Prevents ban checks — local + network level") {

    override fun init() {
        Config.registerHook(name, description, true)
        interceptBanChecks()
        preventShadowban()
        spoofBanStatus()
        interceptNetworkBanChecks()
        Logger.log("BanManagement: Full protection active (local + network)")
    }

    private fun interceptBanChecks() {
        val patterns = listOf(
            "com.grindrapp.android.ban",
            "com.grindrapp.android.data.model.BanStatus",
            "com.grindrapp.android.api.model.Restriction"
        )
        patterns.forEach { pattern ->
            try {
                val clazz = lpparam.classLoader.loadClass(pattern)
                XposedBridge.hookAllMethods(clazz, "isBanned", XC_MethodReplacement.returnConstant(false))
                XposedBridge.hookAllMethods(clazz, "isShadowbanned", XC_MethodReplacement.returnConstant(false))
                XposedBridge.hookAllMethods(clazz, "isRestricted", XC_MethodReplacement.returnConstant(false))
                XposedBridge.hookAllMethods(clazz, "isSuspended", XC_MethodReplacement.returnConstant(false))
                Logger.log("BanMgmt: Intercepted $pattern")
            } catch (_: Throwable) { }
        }
    }

    private fun preventShadowban() {
        val patterns = listOf(
            "com.grindrapp.android.feed",
            "com.grindrapp.android.data.model.Visibility"
        )
        patterns.forEach { pattern ->
            try {
                val clazz = lpparam.classLoader.loadClass(pattern)
                XposedBridge.hookAllMethods(clazz, "isVisible", XC_MethodReplacement.returnConstant(true))
                XposedBridge.hookAllMethods(clazz, "isShown", XC_MethodReplacement.returnConstant(true))
                Logger.log("BanMgmt: Shadowban prevention for $pattern")
            } catch (_: Throwable) { }
        }
    }

    private fun spoofBanStatus() {
        try {
            XposedHelpers.findAndHookMethod("android.content.SharedPreferences",
                lpparam.classLoader, "getBoolean",
                String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        if (key.contains("ban", ignoreCase = true) ||
                            key.contains("restrict", ignoreCase = true) ||
                            key.contains("suspend", ignoreCase = true) ||
                            key.contains("shadow", ignoreCase = true)) {
                            param.result = false
                        }
                    }
                })
            Logger.log("BanMgmt: SharedPreferences ban spoofing active")
        } catch (e: Throwable) { Logger.error("spoofBanStatus:SharedPreferences", e) }
    }

    /**
     * Fix #2: Network-level ban check interception — stream-safe implementation.
     *
     * Strategy: Use Okio's buffered source to peek at the response body without
     * consuming it. We call source.request(Long.MAX_VALUE) to buffer all bytes
     * into the internal Okio Buffer, then clone that buffer for reading. The
     * original source remains intact so Grindr's Retrofit/Gson parsers can
     * read it normally on non-ban responses.
     *
     * Only when a ban IS detected do we replace the entire response object with
     * a clean 200/OK response containing {"status":"ok"}.
     */
    private fun interceptNetworkBanChecks() {
        try {
            val interceptorChain = XposedHelpers.findClass(
                "okhttp3.Interceptor\$Chain", lpparam.classLoader
            )
            val responseBodyClass = XposedHelpers.findClass(
                "okhttp3.ResponseBody", lpparam.classLoader
            )
            val mediaTypeClass = XposedHelpers.findClass(
                "okhttp3.MediaType", lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(interceptorChain, "proceed",
                XposedHelpers.findClass("okhttp3.Request", lpparam.classLoader),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val response = param.result ?: return
                            val body = XposedHelpers.callMethod(response, "body") ?: return

                            // --- Fix #2 core: buffer via Okio without draining the source ---
                            val source = XposedHelpers.callMethod(body, "source") ?: return
                            // Request all bytes into the internal buffer
                            XposedHelpers.callMethod(source, "request", Long.MAX_VALUE)
                            // Get the internal buffer and clone it for safe reading
                            val buffer = XposedHelpers.callMethod(source, "buffer") ?: return
                            val clonedBuffer = XposedHelpers.callMethod(buffer, "clone") ?: return
                            val bodyString = XposedHelpers.callMethod(
                                clonedBuffer, "readString",
                                java.nio.charset.Charset.forName("UTF-8")
                            ) as? String ?: return
                            // Original source is still fully buffered — Grindr can read it normally

                            // Check if response contains ban-related JSON
                            if (bodyString.contains("\"banned\"", ignoreCase = true) ||
                                bodyString.contains("\"suspended\"", ignoreCase = true) ||
                                bodyString.contains("\"restricted\"", ignoreCase = true)) {

                                Logger.log("BanMgmt: Intercepted ban response from network")

                                // Build a clean replacement response
                                val jsonMediaType = XposedHelpers.callStaticMethod(
                                    mediaTypeClass, "parse", "application/json; charset=utf-8"
                                )
                                val cleanBody = XposedHelpers.callStaticMethod(
                                    responseBodyClass, "create",
                                    jsonMediaType,
                                    "{\"status\":\"ok\"}"
                                )
                                val newResponse = XposedHelpers.callMethod(response, "newBuilder")
                                XposedHelpers.callMethod(newResponse, "code", 200)
                                XposedHelpers.callMethod(newResponse, "message", "OK")
                                XposedHelpers.callMethod(newResponse, "body", cleanBody)
                                param.result = XposedHelpers.callMethod(newResponse, "build")
                            }
                            // Non-ban responses: original response is untouched and stream is intact
                        } catch (_: Throwable) {}
                    }
                })
            Logger.log("BanMgmt: OkHttp network interceptor active (stream-safe)")
        } catch (e: Throwable) { Logger.error("interceptNetworkBanChecks:OkHttp", e) }

        // Hook Retrofit Response to strip ban bodies
        try {
            val retrofitResponse = XposedHelpers.findClass(
                "retrofit2.Response", lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(retrofitResponse, "body",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val body = param.result ?: return
                            val bodyStr = body.toString()
                            if (bodyStr.contains("ban", ignoreCase = true) ||
                                bodyStr.contains("suspend", ignoreCase = true)) {
                                Logger.log("BanMgmt: Stripped ban from Retrofit response")
                                param.result = null
                            }
                        } catch (_: Throwable) {}
                    }
                })
            Logger.log("BanMgmt: Retrofit response interceptor active")
        } catch (_: Throwable) { /* Retrofit not used, skip */ }
    }
}
