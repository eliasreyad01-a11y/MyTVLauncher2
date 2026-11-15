package com.mytv.tvos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home: top shelf with the focused app, a Liquid Glass dock plate at the bottom.
 * Down opens the library (the dock rises to the top and the app grid slides up); Back closes it.
 * Menu / Settings key opens Control Center.
 */
class MainActivity : Activity() {

    private lateinit var root: View
    private lateinit var clock: TextView
    private lateinit var dock: RecyclerView
    private lateinit var grid: RecyclerView
    private lateinit var plate: View
    private lateinit var shelf: View
    private lateinit var shelfIcon: ImageView
    private lateinit var shelfName: TextView
    private lateinit var dimView: View
    private lateinit var controlCenter: View
    private var library = false
    private var homeDim = 0.15f

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("h:mm", Locale.US).format(Date())
            handler.postDelayed(this, 15_000L)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        clock = findViewById(R.id.clock)
        dock = findViewById(R.id.dock)
        grid = findViewById(R.id.grid)
        plate = findViewById(R.id.plate)
        shelf = findViewById(R.id.shelf)
        shelfIcon = findViewById(R.id.shelfIcon)
        shelfName = findViewById(R.id.shelfName)
        dimView = findViewById(R.id.dim)
        controlCenter = findViewById(R.id.controlCenter)

        val apps = loadApps()
        dock.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        dock.adapter = AppsAdapter(apps.take(6), { item ->
            shelfIcon.setImageDrawable(item.icon)
            shelfName.text = item.label
        }, { item -> launch(item.packageName) })
        grid.layoutManager = GridLayoutManager(this, 5)
        grid.adapter = AppsAdapter(apps.drop(6), { }, { item -> launch(item.packageName) })

        root.post {
            // The grid starts just under the dock once the dock sits at the top of the screen.
            grid.setPadding(grid.paddingLeft, dp(84) + plate.height + dp(16), grid.paddingRight, grid.paddingBottom)
            grid.translationY = root.height.toFloat()
            dock.getChildAt(0)?.requestFocus()
        }
        wireControlCenter()
    }

    private fun enterLibrary() {
        if (library || grid.adapter?.itemCount == 0) return
        library = true
        val up = -(plate.top - dp(84)).toFloat()
        plate.animate().translationY(up).setDuration(340).start()
        shelf.animate().alpha(0f).setDuration(240).start()
        grid.visibility = View.VISIBLE
        grid.animate().alpha(1f).translationY(0f).setDuration(340).start()
        dimTo(0.55f)
        grid.post { grid.getChildAt(0)?.requestFocus() }
    }

    private fun exitLibrary() {
        if (!library) return
        library = false
        plate.animate().translationY(0f).setDuration(300).start()
        shelf.animate().alpha(1f).setDuration(300).start()
        grid.animate().alpha(0f).translationY(root.height.toFloat()).setDuration(300)
            .withEndAction { if (!library) grid.visibility = View.INVISIBLE }.start()
        dimTo(0.15f)
        dock.post { dock.getChildAt(0)?.requestFocus() }
    }

    private fun dimTo(a: Float) {
        homeDim = a
        dimView.animate().alpha(a).setDuration(300).start()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_BUTTON_Y -> {
                toggleControlCenter(); return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (!library && controlCenter.visibility != View.VISIBLE) {
                enterLibrary(); return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        when {
            controlCenter.visibility == View.VISIBLE -> toggleControlCenter()
            library -> exitLibrary()
        }
    }

    override fun onResume() { super.onResume(); handler.post(ticker) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(ticker) }

    // ---- Control Center ----
    private fun wireControlCenter() {
        val pages = mapOf(
            R.id.tabProfiles to R.id.pageProfiles,
            R.id.tabQuick to R.id.pageQuick,
            R.id.tabHome to R.id.pageHome,
            R.id.tabVpn to R.id.pageVpn
        )
        fun show(tab: Int) {
            for ((t, p) in pages) {
                findViewById<View>(p).visibility = if (t == tab) View.VISIBLE else View.GONE
                findViewById<View>(t).alpha = if (t == tab) 1f else 0.6f
            }
        }
        for (t in pages.keys) {
            findViewById<View>(t).setOnFocusChangeListener { _, f -> if (f) show(t) }
            findViewById<View>(t).setOnClickListener { show(t) }
        }
        show(R.id.tabQuick)
        findViewById<View>(R.id.ccNet).setOnClickListener { start(Settings.ACTION_WIFI_SETTINGS) }
        findViewById<View>(R.id.ccDnd).setOnClickListener { start(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }
        findViewById<View>(R.id.ccSleep).setOnClickListener { start(Settings.ACTION_DISPLAY_SETTINGS) }
        findViewById<View>(R.id.ccPower).setOnClickListener { start(Settings.ACTION_DREAM_SETTINGS) }
        findViewById<View>(R.id.ccSearch).setOnClickListener { start(Intent.ACTION_ASSIST) }
        findViewById<View>(R.id.ccApps).setOnClickListener { start(Settings.ACTION_APPLICATION_SETTINGS) }
        findViewById<View>(R.id.ccAccess).setOnClickListener { start(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        findViewById<View>(R.id.ccSettings).setOnClickListener {
            toggleControlCenter()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun toggleControlCenter() {
        val open = controlCenter.visibility != View.VISIBLE
        controlCenter.visibility = if (open) View.VISIBLE else View.GONE
        if (open) {
            controlCenter.alpha = 0f
            controlCenter.animate().alpha(1f).setDuration(220).start()
            findViewById<View>(R.id.tabQuick).requestFocus()
            dimView.animate().alpha(0.6f).setDuration(250).start()
        } else {
            dimView.animate().alpha(homeDim).setDuration(250).start()
            (if (library) grid.getChildAt(0) else dock.getChildAt(0))?.requestFocus()
        }
    }

    // ---- Apps ----
    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val leanback = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        val launcher = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        return (leanback + launcher)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun launch(pkg: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        } else start(Settings.ACTION_APPLICATION_SETTINGS)
    }

    private fun start(action: String) {
        try { startActivity(Intent(action)) } catch (_: Exception) { }
    }
}
