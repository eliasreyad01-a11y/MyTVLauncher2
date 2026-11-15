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

    private var shader: Shader? = null
    private var shaderH = 0f
    private val rim = 2.2f * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val p = paint
        val h = height.toFloat()
        if (shader == null || shaderH != h) {
            shader = LinearGradient(0f, 0f, 0f, h, glass, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            shaderH = h
        }
        p.style = Paint.Style.FILL
        p.shader = shader
        p.setShadowLayer(18f * resources.displayMetrics.density, 0f, 4f * resources.displayMetrics.density, 0x66001828)
        super.onDraw(canvas)
        p.clearShadowLayer()
        p.shader = null
        val baseColor = p.color
        p.style = Paint.Style.STROKE
        p.color = (baseColor and 0xFFFFFF) or (0xF2 shl 24)
        p.strokeWidth = rim
        p.strokeJoin = Paint.Join.ROUND
        super.onDraw(canvas)
        p.color = baseColor
        p.style = Paint.Style.FILL
    }
}
