package com.mytv.tvos

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Everything the user can change about the clock. Stored in the launcher's settings file; [save] and [load] are the only readers/writers. */
class ClockCfg(
    var kind: String = "digital_thin",
    var pct: Int = 100,
    var color: Int = 0xFFFFFFFF.toInt(),
    var font: String = "light",
    var seconds: Boolean = false,
    var date: Boolean = true,
    var glass: Boolean = false,
    var pos: String = "left",
    var greeting: Boolean = true
) {
    fun copy() = ClockCfg(kind, pct, color, font, seconds, date, glass, pos, greeting)

    /** [prefix] is "" for the home clock and "sleep_" for the sleep screen clock: each has its own settings. */
    fun save(sp: SharedPreferences, prefix: String = "") {
        sp.edit().putString(prefix + "clock_kind", kind).putInt(prefix + "clock_pct", pct).putInt(prefix + "clock_color", color).putString(prefix + "clock_font", font)
            .putBoolean(prefix + "clock_seconds", seconds).putBoolean(prefix + "clock_date", date).putBoolean(prefix + "clock_glass", glass)
            .putString(prefix + "clock_pos", pos).putBoolean(prefix + "clock_greeting", greeting).apply()
    }

    companion object {
        val KINDS = listOf("digital_thin", "digital_big", "analog_hands", "analog_simple", "flip", "minimal", "glass_pill")
        val KIND_LABEL = mapOf(
            "digital_thin" to "Thin Digital", "digital_big" to "Big Digital", "analog_hands" to "Analog with Hands",
            "analog_simple" to "Simple Analog", "flip" to "Flip", "minimal" to "Minimal", "glass_pill" to "Glass Digital"
        )
        val SIZES = listOf(70, 85, 100, 115, 130, 150)
        val SIZES_SLEEP = listOf(120, 150, 180, 210, 250, 300)
        val FONTS = listOf("thin", "light", "regular", "bold", "serif", "condensed")
        val FONT_LABEL = mapOf("thin" to "Thin", "light" to "Light", "regular" to "Regular", "bold" to "Bold", "serif" to "Serif", "condensed" to "Condensed")
        val COLORS = listOf(
            0xFFFFFFFF.toInt() to "White", 0xFFD6DCE6.toInt() to "Silver", 0xFFF5C76B.toInt() to "Gold",
            0xFF8FD3FF.toInt() to "Ice Blue", 0xFF9BF0C8.toInt() to "Mint", 0xFFFFA9BD.toInt() to "Rose", 0xFFFFB366.toInt() to "Orange"
        )
        val POSITIONS = listOf("left", "center", "right")
        val POS_LABEL = mapOf("left" to "Left", "center" to "Center", "right" to "Right")

        /** The sleep screen clock: bigger and thinner by default. */
        fun loadSleep(sp: SharedPreferences): ClockCfg {
            val d = ClockCfg("digital_thin", 180, 0xFFFFFFFF.toInt(), "thin", false, true, false, "center", true)
            val k = sp.getString("sleep_clock_kind", d.kind) ?: d.kind
            return ClockCfg(
                kind = if (k in KINDS) k else d.kind,
                pct = sp.getInt("sleep_clock_pct", d.pct).coerceIn(60, 400),
                color = sp.getInt("sleep_clock_color", d.color),
                font = (sp.getString("sleep_clock_font", d.font) ?: d.font).let { if (it in FONTS) it else d.font },
                seconds = sp.getBoolean("sleep_clock_seconds", d.seconds),
                date = sp.getBoolean("sleep_clock_date", d.date),
                glass = sp.getBoolean("sleep_clock_glass", d.glass),
                pos = "center",
                greeting = sp.getBoolean("sleep_clock_greeting", true)
            )
        }

        fun load(sp: SharedPreferences): ClockCfg {
            // The older two settings (font style light/regular/bold and size m/l/xl) become the defaults of the new ones.
            val oldFont = sp.getString("clock_style", "light") ?: "light"
            val oldPct = when (sp.getString("clock_size", "l")) { "m" -> 85; "xl" -> 130; else -> 100 }
            val kind = sp.getString("clock_kind", "digital_thin") ?: "digital_thin"
            return ClockCfg(
                kind = if (kind in KINDS) kind else "digital_thin",
                pct = sp.getInt("clock_pct", oldPct).coerceIn(50, 200),
                color = sp.getInt("clock_color", 0xFFFFFFFF.toInt()),
                font = (sp.getString("clock_font", oldFont) ?: oldFont).let { if (it in FONTS) it else "light" },
                seconds = sp.getBoolean("clock_seconds", false),
                date = sp.getBoolean("clock_date", true),
                glass = sp.getBoolean("clock_glass", false),
                pos = (sp.getString("clock_pos", "left") ?: "left").let { if (it in POSITIONS) it else "left" }
            )
        }
    }
}

