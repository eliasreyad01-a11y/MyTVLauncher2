package com.mytv.tvos

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Everything is designed on a 1920 x 1080 canvas; this scales those pixels to the real screen. */
class U(ctx: Context) {
    val s: Float = ctx.resources.displayMetrics.widthPixels / 1920f
    fun px(v: Float) = v * s
    fun i(v: Float) = (v * s).toInt()
}

object Glass {
    const val WHITE = 0xFFFFFFFF.toInt()

    private val FOCUSED = intArrayOf(android.R.attr.state_focused)

    fun states(focused: Int, normal: Int) = ColorStateList(arrayOf(FOCUSED, intArrayOf()), intArrayOf(focused, normal))
    fun text() = states(0xFF000000.toInt(), 0xF2FFFFFF.toInt())
    fun dim() = states(0x8C000000.toInt(), 0x80FFFFFF.toInt())

    private fun shape(color: Int, r: Float) = GradientDrawable().apply { setColor(color); cornerRadius = r }

    /** Flat translucent pill that turns solid white when focused (settings rows, menu rows, control center tiles). */
    fun rowBg(u: U, radius: Float = 33f, normal: Int = 0x1FFFFFFF): StateListDrawable = StateListDrawable().apply {
        addState(FOCUSED, shape(WHITE, u.px(radius)))
        addState(intArrayOf(), shape(normal, u.px(radius)))
    }

    fun solid(color: Int, r: Float) = shape(color, r)
}

/** Two-tone silver gear, drawn in code so it scales cleanly (the Settings icon). */
class GearDrawable : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun getIntrinsicWidth() = 512
    override fun getIntrinsicHeight() = 512

    override fun draw(c: Canvas) {
        val b = bounds
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = min(b.width(), b.height()) / 2f * 0.97f
        val teeth = 18
        val step = (2 * PI / teeth).toFloat()
        path.reset()
        for (i in 0 until teeth) {
            val a = i * step
            val pts = listOf(a + 0.05f * step to 0.84f, a + 0.22f * step to 1f, a + 0.62f * step to 1f, a + 0.79f * step to 0.84f)
            for ((ang, k) in pts) {
                val x = cx + cos(ang) * r * k
                val y = cy + sin(ang) * r * k
                if (path.isEmpty) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        path.close()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFF1F1F4.toInt(), 0xFF8D8D93.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.shader = RadialGradient(cx, cy, r * 0.7f, intArrayOf(0xFF55555A.toInt(), 0xFF2E2E31.toInt()), floatArrayOf(0.4f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 0.66f, p)
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFE9E9EC.toInt(), 0xFF98989E.toInt(), Shader.TileMode.CLAMP)
        for (k in 0 until 3) {
            c.save()
            c.rotate(k * 120f - 90f, cx, cy)
            c.drawRoundRect(cx, cy - r * 0.075f, cx + r * 0.66f, cy + r * 0.075f, r * 0.05f, r * 0.05f, p)
            c.restore()
        }
        c.drawCircle(cx, cy, r * 0.2f, p)
        p.shader = null
        p.color = 0xFF3A3A3E.toInt()
        c.drawCircle(cx, cy, r * 0.07f, p)
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A television drawn with two rectangles: the picture on the settings squircle (where tvOS shows the device). */
class TvGlyph(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66101810 }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawRoundRect(RectF(w * 0.1f, h * 0.12f, w * 0.9f, h * 0.7f), w * 0.07f, w * 0.07f, p)
        c.drawRoundRect(RectF(w * 0.4f, h * 0.72f, w * 0.6f, h * 0.86f), w * 0.03f, w * 0.03f, p)
        c.drawRoundRect(RectF(w * 0.25f, h * 0.84f, w * 0.75f, h * 0.92f), w * 0.04f, w * 0.04f, p)
    }
}

/** Round profile picture of the capsule (also the button that opens Control Center). */
class AvatarView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        p.shader = LinearGradient(0f, 0f, w, w, 0xFFFFA63D.toInt(), 0xFF3D8BFF.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(w / 2, w / 2, w / 2, p)
        p.shader = null
        p.color = 0xE6FFFFFF.toInt()
        c.drawCircle(w / 2, w * 0.38f, w * 0.17f, p)
        c.save()
        c.clipRect(0f, 0f, w, w)
        c.drawOval(RectF(w * 0.22f, w * 0.6f, w * 0.78f, w * 1.2f), p)
        c.restore()
    }
}

