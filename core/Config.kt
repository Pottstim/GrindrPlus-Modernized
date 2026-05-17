package com.grindrplus.core

import com.grindrplus.utils.Logger
import org.json.JSONObject
import java.io.File

/**
 * Config — Runtime configuration manager for GrindrPlus.
 *
 * Reads/writes hook settings from a JSON config file.
 * Supports per-hook enable/disable and debug mode toggle.
 */
object Config {

    private const val CONFIG_FILE = "/data/data/com.grindrapp.android/shared_prefs/gp_config.json"
    private val hookSettings = mutableMapOf<String, Boolean>()
    private var debugMode = true
    private var configLoaded = false

    fun init() {
        loadConfig()
    }

    fun initHookSettings(hookName: String, hookDesc: String, defaultEnabled: Boolean = true) {
        if (!hookSettings.containsKey(hookName)) {
            hookSettings[hookName] = defaultEnabled
        }
    }

    fun isHookEnabled(hookName: String): Boolean {
        return hookSettings[hookName] ?: true
    }

    fun setHookEnabled(hookName: String, enabled: Boolean) {
        hookSettings[hookName] = enabled
        saveConfig()
    }

    fun isDebugMode(): Boolean = debugMode

    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
        saveConfig()
    }

    private fun loadConfig() {
        try {
            val file = File(CONFIG_FILE)
            if (!file.exists()) {
                Logger.log("Config: No existing config, using defaults")
                configLoaded = true
                return
            }

            val json = JSONObject(file.readText())
            debugMode = json.optBoolean("debug_mode", true)

            val hooks = json.optJSONObject("hooks")
            if (hooks != null) {
                hooks.keys().forEach { key ->
                    hookSettings[key] = hooks.optBoolean(key, true)
                }
            }

            configLoaded = true
            Logger.log("Config: Loaded (${hookSettings.size} hook settings)")
        } catch (e: Throwable) {
            Logger.error("Config: Load failed, using defaults", e)
            configLoaded = true
        }
    }

    private fun saveConfig() {
        try {
            val json = JSONObject()
            json.put("debug_mode", debugMode)

            val hooksJson = JSONObject()
            hookSettings.forEach { (key, value) ->
                hooksJson.put(key, value)
            }
            json.put("hooks", hooksJson)

            val file = File(CONFIG_FILE)
            file.parentFile?.mkdirs()
            file.writeText(json.toString(2))
        } catch (e: Throwable) {
            Logger.error("Config: Save failed", e)
        }
    }
}
