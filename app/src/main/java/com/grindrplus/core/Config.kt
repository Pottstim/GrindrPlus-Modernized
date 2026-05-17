package com.grindrplus.core

import com.grindrplus.utils.Logger
import org.json.JSONObject
import java.io.File

object Config {
    private val hookEnabled = mutableMapOf<String, Boolean>()
    private val hookDescs = mutableMapOf<String, String>()
    var debugMode = true
        private set

    fun init() {
        hookEnabled.clear()
        hookDescs.clear()
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

    fun isDebugMode() = debugMode
    fun setDebugMode(v: Boolean) { debugMode = v }
}
