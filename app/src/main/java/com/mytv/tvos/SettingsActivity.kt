package com.mytv.tvos

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** tvOS-style settings. Every row either does something real (toggle stored in prefs, system screen) or is absent. */
class SettingsActivity : Activity() {

    private sealed class Row {
        class Nav(val title: String, val value: () -> String? = { null }, val chevron: Boolean = true, val go: () -> Unit) : Row()
        class Toggle(val title: String, val key: String, val desc: String) : Row()
        class Switch(val title: String, val get: () -> Boolean, val set: (Boolean) -> Unit, val desc: String = "") : Row()
        class Label(val text: String) : Row()
    }

    private class Page(val title: String, val rows: () -> List<Row>)

    private lateinit var u: U
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var titleView: TextView
    private lateinit var descView: TextView
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private val stack = ArrayList<Page>()
    private var selectedIndex = ArrayList<Int>()

    private val p by lazy { Prefs(this) }
    private fun toast(s: String) = Toast.makeText(this, L.t(s), Toast.LENGTH_SHORT).show()
    private fun choose(title: String, items: List<Pair<String, () -> Unit>>) { Menus.show(this, title, items) }
    private fun reschedule() = AdhanScheduler.reschedule(this)

    private val methods = listOf(
        23 to "Jordan (Ministry of Awqaf)", 3 to "Muslim World League", 4 to "Umm al-Qura, Makkah",
        5 to "Egyptian General Authority", 1 to "Karachi", 2 to "ISNA (North America)", 13 to "Diyanet, Turkey"
    )
    private val zones = listOf("auto" to "Automatic (by city)", "7200" to "UTC+2", "10800" to "UTC+3 (Amman, summer time)", "14400" to "UTC+4", "3600" to "UTC+1")

    private fun sys(action: String) = { try { startActivity(Intent(action)) } catch (_: Exception) { Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show() } }

    private fun ssText(): String { val m = prefs.getInt("ss_min", 10); return if (m == 0) "Never" else "$m min" }

