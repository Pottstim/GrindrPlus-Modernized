package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.utils.Hook
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Issue #12 fix: Added network-level ban check interception via OkHttp/Retrofit hooks
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
    }

    /**
     * Issue #12 fix: Network-level ban check interception
     * Hooks OkHttp Interceptor and Retrofit Response to strip ban responses
     */
    private fun interceptNetworkBanChecks() {
        // Hook OkHttp3 Interceptor.Chain.proceed to modify ban responses
        try {
            val interceptorChain = XposedHelpers.findClass(
                "okhttp3.Interceptor\$Chain", lpparam.classLoader
            )
            val responseClass = XposedHelpers.findClass("okhttp3.Response", lpparam.classLoader)
            val responseBuilderClass = XposedHelpers.findClass("okhttp3.Response\$Builder", lpparam.classLoader)

            XposedHelpers.findAndHookMethod(interceptorChain, "proceed",
                XposedHelpers.findClass("okhttp3.Request", lpparam.classLoader),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val response = param.result ?: return
                            val code = XposedHelpers.callMethod(response, "code") as? Int ?: return
                            val body = XposedHelpers.callMethod(response, "body") ?: return
                            val bodyString = XposedHelpers.callMethod(body, "string") as? String ?: return

                            // Check if response contains ban-related JSON
                            if (bodyString.contains("\"banned\"", ignoreCase = true) ||
                                bodyString.contains("\"suspended\"", ignoreCase = true) ||
                                bodyString.contains("\"restricted\"", ignoreCase = true)) {

                                Logger.log("BanMgmt: Intercepted ban response from network")

                                // Replace with clean response
                                val newBody = XposedHelpers.callStaticMethod(
                                    XposedHelpers.findClass("okhttp3.ResponseBody", lpparam.classLoader),
                                    "create",
                                    body, // MediaType
                                    "{\"status\":\"ok\"}"
                                )
                                val newResponse = XposedHelpers.newInstance(responseBuilderClass, response)
                                XposedHelpers.callMethod(newResponse, "code", 200)
                                XposedHelpers.callMethod(newResponse, "body", newBody)
                                XposedHelpers.callMethod(newResponse, "message", "OK")
                                param.result = XposedHelpers.callMethod(newResponse, "build")
                            }
                        } catch (_: Throwable) {}
                    }
                })
            Logger.log("BanMgmt: OkHttp network interceptor active")
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
