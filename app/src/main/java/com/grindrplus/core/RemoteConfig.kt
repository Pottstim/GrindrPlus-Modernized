package com.grindrplus.core

import com.grindrplus.utils.Logger
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object RemoteConfig {
    private const val DEFAULT_CONFIG_URL = "https://raw.githubusercontent.com/Pottstim/GrindrPlus-Modernized/main/config/remote.json"
    private var configUrl = DEFAULT_CONFIG_URL
    private var cachedConfig: JSONObject? = null
    private var lastFetchTime = 0L
    private const val CACHE_TTL = 300_000L // 5 minutes
    private val executor = Executors.newSingleThreadExecutor()

    fun setConfigUrl(url: String) { configUrl = url }

    /**
     * Async fetch — safe to call from main thread.
     * Returns cached config immediately, refreshes in background.
     */
    fun fetchAsync(callback: ((JSONObject?) -> Unit)? = null) {
        val cached = getCached()
        if (cached != null) {
            callback?.invoke(cached)
        }
        executor.execute {
            val fresh = fetchBlocking()
            callback?.invoke(fresh)
        }
    }

    /**
     * Synchronous fetch — only call from background threads.
     */
    fun fetchBlocking(force: Boolean = false): JSONObject? {
        val now = System.currentTimeMillis()
        if (!force && cachedConfig != null && (now - lastFetchTime) < CACHE_TTL) {
            return cachedConfig
        }
        return try {
            val url = URL(configUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"
            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(response)
            cachedConfig = json
            lastFetchTime = now
            Logger.log("RemoteConfig: Fetched config v${json.optString("version", "?")}")
            json
        } catch (e: Exception) {
            Logger.error("RemoteConfig: Fetch failed", e)
            cachedConfig
        }
    }

    fun getCached(): JSONObject? = cachedConfig

    /**
     * Get feature toggles from nested "features" object.
     */
    fun getFeatureOverrides(): Map<String, Boolean> {
        val config = getCached() ?: return emptyMap()
        val features = mutableMapOf<String, Boolean>()
        val featuresObj = config.optJSONObject("features") ?: return emptyMap()
        featuresObj.keys().forEach { key ->
            features[key] = featuresObj.optBoolean(key, false)
        }
        return features
    }

    /**
     * Get hook patterns from nested "hooks" object.
     */
    fun getHookPatterns(): List<String> {
        val config = getCached() ?: return emptyList()
        val hooksObj = config.optJSONObject("hooks") ?: return emptyList()
        val patterns = mutableListOf<String>()
        hooksObj.keys().forEach { key ->
            val v = hooksObj.optString(key, "")
            if (v.isNotEmpty()) patterns.add(v)
        }
        return patterns
    }

    /**
     * Get blocked class patterns from nested "blocked" object.
     */
    fun getBlockedClasses(): List<String> {
        val config = getCached() ?: return emptyList()
        val blockedObj = config.optJSONObject("blocked") ?: return emptyList()
        val classes = mutableListOf<String>()
        blockedObj.keys().forEach { key ->
            val v = blockedObj.optString(key, "")
            if (v.isNotEmpty()) classes.add(v)
        }
        return classes
    }

    fun getMinModuleVersion(): String {
        return getCached()?.optString("min_version", "1.0") ?: "1.0"
    }

    fun isUpdateAvailable(currentVersion: String): Boolean {
        val remote = getMinModuleVersion()
        return try {
            val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            false
        } catch (_: Exception) { false }
    }

    fun getNoticeMessage(): String {
        val config = getCached() ?: return ""
        return config.optJSONObject("messages")?.optString("notice", "") ?: ""
    }

    fun getUpdateUrl(): String {
        val config = getCached() ?: return ""
        return config.optJSONObject("messages")?.optString("update_url", "") ?: ""
    }
}
