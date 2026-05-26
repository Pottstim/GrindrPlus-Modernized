package com.grindrplus.core

import com.grindrplus.utils.Logger
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Issue #20: Anonymous analytics for hook survival rate tracking
 * Reports which hooks survive Grindr updates — no personal data collected.
 */
object Analytics {
    private const val REPORT_URL = "https://api.grindrplus.app/v1/report"
    private val executor = Executors.newSingleThreadExecutor()
    private var enabled = true

    fun setEnabled(v: Boolean) { enabled = v }

    /**
     * Report hook results after each session.
     * Called from HookManager after all hooks initialize.
     */
    fun reportHookResults(results: Map<String, Boolean>, grindrVersion: String) {
        if (!enabled) return
        executor.execute {
            try {
                val payload = JSONObject().apply {
                    put("module_version", "2.0.0")
                    put("grindr_version", grindrVersion)
                    put("timestamp", System.currentTimeMillis())
                    put("device_fingerprint", android.os.Build.FINGERPRINT.hashCode()) // Anonymous
                    val hooksJson = JSONObject()
                    results.forEach { (name, success) -> hooksJson.put(name, success) }
                    put("hooks", hooksJson)
                }

                val url = URL(REPORT_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.write(payload.toString().toByteArray())
                val response = conn.responseCode
                conn.disconnect()

                if (response == 200) {
                    Logger.log("Analytics: Report sent successfully")
                }
            } catch (e: Exception) {
                Logger.error("Analytics: Report failed", e)
            }
        }
    }
}
