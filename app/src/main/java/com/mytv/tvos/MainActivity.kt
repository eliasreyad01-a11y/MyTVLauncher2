package com.mytv.tvos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var clock: TextView
    private lateinit var focusName: TextView
    private lateinit var dock: RecyclerView
    private lateinit var controlCenter: View
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("h:mm", Locale.US).format(Date())
            handler.postDelayed(this, 15_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clock = findViewById(R.id.clock)
        focusName = findViewById(R.id.focusName)
        dock = findViewById(R.id.dock)
        controlCenter = findViewById(R.id.controlCenter)

        dock.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        val apps = loadApps()
        dock.adapter = AppsAdapter(apps, { item -> focusName.text = item.label }, { item -> launch(item.packageName) })
        if (apps.isNotEmpty()) focusName.text = apps[0].label
        dock.post { dock.getChildAt(0)?.requestFocus() }

        findViewById<ImageButton>(R.id.btnControl).setOnClickListener { toggleControlCenter() }
        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener { openSettings() }
        findViewById<ImageButton>(R.id.btnHome).setOnClickListener {
            dock.scrollToPosition(0)
            dock.post { dock.getChildAt(0)?.requestFocus() }
        }
        findViewById<ImageButton>(R.id.btnSound).setOnClickListener { start(Settings.ACTION_SOUND_SETTINGS) }
        findViewById<ImageButton>(R.id.btnBt).setOnClickListener { start(Settings.ACTION_BLUETOOTH_SETTINGS) }

        findViewById<View>(R.id.ccWifi).setOnClickListener { start(Settings.ACTION_WIFI_SETTINGS) }
        findViewById<View>(R.id.ccBt).setOnClickListener { start(Settings.ACTION_BLUETOOTH_SETTINGS) }
        findViewById<View>(R.id.ccSound).setOnClickListener { start(Settings.ACTION_SOUND_SETTINGS) }
        findViewById<View>(R.id.ccAccess).setOnClickListener { start(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        findViewById<View>(R.id.ccSettings).setOnClickListener { openSettings() }
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onBackPressed() {
        if (controlCenter.visibility == View.VISIBLE) toggleControlCenter()
        // Home screen: back does nothing, like tvOS.
    }

    private fun toggleControlCenter() {
        controlCenter.visibility = if (controlCenter.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (controlCenter.visibility == View.VISIBLE) controlCenter.findViewById<View>(R.id.ccWifi).requestFocus()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val leanback = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
        )
        val launcher = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        return (leanback + launcher)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun launch(pkg: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) startActivity(intent) else start(Settings.ACTION_APPLICATION_SETTINGS)
    }

    private fun start(action: String) {
        try {
            startActivity(Intent(action))
        } catch (_: Exception) {
            // Not available on this device.
        }
    }
}
