package com.grindrplus.hooks

import com.grindrplus.utils.DynamicHookResolver
import com.grindrplus.utils.Logger
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * EnableUnlimited — Removes all usage limits (cascades, blocks, favorites, etc.)
 */
class EnableUnlimited : Hook("Unlimited Everything", "Removes all usage limits") {

    override fun init() {
        removeCascadeLimits(lpparam)
        removeBlockLimits(lpparam)
        removeFavoriteLimits(lpparam)
        removeAlbumLimits(lpparam)
        removeTapLimits(lpparam)
        removeChatLimits(lpparam)
        removeProfileViewLimits(lpparam)
    }

    private fun removeCascadeLimits(lpparam: XC_LoadPackage.LoadPackageParam) {
        val limitMethods = mapOf(
            "getCascadeLimit" to Int.MAX_VALUE,
            "getDailyCascadeLimit" to Int.MAX_VALUE,
            "getRemainingCascades" to Int.MAX_VALUE,
            "canLoadMoreCascades" to true,
            "hasReachedCascadeLimit" to false,
            "incrementCascadeCount" to null,
            "getCascadeCount" to 0
        )

        hookByPatterns(lpparam, listOf(".*\\.cascade\\..*", ".*\\.feed\\..*"), limitMethods, "Cascade")
    }

    private fun removeBlockLimits(lpparam: XC_LoadPackage.LoadPackageParam) {
        val limitMethods = mapOf(
            "getBlockLimit" to Int.MAX_VALUE,
            "getBlockedCount" to 0,
            "canBlockMore" to true,
            "hasReachedBlockLimit" to false
        )

        hookByPatterns(lpparam, listOf(".*\\.block\\..*"), limitMethods, "Block")
    }

    private fun removeFavoriteLimits(lpparam: XC_LoadPackage.LoadPackageParam) {
        val limitMethods = mapOf(
            "getFavoriteLimit" to Int.MAX_VALUE,
            "getFavoritedCount" to 0,
            "canFavoriteMore" to true,
            "hasReachedFavoriteLimit" to false,
            "isFavorited" to false
        )

        hookByPatterns(lpparam, listOf(".*\\.favorite\\..*", ".*\\.favourite\\..*"), limitMethods, "Favorite")
    }

    private fun removeAlbumLimits(lpparam: XC_LoadPackage.LoadPackageParam) {
        val limitMethods = mapOf(
            "getAlbumLimit" to Int.MAX_VALUE,
            "getAlbumCount" to 0,
            "canAddMoreAlbums" to true,
            "hasReachedAlbumLimit" to false
        )

        hookByPatterns(lpparam, listOf(".*\\.album\\..*"), limitMethods, "Album")
    }

    private fun removeTapLimits(lpparam: XC_LoadPackage.LoadPackageParam) {
        val limitMethods = mapOf(
            "getTapLimit" to Int.MAX_VALUE,
            "getDailyTapLimit" to Int.MAX_VALUE,
            "getRemainingTaps" to Int.MAX_VALUE,
            "canSendTap" to true,
            "hasReachedTapLimit" to false
        )

        hookByPatterns(lpparam, listOf(".*\\.tap\\..*"), limitMethods, "Tap")
    }

    private fun removeChatLimits(lpparam: XC_LoadPackage.LoadPackageParam) {
        val limitMethods = mapOf(
            "getChatLimit" to Int.MAX_VALUE,
            "getUnreadChatCount" to 0,
            "canStartNewChat" to true,
            "hasReachedChatLimit" to false
        )

        hookByPatterns(lpparam, listOf(".*\\.chat\\..*", ".*\\.conversation\\..*"), limitMethods, "Chat")
    }

    private fun removeProfileViewLimits(lpparam: XC_LoadPackage.LoadPackageParam) {
        val limitMethods = mapOf(
            "getProfileViewLimit" to Int.MAX_VALUE,
            "getProfileViewsToday" to 0,
            "canViewMoreProfiles" to true,
            "hasReachedProfileViewLimit" to false
        )

        hookByPatterns(lpparam, listOf(".*\\.profile\\..*", ".*\\.view\\..*"), limitMethods, "ProfileView")
    }

    private fun hookByPatterns(
        lpparam: XC_LoadPackage.LoadPackageParam,
        patterns: List<String>,
        methods: Map<String, Any?>,
        label: String
    ) {
        val resolver = DynamicHookResolver(lpparam)
        patterns.forEach { pattern ->
            resolver.whenClassLoaded(pattern, null, "$label limits") { param ->
                try {
                    val clazz = param.result as? Class<*> ?: return@whenClassLoaded
                    methods.forEach { (method, returnValue) ->
                        try {
                            XposedHelpers.findAndHookMethod(
                                clazz, method,
                                XC_MethodReplacement.returnConstant(returnValue)
                            )
                        } catch (_: Throwable) { }
                    }
                    Logger.log("$label: Hooked ${clazz.name}")
                } catch (e: Throwable) {
                    Logger.error("$label: Hook failed", e)
                }
            }
        }
        resolver.install()
    }
}
