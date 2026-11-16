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
    private var sheen: Paint? = null
    private var sheenH = -1f

    init { paint.isFilterBitmap = true }

    private var sourceVersion = -1

    private fun ensure() {
        // The picture behind the glass changed (custom wallpaper loaded / reset): rebuild the shader and the tint.
        if (paint.shader == null || sourceVersion != Frost.version) {
            sourceVersion = Frost.version
            paint.shader = Frost.shader(ctx)
            themeIdx = -1
        }
        val idx = GlassTheme.key(ctx)
        if (idx != themeIdx) {
            themeIdx = idx
            paint.alpha = (255 * GlassTheme.opacity(ctx)).toInt()
            val t = GlassTheme.current(ctx)
            paint.colorFilter = Frost.filter(t.tint, overlayAlpha)
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
        if (sheen == null || sheenH != bounds.height().toFloat()) {
            sheenH = bounds.height().toFloat()
            sheen = Frost.sheenPaint(sheenH)
        }
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.drawRoundRect(0f, 0f, bounds.width().toFloat(), sheenH, r, r, sheen!!)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

object Frost {
    private var bmp: Bitmap? = null
    private var lum = -1f
    private var custom = false

    /** Bumped whenever the picture behind the glass changes, so glass surfaces know to rebuild their shader. */
    @Volatile var version = 0
        private set

    /**
     * The glass shows the picture that is really on the home screen. A user photo (or slideshow frame) is passed in here;
     * null returns to the built-in photo. Main thread only. Does nothing while the built-in photo is already in use.
     */
    fun setSource(src: Bitmap?) {
        val small = if (src == null) null else shrink(src)
        if (small == null && !custom) return
        bmp = small
        custom = small != null
        lum = -1f
        version++
    }

    /** Smooth ~1/16 scale copy (repeated halving, so no aliasing); null if the bitmap can't be read. */
    private fun shrink(src: Bitmap): Bitmap? = try {
        var cur = src
        while (cur.width / 2 >= 120 && cur.height / 2 >= 68) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        if (cur === src) src.copy(Bitmap.Config.ARGB_8888, false) else cur
    } catch (_: Throwable) { null }

    /** The small picture the glass is made from (custom wallpaper, or the built-in photo). */
    fun bitmap(ctx: Context): Bitmap? = bmp ?: try {
        val o = BitmapFactory.Options().apply { inSampleSize = 16 }
        BitmapFactory.decodeResource(ctx.resources, R.drawable.wp_kirkjufell, o)?.also { bmp = it }
    } catch (_: Throwable) { null }

    /** Average brightness (0..1) of the wallpaper, used to adapt the glass. */
    fun luminance(ctx: Context): Float {
        if (lum >= 0f) return lum
        val b = bitmap(ctx) ?: return 0.5f
        var sum = 0.0
        var n = 0
        val px = IntArray(b.width)
        for (y in 0 until b.height step 2) {
            b.getPixels(px, 0, b.width, 0, y, b.width, 1)
            for (x in px.indices step 2) {
                val c = px[x]
                sum += (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255.0
                n++
            }
        }
        lum = if (n == 0) 0.5f else (sum / n).toFloat()
        return lum
    }

    /** Soft light reflection across the upper part of a glass surface (the 'lit from above' look). */
    fun sheenPaint(height: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, 0f, height * 0.6f, 0x78FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    }

    /** Brighter, slightly more saturated frost: pale clear glass like the iOS / tvOS reference, not a grey plate. */
    fun filter(tint: Int, overlayAlpha: Int): ColorFilter {
        val a = (overlayAlpha / 255f * 2.2f).coerceIn(0.10f, 0.45f)
        val cm = ColorMatrix().apply { setSaturation(1.45f) }
        val mix = ColorMatrix(floatArrayOf(
            1f - a, 0f, 0f, 0f, Color.red(tint) * a + 16f,
            0f, 1f - a, 0f, 0f, Color.green(tint) * a + 16f,
            0f, 0f, 1f - a, 0f, Color.blue(tint) * a + 16f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(mix)
        return ColorMatrixColorFilter(cm)
    }
    fun shader(ctx: Context): Shader? {
      return try {
        val b = bitmap(ctx) ?: return null
        val dm = ctx.resources.displayMetrics
        val sc = maxOf(dm.widthPixels / b.width.toFloat(), dm.heightPixels / b.height.toFloat())
        val m = Matrix()
        m.postScale(sc, sc)
        m.postTranslate((dm.widthPixels - b.width * sc) / 2f, (dm.heightPixels - b.height * sc) / 2f)
        BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { it.setLocalMatrix(m) }
      } catch (_: Throwable) { null }
    }
}
