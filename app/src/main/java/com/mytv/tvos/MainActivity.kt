package com.mytv.tvos

import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * tvOS-style home: the dock plate sits at the bottom with the focused app's big icon above it; Down lifts the page
 * into the library (the background greys out); folders open as a frosted panel; Up reaches the capsule that opens
 * Control Center; long-press OK (or Menu) opens the app menu.
 */
class MainActivity : Activity() {

    private lateinit var u: U
    private lateinit var store: Store
    private lateinit var sp: android.content.SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var wallpaper: ImageView
    private lateinit var bgGray: View
    private lateinit var grid: RecyclerView
    private lateinit var capsule: LinearLayout
    private lateinit var clock: TextClock
    private lateinit var adapter: HomeAdapter

    private var apps: List<AppItem> = emptyList()
    private var lay = Layout(emptyList(), emptyList())
    private var ccView: View? = null
    private var folderView: View? = null
    private var folderKey: String? = null
    private var screensaver: View? = null
    private var moving: String? = null
    private var sleepMin = 0
    private var lastFocusKey: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private val idleRun = Runnable { startScreensaver() }
    private val sleepRun = Runnable { sleepMin = 0; startScreensaver() }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { loadAppsAsync() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        u = U(this)
        store = Store(this)
        sp = getSharedPreferences("tvos_settings", Context.MODE_PRIVATE)

        root = FrameLayout(this).apply { setBackgroundColor(0xFF1B1B1D.toInt()) }
        content = FrameLayout(this)
        wallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.wp_kirkjufell)
            alpha = 0.6f
            visibility = View.GONE
        }
        bgGray = View(this).apply { setBackgroundColor(0xFF707072.toInt()); alpha = 0f }
        adapter = HomeAdapter(u, true, { onTileFocus(it) }, { open(it) }, { showMenu(it, false) })
        grid = RecyclerView(this).apply {
            layoutManager = HomeLayoutManager(this@MainActivity, Store.COLUMNS, u)
            this.adapter = this@MainActivity.adapter
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            setPadding(u.i(65f), u.i(HomeLayoutManager.REST_TOP), u.i(65f), u.i(700f))
            addItemDecoration(PlateDecoration(u))
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) { scrollFx() }
            })
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> scrollFx() }
        }
        content.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))
        content.addView(bgGray, FrameLayout.LayoutParams(-1, -1))
        content.addView(grid, FrameLayout.LayoutParams(-1, -1))
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        buildCapsule()
        setContentView(root)
        loadAppsAsync()
    }

    // ------------------------------------------------------------------ capsule

    private fun buildCapsule() {
        clock = TextClock(this).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(30f))
            setTextColor(Glass.text())
            isDuplicateParentStateEnabled = true
        }
        capsule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            contentDescription = "Control Center"
            setPadding(u.i(26f), 0, u.i(10f), 0)
            background = Glass.rowBg(u, 33f, 0x66333336)
            addView(clock)
            addView(AvatarView(this@MainActivity), LinearLayout.LayoutParams(u.i(48f), u.i(48f)).apply { marginStart = u.i(16f) })
            setOnClickListener { toggleCC() }
            setOnKeyListener { _, code, ev ->
                if (ev.action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN) { focusFirst(); true } else false
            }
        }
        root.addView(capsule, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u.i(66f), Gravity.TOP or Gravity.END).apply {
            topMargin = u.i(48f); marginEnd = u.i(45f)
        })
    }

    // ------------------------------------------------------------------ apps

    private fun loadAppsAsync() {
        Thread {
            val list = queryApps()
            runOnUiThread { if (!isDestroyed) setApps(list) }
        }.start()
    }

    private fun queryApps(): List<AppItem> {
        val pm = packageManager
        val lean = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        val std = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        return (lean + std).distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .map { ri ->
                val banner = try { ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm) } catch (_: Exception) { null }
                AppItem(ri.activityInfo.packageName, ri.loadLabel(pm).toString(), ri.loadIcon(pm), banner, null)
            }
    }

    private fun settingsItem() = AppItem(Store.SETTINGS_PKG, "Settings", GearDrawable(), null, Intent(this, SettingsActivity::class.java))

    /** Also the entry point for tests, which supply their own apps. */
    fun setApps(list: List<AppItem>) {
        apps = list.filter { it.pkg != Store.SETTINGS_PKG } + settingsItem()
        relayout(lastFocusKey)
    }

    private fun applyOptions(): Boolean {
        val labels = sp.getBoolean("labels", false)
        val calm = sp.getBoolean("calm", false)
        val changed = labels != adapter.alwaysLabels || calm != adapter.reduceMotion
        adapter.alwaysLabels = labels
        adapter.reduceMotion = calm
        wallpaper.visibility = if (sp.getBoolean("wall", false)) View.VISIBLE else View.GONE
        val f24 = TimeFmt.is24(this, Prefs(this))
        clock.format12Hour = if (f24) "HH:mm" else "h:mm"
        clock.format24Hour = "HH:mm"
        clock.timeZone = CityClock.gmtId(this)
        return changed
    }

    private fun relayout(focus: String?) {
        applyOptions()
        lay = store.layout(apps)
        adapter.cells = lay.cells()
        adapter.notifyDataSetChanged()
        if (focus != null) focusKey(focus) else if (grid.focusedChild == null && ccView == null && folderView == null) focusFirst()
    }

    private fun focusFirst() { focusKey(lay.dock.firstOrNull()?.pkg ?: lay.library.firstOrNull()?.key ?: return) }

    private fun focusKey(key: String) {
        val pos = adapter.cells.indexOfFirst { it.key == key }
        if (pos < 0) { if (key != lay.dock.firstOrNull()?.pkg) focusFirst(); return }
        grid.post {
            val vh = grid.findViewHolderForAdapterPosition(pos)
            if (vh != null) vh.itemView.requestFocus()
            else {
                grid.scrollToPosition(pos)
                grid.post { grid.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus() }
            }
        }
    }

    private fun onTileFocus(e: Entry) {
        lastFocusKey = e.key
    }

    private fun scrollFx() {
        val v = grid.findViewHolderForAdapterPosition(0)?.itemView
        val off = if (v == null) 100000f else u.px(HomeLayoutManager.REST_TOP) - v.top
        bgGray.alpha = (off / u.px(650f)).coerceIn(0f, 1f) * 0.94f
    }

    private fun open(e: Entry) {
        if (e.isFolder) { openFolder(e); return }
        launch(e.app ?: return)
    }

    private fun launch(a: AppItem) {
        val i = a.intent ?: packageManager.getLeanbackLaunchIntentForPackage(a.pkg) ?: packageManager.getLaunchIntentForPackage(a.pkg)
        if (i == null) { toast("Not available"); return }
        try {
            startActivity(i)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        } catch (_: Exception) { toast("Not available") }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------------ menu / move

    private fun showMenu(e: Entry, inFolder: Boolean) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        if (e.isFolder) {
            items.add("Open" to { openFolder(e) })
            items.add("Rename" to { rename(e) })
            items.add("Delete Folder" to { store.deleteFolder(e.key); relayout(null) })
        } else {
            val a = e.app ?: return
            items.add("Open" to { launch(a) })
            if (!inFolder) {
                items.add("Move" to { startMove(e) })
                if (lay.dock.any { it.pkg == a.pkg }) items.add("Remove from Dock" to { store.removeFromDock(lay, a.pkg); relayout(a.pkg) })
                else items.add("Add to Dock" to { if (store.addToDock(lay, a.pkg)) relayout(a.pkg) else toast("The dock is full") })
                items.add("New Folder with This App" to { val id = store.newFolder(lay, a.pkg); relayout("f:$id") })
                for ((k, n) in store.folderKeys(lay)) items.add("Add to $n" to { store.addToFolder(lay, k, a.pkg); relayout(k) })
            } else {
                items.add("Remove from Folder" to { store.removeFromFolder(lay, a.pkg); closeFolder(); relayout(a.pkg) })
            }
            if (a.pkg != Store.SETTINGS_PKG) {
                items.add("Hide" to { store.hidden = store.hidden + a.pkg; closeFolder(); relayout(null) })
                items.add("App Info" to { startSafe(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))) })
                items.add("Uninstall" to { startSafe(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}"))) })
            }
        }
        Menus.show(this, e.title, items)
    }

    private fun rename(e: Entry) {
        val input = android.widget.EditText(this).apply { setText(e.title); setSelectAllOnFocus(true); setSingleLine() }
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle("Rename Folder")
            .setView(input)
            .setPositiveButton("OK") { _, _ -> store.renameFolder(e.key, input.text.toString()); relayout(e.key) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startMove(e: Entry) {
        moving = e.key
        adapter.movingKey = e.key
        adapter.notifyDataSetChanged()
        focusKey(e.key)
    }

    private fun endMove() {
        val k = moving
        moving = null
        adapter.movingKey = null
        adapter.notifyDataSetChanged()
        if (k != null) focusKey(k)
    }

    private fun moveStep(code: Int) {
        val k = moving ?: return
        val inDock = lay.dock.any { it.pkg == k }
        val delta = when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            KeyEvent.KEYCODE_DPAD_UP -> if (inDock) 0 else -Store.COLUMNS
            KeyEvent.KEYCODE_DPAD_DOWN -> if (inDock) 0 else Store.COLUMNS
            else -> 0
        }
        if (delta == 0) return
        val moved = if (inDock) store.moveInDock(lay, k, delta) else store.moveInLibrary(lay, k, delta)
        if (moved) { lay = store.layout(apps); adapter.cells = lay.cells(); adapter.notifyDataSetChanged(); focusKey(k) }
    }

    // ------------------------------------------------------------------ folder

    private fun openFolder(e: Entry) {
        if (folderView != null) return
        folderKey = e.key
        val fRoot = FrameLayout(this)
        fRoot.addView(Blur.view(this, Blur.fromView(content), 0x59000000), FrameLayout.LayoutParams(-1, -1))
        val title = TextView(this).apply {
            text = e.title
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(40f))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFF1C1C1E.toInt())
            gravity = Gravity.CENTER
            background = Glass.solid(0xE6EDEDF0.toInt(), u.px(47f))
            setPadding(u.i(60f), 0, u.i(60f), 0)
        }
        fRoot.addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u.i(94f), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = u.i(33f) })
        val panel = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x47FFFFFF); cornerRadius = u.px(58f); setStroke(u.i(2f), 0x40FFFFFF)
            }
        }
        val cellsF = e.folderApps.map { Entry(it.pkg, it) }
        val dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val ad = HomeAdapter(u, false, { en -> updateDots(dots, cellsF.indexOfFirst { it.key == en.key } / 9, (cellsF.size + 8) / 9) },
            { en -> en.app?.let { launch(it) } }, { en -> showMenu(en, true) })
        ad.cells = cellsF
        ad.alwaysLabels = sp.getBoolean("labels", false)
        ad.reduceMotion = sp.getBoolean("calm", false)
        val rv = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = ad
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            setPadding(0, u.i(30f), 0, u.i(40f))
        }
        panel.addView(rv, FrameLayout.LayoutParams(-1, -1))
        fRoot.addView(panel, FrameLayout.LayoutParams(u.i(1197f), u.i(771f), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = u.i(150f) })
        fRoot.addView(dots, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u.i(30f), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = u.i(940f) })
        updateDots(dots, 0, (cellsF.size + 8) / 9)
        folderView = fRoot
        root.addView(fRoot, FrameLayout.LayoutParams(-1, -1))
        rv.post { rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
    }

    private fun updateDots(dots: LinearLayout, page: Int, pages: Int) {
        dots.removeAllViews()
        if (pages < 2) return
        for (i in 0 until pages) dots.addView(View(this).apply {
            background = Glass.solid(if (i == page) 0xFFFFFFFF.toInt() else 0x66FFFFFF, u.px(10f))
        }, LinearLayout.LayoutParams(u.i(14f), u.i(14f)).apply { marginStart = u.i(8f); marginEnd = u.i(8f) })
    }

    private fun closeFolder() {
        val v = folderView ?: return
        root.removeView(v)
        folderView = null
        val k = folderKey
        folderKey = null
        if (k != null) focusKey(k)
    }

    private fun blurContent(on: Boolean, radius: Float) {
        if (Build.VERSION.SDK_INT >= 31) {
            content.setRenderEffect(if (on) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null)
        }
    }

    // ------------------------------------------------------------------ control center

    private fun toggleCC() { if (ccView == null) openCC() else closeCC() }

    private fun glassTile(w: Float, h: Float, icon: Int, title: String, sub: String?, vertical: Boolean, action: (TextView?) -> Unit): LinearLayout {
        val subView = TextView(this).apply {
            text = sub ?: ""
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(26f))
            setTextColor(Glass.dim())
            isDuplicateParentStateEnabled = true
            maxLines = 2
            visibility = if (sub == null) View.GONE else View.VISIBLE
        }
        val l = LinearLayout(this).apply {
            orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (vertical) Gravity.BOTTOM or Gravity.START else Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(u.i(30f), u.i(24f), u.i(30f), u.i(24f))
            background = Glass.rowBg(u, 48f, 0x38FFFFFF)
            setOnClickListener { action(subView) }
        }
        val img = ImageView(this).apply {
            setImageResource(icon)
            imageTintList = Glass.text()
            isDuplicateParentStateEnabled = true
        }
        l.addView(img, LinearLayout.LayoutParams(u.i(if (vertical) 72f else 60f), u.i(if (vertical) 72f else 60f)).apply {
            if (vertical) bottomMargin = u.i(18f) else marginEnd = u.i(22f)
        })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; isDuplicateParentStateEnabled = true }
        col.addView(TextView(this).apply {
            text = title
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(32f))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Glass.text())
            isDuplicateParentStateEnabled = true
            maxLines = 1
        })
        col.addView(subView)
        l.addView(col)
        l.layoutParams = LinearLayout.LayoutParams(u.i(w), u.i(h)).apply { setMargins(u.i(8f), u.i(8f), u.i(8f), u.i(8f)) }
        return l
    }

    private fun round(icon: Int, desc: String, action: () -> Unit): View = ImageView(this).apply {
        setImageResource(icon)
        imageTintList = Glass.text()
        contentDescription = desc
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(u.i(30f), u.i(30f), u.i(30f), u.i(30f))
        isFocusable = true
        isClickable = true
        background = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0xFFFFFFFF.toInt()) })
            addState(intArrayOf(), android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0x38FFFFFF) })
        }
        setOnClickListener { closeCC(); action() }
        layoutParams = LinearLayout.LayoutParams(u.i(112f), u.i(112f)).apply { setMargins(u.i(8f), u.i(8f), u.i(8f), u.i(8f)) }
    }

    private fun wifiState(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = try { cm.getNetworkCapabilities(cm.activeNetwork) } catch (_: Exception) { null }
        return when {
            caps == null -> "Not connected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Connected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
    }

    private fun dndOn(): Boolean = try {
        val nm = getSystemService(NotificationManager::class.java)
        nm.isNotificationPolicyAccessGranted && nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    } catch (_: Exception) { false }

    private fun openCC() {
        val frame = FrameLayout(this)
        frame.addView(Blur.view(this, Blur.fromView(content), 0x73000000).apply { setOnClickListener { closeCC() } }, FrameLayout.LayoutParams(-1, -1))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Glass.solid(0xD91C1C1E.toInt(), u.px(70f))
            setPadding(u.i(24f), u.i(24f), u.i(24f), u.i(24f))
        }
        val power = glassTile(230f, 316f, R.drawable.ic_power, "Sleep", null, true) { closeCC(); startScreensaver() }
        val vol = VolumePill(this).also { it.layoutParams = LinearLayout.LayoutParams(u.i(86f), u.i(316f)).apply { setMargins(u.i(8f), u.i(8f), u.i(8f), u.i(8f)) } }
        val wifi = glassTile(560f, 150f, R.drawable.ic_wifi, "Wi-Fi", wifiState(), false) { closeCC(); startSafe(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        val dnd = glassTile(560f, 150f, R.drawable.ic_moon, "Do Not Disturb", if (dndOn()) "On" else "Off", false) { sub ->
            try {
                val nm = getSystemService(NotificationManager::class.java)
                if (nm.isNotificationPolicyAccessGranted) {
                    nm.setInterruptionFilter(if (dndOn()) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_NONE)
                    sub?.text = if (dndOn()) "On" else "Off"
                } else { closeCC(); startSafe(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            } catch (_: Exception) { toast("Not available") }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(wifi); addView(dnd) }
        val rowA = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(power); addView(vol); addView(col) }
        val timerSub = { if (sleepMin == 0) "Off" else "$sleepMin min" }
        val timer = glassTile(440f, 150f, R.drawable.ic_timer, "Sleep Timer", timerSub(), false) { sub ->
            sleepMin = when (sleepMin) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 }
            handler.removeCallbacks(sleepRun)
            if (sleepMin > 0) handler.postDelayed(sleepRun, sleepMin * 60_000L)
            sub?.text = timerSub()
        }
        val audio = glassTile(440f, 150f, R.drawable.ic_audio, "Audio", "Output", false) { closeCC(); startSafe(Intent(Settings.ACTION_SOUND_SETTINGS)) }
        val rowB = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(timer); addView(audio) }
        val rowC = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(round(R.drawable.ic_search, "Search") { startSafe(Intent(Intent.ACTION_ASSIST)) })
            addView(round(R.drawable.ic_access, "Accessibility") { startSafe(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            addView(round(R.drawable.ic_gear, "Settings") { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) })
        }
        val pr = glassTile(440f, 150f, R.drawable.ic_prayer, "Next Prayer", "Loading…", false) { closeCC(); startActivity(Intent(this, SettingsActivity::class.java).putExtra("page", "prayer")) }
        val wx = glassTile(440f, 150f, R.drawable.ic_sun, "Weather", "Loading…", false) { closeCC(); startActivity(Intent(this, SettingsActivity::class.java).putExtra("page", "prayer")) }
        val rowP = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(pr); addView(wx) }
        fillInfo(pr, wx)
        val dateLine = TextView(this).apply {
            text = dateText()
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(30f))
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, u.i(4f), 0, u.i(10f))
        }
        panel.addView(dateLine); panel.addView(rowA); panel.addView(rowB); panel.addView(rowP); panel.addView(rowC)
        frame.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = u.i(140f); marginEnd = u.i(45f)
        })
        ccView = frame
        root.addView(frame, FrameLayout.LayoutParams(-1, -1))
        frame.alpha = 0f
        frame.animate().alpha(1f).setDuration(200).start()
        power.requestFocus()
    }

    private fun dateText(): String {
        val p = Prefs(this)
        val today = PrayerRepository.cityToday(this)
        val g = java.text.SimpleDateFormat("EEE, d MMM yyyy", java.util.Locale.ENGLISH).apply { timeZone = CityClock.timeZone(this@MainActivity) }.format(java.util.Date())
        val h = HijriDate.format(this, today, java.util.Locale.ENGLISH)
        return when (p.dateMode) { 1 -> g; 2 -> h; else -> if (h.isEmpty()) g else "$g  ·  $h" }
    }

    private fun subOf(tile: LinearLayout): TextView = (tile.getChildAt(1) as LinearLayout).getChildAt(1) as TextView
    private fun titleOf(tile: LinearLayout): TextView = (tile.getChildAt(1) as LinearLayout).getChildAt(0) as TextView

    /** Fetches the next prayer and the weather off the main thread and writes them into the two Control Center tiles. */
    private fun fillInfo(prayerTile: LinearLayout, weatherTile: LinearLayout) {
        val app = applicationContext
        Thread {
            val p = Prefs(app)
            CityClock.ensure(app)
            val next = try { PrayerRepository.nextPrayer(app, false) } catch (_: Exception) { null }
            val wx = WeatherRepository.fetch(p.latitude, p.longitude)
            runOnUiThread {
                if (isDestroyed || ccView == null) return@runOnUiThread
                if (next == null) { titleOf(prayerTile).text = "Prayer Times"; subOf(prayerTile).text = "Not available (no internet)" }
                else {
                    val hhmm = next.time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    val mins = java.time.Duration.between(java.time.Instant.now(), next.time.toInstant()).toMinutes().coerceAtLeast(0)
                    titleOf(prayerTile).text = next.name
                    subOf(prayerTile).text = TimeFmt.display(hhmm, TimeFmt.is24(app, p)) + "  ·  in " + (if (mins >= 60) "${mins / 60}h " else "") + "${mins % 60}m"
                }
                if (wx == null) { titleOf(weatherTile).text = "Weather"; subOf(weatherTile).text = "Not available (no internet)" }
                else {
                    titleOf(weatherTile).text = "${wx.tempC}°  " + getString(Weather.labelRes(wx.code))
                    subOf(weatherTile).text = "Today ${wx.today.maxC}°/${wx.today.minC}°\nTomorrow ${wx.tomorrow.maxC}°/${wx.tomorrow.minC}°"
                }
            }
        }.start()
    }

    private fun closeCC() {
        val v = ccView ?: return
        root.removeView(v)
        ccView = null
        lastFocusKey?.let { focusKey(it) }
    }

    // ------------------------------------------------------------------ screensaver / idle

    private fun startScreensaver() {
        if (screensaver != null) return
        closeCC(); closeFolder()
        val f = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val c = TextClock(this).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(240f))
            setTextColor(0xE6FFFFFF.toInt())
            format12Hour = if (TimeFmt.is24(this@MainActivity, Prefs(this@MainActivity))) "HH:mm" else "h:mm"
            timeZone = CityClock.gmtId(this@MainActivity)
            format24Hour = "HH:mm"
        }
        f.addView(c, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        f.alpha = 0f
        screensaver = f
        root.addView(f, FrameLayout.LayoutParams(-1, -1))
        f.animate().alpha(1f).setDuration(600).start()
        drift(c)
    }

    private fun drift(c: View) {
        if (screensaver == null) return
        val dx = (Math.random() * 2 - 1).toFloat() * u.px(300f)
        val dy = (Math.random() * 2 - 1).toFloat() * u.px(200f)
        c.animate().translationX(dx).translationY(dy).setDuration(20000).withEndAction { drift(c) }.start()
    }

    private fun stopScreensaver() {
        val v = screensaver ?: return
        screensaver = null
        root.removeView(v)
    }

    private fun resetIdle() {
        handler.removeCallbacks(idleRun)
        val m = sp.getInt("ss_min", 10)
        if (m > 0) handler.postDelayed(idleRun, m * 60_000L)
    }

    override fun onUserInteraction() { super.onUserInteraction(); resetIdle() }

    // ------------------------------------------------------------------ keys / lifecycle

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (screensaver != null) { stopScreensaver(); resetIdle(); return true }
            if (moving != null) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> endMove()
                    else -> moveStep(event.keyCode)
                }
                return true
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU -> {
                    val f = currentFocus
                    val e = (grid.findContainingViewHolder(f ?: grid) as? HomeAdapter.VH)?.entry
                    if (folderView == null && ccView == null && e != null && !e.isSpacer) { showMenu(e, false); return true }
                }
                KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_BUTTON_Y -> { toggleCC(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            screensaver != null -> stopScreensaver()
            folderView != null -> closeFolder()
            ccView != null -> closeCC()
            grid.findViewHolderForAdapterPosition(0) == null || (grid.findViewHolderForAdapterPosition(0)?.itemView?.top ?: 0) < u.i(HomeLayoutManager.REST_TOP) - 4 -> focusFirst()
            else -> {}
        }
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, pkgReceiver, f, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStop() { super.onStop(); try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {} }

    override fun onResume() {
        super.onResume()
        if (apps.isNotEmpty() && applyOptions()) { adapter.notifyDataSetChanged() }
        if (apps.isNotEmpty()) { lay = store.layout(apps); if (lay.cells().map { it.key } != adapter.cells.map { it.key }) relayout(lastFocusKey) }
        resetIdle()
        clock.timeZone = CityClock.gmtId(this)
        val app = applicationContext
        Thread {
            val changed = CityClock.ensure(app)
            if (Prefs(app).adhanEnabled) AdhanScheduler.rescheduleBlocking(app)
            if (changed) runOnUiThread { if (!isDestroyed) clock.timeZone = CityClock.gmtId(this) }
        }.start()
    }

    override fun onPause() { super.onPause(); handler.removeCallbacks(idleRun) }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home pressed: back to the dock.
        if (screensaver != null) stopScreensaver()
        closeFolder(); closeCC()
        focusFirst()
    }

    private fun startSafe(i: Intent) {
        try { startActivity(i) } catch (_: Exception) { toast("Not available on this device") }
    }
}
