package com.grindrplus.core

import android.content.Context
import android.content.SharedPreferences
import com.grindrplus.utils.Logger

object HookStateStore {
    private const val PREFS_NAME = "grindrplus_hook_state"
    private var prefs: SharedPreferences? = null
    private var initCount = 0

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun incrementInitCount() {
        initCount++
        prefs?.edit()?.putInt("init_count", initCount)?.apply()
    }

    fun getInitCount(): Int = initCount

    fun isSafeMode(): Boolean {
        return prefs?.getBoolean("safe_mode", false) ?: false
    }

    fun setSafeMode(enabled: Boolean) {
        prefs?.edit()?.putBoolean("safe_mode", enabled)?.apply()
    }

    fun recordSuccess(hookName: String) {
        val key = "success_$hookName"
        val count = prefs?.getInt(key, 0) ?: 0
        prefs?.edit()?.putInt(key, count + 1)
            ?.putBoolean("healthy_$hookName", true)
            ?.apply()
    }

    fun recordFailure(hookName: String) {
        val key = "fail_$hookName"
        val count = prefs?.getInt(key, 0) ?: 0
        prefs?.edit()?.putInt(key, count + 1)
            ?.putBoolean("healthy_$hookName", false)
            ?.apply()
    }

    fun getSuccessCount(hookName: String): Int =
        prefs?.getInt("success_$hookName", 0) ?: 0

    fun getFailureCount(hookName: String): Int =
        prefs?.getInt("fail_$hookName", 0) ?: 0

    fun isFailing(hookName: String): Boolean {
        val fails = getFailureCount(hookName)
        val successes = getSuccessCount(hookName)
        // Consider failing if 3+ consecutive failures and no recent success
        return fails >= 3 && fails > successes
    }

    fun getReport(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Hook State Report ===")
        sb.appendLine("Init count: $initCount | Safe mode: ${isSafeMode()}")
        val allKeys = prefs?.all?.keys?.filter { it.startsWith("success_") } ?: emptyList()
        allKeys.forEach { key ->
            val hookName = key.removePrefix("success_")
            val successes = prefs?.getInt(key, 0) ?: 0
            val fails = getFailureCount(hookName)
            val healthy = prefs?.getBoolean("healthy_$hookName", true) ?: true
            sb.appendLine("  ${if (healthy) "✓" else "✗"} $hookName (${successes}✓ ${fails}✗)")
        }
        return sb.toString()
    }

    fun reset() {
        prefs?.edit()?.clear()?.apply()
        initCount = 0
    }
}
