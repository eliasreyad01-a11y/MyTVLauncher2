package com.mytv.tvos

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** One slider bar for the remote: Left/Right change the value (holding the key goes faster), Up/Down move to the next bar. */
class ColorBar(context: Context, private val kind: String, val max: Int, private val colors: (ColorBar) -> IntArray) : View(context) {
    var value = 0
        set(v) { field = v.coerceIn(0, max); invalidate() }
    var onChange: () -> Unit = {}
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    init {
        isFocusable = true; isClickable = true
        setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) false
            else {
                val step = (if (kind == "hue") 4 else 3) * (1 + ev.repeatCount.coerceAtMost(8) / 2)
                when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { value -= step; onChange(); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { value += step; onChange(); true }
                    else -> false
                }
            }
        }
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat(); val w = width.toFloat()
        val pad = h * 0.28f
        r.set(h / 2, pad, w - h / 2, h - pad)
        val cols = colors(this)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(r.left, 0f, r.right, 0f, cols, null, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, p)
        p.shader = null
        val x = r.left + r.width() * value / max
        p.color = 0xFFFFFFFF.toInt(); c.drawCircle(x, h / 2, if (isFocused) h * 0.46f else h * 0.36f, p)
        p.color = 0x33000000; p.style = Paint.Style.STROKE; p.strokeWidth = 3f; c.drawCircle(x, h / 2, if (isFocused) h * 0.46f else h * 0.36f, p)
        if (isFocused) { p.color = 0xFFFFFFFF.toInt(); p.strokeWidth = 4f; c.drawRoundRect(r.left - 4, r.top - 4, r.right + 4, r.bottom + 4, r.height() / 2 + 4, r.height() / 2 + 4, p) }
    }
}

/** Any colour at all: hue, saturation and brightness bars with a live preview, all reachable with the remote. */
object ColorPicker {
    fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)

    fun show(a: Activity, initial: Int, title: String = "Custom Color", onPick: (Int) -> Unit) {
        val u = U(a)
        val hsv = FloatArray(3).also { Color.colorToHSV(initial, it) }
        val d = Dialog(a, android.R.style.Theme_Translucent_NoTitleBar)
        val root = FrameLayout(a)
        root.addView(View(a).apply { setBackgroundColor(0xE6101012.toInt()) }, FrameLayout.LayoutParams(-1, -1))

        fun tv(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(a).apply {
            text = s; setTextSize(TypedValue.COMPLEX_UNIT_PX, u.px(size)); setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
        }

        val swatch = View(a)
        val code = tv("", 44f, 0xFFFFFFFF.toInt(), true).apply { gravity = Gravity.CENTER }
        fun cur() = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], hsv[2])) or 0xFF000000.toInt()

        lateinit var hue: ColorBar; lateinit var sat: ColorBar; lateinit var bri: ColorBar
        fun sync() {
            hsv[0] = hue.value.toFloat(); hsv[1] = sat.value / 100f; hsv[2] = bri.value / 100f
            swatch.background = Glass.solid(cur(), u.px(40f))
            code.text = hex(cur())
            sat.invalidate(); bri.invalidate()
        }
        hue = ColorBar(a, "hue", 359) { IntArray(7) { i -> Color.HSVToColor(floatArrayOf(i * 60f, 1f, 1f)) } }.apply { value = hsv[0].toInt() }
        sat = ColorBar(a, "sat", 100) { intArrayOf(Color.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2])), Color.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2]))) }.apply { value = (hsv[1] * 100).toInt() }
        bri = ColorBar(a, "bri", 100) { intArrayOf(0xFF000000.toInt(), Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f))) }.apply { value = (hsv[2] * 100).toInt() }
        for (b in listOf(hue, sat, bri)) b.onChange = { sync() }

        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = Glass.glass(0x24FFFFFF, u.px(50f), 0.8f, true)
            setPadding(u.i(50f), u.i(36f), u.i(50f), u.i(36f))
        }
        col.addView(tv(L.t(title), 46f, 0xCCFFFFFF.toInt(), true).apply { gravity = Gravity.CENTER })
        col.addView(swatch, LinearLayout.LayoutParams(-1, u.i(120f)).apply { topMargin = u.i(20f) })
        col.addView(code, LinearLayout.LayoutParams(-1, -2).apply { topMargin = u.i(10f); bottomMargin = u.i(10f) })
        for ((name, bar) in listOf("Hue" to hue, "Saturation" to sat, "Brightness" to bri)) {
            col.addView(tv(L.t(name), 28f, 0xB3FFFFFF.toInt()))
            col.addView(bar, LinearLayout.LayoutParams(-1, u.i(64f)).apply { bottomMargin = u.i(8f) })
        }
        val buttons = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(label: String, action: () -> Unit) = TextView(a).apply {
            text = L.t(label); setTextSize(TypedValue.COMPLEX_UNIT_PX, u.px(32f)); setTextColor(Glass.text()); gravity = Gravity.CENTER
            isFocusable = true; isClickable = true
            background = Glass.rowBg(u, 33f, 0x38FFFFFF, true)
            setOnClickListener { action() }
        }
        buttons.addView(button("Done") { onPick(cur()); d.dismiss() }, LinearLayout.LayoutParams(0, u.i(70f), 1f).apply { marginEnd = u.i(10f) })
        buttons.addView(button("Cancel") { d.dismiss() }, LinearLayout.LayoutParams(0, u.i(70f), 1f))
        col.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = u.i(18f) })
        root.addView(col, FrameLayout.LayoutParams(u.i(800f), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        sync()
        d.setContentView(root)
        d.window?.setLayout(-1, -1)
        d.setOnShowListener { hue.requestFocus() }
        d.show()
    }
}
