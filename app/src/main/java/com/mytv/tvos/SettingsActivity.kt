package com.mytv.tvos

import android.app.Activity
import android.content.res.ColorStateList

import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class SettingsActivity : Activity() {

    private lateinit var desc: TextView
    private lateinit var rows: LinearLayout

    private data class Entry(val title: String, val action: String, val description: String)

    private val entries = listOf(
        Entry("General", Settings.ACTION_SETTINGS, "Device settings and system options."),
        Entry("Display", Settings.ACTION_DISPLAY_SETTINGS, "Brightness, resolution and display options."),
        Entry("Sound", Settings.ACTION_SOUND_SETTINGS, "Volume and sound output."),
        Entry("Network", Settings.ACTION_WIFI_SETTINGS, "Wi-Fi and network connections."),
        Entry("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, "Pair and manage Bluetooth devices."),
        Entry("Accounts", Settings.ACTION_SYNC_SETTINGS, "Accounts and sync."),
        Entry("Screen Saver", Settings.ACTION_DREAM_SETTINGS, "Choose what plays when the screen is idle."),
        Entry("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, "Captions, text size and assistance options."),
        Entry("Apps", Settings.ACTION_APPLICATION_SETTINGS, "Installed apps and their permissions."),
        Entry("Language and Region", Settings.ACTION_LOCALE_SETTINGS, "Language, region and formats."),
        Entry("Date and Time", Settings.ACTION_DATE_SETTINGS, "Time zone and clock format."),
        Entry("Keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS, "Keyboard and input methods."),
        Entry("About", Settings.ACTION_DEVICE_INFO_SETTINGS, "Device name, software and storage.")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<TextView>(R.id.title).text = "Settings"
        desc = findViewById(R.id.desc)
        rows = findViewById(R.id.rows)

        for (e in entries) {
            val row = TextView(this).apply {
                text = e.title
                textSize = 20f
                setPadding(dp(24), dp(16), dp(24), dp(16))
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                isFocusable = true
                isClickable = true
                setBackgroundResource(R.drawable.bg_row)
                setTextColor(textColorSelector())
            }
            row.setOnFocusChangeListener { v, focused ->
                if (focused) desc.text = e.description
                v.animate().scaleX(if (focused) 1.03f else 1f).scaleY(if (focused) 1.03f else 1f).setDuration(140).start()
            }
            row.setOnClickListener { start(e.action) }
            rows.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(6)) })
        }
        rows.post { rows.getChildAt(0)?.requestFocus() }
    }

    private fun textColorSelector(): ColorStateList = resources.getColorStateList(R.color.row_text_sel, theme)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun start(action: String) {
        try {
            startActivity(android.content.Intent(action))
        } catch (_: Exception) {
            // Not available on this device.
        }
    }
}
