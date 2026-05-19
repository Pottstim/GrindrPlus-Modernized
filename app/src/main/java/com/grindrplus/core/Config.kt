package com.grindrplus.core

import com.grindrplus.utils.Logger
import org.json.JSONObject
import java.io.File

object Config {
    private val hookEnabled = mutableMapOf<String, Boolean>()
    private val hookDescs = mutableMapOf<String, String>()
    private val featureToggles = mutableMapOf<String, Boolean>()

    var debugMode = true
        private set
    var remoteConfigEnabled = true
        private set
    var safeMode = false
        private set

    // Feature toggle keys — use top-level consts since we're an object
    const val FEATURE_UNLIMITED_MESSAGES = "unlimited_messages"
    const val FEATURE_SEE_VIEWERS = "see_who_viewed"
    const val FEATURE_UNLIMITED_REWINDS = "unlimited_rewinds"
    const val FEATURE_PREMIUM_FILTERS = "premium_filters"
    const val FEATURE_EXPIRING_PHOTOS = "expiring_photos"
    const val FEATURE_TYPING_STATUS = "typing_status"
    const val FEATURE_ADVANCED_SEARCH = "advanced_search"
    const val FEATURE_HIDE_ONLINE = "hide_online"
    const val FEATURE_HIDE_READ_RECEIPTS = "hide_read_receipts"
    const val FEATURE_HIDE_DISTANCE = "hide_distance"

    fun init() {
        hookEnabled.clear()
        hookDescs.clear()
        featureToggles.apply {
            put(FEATURE_UNLIMITED_MESSAGES, true)
            put(FEATURE_SEE_VIEWERS, true)
            put(FEATURE_UNLIMITED_REWINDS, true)
            put(FEATURE_PREMIUM_FILTERS, true)
            put(FEATURE_EXPIRING_PHOTOS, true)
            put(FEATURE_TYPING_STATUS, true)
            put(FEATURE_ADVANCED_SEARCH, true)
            put(FEATURE_HIDE_ONLINE, false)
            put(FEATURE_HIDE_READ_RECEIPTS, false)
            put(FEATURE_HIDE_DISTANCE, false)
        }
    }

    fun registerHook(name: String, desc: String, default: Boolean = true) {
        hookEnabled.putIfAbsent(name, default)
        hookDescs[name] = desc
    }

    fun isHookEnabled(name: String) = hookEnabled[name] != false

    fun setHookEnabled(name: String, enabled: Boolean) {
        hookEnabled[name] = enabled
    }

    fun getHooks(): List<Triple<String, String, Boolean>> =
        hookEnabled.map { (n, e) -> Triple(n, hookDescs[n] ?: "", e) }

    fun isFeatureEnabled(feature: String): Boolean = featureToggles[feature] ?: false

    fun setFeatureEnabled(feature: String, enabled: Boolean) {
        featureToggles[feature] = enabled
    }

    fun getFeatureToggles(): Map<String, Boolean> = featureToggles.toMap()

    fun applyRemoteFeatures(remote: Map<String, Boolean>) {
        remote.forEach { (key, value) ->
            if (featureToggles.containsKey(key)) {
                featureToggles[key] = value
                Logger.log("Config: Remote feature $key = $value")
            }
        }
    }

    fun isDebugMode() = debugMode
    fun setDebugMode(v: Boolean) { debugMode = v }

    fun isRemoteConfigEnabled() = remoteConfigEnabled
    fun setRemoteConfigEnabled(v: Boolean) { remoteConfigEnabled = v }

    fun isSafeMode() = safeMode
    fun setSafeMode(v: Boolean) { safeMode = v }

    fun getHookReport(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Config Report ===")
        sb.appendLine("Debug: $debugMode | Remote: $remoteConfigEnabled | Safe: $safeMode")
        sb.appendLine("Hooks:")
        hookEnabled.forEach { (name, enabled) ->
            sb.appendLine("  ${if (enabled) "✓" else "✗"} $name")
        }
        sb.appendLine("Features:")
        featureToggles.forEach { (name, enabled) ->
            sb.appendLine("  ${if (enabled) "✓" else "✗"} $name")
        }
        return sb.toString()
    }
}
