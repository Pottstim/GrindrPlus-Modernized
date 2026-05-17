package com.grindrplus.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.grindrplus.core.Config
import com.grindrplus.utils.Logger
import com.grindrplus.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val hooksContainer = findViewById<LinearLayout>(R.id.hooksContainer)

        Config.getHooks().forEach { (name, desc, enabled) ->
            val switchView = Switch(this).apply {
                text = name
                isChecked = enabled
                setTextColor(Color.WHITE)
                setOnCheckedChangeListener { _, isChecked ->
                    Config.setHookEnabled(name, isChecked)
                    Logger.log("Config: $name = $isChecked")
                }
            }

            val descView = TextView(this).apply {
                text = desc
                setTextColor(Color.GRAY)
                textSize = 12f
                setPadding(0, 0, 0, 24)
            }

            hooksContainer.addView(switchView)
            hooksContainer.addView(descView)
        }
    }
}
