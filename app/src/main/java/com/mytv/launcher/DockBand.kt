package com.mytv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/** Draws the glass band behind the dock tiles (the first row of the home grid), hugging the tiles that exist. */
class DockBand(context: Context, private val adapter: AppAdapter) : RecyclerView.ItemDecoration() {
    private var band: Drawable? = GlassTheme.drawable(context, R.drawable.bg_dock)
    private var themeIdx = GlassTheme.key(context)
    private val pad = (10 * context.resources.displayMetrics.density).toInt()
    private val radius = 28 * context.resources.displayMetrics.density
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val rect = android.graphics.RectF()
    private val loc = IntArray(2)
    private var shaderReady = false
    private var sheen: android.graphics.Paint? = null
    private var sheenH = -1f

    /** Tiny wallpaper copy, scaled up smoothly = frosted glass. One shared shader, positioned in screen coordinates. */
    private fun prepare(context: Context) {
        shaderReady = true
        try {
            val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 16 }
            val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.wp_kirkjufell, o) ?: return
            val dm = context.resources.displayMetrics
            val sc = maxOf(dm.widthPixels / bmp.width.toFloat(), dm.heightPixels / bmp.height.toFloat())
            val m = android.graphics.Matrix()
            m.postScale(sc, sc)
            m.postTranslate((dm.widthPixels - bmp.width * sc) / 2f, (dm.heightPixels - bmp.height * sc) / 2f)
            val sh = android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            sh.setLocalMatrix(m)
            paint.shader = sh
            paint.isFilterBitmap = true
            applyTheme(context)
        } catch (_: Throwable) { paint.shader = null }
    }

    private fun applyTheme(context: Context) {
        val t = GlassTheme.current(context)
        paint.colorFilter = Frost.filter(t.tint, t.frostAlpha / 2)
        paint.alpha = (255 * GlassTheme.opacity(context)).toInt()
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (adapter.dockCount == 0) return
        val lm = parent.layoutManager ?: return
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var top = 0
        var bottom = 0
        for (p in 0 until adapter.dockCount) {
            if (adapter.items.getOrNull(p)?.packageName?.startsWith(AppAdapter.PAD_PREFIX) == true) continue
            val item = lm.findViewByPosition(p) ?: continue
            val tile: View = item.findViewById(R.id.tile) ?: continue
            val ox = item.left + item.translationX.toInt()
            left = minOf(left, ox + tile.left)
            right = maxOf(right, ox + tile.right)
            top = item.top + tile.top
            bottom = item.top + tile.bottom
        }
        if (left == Int.MAX_VALUE) return
        val drawable = band ?: return
        if (!shaderReady) prepare(parent.context)
        val ti = GlassTheme.key(parent.context)
        if (ti != themeIdx) { themeIdx = ti; band = GlassTheme.drawable(parent.context, R.drawable.bg_dock); applyTheme(parent.context) }
        if (paint.shader != null) {
            parent.getLocationOnScreen(loc)
            rect.set((left - pad).toFloat(), (top - pad).toFloat(), (right + pad).toFloat(), (bottom + pad).toFloat())
            c.save()
            c.translate(-loc[0].toFloat(), -loc[1].toFloat())
            rect.offset(loc[0].toFloat(), loc[1].toFloat())
            c.drawRoundRect(rect, radius, radius, paint)
            c.restore()
        }
        val bh = (bottom - top + 2 * pad).toFloat()
        if (sheen == null || sheenH != bh) { sheenH = bh; sheen = Frost.sheenPaint(bh) }
        c.save()
        c.translate((left - pad).toFloat(), (top - pad).toFloat())
        c.drawRoundRect(0f, 0f, (right - left + 2 * pad).toFloat(), bh, radius, radius, sheen!!)
        c.restore()
        drawable.setBounds(left - pad, top - pad, right + pad, bottom + pad)
        drawable.draw(c)
    }
}
