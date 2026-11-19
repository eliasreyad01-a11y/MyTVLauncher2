package com.mytv.tvos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** A wallpaper that can be downloaded: where its small picture and its full picture are. */
class OnlineWp(val title: String, val thumb: String, val full: String)

/** Small pictures for the wallpaper grid: downloaded off the main thread, cached, and decoded small. */
object Thumbs {
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(40) {}

    fun get(url: String): Bitmap? = cache.get(url)

    fun load(url: String, done: (Bitmap?) -> Unit) {
        cache.get(url)?.let { done(it); return }
        pool.execute {
            val bytes = Net2.download(url, 6_000_000)
            val bmp = bytes?.let { b ->
                try {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(b, 0, b.size, o)
                    var s = 1
                    while (o.outWidth / (s * 2) >= 520) s *= 2
                    BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s })
                } catch (_: Throwable) { null }
            }
            if (bmp != null) cache.put(url, bmp)
            main.post { done(bmp) }
        }
    }
}

object Net2 {
    /** Downloads a file into memory (blocking). Null on any problem or when it is larger than [limit]. */
    fun download(url: String, limit: Int = 12_000_000): ByteArray? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8_000; c.readTimeout = 15_000; c.instanceFollowRedirects = true
            if (c.responseCode !in 200..299) return null
            val out = java.io.ByteArrayOutputStream()
            c.inputStream.use { st ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = st.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > limit) return null
                }
            }
            out.toByteArray()
        } catch (_: Exception) { null } finally { c?.disconnect() }
    }

    /** The online wallpaper list: Bing's picture archive (needs no account) followed by a fixed set of photos from picsum.photos. */
    fun wallpapers(): List<OnlineWp> {
        val out = ArrayList<OnlineWp>()
        try {
            val body = download("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-US", 400_000)?.toString(Charsets.UTF_8)
            if (body != null) {
                val arr = JSONObject(body).getJSONArray("images")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val base = "https://www.bing.com" + o.getString("urlbase")
                    out.add(OnlineWp(o.optString("title", "Bing"), base + "_400x240.jpg", base + "_1920x1080.jpg"))
                }
            }
        } catch (_: Exception) {}
        for (id in PICSUM) out.add(OnlineWp("Photo $id", "https://picsum.photos/id/$id/480/270", "https://picsum.photos/id/$id/1920/1080"))
        return out
    }

    val PICSUM = listOf(10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 22, 24, 25, 27, 28, 29, 37, 38, 39, 40, 41, 42, 43, 44)
}

/** A rounded picture tile (drawn with a shader, so the corners stay round on every TV) with a white rim when focused. */
class ThumbView(context: Context, private val u: U) : View(context) {
    var bmp: Bitmap? = null
        set(v) { field = v; shader = null; invalidate() }
    var failed = false
        set(v) { field = v; invalidate() }
    private var shader: BitmapShader? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val r = RectF()
    private val m = Matrix()

    init { isFocusable = true; isClickable = true }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val rad = u.px(26f)
        r.set(0f, 0f, w, h)
        val b = bmp
        if (b == null) {
            p.shader = null; p.color = if (failed) 0x33FF6B6B else 0x26FFFFFF
            c.drawRoundRect(r, rad, rad, p)
        } else {
            if (shader == null) shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val sc = maxOf(w / b.width, h / b.height)
            m.setScale(sc, sc); m.postTranslate((w - b.width * sc) / 2, (h - b.height * sc) / 2)
            shader!!.setLocalMatrix(m)
            p.shader = shader
            c.drawRoundRect(r, rad, rad, p)
            p.shader = null
        }
        if (isFocused) {
            rim.strokeWidth = u.px(6f); rim.color = 0xFFFFFFFF.toInt()
            r.inset(u.px(3f), u.px(3f))
            c.drawRoundRect(r, rad - u.px(3f), rad - u.px(3f), rim)
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animate().cancel()
        animate().scaleX(if (gainFocus) 1.06f else 1f).scaleY(if (gainFocus) 1.06f else 1f).setDuration(180).start()
        invalidate()
    }
}

/**
 * Full pages that open from the bar at the top (Apps, Weather, Wallpapers): each fills the screen below the bar and keeps a safe
 * margin from the screen edges, because TVs often hide the outermost pixels.
 */
class Pages(private val a: MainActivity) {
    private val u get() = a.u
    private val safe get() = u.i(110f)
    private val ui = Handler(Looper.getMainLooper())

