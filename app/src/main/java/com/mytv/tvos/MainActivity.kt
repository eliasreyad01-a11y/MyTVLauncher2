package com.mytv.tvos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.KeyEvent
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var clock: TextView
    private lateinit var dock: RecyclerView
    private lateinit var grid: RecyclerView
    private lateinit var dimView: View
    private var lastDim = 0.15f

    /** Like tvOS: the wallpaper darkens when you move down into the app rows or open Control Center. */
    private fun dim(a: Float) { lastDim = a; dimView.animate().alpha(a).setDuration(250).start() }
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
        dock = findViewById(R.id.dock)
        grid = findViewById(R.id.grid)
        controlCenter = findViewById(R.id.controlCenter)
        dimView = findViewById(R.id.dim)

        dock.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        val apps = loadApps()
        val dockApps = apps.take(6)
        dock.adapter = AppsAdapter(dockApps, { dim(0.15f) }, { item -> launch(item.packageName) })
        grid.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 5)
        grid.adapter = AppsAdapter(apps.drop(6), { dim(0.55f) }, { item -> launch(item.packageName) })
        dock.post { dock.getChildAt(0)?.requestFocus() }

        findViewById<View>(R.id.ccNet).setOnClickListener { start(Settings.ACTION_WIFI_SETTINGS) }
        findViewById<View>(R.id.ccDnd).setOnClickListener { start(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }
        findViewById<View>(R.id.ccSleep).setOnClickListener { start(Settings.ACTION_DISPLAY_SETTINGS) }
        findViewById<View>(R.id.ccPower).setOnClickListener { start(Settings.ACTION_DREAM_SETTINGS) }
        findViewById<View>(R.id.ccSearch).setOnClickListener { start(Intent.ACTION_ASSIST) }
        findViewById<View>(R.id.ccApps).setOnClickListener { start(Settings.ACTION_APPLICATION_SETTINGS) }
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

    /** tvOS opens Control Center with the remote's dedicated button; on Android TV that is the Menu or Settings key. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS || keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            toggleControlCenter()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (controlCenter.visibility == View.VISIBLE) toggleControlCenter()
        // Home screen: back does nothing, like tvOS.
    }

    private fun toggleControlCenter() {
        val open = controlCenter.visibility != View.VISIBLE
        controlCenter.visibility = if (open) View.VISIBLE else View.GONE
        if (open) { controlCenter.findViewById<View>(R.id.ccNet).requestFocus(); dimView.animate().alpha(0.6f).setDuration(250).start() }
        else dimView.animate().alpha(lastDim).setDuration(250).start()
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
        if (intent != null) { startActivity(intent); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out) } else start(Settings.ACTION_APPLICATION_SETTINGS)
    }

    private fun start(action: String) {
        try {
            startActivity(Intent(action))
        } catch (_: Exception) {
            // Not available on this device.
        }
    }
}
