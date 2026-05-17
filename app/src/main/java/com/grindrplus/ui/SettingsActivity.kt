package com.grindrplus.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.grindrplus.R
import com.grindrplus.core.Config
import com.grindrplus.utils.Logger

/**
 * SettingsActivity — Module configuration UI.
 *
 * Shows hook toggles and module status.
 * Persists settings via Config (JSON file on device).
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val hooksContainer = findViewById<LinearLayout>(R.id.hooksContainer)

        // Check if module is active via Xposed
        val isModuleActive = false // XposedHelpers would check this at runtime
        tvStatus.text = if (isModuleActive) {
            tvStatus.setTextColor(0xFF4CAF50.toInt())
            "✓ Module active — GrindrPlus is loaded"
        } else {
            tvStatus.setTextColor(0xFFE94560.toInt())
            "✗ Module NOT active — enable in LSPosed/LSPatch and reboot"
        }

        // Dynamically add toggle for each hook
        val hooks = listOf(
            "Anti-Detection" to "Hides root, LSPosed, debugger, emulator",
            "PairIP Blocker" to "Blocks PairIP license verification",
            "GMS Spoof" to "Fakes Google Play Services / Play Integrity",
            "Feature Granting" to "Unlimited cascades, Explore, incognito, etc.",
            "Enable Unlimited" to "Removes all usage limits",
            "Ban Management" to "Prevents bans and shadow-bans",
            "Timber Logging" to "Debug logging (performance impact)"
        )

        for ((name, description) in hooks) {
            val switchView = Switch(this).apply {
                text = name
                textSize = 16f
                setTextColor(0xFFFFFFFF)
                isChecked = Config.isHookEnabled(name, default = true)
                setOnCheckedChangeListener { _, isChecked ->
                    Config.setHookEnabled(name, isChecked)
                    Logger.i("GrindrPlus", "Hook '$name' ${if (isChecked) "enabled" else "disabled"}")
                }
            }

            val descView = TextView(this).apply {
                text = description
                textSize = 12f
                setTextColor(0xFFAAAAAA)
                setPadding(48, 0, 0, 16)
            }

            hooksContainer.addView(switchView)
            hooksContainer.addView(descView)
        }
    }
}