/** One view that draws every clock design; it only paints (no layout work per tick) and ticks once a second or once a minute. */
class ClockView(context: Context, private val u: U) : View(context) {

    var cfg = ClockCfg()
        set(v) { field = v; configChanged() }
    var tz: TimeZone = TimeZone.getDefault()
    var is24 = true
        set(v) { if (field != v) { field = v; invalidate() } }

    private val cal = Calendar.getInstance()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var bgKey = ""

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            val now = System.currentTimeMillis()
            val every = if (cfg.seconds || cfg.kind.startsWith("analog")) 1000L else 60_000L
            postDelayed(this, every - (now % every) + 15)
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); removeCallbacks(tick); post(tick) }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }

    private fun configChanged() {
        bgKey = ""
        requestLayout()
        invalidate()
        removeCallbacks(tick)
        if (isAttachedToWindow) post(tick)
    }

    private fun typeface(): Typeface = when (cfg.font) {
        "thin" -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
        "regular" -> Typeface.create("sans-serif", Typeface.NORMAL)
        "bold" -> Typeface.create("sans-serif-medium", Typeface.BOLD)
        "serif" -> Typeface.create("serif", Typeface.NORMAL)
        "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        else -> Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private val s get() = cfg.pct / 100f * fit
    private val isAnalog get() = cfg.kind.startsWith("analog")
    private val isFlip get() = cfg.kind == "flip"
    private val glassBg get() = cfg.glass || cfg.kind == "glass_pill"

    /** Text size (design px) of the digital designs at 100 %. */
    private fun baseText() = when (cfg.kind) { "digital_big" -> 150f; "minimal" -> 76f; "glass_pill" -> 96f; else -> 138f }

    private fun timeText(c: Calendar): String {
        val h = if (is24) c.get(Calendar.HOUR_OF_DAY) else (c.get(Calendar.HOUR).let { if (it == 0) 12 else it })
        val m = c.get(Calendar.MINUTE)
        return (if (is24) String.format(java.util.Locale.ENGLISH, "%02d", h) else "$h") + ":" + String.format(java.util.Locale.ENGLISH, "%02d", m)
    }

    private fun secText(c: Calendar) = ":" + String.format(java.util.Locale.ENGLISH, "%02d", c.get(Calendar.SECOND))

    private val tileW get() = u.px(132f * s)
    private val tileH get() = u.px(176f * s)
    private val gap get() = u.px(10f * s)
    private val groupGap get() = u.px(34f * s)

    private var fit = 1f

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        fit = 1f
        var (w, h) = naturalSize()
        // Never wider than 1700 or taller than 900 design pixels, however large the size: nothing is cut off at the screen edge.
        val lim = u.px(1700f); val limH = u.px(900f)
        if (w > lim || h > limH) { fit = minOf(lim / w, limH / h); val n = naturalSize(); w = n.first; h = n.second }
        setMeasuredDimension(w.toInt() + 1, h.toInt() + 1)
    }

    private fun naturalSize(): Pair<Float, Float> {
        val w: Float
        val h: Float
        when {
            isAnalog -> { w = u.px(300f * s); h = w }
            isFlip -> { w = tileW * 4 + gap * 2 + groupGap + (if (cfg.seconds) u.px(70f * s) else 0f); h = tileH }
            else -> {
                p.typeface = typeface()
                val size = u.px(baseText() * s)
                p.textSize = size
                var tw = p.measureText("00:00")
                if (cfg.seconds) { p.textSize = size * 0.45f; tw += p.measureText(":00") }
                val pad = if (glassBg) u.px(40f * s) else 0f
                w = tw + pad * 2
                h = size * (if (glassBg) 1.05f else 1.12f) + (if (glassBg) pad * 0.5f else 0f)
            }
        }
        return w to h
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); bgKey = "" }

    private fun ensureBackground() {
        val key = "$glassBg|${cfg.kind}|$width|$height"
        if (key == bgKey) return
        bgKey = key
        background = if (glassBg && !isFlip && width > 0) Glass.glass(0x24FFFFFF, if (isAnalog) width / 2f else height / 2f, 1f, true) else null
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) { super.onLayout(changed, l, t, r, b); ensureBackground() }

    override fun onDraw(c: Canvas) {
        cal.timeZone = tz
        cal.timeInMillis = System.currentTimeMillis()
        when {
            isAnalog -> drawAnalog(c)
            isFlip -> drawFlip(c)
            else -> drawDigital(c)
        }
    }

    private fun drawDigital(c: Canvas) {
        val size = u.px(baseText() * s)
        p.typeface = typeface()
        p.style = Paint.Style.FILL
        p.color = cfg.color
        p.textSize = size
        p.textAlign = Paint.Align.LEFT
        p.setShadowLayer(if (glassBg) 0f else u.px(10f), 0f, u.px(2f), 0x66000000)
        val t = timeText(cal)
        val mainW = p.measureText(t)
        var total = mainW
        val sec = if (cfg.seconds) secText(cal) else null
        val secSize = size * 0.45f
        if (sec != null) { p.textSize = secSize; total += p.measureText(sec); p.textSize = size }
        val x = (width - total) / 2f
        val base = height / 2f - (p.ascent() + p.descent()) / 2f
        c.drawText(t, x, base, p)
        if (sec != null) { p.textSize = secSize; p.alpha = 190; c.drawText(sec, x + mainW, base, p) }
        p.clearShadowLayer()
        p.alpha = 255
    }

    private fun drawFlip(c: Canvas) {
        val t = String.format(java.util.Locale.ENGLISH, "%02d", if (is24) cal.get(Calendar.HOUR_OF_DAY) else cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }) +
            String.format(java.util.Locale.ENGLISH, "%02d", cal.get(Calendar.MINUTE))
        val tw = tileW; val th = tileH; val r = u.px(20f * s)
        var x = 0f
        p.typeface = typeface()
        for (i in 0 until 4) {
            rect.set(x, 0f, x + tw, th)
            if (cfg.glass) {
                p.shader = null; p.style = Paint.Style.FILL; p.color = 0x2EFFFFFF
                c.drawRoundRect(rect, r, r, p)
                p.shader = LinearGradient(0f, 0f, 0f, th * 0.55f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, r, r, p)
                p.shader = null
                p.style = Paint.Style.STROKE; p.strokeWidth = u.px(2f); p.color = 0x59FFFFFF
                c.drawRoundRect(rect, r, r, p)
            } else {
                p.style = Paint.Style.FILL
                p.shader = LinearGradient(0f, 0f, 0f, th, 0xFF34343A.toInt(), 0xFF18181B.toInt(), Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, r, r, p)
                p.shader = null
                p.style = Paint.Style.STROKE; p.strokeWidth = u.px(2f); p.color = 0x33FFFFFF
                c.drawRoundRect(rect, r, r, p)
            }
            p.style = Paint.Style.FILL
            p.color = cfg.color
            p.textSize = th * 0.62f
            p.textAlign = Paint.Align.CENTER
            c.drawText(t.substring(i, i + 1), x + tw / 2, th / 2f - (p.ascent() + p.descent()) / 2f, p)
            // The split line of a flip card, and a soft light on its upper half.
            p.color = 0xB3000000.toInt()
            c.drawRect(x + u.px(3f), th / 2f - u.px(1.5f), x + tw - u.px(3f), th / 2f + u.px(1.5f), p)
            x += tw + (if (i == 1) groupGap else gap)
        }
        if (cfg.seconds) {
            p.color = cfg.color; p.alpha = 190; p.textSize = th * 0.22f; p.textAlign = Paint.Align.LEFT
            c.drawText(secText(cal).drop(1), x + u.px(8f), th - u.px(10f), p)
            p.alpha = 255
        }
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawAnalog(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val rad = min(width, height) / 2f
        val withNumbers = cfg.kind == "analog_hands"
        if (!glassBg) {
            p.shader = null; p.style = Paint.Style.FILL; p.color = 0x22FFFFFF
            c.drawCircle(cx, cy, rad - u.px(2f), p)
            p.style = Paint.Style.STROKE; p.strokeWidth = u.px(2.5f); p.color = 0x44FFFFFF
            c.drawCircle(cx, cy, rad - u.px(3f), p)
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = cfg.color
        for (i in 0 until 12) {
            val a = i * PI / 6
            val major = i % 3 == 0
            val r1 = rad * (if (major) 0.80f else 0.86f)
            val r2 = rad * 0.92f
            p.strokeWidth = u.px(if (major) 5f else 3f) * s
            p.alpha = if (major) 235 else 150
            c.drawLine(cx + (sin(a) * r1).toFloat(), cy - (cos(a) * r1).toFloat(), cx + (sin(a) * r2).toFloat(), cy - (cos(a) * r2).toFloat(), p)
        }
        p.alpha = 255
        if (withNumbers) {
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.typeface = typeface()
            p.textSize = rad * 0.26f
            p.color = cfg.color
            for ((i, n) in listOf(0 to "12", 3 to "3", 6 to "6", 9 to "9")) {
                val a = i * PI / 6
                val rr = rad * 0.64f
                c.drawText(n, cx + (sin(a) * rr).toFloat(), cy - (cos(a) * rr).toFloat() - (p.ascent() + p.descent()) / 2f, p)
            }
        }
        val h = (cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f) / 12f
        val m = (cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f) / 60f
        val sec = (cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f) / 60f
        p.style = Paint.Style.STROKE
        p.color = cfg.color
        hand(c, cx, cy, h, rad * 0.48f, u.px(10f) * s)
        hand(c, cx, cy, m, rad * 0.72f, u.px(6f) * s)
        if (cfg.seconds || cfg.kind == "analog_hands") {
            p.color = if (cfg.kind == "analog_hands") 0xFFFFB347.toInt() else cfg.color
            p.alpha = 235
            hand(c, cx, cy, sec, rad * 0.78f, u.px(3f) * s)
            p.alpha = 255
        }
        p.style = Paint.Style.FILL
        p.color = cfg.color
        c.drawCircle(cx, cy, u.px(9f) * s, p)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, frac: Float, len: Float, w: Float) {
        val a = frac * 2 * PI
        p.strokeWidth = w
        c.drawLine(cx - (sin(a) * len * 0.12f).toFloat(), cy + (cos(a) * len * 0.12f).toFloat(), cx + (sin(a) * len).toFloat(), cy - (cos(a) * len).toFloat(), p)
    }
}

