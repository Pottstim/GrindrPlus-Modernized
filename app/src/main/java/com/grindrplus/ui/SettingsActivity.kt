package com.grindrplus.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.grindrplus.core.Config
import com.grindrplus.core.HookStateStore
import com.grindrplus.utils.Logger

/**
 * Issue #4 fix: Full settings UI with per-hook toggles, feature switches, and log viewer
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A1D"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // Title
        root.addView(TextView(this).apply {
            text = "GrindrPlus v2.0"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        // Debug mode toggle
        root.addView(createToggle("Debug Mode", Config.isDebugMode()) { enabled: Boolean ->
            Config.setDebugMode(enabled)
            Logger.log("Settings: Debug mode ${if (enabled) "enabled" else "disabled"}")
        })

        // Remote config toggle
        root.addView(createToggle("Remote Config", Config.isRemoteConfigEnabled()) { enabled: Boolean ->
            Config.setRemoteConfigEnabled(enabled)
            Logger.log("Settings: Remote config ${if (enabled) "enabled" else "disabled"}")
        })

        // Safe mode toggle
        root.addView(createToggle("Safe Mode (essential hooks only)", HookStateStore.isSafeMode()) { enabled: Boolean ->
            HookStateStore.setSafeMode(enabled)
            Logger.log("Settings: Safe mode ${if (enabled) "enabled" else "disabled"}")
        })

        // Section: Feature Toggles
        root.addView(createSectionHeader("Premium Features"))

        val features = listOf(
            "unlimited_messages" to "Unlimited Messages",
            "see_who_viewed" to "See Who Viewed Me",
            "unlimited_rewinds" to "Unlimited Rewinds",
            "premium_filters" to "Premium Filters",
            "expiring_photos" to "Expiring Photos",
            "typing_status" to "Typing Status",
            "advanced_search" to "Advanced Search",
            "hide_online" to "Hide Online Status",
            "hide_read_receipts" to "Hide Read Receipts",
            "hide_distance" to "Hide Distance"
        )

        features.forEach { (key, label) ->
            root.addView(createToggle(label, Config.isFeatureEnabled(key)) { enabled: Boolean ->
                Config.setFeatureEnabled(key, enabled)
                Logger.log("Settings: Feature '$key' ${if (enabled) "enabled" else "disabled"}")
            })
        }

        // Section: Hook Toggles
        root.addView(createSectionHeader("Hooks"))

        val hooks = Config.getRegisteredHooks()
        hooks.forEach { (name, enabled) ->
            root.addView(createToggle(name, enabled) { isEnabled: Boolean ->
                Config.setHookEnabled(name, isEnabled)
                Logger.log("Settings: Hook '$name' ${if (isEnabled) "enabled" else "disabled"}")
            })
        }

        // Test Report button
        root.addView(Button(this).apply {
            text = "View Hook Test Report"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(0, 32, 0, 32)
            setOnClickListener {
                val report = Config.getHookStatusReport()
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Hook Test Report")
                    .setMessage(report)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Copy Logs") { _, _ ->
                        val logs = Logger.getLogs()
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GrindrPlus Logs", logs))
                        Toast.makeText(this@SettingsActivity, "Logs copied", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        })

        // Export Logs button
        root.addView(Button(this).apply {
            text = "Export Logs"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(0, 32, 0, 32)
            setOnClickListener {
                val path = Logger.exportLogs(cacheDir)
                Toast.makeText(this@SettingsActivity,
                    if (path != null) "Logs saved to $path" else "Export failed",
                    Toast.LENGTH_LONG).show()
            }
        })

        // Clear State button
        root.addView(Button(this).apply {
            text = "Reset All State"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#8B0000"))
            setPadding(0, 32, 0, 32)
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Reset State")
                    .setMessage("Clear all hook state and logs?")
                    .setPositiveButton("Reset") { _, _ ->
                        HookStateStore.reset()
                        Logger.clear()
                        Config.reset()
                        Toast.makeText(this@SettingsActivity, "State reset", Toast.LENGTH_SHORT).show()
                        recreate()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })

        scrollView.addView(root)
        setContentView(scrollView)
    }

    private fun createToggle(label: String, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)

            val textView = TextView(this@SettingsActivity).apply {
                text = label
                textSize = 14f
                setTextColor(Color.parseColor("#CCCCCC"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val switchView = Switch(this@SettingsActivity).apply {
                isChecked = initial
                setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
            }

            addView(textView)
            addView(switchView)
        }
    }

    private fun createSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#D4A574"))
            setPadding(0, 32, 0, 16)
        }
    }
}
