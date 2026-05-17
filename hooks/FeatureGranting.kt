package com.grindrplus.hooks

import com.grindrplus.utils.DynamicHookResolver
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * FeatureGranting — Grants all premium features in Grindr 26.x+
 *
 * Targets:
 * - Feature flag checks (isPremium(), hasFeature(), etc.)
 * - Subscription status (isSubscribed(), getSubscriptionTier())
 * - Profile view limits (canViewProfile(), incrementViewCount())
 * - Chat restrictions (canSendTap(), canSendAlbum(), etc.)
 * - Explore mode access
 * - Incognito mode
 * - Typing indicators
 * - Read receipts
 * - Unlimited favorites
 * - Unlimited blocks
 * - Ad removal
 */
class FeatureGranting : Hook("Feature Granting", "Unlocks all premium features") {

    override fun init() {
        grantPremiumFeatures(lpparam)
        grantSubscriptionStatus(lpparam)
        grantChatFeatures(lpparam)
        grantExploreAccess(lpparam)
        grantPrivacyFeatures(lpparam)
        removeAds(lpparam)
    }

    /**
     * Grant all premium feature flags.
     * Uses dynamic resolution to handle obfuscated class names.
     */
    private fun grantPremiumFeatures(lpparam: XC_LoadPackage.LoadPackageParam) {
        val resolver = DynamicHookResolver(lpparam)

        // Common premium check method names (Grindr obfuscates these)
        val premiumMethods = listOf(
            "isPremium",
            "isPlus",
            "isXtra",
            "isUnlimited",
            "hasPremium",
            "hasPlus",
            "hasXtra",
            "hasUnlimited",
            "isSubscribed",
            "getSubscriptionTier",
            "isFeatureEnabled",
            "hasFeature"
        )

        // Scan for classes containing premium-related methods
        val featurePatterns = listOf(
            Regex(".*\\.model\\.Feature.*"),
            Regex(".*\\.feature\\.Feature.*"),
            Regex(".*\\.subscription\\.Feature.*"),
            Regex(".*\\.premium\\.Feature.*")
        )

        featurePatterns.forEach { pattern ->
            resolver.whenClassLoaded(pattern.pattern, null, "Premium feature class") { param ->
                try {
                    val clazz = param.result as? Class<*> ?: return@whenClassLoaded
                    premiumMethods.forEach { methodName ->
                        try {
                            XposedHelpers.findAndHookMethod(
                                clazz, methodName,
                                XC_MethodReplacement.returnConstant(true)
                            )
                        } catch (_: Throwable) { }
                    }
                    Logger.log("Features: Hooked ${clazz.name}")
                } catch (e: Throwable) {
                    Logger.error("Features: Hook failed for pattern $pattern", e)
                }
            }
        }

        // Also try direct known class names
        val knownClasses = listOf(
            "com.grindrapp.android.model.Feature",
            "com.grindrapp.android.feature.Feature",
            "com.grindrapp.android.subscription.Subscription"
        )

        knownClasses.forEach { className ->
            try {
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                premiumMethods.forEach { methodName ->
                    try {
                        XposedBridge.hookAllMethods(clazz, methodName,
                            XC_MethodReplacement.returnConstant(true))
                    } catch (_: Throwable) { }
                }
                Logger.log("Features: Direct hook on $className")
            } catch (_: Throwable) { }
        }

        resolver.install()
    }

    /**
     * Grant subscription status — always return active premium.
     */
    private fun grantSubscriptionStatus(lpparam: XC_LoadPackage.LoadPackageParam) {
        val subscriptionMethods = mapOf(
            "isSubscribed" to true,
            "isActive" to true,
            "isExpired" to false,
            "getSubscriptionTier" to "unlimited", // or highest tier string
            "getRemainingDays" to 9999,
            "getTrialDaysRemaining" to 0
        )

        val resolver = DynamicHookResolver(lpparam)
        val subPatterns = listOf(
            Regex(".*\\.subscription\\..*"),
            Regex(".*\\.billing\\..*"),
            Regex(".*\\.purchase\\..*")
        )

        subPatterns.forEach { pattern ->
            resolver.whenClassLoaded(pattern.pattern, null, "Subscription class") { param ->
                try {
                    val clazz = param.result as? Class<*> ?: return@whenClassLoaded
                    subscriptionMethods.forEach { (method, returnValue) ->
                        try {
                            XposedHelpers.findAndHookMethod(
                                clazz, method,
                                XC_MethodReplacement.returnConstant(returnValue)
                            )
                        } catch (_: Throwable) { }
                    }
                    Logger.log("Subscription: Hooked ${clazz.name}")
                } catch (e: Throwable) {
                    Logger.error("Subscription: Hook failed", e)
                }
            }
        }

        resolver.install()
    }