    private fun root() = Page("Settings") {
        listOf(
            Row.Nav("General") { push(general()) },
            Row.Nav("Home Screen") { push(home()) },
            Row.Nav("Prayer and Adhan", { p.cityName }) { push(prayer()) },
            Row.Nav("Screen Saver", { ssText() }) { push(screensaver()) },
            Row.Nav("Video and Audio", go = sys(Settings.ACTION_SOUND_SETTINGS)),
            Row.Nav("Remotes and Devices", go = sys(Settings.ACTION_BLUETOOTH_SETTINGS)),
            Row.Nav("Accessibility", go = sys(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
            Row.Nav("Apps", go = sys(Settings.ACTION_APPLICATION_SETTINGS)),
            Row.Nav("Network", go = sys(Settings.ACTION_WIFI_SETTINGS)),
            Row.Nav("System", go = sys(Settings.ACTION_SETTINGS))
        )
    }

    private fun general() = Page("General") {
        listOf(
            Row.Nav("Appearance", go = sys(Settings.ACTION_DISPLAY_SETTINGS)),
            Row.Nav("Language and Region", go = sys(Settings.ACTION_LOCALE_SETTINGS)),
            Row.Nav("Date and Time", go = sys(Settings.ACTION_DATE_SETTINGS)),
            Row.Nav("Language", { if (p.language == "ar") "العربية" else "English" }) {
                choose("Language", listOf("English" to { setLang("en") }, "العربية" to { setLang("ar") }))
            },
            Row.Nav("Time Format", { when (p.timeFormat) { "12" -> "12-Hour"; "system" -> "System"; else -> "24-Hour" } }) {
                choose("Time Format", listOf("24-Hour" to { p.timeFormat = "24"; refresh() }, "12-Hour" to { p.timeFormat = "12"; refresh() }, "System" to { p.timeFormat = "system"; refresh() }))
            },
            Row.Nav("Time Zone", { zones.firstOrNull { it.first == p.tzMode }?.second?.substringBefore(" (") ?: "Automatic" }) {
                choose("Time Zone", zones.map { z -> z.second to { p.tzMode = z.first; Thread { CityClock.ensure(applicationContext); AdhanScheduler.rescheduleBlocking(applicationContext) }.start(); refresh() } })
            },
            Row.Nav("Date Display", { when (p.dateMode) { 1 -> "Gregorian"; 2 -> "Hijri"; else -> "Gregorian and Hijri" } }) {
                choose("Date Display", listOf("Gregorian and Hijri" to { p.dateMode = 0; refresh() }, "Gregorian" to { p.dateMode = 1; refresh() }, "Hijri" to { p.dateMode = 2; refresh() }))
            },
            Row.Nav("Hijri Date Adjustment", { (if (p.hijriAdjust > 0) "+" else "") + p.hijriAdjust + " day" }) {
                p.hijriAdjust = if (p.hijriAdjust >= 2) -2 else p.hijriAdjust + 1
            },
            Row.Nav("Storage", go = sys(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
            Row.Nav("About", go = { push(about()) })
        )
    }

    private fun home() = Page("Home Screen") {
        listOf(
            Row.Toggle("Always Show App Names", "labels", "Show the name under every app instead of only the focused one."),
            Row.Toggle("Reduce Motion", "calm", "Turn off the shine and the wiggle in the move mode."),
            Row.Nav("Wallpaper", { when (Wallpapers.mode(prefs)) { "custom" -> java.io.File(prefs.getString("wall_path", "") ?: "").name; "default" -> "Default"; else -> "None" } }) {
                choose("Wallpaper", listOf(
                    "None" to { prefs.edit().putString("wall_mode", "none").apply(); refresh() },
                    "Default" to { prefs.edit().putString("wall_mode", "default").apply(); refresh() },
                    "Choose from TV storage" to { pickWallpaper() }
                ))
            },
            Row.Nav("Clock Style", { when (prefs.getString("clock_style", "light")) { "bold" -> "Bold"; "regular" -> "Regular"; else -> "Light" } }) {
                choose("Clock Style", listOf("Light" to { prefs.edit().putString("clock_style", "light").apply(); refresh() }, "Regular" to { prefs.edit().putString("clock_style", "regular").apply(); refresh() }, "Bold" to { prefs.edit().putString("clock_style", "bold").apply(); refresh() }))
            },
            Row.Nav("Clock Size", { when (prefs.getString("clock_size", "l")) { "m" -> "Medium"; "xl" -> "Extra Large"; else -> "Large" } }) {
                choose("Clock Size", listOf("Medium" to { prefs.edit().putString("clock_size", "m").apply(); refresh() }, "Large" to { prefs.edit().putString("clock_size", "l").apply(); refresh() }, "Extra Large" to { prefs.edit().putString("clock_size", "xl").apply(); refresh() }))
            },
            Row.Label("LAYOUT"),
            Row.Nav("Reset Home Screen Layout") {
                Store(this).reset()
                Toast.makeText(this, "Layout reset", Toast.LENGTH_SHORT).show()
            },
            Row.Nav("Unhide All Apps") {
                Store(this).hidden = emptySet()
                Toast.makeText(this, "All apps are visible again", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun prayer() = Page("Prayer and Adhan") {
        val names = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        listOf<Row>(
            Row.Switch("Play Adhan Automatically", { p.adhanEnabled }, { on ->
                p.adhanEnabled = on
                if (on && android.os.Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
                reschedule()
            }, "Play the Adhan at each prayer time that is switched on below."),
            Row.Nav("City", { p.cityName }) { searchCity() },
            Row.Nav("Calculation Method", { methods.firstOrNull { it.first == p.method }?.second ?: "Method ${p.method}" }) {
                choose("Calculation Method", methods.map { m -> m.second to { p.method = m.first; p.timingsCache = null; reschedule(); refresh() } })
            },
            Row.Nav("Adhan Volume", { "${p.adhanVolume}%" }) { p.adhanVolume = if (p.adhanVolume >= 100) 25 else p.adhanVolume + 25 },
            Row.Nav("Test Adhan") { try { androidx.core.content.ContextCompat.startForegroundService(this, Intent(this, AdhanService::class.java).setAction(AdhanService.ACTION_TEST)) } catch (_: Exception) { toast("Could not start the Adhan") } },
            Row.Nav("Stop Adhan") { AdhanService.stopNow() },
            Row.Label("PRAYERS TO ANNOUNCE")
        ) + names.map { n -> Row.Switch(n, { p.isPrayerEnabled(n) }, { on -> p.setPrayerEnabled(n, on); reschedule() }) } +
            listOf(Row.Label("MANUAL CORRECTION (MINUTES)")) +
            names.map { n -> Row.Nav("$n correction", { (if (p.prayerOffset(n) > 0) "+" else "") + p.prayerOffset(n) + " " + L.t("min") }) {
                val opts = listOf(0, 1, 2, 3, 4, 5, 10, 15, -1, -2, -3, -4, -5, -10, -15)
                choose(L.t(n) + " - " + L.t("MANUAL CORRECTION (MINUTES)"), opts.map { v ->
                    ((if (v > 0) "+" else "") + v + " " + L.t("min")) to { p.setPrayerOffset(n, v); reschedule(); refresh() }
                })
            } }
    }

    private var storageThen: (() -> Unit)? = null

    private fun pickWallpaper() {
        val needed = if (Build.VERSION.SDK_INT >= 33) arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        val open = {
            FileBrowser.show(this, "Choose a picture", setOf("jpg", "jpeg", "png", "webp", "bmp")) { f ->
                prefs.edit().putString("wall_mode", "custom").putString("wall_path", f.absolutePath).putLong("wall_stamp", System.currentTimeMillis()).apply()
                toast("Wallpaper set")
                refresh()
            }
        }
        if (Build.VERSION.SDK_INT < 23 || needed.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) open()
        else { storageThen = open; try { requestPermissions(needed, 21) } catch (_: Exception) { storageThen = null; open() } }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 21) { val then = storageThen; storageThen = null; then?.invoke() } // open even when refused: app folders still work
    }

    private fun searchCity() {
        val input = android.widget.EditText(this).apply { setSingleLine(); hint = L.t("City name") }
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle(L.t("Search City"))
            .setView(input)
            .setPositiveButton(L.t("Search")) { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) Thread {
                    val res = PrayerRepository.searchCities(q, "en")
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        if (res.isEmpty()) toast("No city found (check internet)")
                        else choose("Choose City", res.map { c -> c.display to { pickCity(c) } })
                    }
                }.start()
            }
            .setNegativeButton(L.t("Cancel"), null)
            .show()
    }

    private fun pickCity(c: City) {
        p.cityName = c.display; p.latitude = c.lat; p.longitude = c.lon
        p.cityOffsetKey = null; p.timingsCache = null
        val app = applicationContext
        Thread { CityClock.ensure(app); AdhanScheduler.rescheduleBlocking(app) }.start()
        refresh()
    }

    private fun screensaver() = Page("Screen Saver") {
        listOf(0, 2, 5, 10, 15, 30).map { m ->
            Row.Nav(if (m == 0) "Never" else "After $m minutes", { if (prefs.getInt("ss_min", 10) == m) "✓" else null }, false) {
                prefs.edit().putInt("ss_min", m).apply(); pop()
            }
        }
    }

    private fun about() = Page("About") {
        listOf(
            Row.Nav("Name", { "TV" }, false) {},
            Row.Nav("Version", { try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "" } }, false) {},
            Row.Nav("Model", { android.os.Build.MODEL }, false) {},
            Row.Nav("Android", { android.os.Build.VERSION.RELEASE }, false) {}
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        u = U(this)
        L.ar = Prefs(this).language == "ar"
        prefs = getSharedPreferences("tvos_settings", Context.MODE_PRIVATE)
        val frame = FrameLayout(this).apply { setBackgroundColor(0xFF1B1B1D.toInt()) }
        val wallSrc = Wallpapers.decode(this, prefs, 480) ?: android.graphics.BitmapFactory.decodeResource(resources, R.drawable.wp_kirkjufell, android.graphics.BitmapFactory.Options().apply { inSampleSize = 16 })
        val small = Bitmap.createScaledBitmap(wallSrc, 120, (120f * wallSrc.height / wallSrc.width).toInt().coerceAtLeast(68), true)
        val wall = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageBitmap(Blur.blur(small, 4)) }
        frame.addView(wall, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(this).apply { setBackgroundColor(0xB31B1B1D.toInt()) }, FrameLayout.LayoutParams(-1, -1))
        val squircle = FrameLayout(this).apply { background = Glass.glass(0x33FFFFFF, u.px(120f), 0.9f) }
        squircle.addView(TvGlyph(this), FrameLayout.LayoutParams(u.i(300f), u.i(300f), Gravity.CENTER))
        frame.addView(squircle, FrameLayout.LayoutParams(u.i(540f), u.i(540f)).apply { leftMargin = u.i(255f); topMargin = u.i(240f) })
        titleView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u.px(58f))
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0x80FFFFFF.toInt())
        }
        titleView.gravity = Gravity.CENTER_HORIZONTAL
        frame.addView(titleView, FrameLayout.LayoutParams(u.i(789f), ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = u.i(1056f); topMargin = u.i(70f) })
        descView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u.px(28f))
            setTextColor(0x80FFFFFF.toInt())
            gravity = Gravity.CENTER_HORIZONTAL
        }
        frame.addView(descView, FrameLayout.LayoutParams(u.i(540f), ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = u.i(255f); topMargin = u.i(800f) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(list) }
        frame.addView(scroll, FrameLayout.LayoutParams(u.i(789f), u.i(900f)).apply { leftMargin = u.i(1056f); topMargin = u.i(180f) })
        setContentView(frame)
        push(root())
        if (intent?.getStringExtra("page") == "prayer") push(prayer())
    }

    private fun push(p: Page) {
        if (stack.isNotEmpty()) selectedIndex[selectedIndex.size - 1] = list.indexOfChild(currentFocus)
        stack.add(p); selectedIndex.add(0)
        render(0)
    }

    private var lastIdx = 0
    private fun refresh() { render(lastIdx) }

    private fun setLang(code: String) {
        p.language = code
        L.ar = code == "ar"
        recreate()
    }

    private fun pop() {
        stack.removeAt(stack.size - 1); selectedIndex.removeAt(selectedIndex.size - 1)
        render(selectedIndex.last().coerceAtLeast(0))
    }

    private fun render(focusIndex: Int) {
        val p = stack.last()
        titleView.text = L.t(p.title)
        descView.text = ""
        list.removeAllViews()
        list.alpha = 0f
        list.animate().alpha(1f).setDuration(140).start()
        for (r in p.rows()) list.addView(rowView(r), LinearLayout.LayoutParams(-1, u.i(66f)).apply { bottomMargin = u.i(14f) })
        list.post {
            val v = list.getChildAt(focusIndex.coerceIn(0, list.childCount - 1))
            (if (v?.isFocusable == true) v else (0 until list.childCount).map { list.getChildAt(it) }.firstOrNull { it.isFocusable })?.requestFocus()
        }
    }

    private fun rowView(r: Row): View {
        if (r is Row.Label) return TextView(this).apply {
            text = L.t(r.text)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u.px(26f))
            setTextColor(0x80FFFFFF.toInt())
            gravity = Gravity.BOTTOM
            setPadding(u.i(30f), 0, 0, u.i(8f))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(u.i(30f), 0, u.i(30f), 0)
            background = Glass.rowBg(u, 33f, 0x1FFFFFFF)
        }
        val title = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u.px(32f))
            setTextColor(Glass.text())
            maxLines = 1
            isDuplicateParentStateEnabled = true
        }
        val value = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u.px(30f))
            setTextColor(Glass.dim())
            isDuplicateParentStateEnabled = true
        }
        row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(value)
        if (r is Row.Nav && r.chevron) row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron)
            imageTintList = Glass.dim()
            isDuplicateParentStateEnabled = true
        }, LinearLayout.LayoutParams(u.i(30f), u.i(30f)).apply { marginStart = u.i(14f) })
        when (r) {
            is Row.Nav -> {
                title.text = L.t(r.title)
                value.text = L.t(r.value() ?: "")
                row.setOnClickListener { lastIdx = list.indexOfChild(row); r.go(); if (stack.isNotEmpty()) value.text = L.t(r.value() ?: "") }
            }
            is Row.Toggle -> {
                title.text = L.t(r.title)
                fun sync() { value.text = L.t(if (prefs.getBoolean(r.key, false)) "On" else "Off") }
                sync()
                row.setOnClickListener { prefs.edit().putBoolean(r.key, !prefs.getBoolean(r.key, false)).apply(); sync() }
                row.setOnFocusChangeListener { _, f -> if (f) descView.text = L.t(r.desc) }
            }
            is Row.Switch -> {
                title.text = L.t(r.title)
                fun sync() { value.text = L.t(if (r.get()) "On" else "Off") }
                sync()
                row.setOnClickListener { r.set(!r.get()); sync() }
                row.setOnFocusChangeListener { _, f -> if (f) descView.text = L.t(r.desc) }
            }
            else -> {}
        }
        if (r is Row.Nav) row.setOnFocusChangeListener { _, f -> if (f) descView.text = "" }
        return row
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (stack.size > 1) pop() else finish() }
}
