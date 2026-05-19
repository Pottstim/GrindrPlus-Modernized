package com.grindrplus.utils

import com.grindrplus.core.Config
import de.robv.android.xposed.XposedBridge
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private const val TAG = "GrindrPlus"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val logBuffer = JSONArray()
    private const val MAX_BUFFER = 500

    enum class Level { DEBUG, INFO, WARN, ERROR }

    private fun log(level: Level, msg: String, t: Throwable? = null) {
        try {
            val ts = dateFormat.format(Date())
            val entry = JSONObject().apply {
                put("ts", ts)
                put("level", level.name)
                put("msg", msg)
                t?.let { put("error", "${it.javaClass.simpleName}: ${it.message}") }
            }
            synchronized(logBuffer) {
                logBuffer.put(entry)
                if (logBuffer.length() > MAX_BUFFER) {
                    logBuffer.remove(0)
                }
            }
            val line = "[$TAG] [${level.name}] $msg"
            XposedBridge.log(line)
            t?.let { XposedBridge.log(it) }
        } catch (_: Throwable) {}
    }

    fun d(msg: String) {
        if (Config.isDebugMode()) log(Level.DEBUG, msg)
    }

    fun log(msg: String) = log(Level.INFO, msg)

    fun warn(msg: String) = log(Level.WARN, msg)

    fun error(msg: String, t: Throwable? = null) = log(Level.ERROR, msg, t)

    fun hookResult(hookName: String, success: Boolean, detail: String = "") {
        val status = if (success) "✓ INITIALIZED" else "✗ FAILED"
        val msg = "$status: $hookName${if (detail.isNotEmpty()) " — $detail" else ""}"
        if (success) log(msg) else warn(msg)
    }

    fun getLogs(): String {
        return synchronized(logBuffer) {
            val sb = StringBuilder()
            for (i in 0 until logBuffer.length()) {
                val entry = logBuffer.getJSONObject(i)
                sb.appendLine("[${entry.optString("ts")}] [${entry.optString("level")}] ${entry.optString("msg")}")
                entry.optString("error")?.let { if (it.isNotEmpty()) sb.appendLine("  → $it") }
            }
            sb.toString()
        }
    }

    fun getLogsJson(): String {
        return synchronized(logBuffer) { logBuffer.toString(2) }
    }

    fun exportLogs(dir: File?): String? {
        if (dir == null) return null
        return try {
            val file = File(dir, "grindrplus_log_${System.currentTimeMillis()}.json")
            file.writeText(getLogsJson())
            log("Logs exported to ${file.absolutePath}")
            file.absolutePath
        } catch (e: Throwable) {
            error("Failed to export logs", e)
            null
        }
    }

    fun clear() {
        synchronized(logBuffer) {
            while (logBuffer.length() > 0) {
                logBuffer.remove(0)
            }
        }
    }
}