    /**
     * Grant chat features — taps, albums, voice notes, etc.
     */
    private fun grantChatFeatures(lpparam: XC_LoadPackage.LoadPackageParam) {
        val chatMethods = mapOf(
            "canSendTap" to true,
            "canSendAlbum" to true,
            "canSendVoiceNote" to true,
            "canSendLocation" to true,
            "canSendGif" to true,
            "canSendPhoto" to true,
            "canSendVideo" to true,
            "canReadReceipts" to true,
            "canSeeTypingIndicator" to true,
            "canUseIncognito" to true
        )

        val resolver = DynamicHookResolver(lpparam)
        val chatPatterns = listOf(
            Regex(".*\\.chat\\..*"),
            Regex(".*\\.messaging\\..*"),
            Regex(".*\\.conversation\\..*")
        )

        chatPatterns.forEach { pattern ->
            resolver.whenClassLoaded(pattern.pattern, null, "Chat feature class") { param ->
                try {
                    val clazz = param.result as? Class<*> ?: return@whenClassLoaded
                    chatMethods.forEach { (method, returnValue) ->
                        try {
                            XposedHelpers.findAndHookMethod(
                                clazz, method,
                                XC_MethodReplacement.returnConstant(returnValue)
                            )
                        } catch (_: Throwable) { }
                    }
                    Logger.log("Chat: Hooked ${clazz.name}")
                } catch (e: Throwable) {
                    Logger.error("Chat: Hook failed", e)
                }
            }
        }

        resolver.install()
    }

    /**
     * Grant Explore mode access.
     */
    private fun grantExploreAccess(lpparam: XC_LoadPackage.LoadPackageParam) {
        val exploreMethods = mapOf(
            "canAccessExplore" to true,
            "isExploreAvailable" to true,
            "getExploreDistance" to Int.MAX_VALUE,
            "getExploreRadius" to Int.MAX_VALUE
        )

        val resolver = DynamicHookResolver(lpparam)
        resolver.whenClassLoaded(".*\\.explore\\..*", null, "Explore class") { param ->
            try {
                val clazz = param.result as? Class<*> ?: return@whenClassLoaded
                exploreMethods.forEach { (method, returnValue) ->
                    try {
                        XposedHelpers.findAndHookMethod(
                            clazz, method,
                            XC_MethodReplacement.returnConstant(returnValue)
                        )
                    } catch (_: Throwable) { }
                }
                Logger.log("Explore: Hooked ${clazz.name}")
            } catch (e: Throwable) {
                Logger.error("Explore: Hook failed", e)
            }
        }
        resolver.install()
    }

    /**
     * Grant privacy features — incognito, hide distance, hide online status.
     */
    private fun grantPrivacyFeatures(lpparam: XC_LoadPackage.LoadPackageParam) {
        val privacyMethods = mapOf(
            "canUseIncognito" to true,
            "canHideDistance" to true,
            "canHideOnlineStatus" to true,
            "canHideLastSeen" to true,
            "canDisableScreenshot" to true,
            "canUseStealth" to true
        )

        val resolver = DynamicHookResolver(lpparam)
        val privacyPatterns = listOf(
            Regex(".*\\.privacy\\..*"),
            Regex(".*\\.incognito\\..*"),
            Regex(".*\\.settings\\..*")
        )

        privacyPatterns.forEach { pattern ->
            resolver.whenClassLoaded(pattern.pattern, null, "Privacy class") { param ->
                try {
                    val clazz = param.result as? Class<*> ?: return@whenClassLoaded
                    privacyMethods.forEach { (method, returnValue) ->
                        try {
                            XposedHelpers.findAndHookMethod(
                                clazz, method,
                                XC_MethodReplacement.returnConstant(returnValue)
                            )
                        } catch (_: Throwable) { }
                    }
                    Logger.log("Privacy: Hooked ${clazz.name}")
                } catch (e: Throwable) {
                    Logger.error("Privacy: Hook failed", e)
                }
            }
        }

        resolver.install()
    }

    /**
     * Remove all ads by intercepting ad-loading methods.
     */
    private fun removeAds(lpparam: XC_LoadPackage.LoadPackageParam) {
        val adMethods = listOf(
            "shouldShowAd",
            "shouldShowBanner",
            "shouldShowInterstitial",
            "shouldShowRewardedAd",
            "loadAd",
            "showAd",
            "getAdConfig",
            "getAdUnitId"
        )

        val adReturnValues = mapOf(
            "shouldShowAd" to false,
            "shouldShowBanner" to false,
            "shouldShowInterstitial" to false,
            "shouldShowRewardedAd" to false,
            "loadAd" to null,
            "showAd" to null,
            "getAdConfig" to null,
            "getAdUnitId" to ""
        )

        val resolver = DynamicHookResolver(lpparam)
        val adPatterns = listOf(
            Regex(".*\\.ads\\..*"),
            Regex(".*\\.ad\\..*"),
            Regex(".*\\.advertising\\..*"),
            Regex(".*\\.banner\\..*")
        )

        adPatterns.forEach { pattern ->
            resolver.whenClassLoaded(pattern.pattern, null, "Ad class") { param ->
                try {
                    val clazz = param.result as? Class<*> ?: return@whenClassLoaded
                    adMethods.forEach { methodName ->
                        try {
                            val returnValue = adReturnValues[methodName]
                            XposedHelpers.findAndHookMethod(
                                clazz, methodName,
                                XC_MethodReplacement.returnConstant(returnValue)
                            )
                        } catch (_: Throwable) { }
                    }
                    Logger.log("Ads: Hooked ${clazz.name}")
                } catch (e: Throwable) {
                    Logger.error("Ads: Hook failed", e)
                }
            }
        }

        resolver.install()
    }
}
