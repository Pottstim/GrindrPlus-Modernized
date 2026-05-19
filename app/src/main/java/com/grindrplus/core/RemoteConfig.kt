package com.grindrplus.core

import com.grindrplus.utils.Logger
import java.net.HttpURLConnection
import java.net.URL

object RemoteConfig {
    private const val DEFAULT_CONFIG_URL = "https://raw.githubusercontent.com/Pottstim/GrindrPlus-Modernized/main/config/remote.json"
    private var configUrl = DEFAULT_CONFIG_URL
    private var cachedConfig: Map<String, Any>? = null
    private var lastFetchTime = 0L
    private const val CACHE_TTL = 300_000L // 5 minutes

    fun setConfigUrl(url: String) { configUrl = url }

    fun fetch(force: Boolean = false): Map<String, Any>? {
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
            // Simple JSON parse — extract key values
            val map = mutableMapOf<String, Any>()
            parseJsonToMap(response, map)
            cachedConfig = map
            lastFetchTime = now
            Logger.log("RemoteConfig: Fetched ${map.size} keys")
            map
        } catch (e: Exception) {
            Logger.error("RemoteConfig: Fetch failed", e)
            cachedConfig
        }
    }

    private fun parseJsonToMap(json: String, map: MutableMap<String, Any>) {
        // Simple extraction for flat string/number/boolean values
        val regex = "\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|\\d+|true|false)".toRegex()
        regex.findAll(json).forEach { match ->
            val key = match.groupValues[1]
            val raw = match.groupValues[2]
            val value: Any = when {
                raw == "true" -> true
                raw == "false" -> false
                raw.startsWith("\"") -> raw.trim('"')
                else -> raw.toIntOrNull() ?: raw
            }
            map[key] = value
        }
    }

    fun getHookPatterns(): List<String> {
        val config = fetch() ?: return emptyList()
        val patterns = mutableListOf<String>()
        config.forEach { (key, value) ->
            if (key.startsWith("hook_") && value is String) {
                patterns.add(value)
            }
        }
        return patterns
    }

    fun getBlockedClasses(): List<String> {
        val config = fetch() ?: return emptyList()
        val classes = mutableListOf<String>()
        config.forEach { (key, value) ->
            if (key.startsWith("block_") && value is String) {
                classes.add(value)
            }
        }
        return classes
    }

    fun getFeatureOverrides(): Map<String, Boolean> {
        val config = fetch() ?: return emptyMap()
        val features = mutableMapOf<String, Boolean>()
        config.forEach { (key, value) ->
            if (key.startsWith("feature_") && value is Boolean) {
                features[key] = value
            }
        }
        return features
    }

    fun getMinModuleVersion(): String {
        return fetch()?.get("min_version")?.toString() ?: "1.0"
    }

    fun isUpdateAvailable(currentVersion: String): Boolean {
        val remote = getMinModuleVersion()
        return remote > currentVersion
    }
}