/**
 * The clock page: a live preview and one row per setting. Nothing is written until "Save"; "Cancel" leaves the clock as it was.
 * Remote: OK or Right = next value, Left = previous value, on every row.
 */
object ClockCustomizer {

    fun show(a: Activity, sp: SharedPreferences, onSaved: () -> Unit, prefix: String = "", title: String = "Clock") {
        val u = U(a)
        val prefs = Prefs(a)
        val sleep = prefix == "sleep_"
        val work = if (sleep) ClockCfg.loadSleep(sp) else ClockCfg.load(sp)
        val sizes = if (sleep) ClockCfg.SIZES_SLEEP else ClockCfg.SIZES
        var fmt = prefs.timeFormat

        val d = Dialog(a, android.R.style.Theme_Translucent_NoTitleBar)
        val root = FrameLayout(a)
        root.addView(View(a).apply { setBackgroundColor(0xCC101012.toInt()) }, FrameLayout.LayoutParams(-1, -1))

        val preview = ClockView(a, u)
        preview.tz = CityClock.timeZone(a)
        val dateView = TextView(a).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u.px(36f)); setTextColor(0xF2FFFFFF.toInt()); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val stage = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Glass.glass(0x1AFFFFFF, u.px(58f), 0.6f, true)
            setPadding(u.i(30f), u.i(30f), u.i(30f), u.i(30f))
            addView(preview)
            addView(dateView, LinearLayout.LayoutParams(-2, -2).apply { topMargin = u.i(14f) })
        }
        root.addView(stage, FrameLayout.LayoutParams(u.i(900f), u.i(780f), Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = u.i(70f) })

        val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        val title = TextView(a).apply {
            text = L.t(title); setTextSize(TypedValue.COMPLEX_UNIT_PX, u.px(48f)); typeface = Typeface.DEFAULT_BOLD
            setTextColor(0x99FFFFFF.toInt()); gravity = Gravity.CENTER
        }

        fun refreshPreview() {
            preview.is24 = when (fmt) { "12" -> false; "24" -> true; else -> android.text.format.DateFormat.is24HourFormat(a) }
            preview.cfg = work.copy().also { if (sleep) it.pct = (it.pct * 0.42f).toInt().coerceAtLeast(50) }
            dateView.visibility = if (work.date) View.VISIBLE else View.GONE
            dateView.setTextColor(work.color)
            dateView.text = java.text.SimpleDateFormat("EEEE, d MMMM", L.locale()).apply { timeZone = CityClock.timeZone(a) }.format(java.util.Date())
        }

        class RowDef(val title: String, val value: () -> String, val step: (Int) -> Unit)

        fun <T> cycle(items: List<T>, cur: T, dir: Int): T = items[(items.indexOf(cur).let { if (it < 0) 0 else it } + dir + items.size) % items.size]

        val defs = listOf(
            RowDef("Style", { L.t(ClockCfg.KIND_LABEL[work.kind] ?: work.kind) }) { dir -> work.kind = cycle(ClockCfg.KINDS, work.kind, dir) },
            RowDef("Size", { "${work.pct}%" }) { dir -> work.pct = cycle(sizes, work.pct.let { v -> sizes.minByOrNull { Math.abs(it - v) } ?: 100 }, dir) },
            RowDef("Font", { L.t(ClockCfg.FONT_LABEL[work.font] ?: work.font) }) { dir -> work.font = cycle(ClockCfg.FONTS, work.font, dir) },
            RowDef("Color", { L.t(ClockCfg.COLORS.firstOrNull { it.first == work.color }?.second ?: "Custom") }) { dir ->
                val i = ClockCfg.COLORS.indexOfFirst { it.first == work.color }.let { if (it < 0) 0 else it }
                work.color = ClockCfg.COLORS[(i + dir + ClockCfg.COLORS.size) % ClockCfg.COLORS.size].first
            },
            RowDef("Position", { L.t(ClockCfg.POS_LABEL[work.pos] ?: work.pos) }) { dir -> if (!sleep) work.pos = cycle(ClockCfg.POSITIONS, work.pos, dir) },
            RowDef("Seconds", { L.t(if (work.seconds) "On" else "Off") }) { work.seconds = !work.seconds },
            RowDef("Show Date", { L.t(if (work.date) "On" else "Off") }) { work.date = !work.date },
            RowDef("Greeting", { L.t(if (work.greeting) "On" else "Off") }) { if (sleep) work.greeting = !work.greeting },
            RowDef("Glass Look", { L.t(if (work.glass || work.kind == "glass_pill") "On" else "Off") }) { if (work.kind != "glass_pill") work.glass = !work.glass },
            RowDef("Time Format", { L.t(when (fmt) { "12" -> "12-Hour"; "24" -> "24-Hour"; else -> "System" }) }) { dir ->
                fmt = cycle(listOf("24", "12", "system"), fmt, dir)
            }
        )

        val rows = ArrayList<View>()
        val shown = defs.filter { it.title != "Greeting" || sleep }
        for (def in shown) {
            val t = TextView(a).apply { text = L.t(def.title); setTextSize(TypedValue.COMPLEX_UNIT_PX, u.px(32f)); setTextColor(Glass.text()); isDuplicateParentStateEnabled = true }
            val v = TextView(a).apply { setTextSize(TypedValue.COMPLEX_UNIT_PX, u.px(30f)); setTextColor(Glass.dim()); isDuplicateParentStateEnabled = true }
            val row = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                isFocusable = true; isClickable = true
                setPadding(u.i(30f), 0, u.i(30f), 0)
                background = Glass.rowBg(u, 33f, 0x24FFFFFF, true)
                addView(t, LinearLayout.LayoutParams(0, -2, 1f)); addView(v)
            }
            fun sync() { v.text = def.value() }
            fun move(dir: Int) { def.step(dir); sync(); refreshPreview() }
            sync()
            row.setOnClickListener { move(1) }
            row.setOnKeyListener { _, code, ev ->
                if (ev.action != KeyEvent.ACTION_DOWN) false
                else when (code) { KeyEvent.KEYCODE_DPAD_LEFT -> { move(-1); true }; KeyEvent.KEYCODE_DPAD_RIGHT -> { move(1); true }; else -> false }
            }
            rows.add(row)
            list.addView(row, LinearLayout.LayoutParams(-1, u.i(66f)).apply { bottomMargin = u.i(10f) })
        }

        fun button(label: String, action: () -> Unit) = TextView(a).apply {
            text = L.t(label); setTextSize(TypedValue.COMPLEX_UNIT_PX, u.px(32f)); setTextColor(Glass.text()); gravity = Gravity.CENTER
            isFocusable = true; isClickable = true
            background = Glass.rowBg(u, 33f, 0x38FFFFFF, true)
            setOnClickListener { action() }
        }

        val bar = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(button("Save") {
            work.save(sp, prefix); prefs.timeFormat = fmt; d.dismiss(); onSaved()
        }, LinearLayout.LayoutParams(0, u.i(70f), 1f).apply { marginEnd = u.i(10f) })
        bar.addView(button("Reset") {
            val def = if (sleep) ClockCfg("digital_thin", 180, 0xFFFFFFFF.toInt(), "thin", false, true, false, "center", true) else ClockCfg()
            work.kind = def.kind; work.pct = def.pct; work.color = def.color; work.font = def.font
            work.seconds = def.seconds; work.date = def.date; work.glass = def.glass; work.pos = def.pos; work.greeting = def.greeting
            fmt = "24"
            shown.forEachIndexed { i, _ -> ((rows[i] as LinearLayout).getChildAt(1) as TextView).text = shown[i].value() }
            refreshPreview()
        }, LinearLayout.LayoutParams(0, u.i(70f), 1f).apply { marginEnd = u.i(10f) })
        bar.addView(button("Cancel") { d.dismiss() }, LinearLayout.LayoutParams(0, u.i(70f), 1f))
        list.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = u.i(14f) })

        val right = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(title, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = u.i(18f) })
            addView(list)
        }
        root.addView(right, FrameLayout.LayoutParams(u.i(789f), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = u.i(70f) })

        refreshPreview()
        d.setContentView(root, ViewGroup.LayoutParams(-1, -1))
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(-1, -1)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        d.setOnShowListener { rows.firstOrNull()?.requestFocus() }
        d.show()
    }
}
