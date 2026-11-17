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
    fun rowBg(u: U, radius: Float = 33f, normal: Int = 0x1FFFFFFF, backdrop: Boolean = false): StateListDrawable = StateListDrawable().apply {
        addState(FOCUSED, GlossDrawable(u.px(radius), WHITE, 0.55f))
        addState(intArrayOf(), GlossDrawable(u.px(radius), normal, 1f, backdrop))
    }

    fun solid(color: Int, r: Float) = shape(color, r)
    fun glass(color: Int, r: Float, strength: Float = 1f, backdrop: Boolean = false): Drawable = GlossDrawable(r, color, strength, backdrop)
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
            background = Glass.glass(0xE62A2A2C.toInt(), u.px(48f), 0.7f)
            setPadding(u.i(36f), u.i(32f), u.i(36f), u.i(32f))
            clipChildren = false
            clipToPadding = false
        }
        if (title != null) panel.addView(TextView(a).apply {
            text = L.t(title)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(34f))
            setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, u.i(18f))
        })
        val rows = ArrayList<View>()
        for ((label, action) in items) {
            val row = TextView(a).apply {
                text = L.t(label)
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
            setWindowAnimations(R.style.DialogFade)
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
    private var sheenShader: Shader? = null
    private var sheenW = 0f
    private var sheenH = 0f
    private val clipP = Path()
    private val mask = RectF()
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
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
        // Content is drawn into a layer and cut with an anti-aliased rounded mask: clipPath leaves stair-stepped corners on many TVs.
        val smooth = TileView.smooth
        val layer = if (smooth) c.saveLayer(0f, 0f, w, h, null) else c.save()
        if (!smooth) { clipP.reset(); clipP.addRoundRect(RectF(0f, 0f, w, h), rad, rad, Path.Direction.CW); c.clipPath(clipP) }
        val e = entry
        if (e != null) {
            if (e.isFolder) drawFolder(c, e, w, h) else e.app?.let { drawApp(c, it, w, h) }
        }
        val sh = max(sheen, 0.3f)
        if (sheenShader == null || sheenW != w || sheenH != h) {
            sheenShader = LinearGradient(0f, 0f, w * 0.7f, h, intArrayOf(0x5AFFFFFF, 0x16FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            sheenW = w; sheenH = h
        }
        paint.shader = sheenShader
        paint.alpha = (255 * sh).toInt().coerceIn(0, 255)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        paint.alpha = 255
        if (smooth) { mask.set(0f, 0f, w, h); c.drawRoundRect(mask, rad, rad, maskPaint) }
        c.restoreToCount(layer)
    }

    private val src = android.graphics.Rect()
    private val dst = RectF()
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun letter(c: Canvas, a: AppItem, cx: Float, cy: Float, size: Float) {
        paint.color = 0xE6FFFFFF.toInt()
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        c.drawText(a.label.trim().take(1).uppercase(), cx, cy + size * 0.35f, paint)
    }

    private fun drawApp(c: Canvas, a: AppItem, w: Float, h: Float) {
        val banner = a.bannerBmp
        if (banner != null) {
            val sc = max(w / banner.width, h / banner.height)
            val bw = banner.width * sc
            val bh = banner.height * sc
            dst.set((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2)
            c.drawBitmap(banner, null, dst, bp)
            return
        }
        paint.color = a.tileColor
        c.drawRect(0f, 0f, w, h, paint)
        if (a.pkg == Store.SETTINGS_PKG) {
            paint.shader = LinearGradient(0f, 0f, 0f, h, 0xFFA7A7AD.toInt(), 0xFF6F6F76.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }
        if (a.blank) { letter(c, a, w / 2, h / 2, h * 0.5f); return }
        val s = h * if (a.pkg == Store.SETTINGS_PKG) 0.8f else 0.72f
        dst.set((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2)
        c.drawBitmap(a.iconBmp, null, dst, bp)
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
            if (a.blank) letter(c, a, x + icon / 2, y + icon / 2, icon * 0.5f)
            else { dst.set(ix, iy, ix + s, iy + s); c.drawBitmap(a.iconBmp, null, dst, bp) }
        }
    }

    companion object {
        /** Anti-aliased rounded corners through a mask layer; the plain clip is kept as a switch in Settings in case a TV renders the mask badly. */
        @Volatile var smooth = true
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

/**
 * What is behind the glass: a small blurred copy of the wallpaper. Glass panels draw it magnified inside themselves, which gives
 * the "lens" look of Liquid Glass (the background is seen through the glass, bent and richer in colour) at the cost of one
 * textured rounded rectangle per panel.
 */
object Backdrop {
    @Volatile var bmp: Bitmap? = null
    @Volatile var light = false
    @Volatile var version = 0

    fun set(src: Bitmap?) {
        if (src == null) { bmp = null; light = false; version++; return }
        val w = 96
        val h = (96f * src.height / src.width).toInt().coerceAtLeast(54)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        var sum = 0.0
        var n = 0
        for (y in 0 until h step 3) for (x in 0 until w step 3) {
            val p = small.getPixel(x, y)
            sum += (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)) / 255.0
            n++
        }
        val lum = if (n == 0) 0.0 else sum / n
        val b = Blur.blur(small, 3)
        light = lum > 0.52
        bmp = b
        version++
    }
}

/**
 * Liquid-glass material: the backdrop seen through a lens, a tint that adapts to the wallpaper, a soft top specular, a small
 * light spot and a rim light that is brightest on the top-left and bottom-right edges (like Apple's glass).
 */
class GlossDrawable(private val radius: Float, private val base: Int, private val strength: Float = 1f, private val backdrop: Boolean = false) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hi = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = SAT }
    private val r = RectF()
    private val rim = RectF()
    private val m = android.graphics.Matrix()
    private val loc = IntArray(2)
    private var shader: android.graphics.BitmapShader? = null
    private var shaderVersion = -1
    private var ox = Float.NaN
    private var oy = 0f
    private var rw = 0f
    private var rh = 0f

    /** For drawables that are not attached to a view (the dock plate): where they sit on the screen. */
    fun setOrigin(x: Float, y: Float, screenW: Float, screenH: Float) { ox = x; oy = y; rw = screenW; rh = screenH }

    override fun onBoundsChange(b: android.graphics.Rect) {
        r.set(b)
        val w = b.width().toFloat(); val h = b.height().toFloat()
        val a = (255 * 0.26f * strength).toInt()
        hi.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.top + h * 0.6f, Color.argb(a, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        spot.shader = android.graphics.RadialGradient(b.left + w * 0.2f, b.top + h * 0.02f, max(w, h) * 0.5f, Color.argb((255 * 0.20f * strength).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        val sw = max(1.5f, radius * 0.035f)
        edge.strokeWidth = sw
        edge.shader = LinearGradient(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            intArrayOf(Color.argb((255 * 0.70f * strength).toInt(), 255, 255, 255), Color.argb((255 * 0.10f * strength).toInt(), 255, 255, 255),
                Color.argb((255 * 0.10f * strength).toInt(), 255, 255, 255), Color.argb((255 * 0.42f * strength).toInt(), 255, 255, 255)),
            floatArrayOf(0f, 0.32f, 0.66f, 1f), Shader.TileMode.CLAMP)
        rim.set(b.left + sw / 2, b.top + sw / 2, b.right - sw / 2, b.bottom - sw / 2)
    }

    private fun drawBackdrop(c: Canvas, bd: Bitmap) {
        val v = callback as? View
        var x = ox; var y = oy; var sw = rw; var sh = rh
        if (x.isNaN()) {
            if (v == null) return
            v.getLocationInWindow(loc)
            x = loc[0].toFloat() + bounds.left; y = loc[1].toFloat() + bounds.top
            val root = v.rootView
            sw = root.width.toFloat(); sh = root.height.toFloat()
        }
        if (sw <= 0f || sh <= 0f) return
        if (shader == null || shaderVersion != Backdrop.version) {
            shader = android.graphics.BitmapShader(bd, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shaderVersion = Backdrop.version
        }
        val s = max(sw / bd.width, sh / bd.height)
        m.setScale(s, s)
        m.postTranslate((sw - bd.width * s) / 2, (sh - bd.height * s) / 2)
        m.postScale(1.07f, 1.07f, x + r.width() / 2, y + r.height() / 2)
        m.postTranslate(-x, -y)
        shader!!.setLocalMatrix(m)
        bp.shader = shader
        c.drawRoundRect(r, radius, radius, bp)
    }

    override fun draw(c: Canvas) {
        val bd = if (backdrop) Backdrop.bmp else null
        var body = base
        if (bd != null) {
            drawBackdrop(c, bd)
            body = if (Backdrop.light) 0x5C101014 else 0x1FFFFFFF
        }
        fill.color = body
        c.drawRoundRect(r, radius, radius, fill)
        c.drawRoundRect(r, radius, radius, hi)
        c.drawRoundRect(r, radius, radius, spot)
        c.drawRoundRect(rim, radius, radius, edge)
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        private val SAT = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(1.5f) })
    }
}

/** Weather symbol drawn with paths: sun, moon, clouds, rain, snow, storm, fog - day or night. */
class WeatherGlyph(context: Context) : View(context) {
    var code = 0
    var night = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val tmp = Path()

    fun set(code: Int, night: Boolean) { this.code = code; this.night = night; invalidate() }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.FILL; p.color = 0xFFFFD60A.toInt()
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.28f; p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            c.drawLine(cx + (r * 1.45f * Math.cos(a)).toFloat(), cy + (r * 1.45f * Math.sin(a)).toFloat(),
                cx + (r * 1.9f * Math.cos(a)).toFloat(), cy + (r * 1.9f * Math.sin(a)).toFloat(), p)
        }
        p.style = Paint.Style.FILL
    }

    private fun moon(c: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset(); path.addCircle(cx, cy, r, Path.Direction.CW)
        tmp.reset(); tmp.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW)
        path.op(tmp, Path.Op.DIFFERENCE)
        p.style = Paint.Style.FILL; p.color = 0xFFF2EFE0.toInt()
        c.drawPath(path, p)
    }

    private fun cloud(c: Canvas, x: Float, y: Float, w: Float, color: Int) {
        val h = w * 0.62f
        p.style = Paint.Style.FILL; p.color = color
        c.drawCircle(x + w * 0.30f, y + h * 0.58f, h * 0.36f, p)
        c.drawCircle(x + w * 0.55f, y + h * 0.38f, h * 0.44f, p)
        c.drawCircle(x + w * 0.76f, y + h * 0.62f, h * 0.30f, p)
        c.drawRoundRect(RectF(x + w * 0.10f, y + h * 0.55f, x + w * 0.90f, y + h * 0.95f), h * 0.2f, h * 0.2f, p)
    }

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        val white = 0xFFF5F7FA.toInt(); val gray = 0xFFBFC5CF.toInt()
        when (code) {
            0 -> if (night) moon(c, s * 0.5f, s * 0.5f, s * 0.34f) else sun(c, s * 0.5f, s * 0.5f, s * 0.22f)
            1, 2 -> {
                if (night) moon(c, s * 0.38f, s * 0.36f, s * 0.24f) else sun(c, s * 0.36f, s * 0.36f, s * 0.16f)
                cloud(c, s * 0.22f, s * 0.36f, s * 0.74f, white)
            }
            3 -> cloud(c, s * 0.08f, s * 0.2f, s * 0.84f, gray)
            45, 48 -> {
                cloud(c, s * 0.12f, s * 0.08f, s * 0.76f, gray)
                p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.06f; p.strokeCap = Paint.Cap.ROUND; p.color = 0xFFD5D9E0.toInt()
                for (i in 0..2) c.drawLine(s * 0.2f, s * (0.64f + i * 0.12f), s * 0.8f, s * (0.64f + i * 0.12f), p)
            }
            in 51..67, in 80..82 -> {
                cloud(c, s * 0.1f, s * 0.08f, s * 0.8f, gray)
                p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.06f; p.strokeCap = Paint.Cap.ROUND; p.color = 0xFF4FA3FF.toInt()
                for (i in 0..2) c.drawLine(s * (0.3f + i * 0.2f), s * 0.68f, s * (0.24f + i * 0.2f), s * 0.86f, p)
            }
            in 71..77, 85, 86 -> {
                cloud(c, s * 0.1f, s * 0.08f, s * 0.8f, gray)
                p.style = Paint.Style.FILL; p.color = Color.WHITE
                for (i in 0..2) c.drawCircle(s * (0.3f + i * 0.2f), s * (0.74f + (i % 2) * 0.08f), s * 0.05f, p)
            }
            in 95..99 -> {
                cloud(c, s * 0.1f, s * 0.06f, s * 0.8f, 0xFF8A93A3.toInt())
                path.reset()
                path.moveTo(s * 0.52f, s * 0.56f); path.lineTo(s * 0.38f, s * 0.78f); path.lineTo(s * 0.5f, s * 0.78f)
                path.lineTo(s * 0.44f, s * 0.96f); path.lineTo(s * 0.64f, s * 0.7f); path.lineTo(s * 0.52f, s * 0.7f); path.close()
                p.style = Paint.Style.FILL; p.color = 0xFFFFD60A.toInt(); c.drawPath(path, p)
            }
            else -> if (night) moon(c, s * 0.5f, s * 0.5f, s * 0.34f) else sun(c, s * 0.5f, s * 0.5f, s * 0.22f)
        }
    }
}
