package com.mytv.tvos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** tvOS-style settings: dim title, translucent squircle on the left, flat rows on the right, white focused row. */
class SettingsActivity : Activity() {

    private sealed class Row {
        data class Nav(val title: String, val section: String? = null, val action: String? = null,
                       val value: String? = null, val desc: String = "") : Row()
        data class Toggle(val title: String, val key: String, val def: Boolean, val desc: String = "") : Row()
        data class Label(val text: String) : Row()
    }

    private lateinit var desc: TextView
    private lateinit var rows: LinearLayout
    private val prefs by lazy { getSharedPreferences("tvos_settings", Context.MODE_PRIVATE) }


    private fun pages(): Map<String, Pair<String, List<Row>>> = mapOf(
        "root" to ("Settings" to listOf(
            Row.Nav("General", section = "general"),
            Row.Nav("Profiles and Accounts", action = Settings.ACTION_SYNC_SETTINGS),
            Row.Nav("Video and Audio", section = "video"),
            Row.Nav("Screen Saver", action = Settings.ACTION_DREAM_SETTINGS),
            Row.Nav("Notifications", action = Settings.ACTION_APP_NOTIFICATION_SETTINGS),
            Row.Nav("AirPlay and Apple Home", action = Settings.ACTION_CAST_SETTINGS),
            Row.Nav("Remotes and Devices", action = Settings.ACTION_BLUETOOTH_SETTINGS),
            Row.Nav("Accessibility", action = Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Row.Nav("Apps", section = "apps"),
            Row.Nav("Network", action = Settings.ACTION_WIFI_SETTINGS),
            Row.Nav("System", action = Settings.ACTION_SETTINGS)
        )),
        "apps" to ("Apps" to listOf(
            Row.Toggle("Automatically Update Apps", "upd", true, "Automatically update apps when updates are available."),
            Row.Toggle("Automatically Install Apps", "inst", false, "Automatically install apps that are installed on your other devices."),
            Row.Toggle("Automatically Download In-App Content", "dl", true, "Automatically run apps in the background to download content before you first launch them."),
            Row.Toggle("Offload Unused Apps", "off", false, "Automatically remove unused apps but keep their documents and data."),
            Row.Label("APP SETTINGS"),
            Row.Nav("TV", action = Settings.ACTION_APPLICATION_SETTINGS),
            Row.Nav("Music", action = Settings.ACTION_APPLICATION_SETTINGS),
            Row.Nav("Computers", action = Settings.ACTION_APPLICATION_SETTINGS),
            Row.Nav("Photos", action = Settings.ACTION_APPLICATION_SETTINGS),
            Row.Nav("Fitness", action = Settings.ACTION_APPLICATION_SETTINGS),
            Row.Nav("Podcasts", action = Settings.ACTION_APPLICATION_SETTINGS)
        )),
        "general" to ("General" to listOf(
            Row.Nav("Appearance", action = Settings.ACTION_DISPLAY_SETTINGS, value = "Automatic"),
            Row.Toggle("Sounds and Music", "snd", true, "Play sound effects and music in menus."),
            Row.Nav("Language and Region", action = Settings.ACTION_LOCALE_SETTINGS),
            Row.Nav("Date and Time", action = Settings.ACTION_DATE_SETTINGS),
            Row.Nav("Keyboard", action = Settings.ACTION_INPUT_METHOD_SETTINGS),
            Row.Nav("Storage", action = Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            Row.Nav("About", action = Settings.ACTION_DEVICE_INFO_SETTINGS)
        )),
        "video" to ("Video and Audio" to listOf(
            Row.Label("VIDEO"),
            Row.Nav("Format", action = Settings.ACTION_DISPLAY_SETTINGS, value = "4K SDR"),
            Row.Toggle("Match Dynamic Range", "mdr", false, "Switch the display to the dynamic range of the content."),
            Row.Toggle("Match Frame Rate", "mfr", false, "Switch the display to the frame rate of the content."),
            Row.Label("AUDIO"),
            Row.Nav("Audio Output", action = Settings.ACTION_SOUND_SETTINGS),
            Row.Toggle("Reduce Loud Sounds", "rls", false, "Limit the loudest sounds."),
            Row.Toggle("Sound Effects", "sfx", true, "Play sound effects.")
        ))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        desc = findViewById(R.id.desc)
        rows = findViewById(R.id.rows)
        val key = intent.getStringExtra("section") ?: "root"
        val (title, list) = pages()[key] ?: pages().getValue("root")
        findViewById<TextView>(R.id.title).text = title
        for (r in list) addRow(r)
        rows.post {
            for (i in 0 until rows.childCount) {
                val c = rows.getChildAt(i)
                if (c.isFocusable) { c.requestFocus(); break }
            }
        }
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun addRow(r: Row) {
        if (r is Row.Label) {
            rows.addView(TextView(this).apply {
                text = r.text
                textSize = 11f
                setTextColor(0x66FFFFFF)
                setPadding(dp(10), dp(16), 0, dp(4))
            })
            return
        }
        val title: String; var value: String? = null; var chevron = false; var d = ""
        when (r) {
            is Row.Nav -> { title = r.title; value = r.value; chevron = true; d = r.desc }
            is Row.Toggle -> { title = r.title; value = if (prefs.getBoolean(r.key, r.def)) "On" else "Off"; d = r.desc }
            else -> return
        }
        val dim = resources.getColorStateList(R.color.row_dim_sel, theme)
        val main = resources.getColorStateList(R.color.row_text_sel, theme)
        val valueView = TextView(this).apply {
            text = value ?: ""
            textSize = 15f
            setTextColor(dim)
            isDuplicateParentStateEnabled = true
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            minimumHeight = dp(44)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setBackgroundResource(R.drawable.bg_row)
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 17f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(main)
            isDuplicateParentStateEnabled = true
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(valueView)
        if (chevron) row.addView(TextView(this).apply {
            text = "\u203A"
            textSize = 22f
            setTextColor(dim)
            setPadding(dp(10), 0, 0, 0)
            isDuplicateParentStateEnabled = true
        })
        row.setOnFocusChangeListener { v, f ->
            if (f) desc.text = d
            v.animate().scaleX(if (f) 1.02f else 1f).scaleY(if (f) 1.02f else 1f).setDuration(120).start()
        }
        row.setOnClickListener {
            when (r) {
                is Row.Toggle -> {
                    val now = !prefs.getBoolean(r.key, r.def)
                    prefs.edit().putBoolean(r.key, now).apply()
                    valueView.text = if (now) "On" else "Off"
                }
                is Row.Nav -> when {
                    r.section != null -> startActivity(Intent(this, SettingsActivity::class.java).putExtra("section", r.section))
                    r.action != null -> try { startActivity(Intent(r.action).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        rows.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(3), 0, dp(3)) })
    }
}
