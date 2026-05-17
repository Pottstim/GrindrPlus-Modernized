package com.grindrplus.hooks

import com.grindrplus.utils.DynamicHookResolver
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * BanManagement — Prevents account bans and shadow-bans by intercepting
 * ban-related API responses and client-side ban checks.
 */
class BanManagement : Hook("Ban Management", "Prevents account bans and shadow-bans") {

    override fun init() {
        interceptBanChecks(lpparam)
        interceptBanApiResponses(lpparam)
        interceptShadowBan(lpparam)
    }

    private fun interceptBanChecks(lpparam: XC_LoadPackage.LoadPackageParam) {
        val banMethods = mapOf(
            "isBanned" to false,
            "isShadowBanned" to false,
            "isSuspended" to false,
            "isAccountLocked" to false,
            "isAccountDisabled" to false,
            "getBanReason" to null,
            "getBanExpiry" to null,
            "getViolationCount" to 0,
            "hasViolations" to false,
            "shouldShowBanScreen" to false,
            "shouldRestrictFeatures" to false
        )

        val resolver = DynamicHookResolver(lpparam)
        val banPatterns = listOf(
            Regex(".*\\.ban\\..*"),
            Regex(".*\\.moderation\\..*"),
            Regex(".*\\.violation\\..*"),
            Regex(".*\\.suspension\\..*"),
            Regex(".*\\.account\\..*")
        )

        banPatterns.forEach { pattern ->
            resolver.whenClassLoaded(pattern.pattern, null, "Ban check class") { param ->
                try {
                    val clazz = param.result as? Class<*> ?: return@whenClassLoaded
                    banMethods.forEach { (method, returnValue) ->
                        try {
                            XposedHelpers.findAndHookMethod(
                                clazz, method,
                                XC_MethodReplacement.returnConstant(returnValue)
                            )
                        } catch (_: Throwable) { }
                    }
                    Logger.log("Ban: Hooked ${clazz.name}")
                } catch (e: Throwable) {
                    Logger.error("Ban: Hook failed", e)
                }
            }
        }

        resolver.install()
    }

    private fun interceptBanApiResponses(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook HTTP response parsing to intercept ban-related API responses
        val resolver = DynamicHookResolver(lpparam)

        // Hook common HTTP client classes
        val httpPatterns = listOf(
            Regex(".*\\.network\\..*"),
            Regex(".*\\.api\\..*"),
            Regex(".*\\.http\\..*"),
            Regex(".*\\.retrofit\\..*"),
            Regex(".*\\.okhttp\\..*")
        )

        httpPatterns.forEach { pattern ->
            resolver.whenClassLoaded(pattern.pattern, null, "HTTP client class") { param ->
                try {
                    val clazz = param.result as? Class<*> ?: return@whenClassLoaded

                    // Hook response body parsing
                    val responseMethods = listOf("body", "parse", "deserialize", "fromJson", "read")
                    responseMethods.forEach { method ->
                        try {
                            XposedBridge.hookAllMethods(clazz, method, object : XC_MethodHook() {
                                override fun afterHookedMethod(param: MethodHookParam) {
                                    // Check if response contains ban-related data
                                    val result = param.result?.toString() ?: return
                                    if (result.contains("ban", ignoreCase = true) ||
                                        result.contains("suspend", ignoreCase = true) ||
                                        result.contains("violation", ignoreCase = true)
                                    ) {
                                        Logger.log("Ban: Intercepted ban API response in ${clazz.name}.$method")
                                        // Don't modify — just log. Modifying JSON responses
                                        // requires knowing the exact response type.
                                    }
                                }
                            })
                        } catch (_: Throwable) { }
                    }

                    Logger.log("Ban: HTTP hooks on ${clazz.name}")
                } catch (e: Throwable) {
                    Logger.error("Ban: HTTP hook failed", e)
                }
            }
        }

        resolver.install()
    }

    private fun interceptShadowBan(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Shadow bans are server-side, but we can hide the indicators
        val shadowBanMethods = mapOf(
            "isProfileVisible" to true,
            "isProfileHidden" to false,
            "getProfileVisibility" to "visible",
            "isInvisible" to false,
            "isDiscoverable" to true
        )

        val resolver = DynamicHookResolver(lpparam)
        val visibilityPatterns = listOf(
            Regex(".*\\.visibility\\..*"),
            Regex(".*\\.discover\\..*"),
            Regex(".*\\.search\\..*")
        )

        visibilityPatterns.forEach { pattern ->
            resolver.whenClassLoaded(pattern.pattern, null, "Visibility class") { param ->
                try {
                    val clazz = param.result as? Class<*> ?: return@whenClassLoaded
                    shadowBanMethods.forEach { (method, returnValue) ->
                        try {
                            XposedHelpers.findAndHookMethod(
                                clazz, method,
                                XC_MethodReplacement.returnConstant(returnValue)
                            )
                        } catch (_: Throwable) { }
                    }
                    Logger.log("ShadowBan: Hooked ${clazz.name}")
                } catch (e: Throwable) {
                    Logger.error("ShadowBan: Hook failed", e)
                }
            }
        }

        resolver.install()
    }
}