    private fun text(s: String, size: Float, color: Int = 0xFFFFFFFF.toInt(), bold: Boolean = false) = TextView(a).apply {
        text = s; setTextSize(TypedValue.COMPLEX_UNIT_PX, u.px(size)); setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun scrim(): FrameLayout = FrameLayout(a).apply { setBackgroundColor(0xB30C0E13.toInt()) }

    private fun chip(label: String, action: () -> Unit) = TextView(a).apply {
        text = L.t(label); setTextSize(TypedValue.COMPLEX_UNIT_PX, u.px(30f)); setTextColor(Glass.text()); gravity = Gravity.CENTER
        isFocusable = true; isClickable = true
        setPadding(u.i(34f), 0, u.i(34f), 0)
        background = Glass.rowBg(u, 33f, 0x38FFFFFF, true)
        setOnClickListener { action() }
    }

    // ---------------------------------------------------------------- apps

    fun apps(): View {
        val root = scrim()
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(safe, u.i(140f), safe, 0) }
        col.addView(text(L.t("Apps"), 54f, 0xCCFFFFFF.toInt(), true), LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = u.i(10f) })
        val cells = a.apps.sortedBy { it.label.lowercase() }.map { Entry(it.pkg, it) }
        val ad = HomeAdapter(u, false, { }, { e -> e.app?.let { a.launch(it) } }, { e -> a.showMenu(e, false, true) })
        ad.cells = cells
        ad.alwaysLabels = true
        ad.reduceMotion = a.sp.getBoolean("calm", false)
        val rv = RecyclerView(a).apply {
            layoutManager = GridLayoutManager(a, 6)
            adapter = ad
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false; clipToPadding = false
            setPadding(0, u.i(30f), 0, u.i(60f))
        }
        col.addView(rv, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        return root
    }

    // ---------------------------------------------------------------- weather

