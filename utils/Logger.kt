package com.grindrplus.utils

import de.robv.android.xposed.XposedBridge
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Structured logging for GrindrPlus with both XposedBridge and file fallback.
 */
object Logger {

    private const val TAG = "GrindrPlus"
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var debugMode = true

    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
    }

    fun setLogFile(path: String) {
        try {
            logFile = File(path)
            logFile?.parentFile?.mkdirs()
        } catch (_: Throwable) { }
    }

    fun log(message: String) {
        val timestamped = "[${dateFormat.format(Date())}] $message"
        XposedBridge.log("$TAG: $message")
        writeToFile(timestamped)
    }

    fun error(message: String, throwable: Throwable? = null) {
        val timestamped = "[${dateFormat.format(Date())}] ERROR: $message"
        XposedBridge.log("$TAG ERROR: $message")
        throwable?.let {
            XposedBridge.log("$TAG STACK: ${it.stackTraceToString()}")
        }
        writeToFile(timestamped)
        throwable?.let {
            writeToFile(it.stackTraceToString())
        }
    }

    fun warn(message: String) {
        val timestamped = "[${dateFormat.format(Date())}] WARN: $message"
        XposedBridge.log("$TAG WARN: $message")
        writeToFile(timestamped)
    }

    private fun writeToFile(message: String) {
        try {
            logFile?.appendText("$message\n")
        } catch (_: Throwable) { }
    }
}