object Menus {
    /** Centered dark glass list used for context menus and choices. Returns the dialog so callers can dismiss it. */
    fun show(a: Activity, title: String?, items: List<Pair<String, () -> Unit>>, selected: Int = 0): Dialog {
        val u = U(a)
        val d = Dialog(a, android.R.style.Theme_Translucent_NoTitleBar)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = Glass.solid(0xF22A2A2C.toInt(), u.px(48f))
            setPadding(u.i(36f), u.i(32f), u.i(36f), u.i(32f))
            clipChildren = false
            clipToPadding = false
        }
        if (title != null) panel.addView(TextView(a).apply {
            text = title
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(34f))
            setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, u.i(18f))
        })
        val rows = ArrayList<View>()
        for ((label, action) in items) {
            val row = TextView(a).apply {
                text = label
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(34f))
                setTextColor(Glass.text())
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                maxLines = 1
                setPadding(u.i(30f), 0, u.i(30f), 0)
                background = Glass.rowBg(u, 33f, 0x14FFFFFF)
                setOnClickListener { d.dismiss(); action() }
            }
            rows.add(row)
            panel.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.i(72f)).apply { topMargin = u.i(6f); bottomMargin = u.i(6f) })
        }
        d.setContentView(panel, ViewGroup.LayoutParams(u.i(820f), ViewGroup.LayoutParams.WRAP_CONTENT))
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            setDimAmount(0.55f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        d.setOnShowListener { rows.getOrNull(selected.coerceIn(0, rows.size - 1))?.requestFocus() }
        d.show()
        return d
    }
}

