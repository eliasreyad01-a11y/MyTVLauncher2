package com.mytv.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val LIBRARY_PKG = "__library__"
private const val ADD_PKG = "__add__"

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var wallpaper: WallpaperView
    private lateinit var grid: RecyclerView
    private lateinit var gridManager: GridLayoutManager
    private lateinit var hero: View
    private lateinit var greeting: TextView
    private lateinit var wallFg: android.widget.ImageView
    private var clockTint = 0xFF9FD8EA.toInt()
    private lateinit var wallCaption: TextView
    private lateinit var infoBlock: View
    private lateinit var clockBox: View
    private lateinit var dim: View
    private lateinit var ampm: TextClock
    private lateinit var dateText: TextView
    private lateinit var focusTitle: TextView
    private lateinit var prayerCard: View
    private lateinit var prayerLine: TextView
    private lateinit var btnSettings: View
    private lateinit var weatherText: TextView
    private var weather: Weather? = null
    private var weatherAt = 0L
    private var fetchingWeather = false
    private val chips = LinkedHashMap<String, TextView>()

    /** One adapter for the whole home screen: the dock is simply its first row. */
    private val adapter = AppAdapter(
        onClick = {
            when (it.packageName) {
                LIBRARY_PKG -> showLibrary()
                ADD_PKG -> showAddApps()
                else -> launchApp(it)
            }
        },
        onLongClick = { item, a -> if (item.packageName != LIBRARY_PKG && item.packageName != ADD_PKG) showAppMenu(item, a) },
        onFocus = { onTileFocus(it) }
    )

    private var loadedApps: List<AppItem> = emptyList()
    private var visibleApps: List<AppItem> = emptyList()
    private var lastFocused: AppItem? = null
    private var lastFocusedDock = false
    private var firstLoad = true
    private var move: MoveSession? = null
    private var appliedSpan = 0
    private lateinit var glow: View
    private lateinit var adhanStop: TextView
    private var focusedTile: View? = null
    private var focusedInGrid = false
    private lateinit var glowLabel: TextView
    private lateinit var screensaver: View
    private var screensaverOn = false
    private var libraryItem: AppItem? = null

    private val handler = Handler(Looper.getMainLooper())
    private var nextPrayer: NextPrayer? = null
    private var fetchingPrayer = false
    private var prayerRetryAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            updateHeader()
            handler.postDelayed(this, 20_000)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** Scroll offset at which the dock row has risen to ~40% of the screen height (the open list). */
    private fun listOffset(): Int =
        (grid.paddingTop - resources.displayMetrics.heightPixels * 0.40f).toInt().coerceAtLeast(dp(100))

    private fun rowAlpha(): Float =
        (grid.computeVerticalScrollOffset() / (listOffset() * 0.5f)).coerceIn(0f, 1f)

    /** Fewer favourites than columns: the dock tiles stay centred instead of hugging the left edge. */
    private fun centerDock(only: View? = null) {
        var pads = 0
        for (p in 0 until adapter.dockCount) {
            if (adapter.items.getOrNull(p)?.packageName?.startsWith(AppAdapter.PAD_PREFIX) == true) pads++
        }
        val shift = { v: View ->
            val p = grid.getChildAdapterPosition(v)
            v.translationX = if (p in 0 until adapter.dockCount) pads * v.width / 2f else 0f
        }
        if (only != null) shift(only) else for (i in 0 until grid.childCount) shift(grid.getChildAt(i))
    }

    private fun applyRowAlpha() {
        val a = rowAlpha()
        for (i in 0 until grid.childCount) {
            val v = grid.getChildAt(i)
            val p = grid.getChildAdapterPosition(v)
            v.alpha = if (p >= adapter.dockCount && adapter.dockCount > 0) a else 1f
        }
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    /** The clock is glass: white at the top, taking on the wallpaper's colour towards the bottom. */
    private fun tintClock() {
        clockTint = wallpaper.sampleColor() ?: 0xFF9FD8EA.toInt()
        applyClockShader()
    }

    private fun applyClockShader() {
        val t = clockTint
        val top = (0xB8 shl 24) or (mix(Color.WHITE, t, 0.10f) and 0xFFFFFF)
        val mid = (0x8C shl 24) or (mix(Color.WHITE, t, 0.45f) and 0xFFFFFF)
        val bot = (0x66 shl 24) or (mix(Color.WHITE, t, 0.80f) and 0xFFFFFF)
        findViewById<GlassClock>(R.id.clock).glass = intArrayOf(top, mid, bot)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        // Crash log: if the launcher ever closes by itself, the reason is shown on the next start.
        val crashFile = java.io.File(filesDir, "crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                crashFile.writeText(android.util.Log.getStackTraceString(e).take(2500))
            } catch (_: Exception) {
            }
            previous?.uncaughtException(t, e)
        }
        if (crashFile.exists()) {
            val text = try { crashFile.readText() } catch (_: Exception) { "" }
            crashFile.delete()
            if (text.isNotBlank()) handler.postDelayed({
                val tv = TextView(this).apply {
                    this.text = text
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    setPadding(dp(20), dp(12), dp(20), dp(12))
                }
                android.app.AlertDialog.Builder(this)
                    .setTitle("Crash log")
                    .setView(tv)
                    .setPositiveButton(R.string.ok, null)
                    .create().also { Dialogs.track(it) }.show()
            }, 1500)
        }

        wallpaper = findViewById(R.id.wallpaper)
        grid = findViewById(R.id.grid)
        hero = findViewById(R.id.hero)
        greeting = findViewById(R.id.greeting)
        wallCaption = findViewById(R.id.wallCaption)
        infoBlock = findViewById(R.id.infoBlock)
        clockBox = findViewById(R.id.clockBox)
        dim = findViewById(R.id.dim)
        // Glass clock: white at the top fading to a translucent tint, so it blends into any wallpaper.
        findViewById<TextClock>(R.id.clock).addOnLayoutChangeListener { _, _, t, _, bt, _, ot, _, ob ->
            if ((bt - t) != (ob - ot)) applyClockShader()
        }
        wallFg = findViewById(R.id.wallFg)
        wallpaper.onImage = { tintClock() }
        ampm = findViewById(R.id.ampm)
        dateText = findViewById(R.id.dateText)
        focusTitle = findViewById(R.id.focusTitle)
        prayerCard = findViewById(R.id.prayerCard)
        prayerLine = findViewById(R.id.prayerLine)
        btnSettings = findViewById(R.id.btnSettings)
        weatherText = findViewById(R.id.weatherText)
        glow = findViewById(R.id.focusGlow)
        glow.background?.mutate()
        glowLabel = findViewById(R.id.glowLabel)
        screensaver = findViewById(R.id.screensaver)
        adhanStop = findViewById(R.id.adhanStop)
        adhanStop.setOnClickListener {
            AdhanService.stopNow()
            updateAdhanStop(false)
        }
        setupFocusGlow()
        val latinRoots = listOf<View>(findViewById(R.id.header), findViewById(R.id.cardsRow))
        var lastLatin = 0L
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
            // Cheap guard: this runs on every layout pass, so do the digit scan at most ~4x per second.
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastLatin > 250) {
                lastLatin = now
                latinRoots.forEach { latinize(it) }
            }
        }
        for (id in listOf(R.id.clock, R.id.ampm, R.id.ssClock)) {
            findViewById<TextView>(id).addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun afterTextChanged(e: android.text.Editable?) {
                    findViewById<TextView>(id).post { latinize(findViewById(id)) }
                }
            })
        }

        if (Locale.getDefault().language != "ar") {
            greeting.letterSpacing = 0.15f
            dateText.letterSpacing = 0.1f
        }

        // One RecyclerView holds everything (like Secreto): the dock is the first row, the apps follow.
        // Scrolling is RecyclerView's own smooth scroll, asked for the minimum distance by the manager below.
        val span = prefs.gridSpan
        appliedSpan = span
        gridManager = object : GridLayoutManager(this, span) {
            override fun requestChildRectangleOnScreen(
                parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean
            ): Boolean {
                val dy = if (getPosition(child) < adapter.dockCount) {
                    // The dock row parks at the top, where the clock row and cards sit above it.
                    -parent.computeVerticalScrollOffset()
                } else {
                    val top = child.top
                    val bottom = child.bottom
                    val margin = dp(12)
                    val limit = height - dp(24)
                    var d = when {
                        top < margin -> top - margin
                        bottom <= limit -> 0
                        else -> minOf(top - margin, bottom - limit)
                    }
                    // The first step down from the dock opens the app list: the dock row rises to ~40% of the screen.
                    val off = parent.computeVerticalScrollOffset()
                    val open = listOffset()
                    if (off < open) d = maxOf(d, open - off)
                    d
                }
                if (dy == 0) return false
                if (immediate || !prefs.animations) parent.scrollBy(0, dy) else parent.smoothScrollBy(0, dy)
                return true
            }
        }
        grid.layoutManager = gridManager
        grid.adapter = adapter
        grid.setHasFixedSize(true)
        grid.itemAnimator = null
        grid.addItemDecoration(DockBand(this, adapter))
        // The cards and title above the dock fade away as the rows scroll up over them.
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val off = rv.computeVerticalScrollOffset()
                val open = listOffset().toFloat()
                val a = (1f - off / (open * 0.6f)).coerceIn(0f, 1f)
                hero.alpha = a
                infoBlock.alpha = a
                clockBox.alpha = a
                wallFg.alpha = a.coerceAtLeast(0f)
                // The wallpaper sinks into darkness as the rows scroll up.
                dim.alpha = ((off / (open * 0.75f)).coerceIn(0f, 1f)) * 0.92f
                applyRowAlpha()
            }
        })
        // At home only the dock shows; the app rows fade in as the list opens.
        grid.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                val p = grid.getChildAdapterPosition(view)
                if (p >= adapter.dockCount && adapter.dockCount > 0) view.alpha = rowAlpha()
                else view.alpha = 1f
                view.post { centerDock(view) }
            }
            override fun onChildViewDetachedFromWindow(view: View) {}
        })
        // Rows start just under the cards and title.
        // Everything sits low like on a TV home screen: prayer bar at ~60% of the height, dock right under it.
        val heroTop = (resources.displayMetrics.heightPixels * 0.60f).toInt()
        hero.setPadding(hero.paddingLeft, heroTop, hero.paddingRight, hero.paddingBottom)
        // The rows start under the bar from the very first layout, so nothing shifts or scrolls when the bar appears.
        grid.setPadding(grid.paddingLeft, heroTop + dp(62) + dp(2) + dp(8), grid.paddingRight, (resources.displayMetrics.heightPixels * 0.22f).toInt())
        focusTitle.visibility = View.GONE
        // The padding only grows: when the prayer card or the Adhan button appears the rows move down once,
        // but they never jump back up while the user rests on the home screen.
        hero.addOnLayoutChangeListener { v, _, t, _, b, _, ot, _, ob ->
            if (b - t != ob - ot) {
                val want = v.height + dp(8)
                if (want > grid.paddingTop) grid.setPadding(grid.paddingLeft, want, grid.paddingRight, grid.paddingBottom)
            }
        }

        findViewById<View>(R.id.btnWifi).setOnClickListener {
            startActivitySafe(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        findViewById<View>(R.id.btnNet).setOnClickListener {
            startActivitySafe(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
        findViewById<View>(R.id.btnSearch).setOnClickListener { showSearch() }
        btnSettings.setOnClickListener { showQuickSettings() }

        val prayerRow = findViewById<LinearLayout>(R.id.prayerRow)
        for (name in PrayerRepository.PRAYERS) {
            val chip = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(dp(16), dp(5), dp(16), dp(5))
            }
            chips[name] = chip
            prayerRow.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(4) })
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // A launcher never exits; Back only cancels an active move.
                if (move != null) finishMove(false)
            }
        })

        registerPackageReceiver()
        AdhanScheduler.reschedule(this)
    }

    override fun onResume() {
        super.onResume()
        wallpaper.apply(prefs)
        wallpaper.onResume()
        val fgRes = wallpaper.foregroundRes(prefs)
        if (fgRes != 0) {
            wallFg.setImageResource(fgRes)
            wallFg.colorFilter = android.graphics.PorterDuffColorFilter(
                Color.argb(prefs.dim.coerceIn(0, 90) * 255 / 100, 0, 0, 0), android.graphics.PorterDuff.Mode.SRC_ATOP
            )
            wallFg.visibility = View.VISIBLE
        } else wallFg.visibility = View.GONE
        wallpaper.post { tintClock() }
        val is24 = TimeFmt.is24(this, prefs)
        val clock = findViewById<TextClock>(R.id.clock)
        clock.format12Hour = if (is24) "HH:mm" else "h:mm"
        clock.format24Hour = if (is24) "HH:mm" else "h:mm"
        ampm.format12Hour = "a"
        ampm.format24Hour = "a"
        ampm.visibility = if (is24) View.GONE else View.VISIBLE
        applyClockZone()
        Thread {
            val changed = CityClock.ensure(applicationContext)
            if (changed) runOnUiThread {
                applyClockZone()
                nextPrayer = null
                prayerRetryAt = 0L
                updateHeader()
                AdhanScheduler.reschedule(applicationContext)
            }
        }.start()
        // Refetch prayer times / weather only when a setting that affects them changed (not on every return).
        val sig = listOf(
            prefs.latitude, prefs.longitude, prefs.method, prefs.tzMode, prefs.timeFormat,
            prefs.showPrayerCard, prefs.showWeather,
            PrayerRepository.PRAYERS.joinToString(",") { prefs.prayerOffset(it).toString() }
        ).joinToString("|")
        if (sig != settingsSig) {
            settingsSig = sig
            nextPrayer = null
            prayerRetryAt = 0L
            weatherAt = 0L
        } else {
            showNextPrayer()
        }
        applyLookOptions()
        AdhanService.onChanged = { runOnUiThread { updateAdhanStop(true) } }
        updateAdhanStop(false)
        updateHomeButtons()
        // Loading every app icon is the slow part: only redo it when something was installed/removed.
        if (appsDirty || loadedApps.isEmpty() || System.currentTimeMillis() - appsLoadedAt > 15 * 60_000L) reload()
        else applyLists()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        resetIdle()
    }

    override fun onPause() {
        super.onPause()
        wallpaper.onPause()
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(idleRunnable)
        if (screensaverOn) stopScreensaver()
        AdhanService.onChanged = null
    }

    override fun onDestroy() {
        packageReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) { }
        }
        packageReceiver = null
        super.onDestroy()
    }

    private var settingsSig = ""
    private var appsDirty = true
    private var appsLoadedAt = 0L

    /** Refreshes the app lists as soon as an app is installed or removed while Home is visible. */
    private var packageReceiver: android.content.BroadcastReceiver? = null

    private fun registerPackageReceiver() {
        if (packageReceiver != null) return
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                appsDirty = true
                handler.removeCallbacks(reloadRunnable)
                handler.postDelayed(reloadRunnable, 800)
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(r, filter)
        packageReceiver = r
    }

    private val reloadRunnable = Runnable { reload() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already on the home screen.
        // Home stays inside the launcher: close anything open and return to the first tile.
        if (move != null) finishMove(false)
        Dialogs.dismissAll()
        menuPopup?.let { try { it.dismiss() } catch (e: Exception) { } }
        menuPopup = null
        grid.scrollToPosition(0)
        val adhan = intent.getBooleanExtra("adhan_stop", false)
        updateAdhanStop(adhan)
        if (!adhan) {
            grid.post { tileOf(0)?.requestFocus() }
        }
    }

    private var menuPopup: PopupWindow? = null

    /** Shows 0-9 instead of Arabic-Indic digits, so the clock, date, weather and prayer times match. */
    private fun latinize(v: View) {
        if (v is RecyclerView) return
        if (v is TextView) {
            val t = v.text
            if (t.any { it in '\u0660'..'\u0669' }) {
                // Edit in place so spans (sizes, colours) survive.
                val sb = android.text.SpannableStringBuilder(t)
                for (i in 0 until sb.length) {
                    val c = sb[i]
                    if (c in '\u0660'..'\u0669') sb.replace(i, i + 1, ('0' + (c - '\u0660')).toString())
                }
                v.text = sb
            }
        } else if (v is ViewGroup) {
            for (i in 0 until v.childCount) latinize(v.getChildAt(i))
        }
    }

    // ---------------------------------------------------------------- clock screensaver

    private val idleRunnable = Runnable { startScreensaver() }
    private fun resetIdle() {
        handler.removeCallbacks(idleRunnable)
        val m = prefs.screensaverMinutes
        if (m > 0 && !screensaverOn) handler.postDelayed(idleRunnable, m * 60_000L)
    }

    private fun startScreensaver() {
        if (Dialogs.anyOpen() || menuPopup?.isShowing == true || AdhanService.isPlaying) {
            resetIdle()
            return
        }
        val c = findViewById<TextClock>(R.id.ssClock)
        val clock = findViewById<TextClock>(R.id.clock)
        c.timeZone = CityClock.gmtId(this)
        c.format12Hour = clock.format12Hour
        c.format24Hour = clock.format24Hour
        screensaverOn = true
        // Same wallpaper as the home page: only the apps and cards fade away, the clock drifts over it.
        findViewById<View>(R.id.content).animate().alpha(0f).setDuration(400).start()
        glow.visibility = View.INVISIBLE
        glowLabel.visibility = View.INVISIBLE
        findViewById<TextView>(R.id.ssDate).text = dateText.text
        findViewById<TextView>(R.id.ssSub).text = findViewById<TextView>(R.id.dateGreg).text
        val box = findViewById<View>(R.id.ssBox)
        box.alpha = 0f
        box.scaleX = 0.96f
        box.scaleY = 0.96f
        screensaver.visibility = View.VISIBLE
        box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    private fun stopScreensaver() {
        screensaverOn = false
        findViewById<View>(R.id.ssBox).animate().cancel()
        screensaver.visibility = View.GONE
        findViewById<View>(R.id.content).animate().alpha(1f).setDuration(150).start()
        focusedTile?.let { placeGlow(it, false) }
        resetIdle()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (!screensaverOn) resetIdle()
    }



    private fun applyClockZone() {
        val id = CityClock.gmtId(this)
        val clock = findViewById<TextClock>(R.id.clock)
        if (clock.timeZone != id) clock.timeZone = id
        if (ampm.timeZone != id) ampm.timeZone = id
    }

    /** Shows the Stop button next to the weather and prayer times while the Adhan plays. */
    private fun updateAdhanStop(focus: Boolean) {
        val playing = AdhanService.isPlaying
        adhanStop.visibility = if (playing) View.VISIBLE else View.GONE
        if (playing && focus) {
            grid.scrollToPosition(0)
            adhanStop.requestFocus()
        }
    }

    /** One shared, gliding focus highlight instead of a heavy shadow on every tile. */
    private fun setupFocusGlow() {
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus != null && newFocus.id == R.id.tile) {
                focusedTile = newFocus
                focusedInGrid = !adapter.isDockAt(positionOf(newFocus))
                placeGlow(newFocus, true)
            } else {
                if (newFocus?.id == R.id.adhanStop) grid.smoothScrollBy(0, -grid.computeVerticalScrollOffset())
                focusedTile = null
                glow.visibility = View.INVISIBLE
                glowLabel.visibility = View.INVISIBLE
            }
        }
        // While tiles animate (apps added/removed/moved) the glow follows the focused tile every frame.
        window.decorView.viewTreeObserver.addOnPreDrawListener {
            val t = focusedTile
            if (t != null) placeGlow(t, false)
            true
        }
    }

    /** Adapter position of a tile view, or -1. */
    private fun positionOf(tile: View): Int {
        val item = grid.findContainingItemView(tile) ?: return -1
        return grid.getChildAdapterPosition(item)
    }

    private var glowLastFrame = 0L
    private var glowLabelName: String? = null
    private val glowVisible = Rect()
    private val glowTileLoc = IntArray(2)
    private val glowRootLoc = IntArray(2)

    private fun placeGlow(tile: View, glide: Boolean) {
        val visible = glowVisible
        if (!tile.isShown || !tile.getGlobalVisibleRect(visible) || visible.height() < tile.height * 0.5f) {
            glow.visibility = View.INVISIBLE
            glowLabel.visibility = View.INVISIBLE
            return
        }
        val tileLoc = glowTileLoc
        val rootLoc = glowRootLoc
        tile.getLocationInWindow(tileLoc)
        (glow.parent as View).getLocationInWindow(rootLoc)
        // getLocationInWindow includes the tile's current scale, so recover its centre first.
        val cx = tileLoc[0] - rootLoc[0] + tile.width * tile.scaleX / 2f
        val cy = tileLoc[1] - rootLoc[1] + tile.height * tile.scaleY / 2f
        val target = if (prefs.animations) AppAdapter.FOCUS_SCALE else 1f
        val w = tile.width * target + dp(8)
        val h = tile.height * target + dp(8)
        val lp = glow.layoutParams
        if (lp.width != w.toInt() || lp.height != h.toInt()) {
            lp.width = w.toInt()
            lp.height = h.toInt()
            glow.layoutParams = lp
        }
        val x = cx - w / 2f
        val y = cy - h / 2f
        val wasVisible = glow.visibility == View.VISIBLE
        glow.visibility = View.VISIBLE
        // The app name floats under a grid tile (dock tiles use the big title above the dock).
        val name = tile.tag as? String
        val showLabel = focusedInGrid && !name.isNullOrEmpty()
        if (showLabel) {
            if (glowLabelName != name) {
                glowLabelName = name
                glowLabel.text = name
            }
            val llp = glowLabel.layoutParams
            if (llp.width != w.toInt()) {
                llp.width = w.toInt()
                glowLabel.layoutParams = llp
            }
            glowLabel.visibility = View.VISIBLE
        } else {
            glowLabel.visibility = View.INVISIBLE
        }
        // The highlight is glued to its tile: its position is always the tile's *current* position (so it can
        // never lag behind or ghost while the page slides), plus a small leftover offset from the previous tile
        // that dies away in ~150 ms. That keeps the glide between tiles but removes the lag.
        val now = android.os.SystemClock.uptimeMillis()
        var left = ((now - glowLastFrame).coerceIn(1L, 40L)) / 1000f
        glowLastFrame = now
        if (!wasVisible || !prefs.animations) {
            glowOffX = 0f; glowOffY = 0f; glowVx = 0f; glowVy = 0f
        } else if (tile !== glowTile) {
            glowOffX = glow.x - x; glowOffY = glow.y - y
            glowVx = 0f; glowVy = 0f
        }
        glowTile = tile
        if (glowOffX != 0f || glowOffY != 0f) {
            while (left > 0f) {
                val dt = minOf(left, 0.004f)
                left -= dt
                glowVx += (-GLOW_W * GLOW_W * glowOffX - 2f * GLOW_W * glowVx) * dt
                glowVy += (-GLOW_W * GLOW_W * glowOffY - 2f * GLOW_W * glowVy) * dt
                glowOffX += glowVx * dt
                glowOffY += glowVy * dt
            }
            if (Math.abs(glowOffX) < 0.5f && Math.abs(glowOffY) < 0.5f && Math.abs(glowVx) < 6f && Math.abs(glowVy) < 6f) {
                glowOffX = 0f; glowOffY = 0f; glowVx = 0f; glowVy = 0f
            }
        }
        glow.x = x + glowOffX
        glow.y = y + glowOffY
        glowLabel.x = glow.x
        glowLabel.y = glow.y + h - dp(2)
        tintGlow(tile)
    }

    private var glowOffX = 0f
    private var glowOffY = 0f
    private var glowTile: View? = null
    private var glowVx = 0f
    private var glowVy = 0f
    private var glowTint = 0x2EFFFFFF
    private val ambientCache = HashMap<String, Int>()

    /** Dominant colour of an app's picture, lifted so it always reads on a dark background. */
    private fun ambientOf(item: AppItem): Int = ambientCache.getOrPut(item.packageName) {
        try {
            val bmp = ((item.banner ?: item.icon) as? android.graphics.drawable.BitmapDrawable)?.bitmap
                ?: return@getOrPut 0xFFFFFF
            val small = android.graphics.Bitmap.createScaledBitmap(bmp, 8, 4, true)
            var r = 0L; var g = 0L; var b = 0L; var n = 0
            for (yy in 0 until small.height) for (xx in 0 until small.width) {
                val c = small.getPixel(xx, yy)
                if (android.graphics.Color.alpha(c) < 128) continue
                r += android.graphics.Color.red(c); g += android.graphics.Color.green(c); b += android.graphics.Color.blue(c); n++
            }
            if (n == 0) return@getOrPut 0xFFFFFF
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
            hsv[1] = minOf(1f, hsv[1] * 1.15f)
            hsv[2] = maxOf(0.6f, hsv[2])
            android.graphics.Color.HSVToColor(hsv) and 0xFFFFFF
        } catch (e: Exception) {
            0xFFFFFF
        }
    }

    /** Eases the outer glow ring toward the focused app's colour (white for unknown). */
    private fun tintGlow(tile: View) {
        val item = tile.getTag(R.id.tile) as? AppItem
        val rgb = if (item != null && prefs.animations) ambientOf(item) else 0xFFFFFF
        val target = 0x70000000 or rgb
        if (glowTint == target) return
        glowTint = target
        val ring = (glow.background as? android.graphics.drawable.LayerDrawable)?.getDrawable(0) as? android.graphics.drawable.GradientDrawable
        ring?.setStroke(dp(8), glowTint)
    }

    private companion object {
        const val GLOW_W = 26f // critically damped offset decay: no overshoot, ~150 ms
    }

    // ---------------------------------------------------------------- apps

    private fun tileOf(index: Int): View? =
        grid.findViewHolderForAdapterPosition(index)?.itemView?.findViewById(R.id.tile)

    private fun onTileFocus(item: AppItem) {
        lastFocused = item
        lastFocusedDock = adapter.isDock(item.packageName)
        if (focusTitle.text != item.label) focusTitle.text = item.label
    }

    private fun applyLookOptions() {
        val glossy = prefs.glossy
        val animate = prefs.animations
        var changed = false
        if (adapter.glossy != glossy || adapter.animate != animate) {
            adapter.glossy = glossy
            adapter.animate = animate
            changed = true
        }
        if (changed) adapter.notifyDataSetChanged()
        greeting.visibility = View.GONE
        findViewById<View>(R.id.btnWifi).visibility = if (prefs.showWifiButton) View.VISIBLE else View.GONE
    }

    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val r = packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == packageName
    }

    /** The "set as default" button only shows while the Home button still opens another launcher. */
    private fun updateHomeButtons() {
    }

    /** Asks Android to make this launcher the Home app (system dialog), with fallbacks. */
    private fun requestHomeRole() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                val rm = getSystemService(android.app.role.RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) &&
                    !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
                ) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), 77)
                    return
                }
            } catch (e: Exception) {
                // fall through to the settings page
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            AlertDialog.Builder(this, R.style.LauncherDialog)
                .setTitle(R.string.menu_set_home)
                .setMessage(R.string.set_home_text)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        updateHomeButtons()
    }

    /** Opens the TV's own home screen (the launcher is meant to stay on Home). */
    private fun exitToSystem() {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val others = packageManager.queryIntentActivities(homeIntent, 0)
            .map { it.activityInfo }
            .filter { it.packageName != packageName }
        val pick = others.firstOrNull { it.packageName == "com.google.android.tvlauncher" }
            ?: others.firstOrNull { it.packageName.contains("launcher") }
            ?: others.firstOrNull()
        if (pick == null) {
            Toast.makeText(this, R.string.no_system_home, Toast.LENGTH_LONG).show()
            return
        }
        startActivitySafe(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClassName(pick.packageName, pick.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun reload() {
        if (move != null) return
        Thread {
            val loaded = AppRepository.load(this)
            val sorted = AppRepository.sorted(loaded, prefs.appOrder)
            runOnUiThread {
                if (move != null) return@runOnUiThread
                loadedApps = sorted
                appsDirty = false
                appsLoadedAt = System.currentTimeMillis()
                applyLists()
            }
        }.start()
    }

    private fun applyLists() {
        val hidden = prefs.hidden.toSet()
        visibleApps = loadedApps.filter { it.packageName !in hidden }
        if (!prefs.favoritesInit && visibleApps.isNotEmpty()) {
            // First run: the first five apps become the favorites row; the user can edit it later.
            if (prefs.favorites.isEmpty()) prefs.favorites = visibleApps.take(prefs.gridSpan - 1).map { it.packageName }
            prefs.favoritesInit = true
        }
        val favs = prefs.favorites.mapNotNull { pkg -> visibleApps.firstOrNull { it.packageName == pkg } }

        val span = prefs.gridSpan
        // Dock row = favourites + the add tile, padded with invisible tiles to exactly one row.
        val docked = favs.take(span - 1)
        val pads = (span - docked.size - 1).coerceAtLeast(0)
        val dockList = docked + addTile() + List(pads) { padTile(it) }
        val dockKeys = docked.map { it.packageName }.toSet()
        val cap = prefs.homeRows * span
        val rows: List<AppItem> = if (cap <= 0) emptyList() else {
            val rest = visibleApps.filter { it.packageName !in dockKeys }
            rest.take(cap - 1) + libraryTile()
        }
        if (span != appliedSpan) {
            appliedSpan = span
            gridManager.spanCount = span
        }
        // Only rebind when something changed, so D-pad focus survives returning Home.
        val all = dockList + rows
        val oldNames = adapter.items.map { it.packageName }
        val focusIdx = lastFocused?.let { oldNames.indexOf(it.packageName) } ?: -1
        val focusName = lastFocused?.packageName
        adapter.dockCount = dockList.size
        if (oldNames != all.map { it.packageName }) {
            adapter.submit(all)
        }
        // If the focused tile was removed or moved away, focus stays at the same place (the tile that took its spot).
        val restore = Runnable {
            if (move != null) return@Runnable
            val f = currentFocus
            val inGrid = f != null && grid.findContainingItemView(f) != null
            if (inGrid) return@Runnable
            var t = if (focusIdx >= 0 && adapter.items.getOrNull(focusIdx)?.packageName != focusName)
                focusIdx.coerceAtMost(adapter.items.size - 1) else 0
            while (t > 0 && adapter.items[t].packageName.startsWith(AppAdapter.PAD_PREFIX)) t--
            val tile = tileOf(t)
            if (tile != null) tile.requestFocus() else grid.post { tileOf(t)?.requestFocus() }
        }
        grid.post { centerDock() }
        grid.post(restore)
        handler.postDelayed(restore, 300)

        if (firstLoad && visibleApps.isNotEmpty()) {
            firstLoad = false
            grid.post { tileOf(0)?.requestFocus() }
        }
    }

    private fun padTile(i: Int) = AppItem(
        packageName = AppAdapter.PAD_PREFIX + i,
        label = "",
        icon = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT),
        banner = null,
        intent = Intent()
    )

    private fun launchApp(item: AppItem) {
        try {
            startActivity(item.intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cannot_launch, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startActivitySafe(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cannot_launch, Toast.LENGTH_SHORT).show()
        }
    }

    private var addItem: AppItem? = null
    private fun addTile(): AppItem = addItem ?: AppItem(
        packageName = ADD_PKG,
        label = getString(R.string.add_apps_tile),
        icon = ContextCompat.getDrawable(this, R.drawable.ic_add)!!,
        banner = null,
        intent = Intent()
    ).also { addItem = it }

    /** Picker: tap an app to put it on the home dock; stays open so several can be added. */
    private fun showAddApps() {
        val dialog = Dialogs.track(android.app.Dialog(this, R.style.LauncherPlainDialog))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog)
            setPadding(dp(28), dp(22), dp(28), dp(14))
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.add_apps_title)
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.add_apps_hint)
            textSize = 15f
            alpha = 0.8f
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(10))
        })
        val rv = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, prefs.gridSpan)
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(8))
            itemAnimator = null
        }
        lateinit var adapter: AppAdapter
        adapter = AppAdapter(
            onClick = { item ->
                val i = adapter.items.indexOfFirst { it.packageName == item.packageName }
                if (i >= 0) {
                    toggleFavorite(item)
                    adapter.items.removeAt(i)
                    adapter.notifyItemRemoved(i)
                    Toast.makeText(this, getString(R.string.added_to_home, item.label), Toast.LENGTH_SHORT).show()
                    rv.post {
                        val n = minOf(i, adapter.items.size - 1)
                        if (n >= 0) rv.findViewHolderForAdapterPosition(n)?.itemView?.requestFocus()
                        else dialog.dismiss()
                    }
                }
            },
            onLongClick = { _, _ -> }
        )
        adapter.focusRing = true
        val favSet = prefs.favorites.toSet()
        adapter.items.addAll(visibleApps.filter { it.packageName !in favSet })
        rv.adapter = adapter
        val dm = resources.displayMetrics
        root.addView(rv, LinearLayout.LayoutParams((dm.widthPixels * 0.88f).toInt(), (dm.heightPixels * 0.62f).toInt()))
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        rv.post { rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
    }

    private fun libraryTile(): AppItem = libraryItem ?: AppItem(
        packageName = LIBRARY_PKG,
        label = getString(R.string.library_title),
        icon = ContextCompat.getDrawable(this, R.drawable.ic_apps)!!,
        banner = null,
        intent = Intent()
    ).also { libraryItem = it }

    /** The app library: every visible app in one grid, with the same long-press options. */
    private var adapterRef: AppAdapter? = null

    private fun showLibrary() {
        val dialog = Dialogs.track(android.app.Dialog(this, R.style.LauncherPlainDialog))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog)
            setPadding(dp(28), dp(22), dp(28), dp(14))
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.library_title_fmt, visibleApps.size)
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.library_hint)
            textSize = 15f
            alpha = 0.8f
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(10))
        })
        val rv = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, prefs.gridSpan)
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(8))
        }
        val adapter = AppAdapter(
            onClick = { item -> dialog.dismiss(); launchApp(item) },
            onLongClick = { item, a -> showAppMenu(item, a, inLibrary = true) { dialog.dismiss() } }
        )
        adapterRef = adapter
        adapter.focusRing = true
        adapter.items.addAll(visibleApps)
        rv.adapter = adapter
        val dm = resources.displayMetrics
        root.addView(rv, LinearLayout.LayoutParams((dm.widthPixels * 0.88f).toInt(), (dm.heightPixels * 0.62f).toInt()))
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        rv.post { rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
    }

    private fun showAppMenu(
        item: AppItem, adapter: AppAdapter, inLibrary: Boolean = false, onChanged: () -> Unit = {}
    ) {
        val isFav = prefs.favorites.contains(item.packageName)
        val entries = ArrayList<Pair<String, () -> Unit>>()
        entries.add(getString(R.string.open) to { launchApp(item) })
        entries.add(getString(if (isFav) R.string.remove_fav else R.string.add_fav) to {
            toggleFavorite(item)
            if (inLibrary) onChanged()
        })
        if (inLibrary && !isFav) {
            val onHome = adapter.items.drop(adapter.dockCount).any { it.packageName == item.packageName }
            entries.add(getString(if (onHome) R.string.remove_home else R.string.add_home) to {
                val rest = loadedApps.map { it.packageName }.filter { it != item.packageName }
                prefs.appOrder = if (onHome) rest + item.packageName else listOf(item.packageName) + rest
                loadedApps = AppRepository.sorted(loadedApps, prefs.appOrder)
                applyLists()
                if (!onHome) Toast.makeText(this, getString(R.string.added_to_home, item.label), Toast.LENGTH_SHORT).show()
                onChanged()
            })
        }
        if (!inLibrary) entries.add(getString(R.string.move_app) to { startMove(item, adapter) })
        entries.add(getString(R.string.app_info) to {
            startActivitySafe(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${item.packageName}"))
            )
        })
        entries.add(getString(R.string.hide_app) to {
            prefs.hidden = prefs.hidden + item.packageName
            applyLists()
            if (inLibrary) onChanged()
        })
        entries.add(getString(R.string.uninstall) to {
            startActivitySafe(Intent(Intent.ACTION_DELETE, Uri.parse("package:${item.packageName}")))
        })
        entries.add(getString(R.string.cancel) to {})
        Dialogs.showChoice(this, item.label, entries.map { it.first }) { which -> entries[which].second() }
    }

    private fun toggleFavorite(item: AppItem) {
        val list = prefs.favorites.toMutableList()
        if (!list.remove(item.packageName)) list.add(item.packageName)
        prefs.favorites = list
        applyLists()
    }

    // ---------------------------------------------------------------- quick settings

    private fun showQuickSettings() {
        val entries: List<Pair<Int, () -> Unit>> = listOf(
            R.string.qs_network to { startActivitySafe(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            R.string.qs_display to { startActivitySafe(Intent(Settings.ACTION_DISPLAY_SETTINGS)) },
            R.string.qs_sound to { startActivitySafe(Intent(Settings.ACTION_SOUND_SETTINGS)) },
            R.string.qs_apps to { startActivitySafe(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) },
            R.string.qs_hidden to { showHiddenApps() },
            R.string.qs_stats to { showStats() },
            R.string.qs_tv to { startActivitySafe(Intent(Settings.ACTION_SETTINGS)) },
            R.string.qs_launcher to { startActivity(Intent(this, SettingsActivity::class.java)) }
        )
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_menu)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        lateinit var popup: PopupWindow
        for ((res, action) in entries) {
            content.addView(TextView(this).apply {
                text = getString(res)
                textSize = 16f
                setTextColor(ContextCompat.getColorStateList(this@MainActivity, R.color.menu_text)!!)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                isFocusable = true
                isClickable = true
                setBackgroundResource(R.drawable.bg_menu_item)
                setOnClickListener {
                    popup.dismiss()
                    action()
                }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        // Long list: it scrolls inside a panel no taller than the screen.
        val maxH = (resources.displayMetrics.heightPixels * 0.78f).toInt()
        content.measure(
            View.MeasureSpec.makeMeasureSpec(dp(250), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val scroller = android.widget.ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content)
        }
        popup = PopupWindow(scroller, dp(250), minOf(content.measuredHeight, maxH), true)
        popup.elevation = dp(12).toFloat()
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        menuPopup = popup
        popup.showAsDropDown(btnSettings, 0, dp(8), Gravity.END)
        content.getChildAt(0)?.requestFocus()
    }

    /** Memory, storage and device facts, like Secreto's stats. */
    private fun showStats() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val st = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        fun gb(b: Long) = String.format(Locale.US, "%.1f GB", b / 1073741824.0)
        val dm = resources.displayMetrics
        val text = listOf(
            getString(R.string.stats_memory) + ": " + gb(mi.totalMem - mi.availMem) + " / " + gb(mi.totalMem),
            getString(R.string.stats_storage) + ": " + gb(st.totalBytes - st.availableBytes) + " / " + gb(st.totalBytes),
            getString(R.string.stats_apps) + ": " + loadedApps.size,
            getString(R.string.stats_screen) + ": " + dm.widthPixels + "x" + dm.heightPixels,
            getString(R.string.stats_device) + ": " + android.os.Build.MODEL + " (Android " + android.os.Build.VERSION.RELEASE + ")"
        ).joinToString("\n")
        AlertDialog.Builder(this, R.style.LauncherDialog)
            .setTitle(R.string.qs_stats)
            .setMessage(text)
            .setPositiveButton(R.string.ok, null)
            .create().also { Dialogs.track(it) }.show()
    }

    // ---------------------------------------------------------------- top menu

    private fun sortAppsAz() {
        prefs.appOrder = emptyList()
        loadedApps = AppRepository.sorted(loadedApps, emptyList())
        applyLists()
        Toast.makeText(this, R.string.sorted_done, Toast.LENGTH_SHORT).show()
    }

    private fun refreshWeather() {
        if (!prefs.showWeather) {
            weatherText.visibility = View.GONE
            return
        }
        val now = System.currentTimeMillis()
        weather?.let { bindWeather(it) }
        if (fetchingWeather || now - weatherAt < 30 * 60_000L) return
        fetchingWeather = true
        val lat = prefs.latitude
        val lon = prefs.longitude
        Thread {
            val w = WeatherRepository.fetch(lat, lon)
            runOnUiThread {
                fetchingWeather = false
                // Retry sooner when the request failed (no network).
                weatherAt = if (w != null) System.currentTimeMillis() else System.currentTimeMillis() - 25 * 60_000L
                if (w != null) {
                    weather = w
                    bindWeather(w)
                }
            }
        }.start()
    }

    private fun bindWeather(w: Weather) {
        weatherText.text = "${w.emoji} ${w.tempC}\u00B0"
        weatherText.visibility = View.VISIBLE
    }

    private fun showModuleCards() {
        val labels = arrayOf(getString(R.string.card_prayer), getString(R.string.card_weather))
        val checked = booleanArrayOf(prefs.showPrayerCard, prefs.showWeather)
        AlertDialog.Builder(this, R.style.LauncherDialog)
            .setTitle(R.string.menu_module_cards)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.ok) { _, _ ->
                prefs.showPrayerCard = checked[0]
                prefs.showWeather = checked[1]
                weatherAt = 0L
                refreshWeather()
                nextPrayer = null
                prayerRetryAt = 0L
                if (checked[0]) refreshPrayerCard() else prayerCard.visibility = View.GONE
            }
            .setNegativeButton(R.string.cancel, null)
            .create().also { Dialogs.track(it) }.show()
    }

    private fun showEditDock() {
        val apps = visibleApps
        val labels = apps.map { it.label }.toTypedArray()
        val inDock = prefs.favorites.toSet()
        val checked = BooleanArray(apps.size) { apps[it].packageName in inDock }
        AlertDialog.Builder(this, R.style.LauncherDialog)
            .setTitle(R.string.menu_edit_dock)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.ok) { _, _ ->
                val chosen = apps.filterIndexed { i, _ -> checked[i] }.map { it.packageName }
                val kept = prefs.favorites.filter { it in chosen }
                prefs.favorites = kept + chosen.filter { it !in kept }
                applyLists()
            }
            .setNegativeButton(R.string.cancel, null)
            .create().also { Dialogs.track(it) }.show()
    }

    private fun showHiddenApps() {
        val apps = loadedApps
        val labels = apps.map { it.label }.toTypedArray()
        val hidden = prefs.hidden.toSet()
        val checked = BooleanArray(apps.size) { apps[it].packageName in hidden }
        AlertDialog.Builder(this, R.style.LauncherDialog)
            .setTitle(R.string.menu_hidden_apps)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.ok) { _, _ ->
                prefs.hidden = apps.filterIndexed { i, _ -> checked[i] }.map { it.packageName }
                applyLists()
            }
            .setNegativeButton(R.string.cancel, null)
            .create().also { Dialogs.track(it) }.show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this, R.style.LauncherDialog)
            .setTitle(R.string.about)
            .setMessage(R.string.about_text)
            .setPositiveButton(R.string.ok, null)
            .create().also { Dialogs.track(it) }.show()
    }

    // ---------------------------------------------------------------- move app

    private inner class MoveSession(
        val span: Int,
        val isFav: Boolean,
        val pkg: String,
        val original: List<AppItem>
    ) {
        /** Apps can only move inside their own part: favourites in the dock row, the rest in the grid. */
        fun step(delta: Int) {
            val items = adapter.items
            val from = items.indexOfFirst { it.packageName == pkg }
            val to = from + delta
            val lo = if (isFav) 0 else adapter.dockCount
            val hi = if (isFav) items.indexOfFirst { it.packageName == ADD_PKG } - 1
                     else items.indexOfLast { it.packageName != LIBRARY_PKG }
            if (from < 0 || to < lo || to > hi) return
            val item = items.removeAt(from)
            items.add(to, item)
            adapter.notifyItemMoved(from, to)
            // Keep the moved tile on screen.
            grid.postDelayed({
                tileOf(to)?.let { it.requestRectangleOnScreen(Rect(0, 0, it.width, it.height), false) }
            }, 80)
        }
    }

    private fun startReorder() {
        val target = lastFocused?.takeIf { f -> visibleApps.any { it.packageName == f.packageName } }
            ?: visibleApps.firstOrNull()
            ?: return
        startMove(target, adapter)
    }

    private fun startMove(item: AppItem, adapter: AppAdapter) {
        val isFav = adapter.isDock(item.packageName)
        val span = if (isFav) 1 else gridManager.spanCount
        move = MoveSession(span, isFav, item.packageName, ArrayList(adapter.items))
        adapter.movingPackage = item.packageName
        val index = adapter.items.indexOfFirst { it.packageName == item.packageName }
        adapter.notifyItemChanged(index)
        grid.post { tileOf(index)?.requestFocus() }
        Toast.makeText(this, R.string.move_hint, Toast.LENGTH_LONG).show()
    }

    private fun finishMove(save: Boolean) {
        val s = move ?: return
        move = null
        adapter.movingPackage = null
        if (save) {
            if (s.isFav) {
                prefs.favorites = adapter.items.take(adapter.dockCount)
                    .map { it.packageName }.filter { it != ADD_PKG && !it.startsWith(AppAdapter.PAD_PREFIX) }
            } else {
                val subset = adapter.items.drop(adapter.dockCount).map { it.packageName }.filter { it != LIBRARY_PKG }
                val rest = loadedApps.map { it.packageName }.filter { it !in subset }
                prefs.appOrder = subset + rest
                loadedApps = AppRepository.sorted(loadedApps, prefs.appOrder)
                val hidden = prefs.hidden.toSet()
                visibleApps = loadedApps.filter { it.packageName !in hidden }
            }
        } else {
            adapter.items.clear()
            adapter.items.addAll(s.original)
        }
        adapter.notifyDataSetChanged()
        val index = adapter.items.indexOfFirst { it.packageName == s.pkg }
        grid.post { tileOf(index)?.requestFocus() }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screensaverOn) {
            if (event.action == KeyEvent.ACTION_DOWN) stopScreensaver()
            return true
        }
        val s = move
        if (s != null && event.keyCode != KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                // The dock and the grid are always laid out left-to-right, whatever the UI language.
                val right = 1
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> s.step(-right)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> s.step(right)
                    KeyEvent.KEYCODE_DPAD_UP -> if (s.span > 1) s.step(-s.span)
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (s.span > 1) s.step(s.span)
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> if (event.repeatCount == 0) finishMove(true)
                }
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) &&
            wrapSideways(event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) return true
        // A held D-pad key repeats 30-50 times a second; moving focus that often only piles up work,
        // so repeats are paced to ~25 moves a second and the highlight and page glide instead of stutter.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 && isDpad(event.keyCode)) {
            val now = event.eventTime
            if (now - lastRepeatMove < 40) return true
            lastRepeatMove = now
        }
        return super.dispatchKeyEvent(event)
    }

    /** Left/right at the end of a row wraps to the other end instead of escaping to the header buttons. */
    private fun wrapSideways(right: Boolean): Boolean {
        val f = currentFocus ?: return false
        if (f.id != R.id.tile) return false
        val pos = positionOf(f)
        if (pos < 0) return false
        val span = gridManager.spanCount
        val items = adapter.items
        val first = pos - pos % span
        var last = minOf(first + span - 1, items.size - 1)
        while (last > first && items[last].packageName.startsWith(AppAdapter.PAD_PREFIX)) last--
        val target = when {
            right && pos == last -> first
            !right && pos == first -> last
            else -> return false
        }
        if (target == pos) return true
        tileOf(target)?.requestFocus()
        return true
    }

    private var lastRepeatMove = 0L
    private fun isDpad(k: Int) = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN ||
        k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT

    // ---------------------------------------------------------------- search

    private fun showSearch() {
        val view = layoutInflater.inflate(R.layout.dialog_search, null)
        view.setBackgroundResource(R.drawable.bg_dialog)
        view.setPadding(dp(28), dp(24), dp(28), dp(18))
        val input = view.findViewById<EditText>(R.id.searchInput)
        val results = view.findViewById<RecyclerView>(R.id.searchResults)
        val dialog = Dialogs.track(android.app.Dialog(this, R.style.LauncherPlainDialog))
        val adapter = AppAdapter(
            onClick = {
                dialog.dismiss()
                launchApp(it)
            },
            onLongClick = { _, _ -> }
        )
        adapter.focusRing = true
        results.layoutManager = GridLayoutManager(this, 4)
        results.adapter = adapter
        adapter.items.addAll(visibleApps)

        input.doAfterTextChanged { text ->
            val q = text?.toString()?.trim().orEmpty()
            adapter.items.clear()
            adapter.items.addAll(
                if (q.isEmpty()) visibleApps else visibleApps.filter { it.label.contains(q, ignoreCase = true) }
            )
            adapter.notifyDataSetChanged()
        }

        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout((resources.displayMetrics.widthPixels * 0.62f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        dialog.show()
    }

    // ---------------------------------------------------------------- header & prayer card

    private fun updateHeader() {
        val locale = Locale.getDefault()
        val isArabic = locale.language == "ar"

        applyClockZone()
        val cityTz = CityClock.timeZone(this)
        val hour = Calendar.getInstance(cityTz).get(Calendar.HOUR_OF_DAY)
        val greet = getString(
            when {
                hour < 12 -> R.string.greeting_morning
                hour < 17 -> R.string.greeting_afternoon
                else -> R.string.greeting_evening
            }
        )
        greeting.text = if (isArabic) greet else greet.uppercase(locale)

        val weekday = SimpleDateFormat("EEEE", locale).apply { timeZone = cityTz }.format(Date())
        val hijri = HijriDate.format(this, PrayerRepository.cityToday(this), locale)
        val hijriLine = hijri.ifEmpty { weekday }
        dateText.text = if (isArabic) hijriLine else hijriLine.uppercase(locale)
        val greg = SimpleDateFormat("d MMMM yyyy", locale).apply { timeZone = cityTz }.format(Date())
        val dayMonth = SimpleDateFormat("EEEE d MMMM", locale).apply { timeZone = cityTz }.format(Date())
        findViewById<TextView>(R.id.dateGreg).text = dayMonth
        wallCaption.text = getString(
            when (prefs.wallpaperPreset) {
                "photo1" -> R.string.cap_photo1
                "photo3" -> R.string.cap_photo3
                "photo6" -> R.string.cap_photo6
                else -> R.string.cap_kirkjufell
            }
        )
        wallCaption.visibility = if (prefs.wallpaperType == "none") View.VISIBLE else View.GONE

        refreshWeather()
        val np = nextPrayer
        val due = np == null || !np.time.toInstant().isAfter(Instant.now())
        if (due && !fetchingPrayer && System.currentTimeMillis() >= prayerRetryAt) refreshPrayerCard()
        showNextPrayer()
    }

    private fun refreshPrayerCard() {
        if (!prefs.showPrayerCard) {
            prayerCard.visibility = View.GONE
            return
        }
        fetchingPrayer = true
        val appContext = applicationContext
        Thread {
            val today = PrayerRepository.getTimings(appContext, PrayerRepository.cityToday(appContext))
            val next = PrayerRepository.nextPrayer(appContext, false)
            runOnUiThread {
                fetchingPrayer = false
                nextPrayer = next
                if (next == null) prayerRetryAt = System.currentTimeMillis() + 5 * 60_000L
                bindPrayerCard(today)
            }
        }.start()
    }

    private fun bindPrayerCard(today: DayTimings?) {
        if (!prefs.showPrayerCard || today == null) {
            prayerCard.visibility = View.GONE
            return
        }
        prayerCard.visibility = View.VISIBLE
        val next = nextPrayer?.name
        for ((name, chip) in chips) {
            chip.text = "${prayerLabel(name)}\n${TimeFmt.display(today.times[name], TimeFmt.is24(this, prefs))}"
            if (name == next) {
                chip.setBackgroundResource(R.drawable.bg_chip_next)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.background = null
                chip.setTextColor(Color.WHITE)
            }
        }
        showNextPrayer()
    }

    private fun showNextPrayer() {
        val np = nextPrayer
        if (np == null) {
            prayerLine.visibility = View.GONE
            return
        }
        val minutes = Duration.between(Instant.now(), np.time.toInstant()).toMinutes().coerceAtLeast(0) + 1
        val remaining = if (minutes >= 60) {
            getString(R.string.duration_hm, (minutes / 60).toInt(), (minutes % 60).toInt())
        } else {
            getString(R.string.duration_m, minutes.toInt())
        }
        val time = TimeFmt.display(np.time.format(DateTimeFormatter.ofPattern("HH:mm")), TimeFmt.is24(this, prefs))
        val title = getString(R.string.next_prayer_title)
        val sp = android.text.SpannableString(title + "\n" + getString(R.string.next_prayer_short, prayerLabel(np.name), remaining))
        sp.setSpan(android.text.style.RelativeSizeSpan(0.78f), 0, title.length, 0)
        sp.setSpan(android.text.style.ForegroundColorSpan(0xB3FFFFFF.toInt()), 0, title.length, 0)
        prayerLine.text = sp
        prayerLine.visibility = View.VISIBLE
    }

    private fun prayerLabel(name: String): String = getString(
        when (name) {
            "Fajr" -> R.string.prayer_fajr
            "Dhuhr" -> R.string.prayer_dhuhr
            "Asr" -> R.string.prayer_asr
            "Maghrib" -> R.string.prayer_maghrib
            else -> R.string.prayer_isha
        }
    )
}