    private fun glassCard(radius: Float = 46f) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = Glass.glass(0x2EFFFFFF, u.px(radius), 0.9f, true)
    }

    private fun dayCard(label: String, f: DayForecast): View = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = Glass.glass(0x2EFFFFFF, u.px(40f), 0.8f, true)
        setPadding(u.i(34f), u.i(20f), u.i(34f), u.i(20f))
        addView(WeatherGlyph(a).apply { set(f.code, false) }, LinearLayout.LayoutParams(u.i(130f), u.i(130f)).apply { marginEnd = u.i(26f) })
        addView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(L.t(label), 30f, 0xB3FFFFFF.toInt()))
            addView(text("${f.maxC}° / ${f.minC}°", 56f, 0xFFFFFFFF.toInt(), true))
            addView(text(L.t(a.getString(Weather.labelRes(f.code))) + "  ·  " + L.t("Rain") + " " + f.rainPct + "%", 28f, 0xB3FFFFFF.toInt()))
        })
    }

    fun weather(): View {
        val root = scrim()
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(safe, u.i(140f), safe, u.i(60f)) }
        val head = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        head.addView(text(Prefs(a).cityName, 54f, 0xFFFFFFFF.toInt(), true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        col.addView(head)
        val body = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        col.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = u.i(20f) })
        val refresh = chip("Refresh") { }
        col.addView(refresh, LinearLayout.LayoutParams(u.i(360f), u.i(70f)).apply { topMargin = u.i(20f) })

        fun fill(w: Weather?, loading: Boolean) {
            body.removeAllViews()
            if (w == null) {
                body.addView(text(L.t(if (loading) "Loading…" else "Not available (no internet)"), 40f, 0xCCFFFFFF.toInt()), LinearLayout.LayoutParams(-2, -2).apply { topMargin = u.i(80f) })
                return
            }
            val now = glassCard().apply { setPadding(u.i(50f), u.i(40f), u.i(50f), u.i(40f)); gravity = Gravity.CENTER }
            now.addView(WeatherGlyph(a).apply { set(w.code, !w.isDay) }, LinearLayout.LayoutParams(u.i(330f), u.i(330f)))
            now.addView(text("${w.tempC}°", 190f, 0xFFFFFFFF.toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); includeFontPadding = false
            })
            now.addView(text(L.t(a.getString(Weather.labelRes(w.code))), 46f).apply { gravity = Gravity.CENTER_HORIZONTAL })
            now.addView(text(L.t("Feels like") + " ${w.feelsC}°   ·   " + L.t("Humidity") + " ${w.humidity}%   ·   " + L.t("Wind") + " ${w.windKmh} km/h", 28f, 0xB3FFFFFF.toInt()).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, u.i(14f), 0, 0) })
            body.addView(now, LinearLayout.LayoutParams(0, -1, 1.15f).apply { marginEnd = u.i(30f) })
            val right = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            right.addView(dayCard("Today", w.today), LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = u.i(24f) })
            right.addView(dayCard("Tomorrow", w.tomorrow), LinearLayout.LayoutParams(-1, 0, 1f))
            body.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        }

        fun load() {
            fill(a.weather, a.weather == null)
            val app = a.applicationContext
            Thread {
                val p = Prefs(app)
                val w = WeatherRepository.fetch(p.latitude, p.longitude)
                if (w != null) Net.evidence()
                ui.post {
                    if (a.isDestroyed || root.parent == null) return@post
                    if (w != null) { a.weather = w; a.updateInfo() }
                    fill(a.weather, false)
                }
            }.start()
        }
        refresh.setOnClickListener { load() }
        load()
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        return root
    }

    // ---------------------------------------------------------------- wallpapers

    private inner class WpAdapter(val items: MutableList<OnlineWp>) : RecyclerView.Adapter<WpAdapter.VH>() {
        inner class VH(val v: ThumbView) : RecyclerView.ViewHolder(v) { var item: OnlineWp? = null }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = ThumbView(a, u)
            v.layoutParams = RecyclerView.LayoutParams(-1, u.i(262f))
            val vh = VH(v)
            v.setOnClickListener { vh.item?.let { download(it) } }
            return vh
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val it = items[position]
            h.item = it
            h.v.failed = false
            h.v.bmp = Thumbs.get(it.thumb)
            if (h.v.bmp == null) Thumbs.load(it.thumb) { b ->
                if (h.item === it) { if (b != null) h.v.bmp = b else h.v.failed = true }
            }
        }

        override fun getItemCount() = items.size
    }

    private fun download(w: OnlineWp) {
        Toast.makeText(a, L.t("Downloading…"), Toast.LENGTH_SHORT).show()
        Thread {
            val bytes = Net2.download(w.full)
            val bmp = bytes?.let { b -> WallpaperPicker.decodeStream({ java.io.ByteArrayInputStream(b) }) }
            ui.post {
                if (a.isDestroyed) return@post
                if (bmp == null) Toast.makeText(a, L.t("Download failed (check internet)"), Toast.LENGTH_LONG).show()
                else WallpaperPicker.previewBitmap(a, w.title, bmp) { a.applyOptions() }
            }
        }.start()
    }

    fun wallpapers(): View {
        val root = scrim()
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(safe, u.i(140f), safe, 0) }
        col.addView(text(L.t("Wallpapers"), 54f, 0xCCFFFFFF.toInt(), true), LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = u.i(16f) })
        val chips = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        fun addChip(label: String, action: () -> Unit) = chips.addView(chip(label, action), LinearLayout.LayoutParams(-2, u.i(66f)).apply { marginEnd = u.i(14f) })
        addChip("Choose from TV storage") { WallpaperPicker.pickStorage(a) { a.applyOptions() } }
        if (WallpaperPicker.hasSystemPicker(a)) addChip("Choose with the system picker") { WallpaperPicker.pickSystem(a) { a.applyOptions() } }
        addChip("Default") { a.sp.edit().putString("wall_mode", "default").apply(); a.applyOptions(); Toast.makeText(a, L.t("Wallpaper set"), Toast.LENGTH_SHORT).show() }
        addChip("None") { a.sp.edit().putString("wall_mode", "none").apply(); a.applyOptions(); Toast.makeText(a, L.t("Wallpaper set"), Toast.LENGTH_SHORT).show() }
        col.addView(chips)
        col.addView(text(L.t("Online wallpapers — press one to download it"), 28f, 0x99FFFFFF.toInt()), LinearLayout.LayoutParams(-2, -2).apply { topMargin = u.i(22f) })
        val items = ArrayList<OnlineWp>()
        for (id in Net2.PICSUM) items.add(OnlineWp("Photo $id", "https://picsum.photos/id/$id/480/270", "https://picsum.photos/id/$id/1920/1080"))
        val ad = WpAdapter(items)
        val rv = RecyclerView(a).apply {
            layoutManager = GridLayoutManager(a, 4)
            adapter = ad
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false; clipToPadding = false
            setPadding(u.i(12f), u.i(20f), u.i(12f), u.i(60f))
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    val m = u.i(14f); outRect.set(m, m, m, m)
                }
            })
        }
        col.addView(rv, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        // Bing's daily pictures go first once the list arrives.
        Thread {
            val all = Net2.wallpapers()
            ui.post {
                if (a.isDestroyed || root.parent == null) return@post
                items.clear(); items.addAll(all); ad.notifyDataSetChanged()
            }
        }.start()
        return root
    }
}
