package com.mytv.tvos

import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
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

    internal lateinit var u: U
    private lateinit var store: Store
    internal lateinit var sp: android.content.SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var wallpaper: ImageView
    private lateinit var bgGray: View
    private lateinit var grid: RecyclerView
    private lateinit var capsule: LinearLayout
    private lateinit var clock: TextClock
    private lateinit var infoBlock: LinearLayout
    private lateinit var bigClock: ClockView
    private lateinit var dateText: TextView
    private lateinit var wxText: TextView
    private lateinit var prText: TextView
    private lateinit var wxPill: View
    private lateinit var wxGlyph: WeatherGlyph
    private lateinit var stopPill: View
    private var adhanWasPlaying = false
    private lateinit var prPill: View
    private var builtAr = false
    private var leftForApp = false
    private var folderRv: RecyclerView? = null
    private var folderAd: HomeAdapter? = null
    private var nextPrayer: NextPrayer? = null
    private lateinit var prayerStrip: LinearLayout
    private val prayerCells = LinkedHashMap<String, Triple<View, TextView, TextView>>()
    private var prayerTimes: Map<String, String>? = null
    private lateinit var pagesBar: LinearLayout
    private val pageItems = ArrayList<View>()
    private var netListener: (() -> Unit)? = null
    private var lastFocusPos = 0
    internal var weather: Weather? = null
    private lateinit var adapter: HomeAdapter

    internal var apps: List<AppItem> = emptyList()
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
        L.ar = Prefs(this).language == "ar"
        Digits.load(Prefs(this))
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener(focusGuard)
        builtAr = L.ar
        GlassStyle.load(getSharedPreferences("tvos_settings", Context.MODE_PRIVATE))
        u = U(this)
        store = Store(this)
        sp = getSharedPreferences("tvos_settings", Context.MODE_PRIVATE)

        root = FrameLayout(this).apply { setBackgroundColor(0xFF1B1B1D.toInt()) }
        content = FrameLayout(this)
        wallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.92f
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
        buildInfo()
        content.addView(grid, FrameLayout.LayoutParams(-1, -1))
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        buildCapsule()
        buildPagesBar()
        buildStopPill()
        setContentView(root)
        loadAppsAsync()
    }

    // ------------------------------------------------------------------ home info: clock, date, weather, next prayer

    private fun pill(icon: View, tv: TextView, height: Float = 60f): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(u.i(18f), 0, u.i(26f), 0)
        background = Glass.glass(0x2EFFFFFF, u.px(height / 2), 1f, true)
        addView(icon, LinearLayout.LayoutParams(u.i(height * 0.62f), u.i(height * 0.62f)).apply { marginEnd = u.i(14f) })
        addView(tv)
    }

    private fun iconView(res: Int) = ImageView(this).apply { setImageResource(res); imageTintList = android.content.res.ColorStateList.valueOf(0xE6FFFFFF.toInt()) }

    private fun smallText(sizePx: Float, color: Int) = TextView(this).apply {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(sizePx))
        setTextColor(color)
        maxLines = 1
    }

    /** Pushes the saved clock settings (design, size, colour, position, 12/24h, time zone, date line) into the home clock. */
    private fun applyClock() {
        val cfg = ClockCfg.load(sp)
        bigClock.tz = CityClock.timeZone(this)
        bigClock.is24 = TimeFmt.is24(this, Prefs(this))
        bigClock.cfg = cfg
        infoBlock.post { infoBlock.requestLayout() }
        dateText.visibility = if (cfg.date) View.VISIBLE else View.GONE
        dateText.setTextColor(cfg.color and 0x00FFFFFF or 0xF2000000.toInt())
        val g = when (cfg.pos) { "center" -> Gravity.CENTER_HORIZONTAL; "right" -> Gravity.END; else -> Gravity.START }
        infoBlock.gravity = g
        (infoBlock.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            lp.gravity = Gravity.TOP or g
            lp.leftMargin = u.i(80f); lp.rightMargin = u.i(80f)
            infoBlock.layoutParams = lp
        }
    }

    /** The five prayers of today in one small glass strip; the next one is lit. */
    private fun buildPrayerStrip(): View {
        prayerStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(u.i(8f), u.i(6f), u.i(8f), u.i(6f))
            background = Glass.glass(0x24FFFFFF, u.px(30f), 0.8f, true)
            visibility = View.GONE
        }
        for (name in listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")) {
            val n = smallText(21f, 0xB3FFFFFF.toInt()).apply { gravity = Gravity.CENTER }
            val tm = smallText(27f, 0xFFFFFFFF.toInt()).apply { gravity = Gravity.CENTER; typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL) }
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setPadding(u.i(18f), u.i(8f), u.i(18f), u.i(8f))
                addView(n); addView(tm)
            }
            prayerCells[name] = Triple(cell, n, tm)
            prayerStrip.addView(cell)
        }
        return prayerStrip
    }

    private fun updateStrip() {
        val times = prayerTimes
        if (times == null) { prayerStrip.visibility = View.GONE; return }
        prayerStrip.visibility = View.VISIBLE
        val is24 = TimeFmt.is24(this, Prefs(this))
        val next = nextPrayer?.name
        for ((name, cell) in prayerCells) {
            cell.second.text = L.t(name)
            cell.third.text = TimeFmt.display(times[name], is24, L.locale())
            cell.first.background = if (name == next) GlossDrawable(u.px(24f), 0x55FFFFFF, 1f) else null
        }
    }

    private fun buildInfo() {
        bigClock = ClockView(this, u)
        dateText = smallText(36f, 0xF2FFFFFF.toInt()).apply {
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setShadowLayer(u.px(8f), 0f, u.px(2f), 0x80000000.toInt())
        }
        wxText = smallText(26f, 0xE6FFFFFF.toInt())
        prText = smallText(28f, 0xF2FFFFFF.toInt())
        wxGlyph = WeatherGlyph(this)
        wxPill = pill(wxGlyph, wxText, 68f)
        prPill = pill(iconView(R.drawable.ic_prayer), prText, 68f)
        wxPill.visibility = View.GONE
        prPill.visibility = View.GONE
        val pills = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(wxPill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u.i(68f)).apply { marginEnd = u.i(16f) })
            addView(prPill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u.i(68f)))
        }
        infoBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(bigClock, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(dateText, LinearLayout.LayoutParams(-2, -2).apply { topMargin = u.i(4f); bottomMargin = u.i(20f) })
            addView(pills)
            addView(buildPrayerStrip(), LinearLayout.LayoutParams(-2, -2).apply { topMargin = u.i(14f) })
        }
        content.addView(infoBlock, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { leftMargin = u.i(80f); topMargin = u.i(146f) })
    }

    private fun buildStopPill() {
        val tv = smallText(30f, Glass.text().defaultColor)
        tv.setTextColor(Glass.text())
        tv.text = L.t("Stop Adhan")
        tv.isDuplicateParentStateEnabled = true
        val icon = iconView(R.drawable.ic_prayer).apply { imageTintList = Glass.text(); isDuplicateParentStateEnabled = true }
        stopPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(u.i(26f), 0, u.i(34f), 0)
            isFocusable = true
            isClickable = true
            visibility = View.GONE
            background = Glass.rowBg(u, 36f, 0x40FFFFFF, true)
            addView(icon, LinearLayout.LayoutParams(u.i(40f), u.i(40f)).apply { marginEnd = u.i(14f) })
            addView(tv)
            setOnClickListener { AdhanService.stopNow(); updateStopPill() }
        }
        root.addView(stopPill, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u.i(72f), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = u.i(690f) })
    }

    /** The Stop button exists on the home screen only while the Adhan is actually playing. */
    private fun updateStopPill() {
        val playing = AdhanService.isPlaying
        if (playing) ((stopPill as LinearLayout).getChildAt(1) as TextView).text = L.t("Stop Adhan") + (AdhanService.currentPrayer?.let { " · " + L.t(it) } ?: "")
        stopPill.visibility = if (playing) View.VISIBLE else View.GONE
        if (playing && !adhanWasPlaying && ccView == null && folderView == null && moving == null) stopPill.requestFocus()
        if (!playing && adhanWasPlaying && stopPill.hasFocus()) lastFocusKey?.let { focusKey(it) }
        adhanWasPlaying = playing
    }

    private fun dateString(): String {
        val p = Prefs(this)
        val today = PrayerRepository.cityToday(this)
        val g = java.text.SimpleDateFormat("EEEE, d MMMM", L.locale()).apply { timeZone = CityClock.timeZone(this@MainActivity) }.format(java.util.Date())
        val h = HijriDate.format(this, today, L.locale())
        return Digits.fix(when (p.dateMode) { 1 -> g; 2 -> h; else -> if (h.isEmpty()) g else "$g  ·  $h" })
    }

    /** Refreshes the text of the clock block; cheap, called every minute and after the data arrives. */
    internal fun updateInfo() {
        dateText.text = dateString()
        updateStrip()
        val p = Prefs(this)
        val w = weather
        if (w == null || sp.getBoolean("wx_hide", false)) wxPill.visibility = View.GONE else {
            wxPill.visibility = View.VISIBLE
            val night = !w.isDay
            wxGlyph.set(w.code, night)
            val temp = "${w.tempC}°"
            val rest = "  ${L.t(getString(Weather.labelRes(w.code)))}  ·  ${w.today.maxC}°/${w.today.minC}°"
            val city = Prefs(this).weatherCityName.substringBefore(",").trim()
            val head = if (city.isEmpty()) "" else "$city   "
            val sp2 = android.text.SpannableString(head + temp + rest)
            sp2.setSpan(android.text.style.AbsoluteSizeSpan(u.i(38f)), head.length, head.length + temp.length, 0)
            sp2.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), head.length, head.length + temp.length, 0)
            if (head.isNotEmpty()) sp2.setSpan(android.text.style.ForegroundColorSpan(0xB3FFFFFF.toInt()), 0, head.length, 0)
            wxText.text = sp2
        }
        prPill.visibility = View.GONE // the full prayer strip shows every time; the extra "next prayer" capsule is gone
    }

    private val infoTick = object : Runnable {
        override fun run() { updateInfo(); handler.postDelayed(this, 30_000) }
    }

    private val stopPoll = object : Runnable {
        override fun run() { updateStopPill(); handler.postDelayed(this, 1000) }
    }

    private fun refreshInfoData() {
        val app = applicationContext
        Thread {
            val p = Prefs(app)
            CityClock.ensure(app)
            val n = try { PrayerRepository.nextPrayer(app, false) } catch (_: Exception) { null }
            val w = WeatherRepository.fetch(p.weatherLat, p.weatherLon)
            val pt = try { PrayerRepository.getTimings(app, PrayerRepository.cityToday(app))?.times } catch (_: Exception) { null }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (pt != null) prayerTimes = pt
                if (n != null) nextPrayer = n
                if (w != null) weather = w
                if (n != null || w != null) Net.evidence()
                clock.timeZone = CityClock.gmtId(this)
                applyClock()
                updateInfo()
            }
        }.start()
    }

    // ------------------------------------------------------------------ pages bar

    private val PAGES = listOf("Home" to R.drawable.ic_home, "Apps" to R.drawable.ic_apps, "Weather" to R.drawable.ic_sun,
        "Wallpapers" to R.drawable.ic_image, "Clock" to R.drawable.ic_clock, "Settings" to R.drawable.ic_gear)

    private fun pageBg() = android.graphics.drawable.StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), GlossDrawable(u.px(30f), 0xFFFFFFFF.toInt(), 0.55f))
        addState(intArrayOf(android.R.attr.state_selected), GlossDrawable(u.px(30f), 0x47FFFFFF, 1f))
        addState(intArrayOf(), android.graphics.drawable.ColorDrawable(0))
    }

    /** The six pages along the top: Home, Apps, Weather, Wallpapers, Clock, Settings. Up from the dock reaches them, Down returns. */
    private fun buildPagesBar() {
        pagesBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            setPadding(u.i(8f), u.i(8f), u.i(8f), u.i(8f))
            background = Glass.glass(0x38333336, u.px(38f), 1f, true)
        }
        PAGES.forEachIndexed { i, (label, icon) ->
            val img = ImageView(this).apply { setImageResource(icon); imageTintList = Glass.text(); isDuplicateParentStateEnabled = true }
            val tv = TextView(this).apply {
                text = L.t(label); setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(26f)); setTextColor(Glass.text())
                maxLines = 1; isDuplicateParentStateEnabled = true
            }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                setPadding(u.i(20f), 0, u.i(24f), 0)
                background = pageBg()
                contentDescription = label
                addView(img, LinearLayout.LayoutParams(u.i(32f), u.i(32f)).apply { marginEnd = u.i(10f) })
                addView(tv)
                setOnClickListener { openPage(i) }
                setOnKeyListener { _, code, ev -> if (ev.action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN) { downFromTop(); true } else false }
            }
            pagesBar.addView(item, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u.i(60f)).apply { if (i > 0) marginStart = u.i(4f) })
            pageItems.add(item)
        }
        setPage(0)
        root.addView(pagesBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u.i(76f), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = u.i(38f) })
    }

    /** Down from the bar or the capsule goes into whatever is open: the page, or the home tiles when nothing is open. */
    private fun downFromTop() {
        val pv = pageView
        if (pv != null) firstFocusable(pv)?.requestFocus() else if (ccView == null && folderView == null) focusFirst()
    }

    private fun isInside(v: View?, anc: View?): Boolean { var c = v; while (c != null) { if (c === anc) return true; c = c.parent as? View }; return false }

    /** Where the cursor may be right now: inside Control Center / the open folder, or inside the open page, the pages bar and the capsule. */
    private fun focusAllowed(v: View): Boolean {
        val scope: View? = ccView ?: folderView
        if (scope != null) return isInside(v, scope)
        val pv = pageView ?: return true
        return isInside(v, pv) || isInside(v, pagesBar) || v === capsule
    }

    private var builtContrast = -1
    private var reverting = false

    /**
     * Keeps the remote from leaking between layers. It does not search ahead (a look-ahead search makes a RecyclerView scroll
     * twice per key press): it only reacts when the cursor has actually landed outside the allowed layer and puts it back.
     */
    private val focusGuard = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { old, new ->
        if (!reverting && new != null && !focusAllowed(new)) {
            reverting = true
            try {
                val scope: View? = ccView ?: folderView ?: pageView
                (old?.takeIf { it.isAttachedToWindow && focusAllowed(it) } ?: scope?.let { firstFocusable(it) })?.requestFocus()
            } finally { reverting = false }
        }
    }

    /** For tests and for a key press: put the cursor back inside the allowed layer if it is outside. */
    internal fun enforceFocusScope() {
        val f = window?.decorView?.findFocus()
        val scope: View? = ccView ?: folderView ?: pageView
        if (scope != null && (f == null || !focusAllowed(f))) firstFocusable(scope)?.requestFocus()
    }

    private fun setPage(i: Int) { pageItems.forEachIndexed { j, v -> v.isSelected = j == i } }

    private var pageView: View? = null
    private val pages by lazy { Pages(this) }

    /** Opens a full page below the pages bar: the home grid and clock step aside, the wallpaper stays visible behind it. */
    private fun showPage(i: Int, v: View) {
        closePage(false)
        closeCC(); closeFolder()
        pageView = v
        setPage(i)
        grid.visibility = View.INVISIBLE
        infoBlock.visibility = View.INVISIBLE
        root.addView(v, root.indexOfChild(pagesBar), FrameLayout.LayoutParams(-1, -1))
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(160).start()
        v.post { firstFocusable(v)?.requestFocus() }
    }

    private fun firstFocusable(v: View): View? {
        if (v.visibility != View.VISIBLE) return null
        if (v.isFocusable) return v   // a focusable tile or row counts even though it is a ViewGroup (the app tiles are)
        if (v is ViewGroup) for (i in 0 until v.childCount) firstFocusable(v.getChildAt(i))?.let { return it }
        return null
    }

    internal fun closePage(restoreFocus: Boolean = true) {
        val v = pageView ?: return
        root.removeView(v)
        pageView = null
        grid.visibility = View.VISIBLE
        infoBlock.visibility = View.VISIBLE
        setPage(0)
        if (restoreFocus) focusFirst()
    }

    private fun openPage(i: Int) {
        when (i) {
            0 -> { closePage(false); closeCC(); closeFolder(); setPage(0); focusFirst() }
            1 -> showPage(1, pages.apps())
            2 -> showPage(2, pages.weather())
            3 -> showPage(3, pages.wallpapers())
            4 -> { closePage(false); ClockCustomizer.show(this, sp, onSaved = { applyOptions(); updateInfo() }) }
            else -> { closePage(false); startActivity(Intent(this, SettingsActivity::class.java)) }
        }
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
            background = Glass.rowBg(u, 33f, 0x66333336, true)
            addView(clock)
            addView(AvatarView(this@MainActivity), LinearLayout.LayoutParams(u.i(48f), u.i(48f)).apply { marginStart = u.i(16f) })
            setOnClickListener { toggleCC() }
            setOnKeyListener { _, code, ev ->
                if (ev.action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN) { downFromTop(); true } else false
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
                val label = try { ri.loadLabel(pm).toString() } catch (_: Exception) { ri.activityInfo.packageName }
                // Try the activity icon, then the application icon, then its logo: the first one that actually shows something wins.
                val candidates = listOf<() -> android.graphics.drawable.Drawable?>(
                    { ri.loadIcon(pm) }, { ri.activityInfo.applicationInfo.loadIcon(pm) }, { ri.activityInfo.applicationInfo.loadLogo(pm) }, { pm.getApplicationIcon(ri.activityInfo.packageName) }
                )
                var icon: android.graphics.drawable.Drawable? = null
                for (cand in candidates) {
                    val d = try { cand() } catch (_: Throwable) { null } ?: continue
                    if (icon == null) icon = d
                    val bmp = Art.render(d, 96, 96)
                    if (!Art.isBlank(bmp, Art.edgeColor(bmp))) { icon = d; break }
                }
                AppItem(ri.activityInfo.packageName, label, icon ?: GearDrawable(), banner, null).also { it.prepare() }
            }
    }

    private fun settingsItem() = AppItem(Store.SETTINGS_PKG, "Settings", GearDrawable(), null, Intent(this, SettingsActivity::class.java)).also { it.prepare() }

    /** Also the entry point for tests, which supply their own apps. */
    fun setApps(list: List<AppItem>) {
        apps = list.filter { it.pkg != Store.SETTINGS_PKG } + settingsItem()
        relayout(lastFocusKey)
    }

    internal fun applyOptions(): Boolean {
        val labels = sp.getBoolean("labels", false)
        val calm = sp.getBoolean("calm", false)
        val changed = labels != adapter.alwaysLabels || calm != adapter.reduceMotion || TileView.smooth != sp.getBoolean("smooth_edges", true)
        adapter.alwaysLabels = labels
        adapter.reduceMotion = calm
        TileView.smooth = sp.getBoolean("smooth_edges", true)
        loadWallpaper()
        val f24 = TimeFmt.is24(this, Prefs(this))
        clock.format12Hour = if (f24) "HH:mm" else "h:mm"
        clock.format24Hour = "HH:mm"
        clock.timeZone = CityClock.gmtId(this)
        applyClock()
        return changed
    }

    private var wallKey = ""

    private fun loadWallpaper() {
        val key = Wallpapers.key(sp)
        if (key == wallKey) return
        wallKey = key
        if (Wallpapers.mode(sp) == "none") {
            wallpaper.visibility = View.GONE; wallpaper.setImageDrawable(null)
            Backdrop.set(null); root.invalidate()
            return
        }
        // Show a small copy at once (kept in memory between visits), then swap in the full-size one when it is ready.
        val quick = Wallpapers.quick(key)
        if (quick != null) { wallpaper.setImageBitmap(quick); wallpaper.visibility = View.VISIBLE; Backdrop.set(quick); root.invalidate() }
        Thread {
            val low = quick ?: Wallpapers.decode(this, sp, 1024)?.also { Wallpapers.keepQuick(key, it) }
            if (quick == null && low != null) runOnUiThread {
                if (!isDestroyed && key == wallKey) { wallpaper.setImageBitmap(low); wallpaper.visibility = View.VISIBLE; Backdrop.set(low); root.invalidate() }
            }
            val bmp = Wallpapers.decode(this, sp)
            if (bmp != null && key == wallKey) Backdrop.set(bmp)
            runOnUiThread {
                if (isDestroyed || key != wallKey) return@runOnUiThread
                if (bmp == null) { wallpaper.visibility = View.GONE; Backdrop.set(null) }
                else { wallpaper.setImageBitmap(bmp); wallpaper.visibility = View.VISIBLE }
                root.invalidate()
            }
        }.start()
    }

    private fun relayout(focus: String?) {
        applyOptions()
        lay = store.layout(apps)
        adapter.cells = lay.cells()
        adapter.notifyDataSetChanged()
        if (focus != null) focusKey(focus) else if (grid.focusedChild == null && ccView == null && folderView == null && pageView == null) focusNear(lastFocusPos)
    }

    private fun focusFirst() { focusKey(lay.dock.firstOrNull()?.pkg ?: lay.library.firstOrNull()?.key ?: return) }

    /** The cursor stays where it was after a change (an app removed, hidden, moved): the nearest tile at that position. */
    private fun focusNear(p: Int) {
        val cells = adapter.cells
        if (cells.isEmpty() || pageView != null) return
        val i = p.coerceIn(0, cells.size - 1)
        var j = i
        while (j < cells.size && cells[j].isSpacer) j++
        if (j >= cells.size) { j = i; while (j >= 0 && cells[j].isSpacer) j-- }
        if (j < 0) return
        focusKey(cells[j].key)
    }

    private fun focusKey(key: String) {
        val pos = adapter.cells.indexOfFirst { it.key == key }
        if (pos < 0) { focusNear(lastFocusPos); return }
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
        lastFocusPos = adapter.cells.indexOf(e).coerceAtLeast(0)
        if (pageView == null) setPage(if (adapter.cells.indexOf(e) >= Store.COLUMNS) 1 else 0)
    }

    private fun scrollFx() {
        val v = grid.findViewHolderForAdapterPosition(0)?.itemView
        val off = if (v == null) 100000f else u.px(HomeLayoutManager.REST_TOP) - v.top
        bgGray.alpha = (off / u.px(650f)).coerceIn(0f, 1f) * 0.94f
        infoBlock.alpha = (1f - off / u.px(220f)).coerceIn(0f, 1f)
    }

    private fun open(e: Entry) {
        if (e.isFolder) { openFolder(e); return }
        launch(e.app ?: return)
    }

    internal fun launch(a: AppItem) {
        val i = a.intent ?: packageManager.getLeanbackLaunchIntentForPackage(a.pkg) ?: packageManager.getLaunchIntentForPackage(a.pkg)
        if (i == null) { toast("Not available"); return }
        closeCC()
        try {
            leftForApp = true
            startActivity(i)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        } catch (_: Exception) { toast("Not available") }
    }

    private fun toast(s: String) = Toast.makeText(this, L.t(s), Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------------ menu / move

    internal fun showMenu(e: Entry, inFolder: Boolean, compact: Boolean = false) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        if (e.isFolder) {
            items.add("Open" to { openFolder(e) })
            items.add("Rename" to { rename(e) })
            items.add("Delete Folder" to { store.deleteFolder(e.key); relayout(null) })
        } else {
            val a = e.app ?: return
            items.add("Open" to { launch(a) })
            if (compact) {
                if (lay.dock.any { it.pkg == a.pkg }) items.add("Remove from Favorites" to { store.removeFromDock(lay, a.pkg); relayout(null) })
                else items.add("Add to Favorites" to { if (store.addToDock(lay, a.pkg)) relayout(null) else toast("Favorites are full") })
            } else if (!inFolder) {
                items.add("Move" to { startMove(e) })
                if (lay.dock.any { it.pkg == a.pkg }) items.add("Remove from Favorites" to { store.removeFromDock(lay, a.pkg); relayout(a.pkg) })
                else items.add("Add to Favorites" to { if (store.addToDock(lay, a.pkg)) relayout(a.pkg) else toast("Favorites are full") })
                items.add("New Folder with This App" to { val id = store.newFolder(lay, a.pkg); relayout("f:$id") })
                for ((k, n) in store.folderKeys(lay)) items.add("Add to $n" to { store.addToFolder(lay, k, a.pkg); relayout(k) })
            } else {
                items.add("Remove from Folder" to { store.removeFromFolder(lay, a.pkg); closeFolder(); relayout(a.pkg) })
            }
            if (a.pkg != Store.SETTINGS_PKG) {
                items.add("Hide" to { store.hidden = store.hidden + a.pkg; closeFolder(); closePage(false); relayout(null) })
                items.add("App Info" to { startSafe(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))) })
                items.add("Uninstall" to { uninstall(a.pkg) })
            }
        }
        Menus.show(this, e.title, items)
    }

    private fun rename(e: Entry) {
        val input = android.widget.EditText(this).apply { setText(e.title); setSelectAllOnFocus(true); setSingleLine() }
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle(L.t("Rename Folder"))
            .setView(input)
            .setPositiveButton(L.t("OK")) { _, _ -> store.renameFolder(e.key, input.text.toString()); relayout(e.key) }
            .setNegativeButton(L.t("Cancel"), null)
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
        fRoot.addView(Blur.view(this, Blur.fromView(content, 32, 2), 0x59000000), FrameLayout.LayoutParams(-1, -1))
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
            background = Glass.glass(0x4DFFFFFF, u.px(58f), 0.8f)
        }
        val cellsF = e.folderApps.map { Entry(it.pkg, it) }
        val dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val ad = HomeAdapter(u, false, { en -> updateDots(dots, cellsF.indexOfFirst { it.key == en.key } / 9, (cellsF.size + 8) / 9) },
            { en -> en.app?.let { launch(it) } }, { en -> showMenu(en, true) })
        ad.cells = cellsF
        ad.alwaysLabels = sp.getBoolean("labels", false)
        ad.reduceMotion = sp.getBoolean("calm", false)
        val rv = RecyclerView(this).apply {
            layoutManager = RowScrollGrid(this@MainActivity, 3)
            adapter = ad
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            setPadding(0, u.i(30f), 0, u.i(40f))
        }
        folderRv = rv; folderAd = ad
        panel.addView(rv, FrameLayout.LayoutParams(-1, -1))
        fRoot.addView(panel, FrameLayout.LayoutParams(u.i(1197f), u.i(771f), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = u.i(150f) })
        fRoot.addView(dots, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u.i(30f), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = u.i(940f) })
        updateDots(dots, 0, (cellsF.size + 8) / 9)
        folderView = fRoot
        root.addView(fRoot, FrameLayout.LayoutParams(-1, -1))
        fRoot.alpha = 0f
        panel.pivotX = u.px(598f); panel.pivotY = u.px(385f); panel.scaleX = 0.9f; panel.scaleY = 0.9f
        fRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        fRoot.animate().alpha(1f).setDuration(130).start()
        panel.animate().scaleX(1f).scaleY(1f).setDuration(170).setInterpolator(android.view.animation.DecelerateInterpolator(1.8f))
            .withEndAction { fRoot.setLayerType(View.LAYER_TYPE_NONE, null) }.start()
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
        folderView = null
        (v as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        v.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        v.animate().alpha(0f).setDuration(100).withEndAction { root.removeView(v) }.start()
        folderRv = null; folderAd = null
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

    /** Translates every line of a multi-line text. */
    private fun tl(s: String) = s.split("\n").joinToString("\n") { L.t(it) }

    private fun glassTile(w: Float, h: Float, icon: Int, title: String, sub: String?, vertical: Boolean, action: (TextView?) -> Unit): LinearLayout {
        val subView = TextView(this).apply {
            text = tl(sub ?: "")
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
        val titleView = TextView(this).apply {
            text = L.t(title)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(32f))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Glass.text())
            isDuplicateParentStateEnabled = true
            maxLines = 1
        }
        col.addView(titleView)
        col.addView(subView)
        l.tag = Pair(titleView, subView)
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

    private fun dndOn(): Boolean = try {
        val nm = getSystemService(NotificationManager::class.java)
        nm.isNotificationPolicyAccessGranted && nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    } catch (_: Exception) { false }

    private fun askLocationOnce() {
        if (Build.VERSION.SDK_INT < 23 || sp.getBoolean("asked_loc", false)) return
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        sp.edit().putBoolean("asked_loc", true).apply()
        try { requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 11) } catch (_: Exception) {}
    }

    private fun openCC() {
        askLocationOnce()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Glass.glass(0xA82A2A2E.toInt(), u.px(70f), 0.9f, true)
            setPadding(u.i(24f), u.i(24f), u.i(24f), u.i(24f))
        }
        val power = glassTile(230f, 316f, R.drawable.ic_power, "Sleep", null, true) { closeCC(); startScreensaver() }
        val vol = VolumePill(this).also { it.layoutParams = LinearLayout.LayoutParams(u.i(86f), u.i(316f)).apply { setMargins(u.i(8f), u.i(8f), u.i(8f), u.i(8f)) } }
        Net.refresh(this)
        val (wt, ws) = Net.describe(Net.state)
        val wifi = glassTile(560f, 150f, R.drawable.ic_wifi, wt, ws, false) { closeCC(); startSafe(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        @Suppress("UNCHECKED_CAST")
        val wifiViews = wifi.tag as Pair<TextView, TextView>
        val nl: () -> Unit = {
            if (ccView != null) { val (t2, s2) = Net.describe(Net.state); wifiViews.first.text = L.t(t2); wifiViews.second.text = tl(s2) }
        }
        netListener?.let { Net.removeListener(it) }
        netListener = nl
        Net.addListener(nl)
        val dnd = glassTile(560f, 150f, R.drawable.ic_moon, "Do Not Disturb", if (dndOn()) "On" else "Off", false) { sub ->
            try {
                val nm = getSystemService(NotificationManager::class.java)
                if (nm.isNotificationPolicyAccessGranted) {
                    nm.setInterruptionFilter(if (dndOn()) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_NONE)
                    sub?.text = L.t(if (dndOn()) "On" else "Off")
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
            sub?.text = L.t(timerSub())
        }
        val audio = glassTile(440f, 150f, R.drawable.ic_audio, "Audio", "Output", false) { closeCC(); startSafe(Intent(Settings.ACTION_SOUND_SETTINGS)) }
        val rowB = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(timer); addView(audio) }
        val rowC = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(round(R.drawable.ic_search, "Search") { showSearch() })
            addView(round(R.drawable.ic_access, "Accessibility") { startSafe(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            addView(round(R.drawable.ic_gear, "Settings") { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) })
        }
        panel.addView(rowA); panel.addView(rowB); panel.addView(rowC)
        ccView = panel
        root.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = u.i(140f); marginEnd = u.i(45f)
        })
        panel.pivotX = u.px(1000f); panel.pivotY = 0f
        panel.alpha = 0f; panel.scaleX = 0.94f; panel.scaleY = 0.94f; panel.translationY = -u.px(24f)
        panel.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(190)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.8f)).withEndAction { panel.setLayerType(View.LAYER_TYPE_NONE, null) }.start()
        power.requestFocus()
    }

    private fun showSearch() {
        val d = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Glass.glass(0xE62A2A2C.toInt(), u.px(48f), 0.7f)
            setPadding(u.i(36f), u.i(32f), u.i(36f), u.i(32f))
        }
        val input = android.widget.EditText(this).apply {
            hint = L.t("Type an app name")
            setSingleLine()
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(36f))
            setTextColor(Color.WHITE)
            setHintTextColor(0x80FFFFFF.toInt())
            background = Glass.glass(0x2EFFFFFF, u.px(33f), 0.5f)
            setPadding(u.i(30f), 0, u.i(30f), 0)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, u.i(78f)))
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(results, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = u.i(12f) })
        fun refresh(q: String) {
            results.removeAllViews()
            if (q.isBlank()) return
            val list = apps.filter { it.label.contains(q.trim(), true) }.sortedBy { !it.label.startsWith(q.trim(), true) }.take(7)
            if (list.isEmpty()) results.addView(TextView(this).apply {
                text = L.t("No apps found"); setTextColor(0x99FFFFFF.toInt()); setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(30f)); setPadding(u.i(30f), u.i(20f), 0, u.i(10f))
            })
            for (a in list) results.addView(TextView(this).apply {
                text = a.label
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(34f))
                setTextColor(Glass.text())
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true; isClickable = true; maxLines = 1
                setPadding(u.i(24f), 0, u.i(30f), 0)
                compoundDrawablePadding = u.i(20f)
                setCompoundDrawables(android.graphics.drawable.BitmapDrawable(resources, a.iconBmp).apply { setBounds(0, 0, u.i(52f), u.i(52f)) }, null, null, null)
                background = Glass.rowBg(u, 33f, 0x14FFFFFF)
                setOnClickListener { d.dismiss(); launch(a) }
            }, LinearLayout.LayoutParams(-1, u.i(72f)).apply { topMargin = u.i(6f); bottomMargin = u.i(6f) })
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { refresh(s?.toString().orEmpty()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        d.setContentView(panel, ViewGroup.LayoutParams(u.i(900f), ViewGroup.LayoutParams.WRAP_CONTENT))
        d.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            attributes = attributes.apply { y = u.i(150f); dimAmount = 0.55f }
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        d.show()
        input.requestFocus()
    }

    private fun closeCC() {
        netListener?.let { Net.removeListener(it) }; netListener = null
        val v = ccView ?: return
        ccView = null
        (v as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        v.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).translationY(-u.px(14f)).setDuration(120)
            .withEndAction { root.removeView(v) }.start()
        lastFocusKey?.let { focusKey(it) }
    }

    // ------------------------------------------------------------------ screensaver / idle

    private fun greeting(): String {
        val h = java.util.Calendar.getInstance(CityClock.timeZone(this)).get(java.util.Calendar.HOUR_OF_DAY)
        return L.t(when { h in 5..11 -> "Good morning"; h in 12..16 -> "Good afternoon"; h in 17..20 -> "Good evening"; else -> "Good night" })
    }

    /** The sleep screen: the wallpaper, dimmed, with a greeting, the clock in the design chosen for it, and the date; fixed in the centre. */
    private fun startScreensaver() {
        if (screensaver != null) return
        closeCC(); closeFolder(); closePage(false)
        val cfg = ClockCfg.loadSleep(sp)
        val f = FrameLayout(this).apply { setBackgroundColor(0xFF0A0B0E.toInt()) }
        val pic = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
        f.addView(pic, FrameLayout.LayoutParams(-1, -1))
        f.addView(View(this).apply { setBackgroundColor(0x80000000.toInt()) }, FrameLayout.LayoutParams(-1, -1))
        val spaced = !L.ar
        fun small(s: String, size: Float) = TextView(this).apply {
            text = if (spaced) s.uppercase(L.locale()) else s
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(size)); setTextColor(cfg.color and 0x00FFFFFF or 0xE6000000.toInt()); gravity = Gravity.CENTER
            if (spaced) letterSpacing = 0.32f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val time = ClockView(this, u).apply {
            tz = CityClock.timeZone(this@MainActivity)
            is24 = TimeFmt.is24(this@MainActivity, Prefs(this@MainActivity))
            this.cfg = cfg
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            if (cfg.greeting) addView(small(greeting(), 28f))
            addView(time, LinearLayout.LayoutParams(-2, -2).apply { topMargin = u.i(10f); bottomMargin = u.i(10f) })
            if (cfg.date) addView(small(dateString().replace(",", ""), 30f))
        }
        f.addView(col, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER).apply { bottomMargin = u.i(40f) })
        f.alpha = 0f
        screensaver = f
        root.addView(f, FrameLayout.LayoutParams(-1, -1))
        f.animate().alpha(1f).setDuration(500).start()
        val app = applicationContext
        Thread {
            val bmp = Wallpapers.decode(app, sp, 1280) ?: DefaultWall.bitmap(960)
            runOnUiThread {
                if (isDestroyed || screensaver !== f) return@runOnUiThread
                pic.setImageBitmap(bmp); pic.animate().alpha(1f).setDuration(600).start()
            }
        }.start()
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
            if ((event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && wrapRow(event.keyCode)) return true
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && upFromPageTop()) return true
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

    /** Up from the first row of a page's grid (Apps, Wallpapers) goes to the pages bar, on the item of the open page. */
    private fun upFromPageTop(): Boolean {
        val pv = pageView ?: return false
        val f = window?.decorView?.findFocus() ?: return false
        var c: View? = f
        while (c != null && c !== pv && c !is RecyclerView) c = c.parent as? View
        val rv = c as? RecyclerView ?: return false
        val span = (rv.layoutManager as? GridLayoutManager)?.spanCount ?: return false
        val pos = rv.findContainingViewHolder(f)?.bindingAdapterPosition ?: return false
        if (pos < 0 || pos >= span) return false
        val target = pageItems.firstOrNull { it.isSelected } ?: pageItems.firstOrNull() ?: return false
        target.requestFocus()
        return true
    }

    /** Last app of a row + Right goes to the first app of the row, and the other way round. */
    private fun wrapRow(code: Int): Boolean {
        val f = window?.decorView?.findFocus() ?: currentFocus ?: return false
        // The top ring: the six pages and the clock capsule, in screen order. Right from the last one goes to the first (and back).
        val ring = (pageItems + capsule).filter { it.visibility == View.VISIBLE }.sortedBy { v -> IntArray(2).also { v.getLocationInWindow(it) }[0] }
        val ri = ring.indexOf(f)
        if (ri >= 0) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT && ri == ring.size - 1) { ring.first().requestFocus(); return true }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && ri == 0) { ring.last().requestFocus(); return true }
            return false
        }
        // A horizontal row of buttons (the pages bar, the wallpaper buttons): last -> first, first -> last.
        (f.parent as? LinearLayout)?.takeIf { it.orientation == LinearLayout.HORIZONTAL }?.let { row ->
            val items = (0 until row.childCount).map { row.getChildAt(it) }.filter { it.isFocusable && it.visibility == View.VISIBLE }
            val i = items.indexOf(f)
            if (items.size >= 2 && i >= 0) {
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT && i == items.size - 1) { items.first().requestFocus(); return true }
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && i == 0) { items.last().requestFocus(); return true }
            }
        }
        // A grid on a page (Apps, Wallpapers): the same wrap along each row.
        pageView?.let { pv ->
            var c: View? = f
            while (c != null && c !== pv && c !is RecyclerView) c = c.parent as? View
            val rv = c as? RecyclerView ?: return false
            val span = (rv.layoutManager as? GridLayoutManager)?.spanCount ?: return false
            val pos = rv.findContainingViewHolder(f)?.bindingAdapterPosition ?: return false
            val count = rv.adapter?.itemCount ?: return false
            val start = pos / span * span
            val end = minOf(start + span, count) - 1
            val target = when {
                code == KeyEvent.KEYCODE_DPAD_RIGHT && pos == end && end > start -> start
                code == KeyEvent.KEYCODE_DPAD_LEFT && pos == start && end > start -> end
                else -> return false
            }
            rv.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus() ?: run { rv.scrollToPosition(target); rv.post { rv.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus() } }
            return true
        }
        val rv = if (folderView != null) folderRv else grid
        val ad = if (folderView != null) folderAd else adapter
        val span = if (folderView != null) 3 else Store.COLUMNS
        if (rv == null || ad == null) return false
        val pos = rv.findContainingViewHolder(f)?.bindingAdapterPosition ?: return false
        if (pos < 0) return false
        val start = (pos / span) * span
        val ids = (start until minOf(start + span, ad.cells.size)).filter { !ad.cells[it].isSpacer }
        if (ids.size < 2) return false
        val target = when {
            code == KeyEvent.KEYCODE_DPAD_RIGHT && pos == ids.last() -> ids.first()
            code == KeyEvent.KEYCODE_DPAD_LEFT && pos == ids.first() -> ids.last()
            else -> return false
        }
        rv.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus()
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            screensaver != null -> stopScreensaver()
            pageView != null -> closePage()
            folderView != null -> closeFolder()
            ccView != null -> closeCC()
            pagesBar.hasFocus() || capsule.hasFocus() -> focusFirst()
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
        if (reloadAppsOnStart) { reloadAppsOnStart = false; loadAppsAsync() }
        Net.start(applicationContext)
    }

    override fun onStop() {
        super.onStop()
        Net.stop()
        try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
        // Another app (a video, YouTube...) is in front: give back what the launcher holds, so that app has more memory.
        Thumbs.clear()
        handler.removeCallbacks(infoTick)
        wallpaper.setImageDrawable(null)
        Backdrop.set(null)
        wallKey = ""
    }

    override fun onResume() {
        super.onResume()
        if (wallKey.isEmpty()) loadWallpaper()
        GlassStyle.load(sp)
        window.decorView.invalidate()
        val wantAr = Prefs(this).language == "ar"
        if (wantAr != builtAr) { L.ar = wantAr; recreate(); return }
        if (builtContrast < 0) builtContrast = Contrast.level
        if (Contrast.level != builtContrast) { recreate(); return }
        if (apps.isNotEmpty() && applyOptions()) { adapter.notifyDataSetChanged() }
        if (apps.isNotEmpty()) { lay = store.layout(apps); if (lay.cells().map { it.key } != adapter.cells.map { it.key }) relayout(lastFocusKey) }
        resetIdle()
        clock.timeZone = CityClock.gmtId(this)
        if (leftForApp) {
            // Coming back from an app: the same soft fade as going into one (a tiny settle from 1.03 to 1), no sliding.
            leftForApp = false
            content.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            content.alpha = 0f; content.scaleX = 1.03f; content.scaleY = 1.03f
            content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
                .withEndAction { content.setLayerType(View.LAYER_TYPE_NONE, null) }.start()
        }
        handler.removeCallbacks(stopPoll); handler.post(stopPoll)
        handler.removeCallbacks(infoTick); handler.postDelayed(infoTick, 400); handler.postDelayed({ if (!isDestroyed) refreshInfoData() }, 900)
        val app = applicationContext
        Thread {
            val changed = CityClock.ensure(app)
            if (Prefs(app).adhanEnabled) AdhanScheduler.rescheduleBlocking(app)
            if (changed) runOnUiThread { if (!isDestroyed) clock.timeZone = CityClock.gmtId(this) }
        }.start()
    }

    override fun onPause() { super.onPause(); handler.removeCallbacks(idleRun); handler.removeCallbacks(infoTick); handler.removeCallbacks(stopPoll) }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        WallpaperPicker.onPermission(requestCode)
        if (requestCode == 11) Net.refresh(this) // the Wi-Fi name can only be read once Location is allowed
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        WallpaperPicker.onActivityResult(this, requestCode, resultCode, data)
        applyOptions()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home pressed: back to the dock.
        if (screensaver != null) stopScreensaver()
        closeFolder(); closeCC()
        focusFirst()
    }

    /** True after the system uninstaller was opened: the package receiver is off while we are stopped, so the app list is reloaded on return. */
    private var reloadAppsOnStart = false

    private fun uninstall(pkg: String) {
        val info = try { packageManager.getApplicationInfo(pkg, 0) } catch (_: Exception) { null }
        if (info == null) { loadAppsAsync(); return }
        val system = info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        val updated = info.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        // A built-in app (often YouTube on a TV) cannot be removed by any app: only its updates can be.
        if (system && !updated) { toast("This is a system app: it cannot be uninstalled"); return }
        val uri = Uri.parse("package:$pkg")
        reloadAppsOnStart = true
        try { startActivity(Intent(Intent.ACTION_DELETE, uri)); fadeOut() }
        catch (_: Exception) {
            try { startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri)); fadeOut() }
            catch (_: Exception) { reloadAppsOnStart = false; toast("Not available on this device") }
        }
    }

    /** The same soft fade as when an app is opened, instead of the system slide/zoom that makes the home screen jump. */
    private fun fadeOut() = overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

    private fun startSafe(i: Intent) {
        try {
            startActivity(i)
            fadeOut()
        } catch (_: Exception) { toast("Not available on this device") }
    }
}
