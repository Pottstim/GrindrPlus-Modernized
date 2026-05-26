package com.grindrplus.core

import android.content.Context
import android.content.SharedPreferences
import com.grindrplus.utils.Logger

/**
 * Issue #14 fix: Adaptive failure threshold with exponential backoff
 * Instead of simple count, uses time-decayed scoring so a hook that
 * failed 3 times yesterday but succeeds today isn't permanently disabled.
 */
object HookStateStore {
    private const val PREFS_NAME = "grindrplus_hook_state"
    private const val KEY_INIT_COUNT = "init_count"
    private const val KEY_SAFE_MODE = "safe_mode"
    private const val KEY_PREFIX_SUCCESS = "success_"
    private const val KEY_PREFIX_FAIL = "fail_"
    private const val KEY_PREFIX_LAST_FAIL = "last_fail_"
    private const val KEY_PREFIX_CONSECUTIVE_FAILS = "consec_fails_"

    // Exponential backoff: allow retry after 2^failCount minutes
    private const val BASE_BACKOFF_MINUTES = 2L
    private const val MAX_BACKOFF_MINUTES = 240L // 4 hours max
    private const val DECAY_WINDOW_MS = 3600_000L // 1 hour — successes decay

    private var prefs: SharedPreferences? = null
    private val sessionSuccesses = mutableMapOf<String, Int>()
    private val sessionFails = mutableMapOf<String, Int>()

    fun init(context: Context?) {
        if (context != null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun incrementInitCount() {
        val count = (prefs?.getInt(KEY_INIT_COUNT, 0) ?: 0) + 1
        prefs?.edit()?.putInt(KEY_INIT_COUNT, count)?.apply()
    }

    fun getInitCount(): Int = prefs?.getInt(KEY_INIT_COUNT, 0) ?: 0

    fun setSafeMode(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_SAFE_MODE, enabled)?.apply()
    }

    fun isSafeMode(): Boolean = prefs?.getBoolean(KEY_SAFE_MODE, false) ?: false

    fun recordSuccess(hookName: String) {
        sessionSuccesses[hookName] = (sessionSuccesses[hookName] ?: 0) + 1
        val totalSuccess = (prefs?.getInt("$KEY_PREFIX_SUCCESS$hookName", 0) ?: 0) + 1
        prefs?.edit()?.apply {
            putInt("$KEY_PREFIX_SUCCESS$hookName", totalSuccess)
            // Reset consecutive fails on success
            putInt("$KEY_PREFIX_CONSECUTIVE_FAILS$hookName", 0)
            apply()
        }
    }

    fun recordFailure(hookName: String) {
        sessionFails[hookName] = (sessionFails[hookName] ?: 0) + 1
        val totalFails = (prefs?.getInt("$KEY_PREFIX_FAIL$hookName", 0) ?: 0) + 1
        val consecutiveFails = (prefs?.getInt("$KEY_PREFIX_CONSECUTIVE_FAILS$hookName", 0) ?: 0) + 1
        prefs?.edit()?.apply {
            putInt("$KEY_PREFIX_FAIL$hookName", totalFails)
            putInt("$KEY_PREFIX_CONSECUTIVE_FAILS$hookName", consecutiveFails)
            putLong("$KEY_PREFIX_LAST_FAIL$hookName", System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Issue #14 fix: Adaptive failure check with exponential backoff
     * A hook is "failing" only if:
     * 1. It has 3+ consecutive failures, AND
     * 2. The backoff period hasn't elapsed since last failure
     *
     * Backoff doubles each consecutive failure: 2min → 4min → 8min → ... → 240min
     */
    fun isFailing(hookName: String): Boolean {
        val consecutiveFails = prefs?.getInt("$KEY_PREFIX_CONSECUTIVE_FAILS$hookName", 0) ?: 0
        if (consecutiveFails < 3) return false

        val lastFailTime = prefs?.getLong("$KEY_PREFIX_LAST_FAIL$hookName", 0) ?: 0
        if (lastFailTime == 0L) return false

        // Calculate backoff: 2^consecutiveFails minutes, capped at MAX
        val backoffMinutes = minOf(
            BASE_BACKOFF_MINUTES * (1L shl (consecutiveFails - 1).coerceAtMost(8)),
            MAX_BACKOFF_MINUTES
        )
        val backoffMs = backoffMinutes * 60 * 1000
        val elapsed = System.currentTimeMillis() - lastFailTime

        val stillInBackoff = elapsed < backoffMs
        if (stillInBackoff) {
            val remainingMin = (backoffMs - elapsed) / 60000
            Logger.log("HookState: $hookName in backoff — ${remainingMin}min remaining (${consecutiveFails} consecutive fails)")
        }
        return stillInBackoff
    }

    fun getSuccessCount(hookName: String): Int =
        (prefs?.getInt("$KEY_PREFIX_SUCCESS$hookName", 0) ?: 0) + (sessionSuccesses[hookName] ?: 0)

    fun getFailureCount(hookName: String): Int =
        (prefs?.getInt("$KEY_PREFIX_FAIL$hookName", 0) ?: 0) + (sessionFails[hookName] ?: 0)

    fun getConsecutiveFailures(hookName: String): Int =
        prefs?.getInt("$KEY_PREFIX_CONSECUTIVE_FAILS$hookName", 0) ?: 0

    fun getReport(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Hook State Report ===")
        sb.appendLine("Init count: ${getInitCount()}")
        sb.appendLine("Safe mode: ${isSafeMode()}")
        sb.appendLine("Session successes: $sessionSuccesses")
        sb.appendLine("Session failures: $sessionFails")
        return sb.toString()
    }

    fun reset() {
        prefs?.edit()?.clear()?.apply()
        sessionSuccesses.clear()
        sessionFails.clear()
    }
}
