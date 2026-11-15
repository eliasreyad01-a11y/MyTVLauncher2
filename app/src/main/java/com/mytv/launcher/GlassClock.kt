package com.mytv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.TextClock

/**
 * A clock drawn like frosted glass: a translucent gradient fill (tinted by the wallpaper) with a bright rim,
 * so it reads on any picture. The rim comes from the text colour, the fill from [glass] (top, middle, bottom).
 */
class GlassClock @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextClock(context, attrs) {

    var glass: IntArray = intArrayOf(0xB4DFF4FE.toInt(), 0x96A0E0F8.toInt(), 0x88B4EAF8.toInt())
        set(value) {
            field = value
            shader = null
            invalidate()
        }

    /** Digits are converted to Latin the moment the text is set, so a minute change never flashes Arabic digits first. */
    override fun setText(text: CharSequence?, type: BufferType?) {
        if (text == null) { super.setText(text, type); return }
        val sb = StringBuilder(text.length)
        for (ch in text) sb.append(
            when (ch) {
                in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                else -> ch
            }
        )
        super.setText(sb, type)
    }

    private var shader: Shader? = null
    private var shaderH = 0f
    private val rim = 2.6f * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val p = paint
        val h = height.toFloat()
        if (shader == null || shaderH != h) {
            shader = LinearGradient(0f, 0f, 0f, h, glass, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            shaderH = h
        }
        p.style = Paint.Style.FILL
        p.shader = shader
        super.onDraw(canvas)
        p.shader = null
        val baseColor = p.color
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        // Soft dark halo (several thin strokes instead of setShadowLayer, which some TV GPUs draw as a pale box),
        // then a thin dark outline under the bright rim so the digits stay readable on pale sky.
        val d = resources.displayMetrics.density
        p.color = 0x14102030
        p.strokeWidth = rim + 12f * d
        super.onDraw(canvas)
        p.color = 0x1E102030
        p.strokeWidth = rim + 7f * d
        super.onDraw(canvas)
        p.color = 0x59102030
        p.strokeWidth = rim + 2.6f * d
        super.onDraw(canvas)
        p.color = (baseColor and 0xFFFFFF) or (0xFF shl 24)
        p.strokeWidth = rim
        super.onDraw(canvas)
        p.color = baseColor
        p.style = Paint.Style.FILL
    }
}
