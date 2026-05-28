package com.grindrplus.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.grindrplus.BuildConfig
import com.grindrplus.core.Config
import com.grindrplus.core.HookStateStore
import com.grindrplus.utils.Logger

/**
 * Launcher Activity — GrindrPlus module settings UI.
 *
 * Crash fixes applied:
 *
 * Fix A: Config.init() and HookStateStore.init(context) are now called in onCreate()
 *   before any reads. Previously, SettingsActivity read from Config and HookStateStore
 *   before they were initialised — they are only initialised inside Grindr's process via
 *   GrindrPlus.kt. In standalone mode (opening the app directly) neither singleton had
 *   been set up, causing NPE / silent crash on the very first Config.isDebugMode() call.
 *
 * Fix B: Logger.kt has been updated to call XposedBridge reflectively so that
 *   NoClassDefFoundError is caught at runtime rather than crashing at class-load time.
 *   Logger calls here are therefore safe in both standalone and hooked modes.
 *
 * Fix C: Theme.GrindrPlus now extends Theme.AppCompat.NoActionBar (see themes.xml).
 *   The previous parent (android:Theme.Material.Light.NoActionBar) is not an AppCompat
 *   theme, causing "You need to use a Theme.AppCompat theme" crash on AppCompatActivity.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fix A: Initialise singletons with a real Context before any reads
        Config.init()
        HookStateStore.init(this)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A1D"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // ── Header ────────────────────────────────────────────────────────────
        root.addView(TextView(this).apply {
            text = "GrindrPlus v${BuildConfig.VERSION_NAME}"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        })

        root.addView(TextView(this).apply {
            text = "Xposed module — enable in LSPosed/LSPatch and select Grindr as the scope."
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        // ── Module active indicator ───────────────────────────────────────────
        val isModuleActive = isXposedActive()
        root.addView(TextView(this).apply {
            text = if (isModuleActive) "✓  Module is ACTIVE" else "✗  Module is NOT active (enable in LSPosed)"
            textSize = 14f
            setTextColor(if (isModuleActive) Color.parseColor("#4CAF50") else Color.parseColor("#FF5722"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        // ── General toggles ───────────────────────────────────────────────────
        root.addView(createSectionHeader("General"))

        root.addView(createToggle("Debug Mode", Config.isDebugMode()) { enabled ->
            Config.setDebugMode(enabled)
            Logger.log("Settings: Debug mode ${if (enabled) "enabled" else "disabled"}")
        })

        root.addView(createToggle("Remote Config", Config.isRemoteConfigEnabled()) { enabled ->
            Config.setRemoteConfigEnabled(enabled)
            Logger.log("Settings: Remote config ${if (enabled) "enabled" else "disabled"}")
        })

        root.addView(createToggle("Safe Mode (essential hooks only)", HookStateStore.isSafeMode()) { enabled ->
            HookStateStore.setSafeMode(enabled)
            Logger.log("Settings: Safe mode ${if (enabled) "enabled" else "disabled"}")
        })

        // ── Premium feature toggles ───────────────────────────────────────────
        root.addView(createSectionHeader("Premium Features"))

        val features = listOf(
            Config.FEATURE_UNLIMITED_MESSAGES  to "Unlimited Messages",
            Config.FEATURE_SEE_VIEWERS         to "See Who Viewed Me",
            Config.FEATURE_UNLIMITED_REWINDS   to "Unlimited Rewinds",
            Config.FEATURE_PREMIUM_FILTERS     to "Premium Filters",
            Config.FEATURE_EXPIRING_PHOTOS     to "Expiring Photos",
            Config.FEATURE_TYPING_STATUS       to "Typing Status",
            Config.FEATURE_ADVANCED_SEARCH     to "Advanced Search",
            Config.FEATURE_HIDE_ONLINE         to "Hide Online Status",
            Config.FEATURE_HIDE_READ_RECEIPTS  to "Hide Read Receipts",
            Config.FEATURE_HIDE_DISTANCE       to "Hide Distance"
        )

        features.forEach { (key, label) ->
            root.addView(createToggle(label, Config.isFeatureEnabled(key)) { enabled ->
                Config.setFeatureEnabled(key, enabled)
                Logger.log("Settings: Feature '$key' ${if (enabled) "enabled" else "disabled"}")
            })
        }

        // ── Hook status ───────────────────────────────────────────────────────
        root.addView(createSectionHeader("Hooks"))

        val hooks = Config.getRegisteredHooks()
        if (hooks.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No hooks registered yet — hooks are registered when Grindr runs with the module active."
                textSize = 13f
                setTextColor(Color.parseColor("#888888"))
                setPadding(0, 8, 0, 8)
            })
        } else {
            hooks.forEach { (name, enabled) ->
                root.addView(createToggle(name, enabled) { isEnabled ->
                    Config.setHookEnabled(name, isEnabled)
                    Logger.log("Settings: Hook '$name' ${if (isEnabled) "enabled" else "disabled"}")
                })
            }
        }

        // ── Diagnostics ───────────────────────────────────────────────────────
        root.addView(createSectionHeader("Diagnostics"))

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

        root.addView(Button(this).apply {
            text = "Export Logs"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(0, 32, 0, 32)
            setOnClickListener {
                val path = Logger.exportLogs(cacheDir)
                Toast.makeText(
                    this@SettingsActivity,
                    if (path != null) "Logs saved to $path" else "Export failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

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

    /**
     * Returns whether the Xposed module is currently active in this process.
     * In standalone mode this always returns false. When running inside Grindr's
     * process under LSPosed/LSPatch, GrindrPlus hooks this method to return true.
     */
    private fun isXposedActive(): Boolean = false

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

            @Suppress("DEPRECATION")
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