/** Tile art: the app's banner, or its icon on its own background colour; or a folder of mini icons. */
class TileView(context: Context, private val u: U) : View(context) {
    var entry: Entry? = null
        set(v) { field = v; invalidate() }
    var sheen = 0f
        set(v) { field = v; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val tmp = android.graphics.Rect()

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, u.px(RADIUS))
            }
        }
        clipToOutline = false
    }

    private fun drawIn(c: Canvas, d: Drawable, l: Int, t: Int, r: Int, b: Int) {
        d.copyBounds(tmp)
        d.setBounds(l, t, r, b)
        d.draw(c)
        d.bounds = tmp
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val rad = u.px(RADIUS)
        path.reset()
        path.addRoundRect(RectF(0f, 0f, w, h), rad, rad, Path.Direction.CW)
        c.save()
        c.clipPath(path)
        val e = entry
        if (e != null) {
            if (e.isFolder) drawFolder(c, e, w, h) else e.app?.let { drawApp(c, it, w, h) }
        }
        if (sheen > 0f) {
            paint.shader = LinearGradient(0f, 0f, w * 0.7f, h, intArrayOf(
                Color.argb((90 * sheen).toInt(), 255, 255, 255), Color.argb((22 * sheen).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255)
            ), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }
        c.restore()
    }

    private fun drawApp(c: Canvas, a: AppItem, w: Float, h: Float) {
        val banner = a.banner
        if (banner != null && banner.intrinsicWidth > 0) {
            val sc = max(w / banner.intrinsicWidth, h / banner.intrinsicHeight)
            val bw = banner.intrinsicWidth * sc
            val bh = banner.intrinsicHeight * sc
            drawIn(c, banner, ((w - bw) / 2).toInt(), ((h - bh) / 2).toInt(), ((w + bw) / 2).toInt(), ((h + bh) / 2).toInt())
            return
        }
        paint.color = a.tileColor
        c.drawRect(0f, 0f, w, h, paint)
        if (a.pkg == Store.SETTINGS_PKG) {
            paint.shader = LinearGradient(0f, 0f, 0f, h, 0xFFA7A7AD.toInt(), 0xFF6F6F76.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }
        val s = h * if (a.pkg == Store.SETTINGS_PKG) 0.8f else 0.72f
        drawIn(c, a.icon, ((w - s) / 2).toInt(), ((h - s) / 2).toInt(), ((w + s) / 2).toInt(), ((h + s) / 2).toInt())
    }

    private fun drawFolder(c: Canvas, e: Entry, w: Float, h: Float) {
        paint.shader = LinearGradient(0f, 0f, 0f, h, 0xFF8E8E93.toInt(), 0xFF6E6E73.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        val pad = h * 0.09f
        val cell = (h - 2 * pad) / 3f
        val gap = cell * 0.12f
        val icon = cell - gap
        val left = (w - (3 * cell)) / 2f
        for ((i, a) in e.folderApps.take(9).withIndex()) {
            val x = left + (i % 3) * cell + gap / 2
            val y = pad + (i / 3) * cell + gap / 2
            paint.color = a.tileColor
            c.drawRoundRect(RectF(x, y, x + icon, y + icon), icon * 0.22f, icon * 0.22f, paint)
            val s = icon * 0.78f
            val ix = x + (icon - s) / 2
            val iy = y + (icon - s) / 2
            drawIn(c, a.icon, ix.toInt(), iy.toInt(), (ix + s).toInt(), (iy + s).toInt())
        }
    }

    companion object {
        const val RADIUS = 27f
        const val W = 249f
        const val H = 150f
    }
}

/** Software blur that works on every Android version (the TV may be older than API 31, where RenderEffect does not exist). */
object Blur {
    /** [src] drawn small and blurred; show it stretched over the screen (bilinear scaling smooths it further). */
    fun fromView(v: View, divisor: Int = 12, passes: Int = 3): Bitmap {
        val w = (v.width / divisor).coerceAtLeast(8)
        val h = (v.height / divisor).coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(w.toFloat() / v.width, h.toFloat() / v.height)
        v.draw(c)
        return blur(bmp, passes)
    }

    fun blur(src: Bitmap, passes: Int = 3): Bitmap {
        val w = src.width; val h = src.height
        var a = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        var b = IntArray(w * h)
        repeat(passes) {
            box(a, b, w, h, true); box(b, a, w, h, false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    private fun box(s: IntArray, d: IntArray, w: Int, h: Int, horiz: Boolean) {
        val r = 2
        val n = if (horiz) h else w
        val len = if (horiz) w else h
        for (line in 0 until n) {
            for (i in 0 until len) {
                var ra = 0; var rr = 0; var rg = 0; var rb = 0; var cnt = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, len - 1)
                    val p = if (horiz) s[line * w + j] else s[j * w + line]
                    ra += p ushr 24; rr += (p shr 16) and 255; rg += (p shr 8) and 255; rb += p and 255; cnt++
                }
                val o = ((ra / cnt) shl 24) or ((rr / cnt) shl 16) or ((rg / cnt) shl 8) or (rb / cnt)
                if (horiz) d[line * w + i] = o else d[i * w + line] = o
            }
        }
    }

    fun view(ctx: Context, bmp: Bitmap, dim: Int): android.widget.ImageView = android.widget.ImageView(ctx).apply {
        setImageBitmap(bmp)
        scaleType = android.widget.ImageView.ScaleType.FIT_XY
        colorFilter = android.graphics.PorterDuffColorFilter(dim, android.graphics.PorterDuff.Mode.SRC_OVER)
    }
}

/** The big icon above the dock: drawn with rounded corners like the app icons on tvOS. */
class ShelfView(context: Context) : android.widget.ImageView(context) {
    private val path = android.graphics.Path()
    override fun onDraw(c: Canvas) {
        val d = drawable
        if (d == null) { super.onDraw(c); return }
        val r = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        imageMatrix.mapRect(r)
        path.reset()
        val rad = minOf(r.width(), r.height()) * 0.22f
        path.addRoundRect(r, rad, rad, android.graphics.Path.Direction.CW)
        c.save(); c.clipPath(path); super.onDraw(c); c.restore()
    }
}
