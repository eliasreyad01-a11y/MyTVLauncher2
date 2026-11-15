package com.mytv.launcher

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View

/**
 * Frosted-glass fill: a small, smoothly scaled copy of the wallpaper drawn in screen coordinates, so the glass shows the
 * (blurred) picture that is really behind it. One shared bitmap shader; the canvas is translated instead of the shader moved.
 */
class FrostDrawable(private val ctx: Context, private val radiusDp: Float, private val overlayAlpha: Int = 0x22) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val loc = IntArray(2)
    private var themeIdx = -1

    init { paint.isFilterBitmap = true }

    private fun ensure() {
        val idx = GlassTheme.index(ctx)
        if (paint.shader == null) paint.shader = Frost.shader(ctx)
        if (idx != themeIdx) {
            themeIdx = idx
            val t = GlassTheme.current(ctx)
            paint.colorFilter = PorterDuffColorFilter((overlayAlpha shl 24) or (t.tint and 0xFFFFFF), PorterDuff.Mode.SRC_OVER)
        }
    }

    override fun draw(canvas: Canvas) {
        var cb: Callback? = callback
        while (cb is Drawable) cb = cb.callback
        val v = cb as? View ?: return
        ensure()
        if (paint.shader == null) return
        v.getLocationOnScreen(loc)
        val r = radiusDp * ctx.resources.displayMetrics.density
        canvas.save()
        canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
        rect.set(bounds)
        rect.offset(loc[0].toFloat(), loc[1].toFloat())
        canvas.drawRoundRect(rect, r, r, paint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

object Frost {
    private var bmp: Bitmap? = null
    fun shader(ctx: Context): Shader? {
      return try {
        val b = bmp ?: run {
            val o = BitmapFactory.Options().apply { inSampleSize = 16 }
            BitmapFactory.decodeResource(ctx.resources, R.drawable.wp_kirkjufell, o)?.also { bmp = it }
        } ?: return null
        val dm = ctx.resources.displayMetrics
        val sc = maxOf(dm.widthPixels / b.width.toFloat(), dm.heightPixels / b.height.toFloat())
        val m = Matrix()
        m.postScale(sc, sc)
        m.postTranslate((dm.widthPixels - b.width * sc) / 2f, (dm.heightPixels - b.height * sc) / 2f)
        BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { it.setLocalMatrix(m) }
      } catch (_: Throwable) { null }
    }
}
