package com.mytv.tvos

import android.app.Activity
import android.content.Context
import android.content.Intent
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

    private fun sys(action: String) = { try { startActivity(Intent(action)) } catch (_: Exception) { Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show() } }

    private fun ssText(): String { val m = prefs.getInt("ss_min", 10); return if (m == 0) "Never" else "$m min" }

    private fun root() = Page("Settings") {
        listOf(
            Row.Nav("General") { push(general()) },
            Row.Nav("Home Screen") { push(home()) },
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
            Row.Toggle("24-Hour Time", "h24", "Show the clock in 24-hour format."),
            Row.Nav("Storage", go = sys(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
            Row.Nav("About", go = { push(about()) })
        )
    }

    private fun home() = Page("Home Screen") {
        listOf(
            Row.Toggle("Always Show App Names", "labels", "Show the name under every app instead of only the focused one."),
            Row.Toggle("Reduce Motion", "calm", "Turn off the shine and the wiggle in the move mode."),
            Row.Toggle("Wallpaper", "wall", "Show a picture behind the home screen."),
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
        prefs = getSharedPreferences("tvos_settings", Context.MODE_PRIVATE)
        val frame = FrameLayout(this).apply { setBackgroundColor(0xFF1B1B1D.toInt()) }
        val small = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.wp_kirkjufell, android.graphics.BitmapFactory.Options().apply { inSampleSize = 16 })
        val wall = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageBitmap(Blur.blur(small, 4)) }
        frame.addView(wall, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(this).apply { setBackgroundColor(0xB31B1B1D.toInt()) }, FrameLayout.LayoutParams(-1, -1))
        val squircle = FrameLayout(this).apply { background = Glass.solid(0x33FFFFFF, u.px(120f)) }
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
    }

    private fun push(p: Page) {
        if (stack.isNotEmpty()) selectedIndex[selectedIndex.size - 1] = list.indexOfChild(currentFocus)
        stack.add(p); selectedIndex.add(0)
        render(0)
    }

    private fun pop() {
        stack.removeAt(stack.size - 1); selectedIndex.removeAt(selectedIndex.size - 1)
        render(selectedIndex.last().coerceAtLeast(0))
    }

    private fun render(focusIndex: Int) {
        val p = stack.last()
        titleView.text = p.title
        descView.text = ""
        list.removeAllViews()
        for (r in p.rows()) list.addView(rowView(r), LinearLayout.LayoutParams(-1, u.i(66f)).apply { bottomMargin = u.i(14f) })
        list.post {
            val v = list.getChildAt(focusIndex.coerceIn(0, list.childCount - 1))
            (if (v?.isFocusable == true) v else (0 until list.childCount).map { list.getChildAt(it) }.firstOrNull { it.isFocusable })?.requestFocus()
        }
    }

    private fun rowView(r: Row): View {
        if (r is Row.Label) return TextView(this).apply {
            text = r.text
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
                title.text = r.title
                value.text = r.value() ?: ""
                row.setOnClickListener { r.go(); if (stack.isNotEmpty()) value.text = r.value() ?: "" }
            }
            is Row.Toggle -> {
                title.text = r.title
                fun sync() { value.text = if (prefs.getBoolean(r.key, false)) "On" else "Off" }
                sync()
                row.setOnClickListener { prefs.edit().putBoolean(r.key, !prefs.getBoolean(r.key, false)).apply(); sync() }
                row.setOnFocusChangeListener { _, f -> if (f) descView.text = r.desc }
            }
            else -> {}
        }
        if (r is Row.Nav) row.setOnFocusChangeListener { _, f -> if (f) descView.text = "" }
        return row
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (stack.size > 1) pop() else finish() }
}
