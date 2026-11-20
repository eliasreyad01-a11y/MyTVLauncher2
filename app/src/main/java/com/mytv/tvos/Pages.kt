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

    fun clear() = cache.evictAll()

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
            c.connectTimeout = 8_000; c.readTimeout = 25_000; c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "MyTVLauncher/2.7 (Android TV launcher; github.com/eliasreyad01-a11y/MyTVLauncher2)")
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

    /**
     * The online wallpaper list, all of it real 4K (3840 x 2160 or larger) from two public, well-known sources:
     * Bing's daily picture archive ("_UHD" files) and the featured landscape photographs of Wikimedia Commons.
     */
    fun wallpapers(): List<OnlineWp> {
        val out = ArrayList<OnlineWp>()
        try {
            val body = download("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-US", 400_000)?.toString(Charsets.UTF_8)
            if (body != null) out.addAll(parseBing(body))
        } catch (_: Exception) {}
        for (cat in listOf("Featured_pictures_of_landscapes", "Featured_pictures_of_skies")) out.addAll(commons(cat))
        return out
    }

    fun parseBing(json: String): List<OnlineWp> {
        val out = ArrayList<OnlineWp>()
        try {
            val arr = JSONObject(json).getJSONArray("images")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val base = "https://www.bing.com" + o.getString("urlbase")
                out.add(OnlineWp(o.optString("title", "Bing"), base + "_400x240.jpg", base + "_UHD.jpg"))
            }
        } catch (_: Exception) {}
        return out
    }

    /** Wikimedia Commons: files of a category that are at least 3840 x 2160, with a 3840-wide and a 500-wide address each. */
    fun commons(category: String): List<OnlineWp> {
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=categorymembers&gcmtitle=Category:$category" +
            "&gcmtype=file&gcmlimit=60&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=3840"
        val body = try { download(url, 2_000_000)?.toString(Charsets.UTF_8) } catch (_: Exception) { null } ?: return emptyList()
        return parseCommons(body)
    }

    fun parseCommons(json: String, max: Int = 16): List<OnlineWp> {
        val out = ArrayList<OnlineWp>()
        try {
            val pages = JSONObject(json).getJSONObject("query").getJSONObject("pages")
            val keys = pages.keys()
            while (keys.hasNext()) {
                val pg = pages.getJSONObject(keys.next())
                val info = pg.getJSONArray("imageinfo").getJSONObject(0)
                if (info.optString("mime") != "image/jpeg" || info.optInt("width") < 3840 || info.optInt("height") < 2160) continue
                val full = info.optString("thumburl")
                if (!full.contains("/3840px-")) continue
                val title = pg.optString("title", "").removePrefix("File:").substringBeforeLast('.').replace('_', ' ').take(46)
                out.add(OnlineWp(title, full.replace("/3840px-", "/500px-"), full))
                if (out.size >= max) break
            }
        } catch (_: Exception) {}
        return out
    }
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
            layoutManager = RowScrollGrid(a, 6)
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
        val body = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = u.i(16f) })
        val refresh = chip("Refresh") { }
        col.addView(refresh, LinearLayout.LayoutParams(u.i(360f), u.i(66f)).apply { topMargin = u.i(16f) })

        fun infoTile(label: String, value: String): View = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            background = Glass.glass(0x24FFFFFF, u.px(30f), 0.7f, true)
            setPadding(u.i(26f), u.i(8f), u.i(26f), u.i(8f))
            addView(text(L.t(label), 24f, 0xB3FFFFFF.toInt()))
            addView(text(value, 38f, 0xFFFFFFFF.toInt(), true).apply { maxLines = 1 })
        }

        fun compass(deg: Int): String {
            if (deg < 0) return ""
            val names = if (L.ar) listOf("ش", "ش.ق", "ق", "ج.ق", "ج", "ج.غ", "غ", "ش.غ") else listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            return names[((deg + 22.5) / 45).toInt() % 8]
        }

        fun uvText(uv: Int): String =
            if (uv < 0) "—" else "$uv · " + L.t(when { uv <= 2 -> "Low"; uv <= 5 -> "Moderate"; uv <= 7 -> "High"; uv <= 10 -> "Very high"; else -> "Extreme" })

        fun titled(title: String, weight: Float, content: View, bottom: Int = 14): View = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = Glass.glass(0x24FFFFFF, u.px(36f), 0.8f, true)
            setPadding(u.i(30f), u.i(14f), u.i(30f), u.i(10f))
            addView(text(L.t(title), 24f, 0xB3FFFFFF.toInt()))
            addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
            layoutParams = LinearLayout.LayoutParams(-1, 0, weight).apply { bottomMargin = u.i(bottom.toFloat()) }
        }

        fun cell(top: String, glyphCode: Int, night: Boolean, main: String, sub: String): View = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            addView(text(top, 26f, 0xCCFFFFFF.toInt()).apply { gravity = Gravity.CENTER_HORIZONTAL; maxLines = 1 })
            addView(WeatherGlyph(a).apply { set(glyphCode, night) }, LinearLayout.LayoutParams(u.i(72f), u.i(72f)))
            addView(text(main, 32f, 0xFFFFFFFF.toInt(), true).apply { gravity = Gravity.CENTER_HORIZONTAL; maxLines = 1 })
            addView(text(sub, 22f, 0x99FFFFFF.toInt()).apply { gravity = Gravity.CENTER_HORIZONTAL; maxLines = 1 })
        }

        fun fill(w: Weather?, loading: Boolean) {
            body.removeAllViews()
            if (w == null) {
                body.addView(text(L.t(if (loading) "Loading…" else "Not available (no internet)"), 40f, 0xCCFFFFFF.toInt()), LinearLayout.LayoutParams(-2, -2).apply { topMargin = u.i(80f) })
                return
            }
            val is24 = TimeFmt.is24(a, Prefs(a))
            val row1 = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            val now = glassCard().apply { setPadding(u.i(30f), u.i(14f), u.i(30f), u.i(14f)); gravity = Gravity.CENTER_VERTICAL }
            val nowRow = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            nowRow.addView(WeatherGlyph(a).apply { set(w.code, !w.isDay) }, LinearLayout.LayoutParams(u.i(230f), u.i(230f)).apply { marginEnd = u.i(24f) })
            val nowCol = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            nowCol.addView(text("${w.tempC}°", 150f, 0xFFFFFFFF.toInt()).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); includeFontPadding = false })
            nowCol.addView(text(L.t(a.getString(Weather.labelRes(w.code))), 40f))
            nowCol.addView(text("${L.t("High")} ${w.today.maxC}°   ${L.t("Low")} ${w.today.minC}°", 28f, 0xCCFFFFFF.toInt()))
            nowCol.addView(text(L.t("Feels like") + " ${w.feelsC}°", 28f, 0xB3FFFFFF.toInt()))
            nowRow.addView(nowCol)
            now.addView(nowRow)
            row1.addView(now, LinearLayout.LayoutParams(0, -1, 1.2f).apply { marginEnd = u.i(20f) })

            val grid = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            val tiles = listOf(
                "Humidity" to "${w.humidity}%", "Wind" to ("${w.windKmh} km/h " + compass(w.windDirDeg)).trim(),
                "Pressure" to (if (w.pressureHpa > 0) "${w.pressureHpa} hPa" else "—"), "UV Index" to uvText(w.uvIndex),
                "Sunrise" to (if (w.sunrise.isEmpty()) "—" else TimeFmt.display(w.sunrise, is24, L.locale())),
                "Sunset" to (if (w.sunset.isEmpty()) "—" else TimeFmt.display(w.sunset, is24, L.locale()))
            )
            for (r in 0 until 3) {
                val rowL = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                for (c in 0 until 2) {
                    val (lb, vl) = tiles[r * 2 + c]
                    rowL.addView(infoTile(lb, vl), LinearLayout.LayoutParams(0, -1, 1f).apply { if (c == 0) marginEnd = u.i(14f) })
                }
                grid.addView(rowL, LinearLayout.LayoutParams(-1, 0, 1f).apply { if (r < 2) bottomMargin = u.i(14f) })
            }
            row1.addView(grid, LinearLayout.LayoutParams(0, -1, 1f))
            body.addView(row1, LinearLayout.LayoutParams(-1, 0, 1.55f).apply { bottomMargin = u.i(14f) })

            // The next hours.
            val hours = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            for (hp in w.hourly.take(8)) hours.addView(
                cell(TimeFmt.display(hp.hhmm, is24, L.locale()), hp.code, !hp.isDay, "${hp.tempC}°", if (hp.rainPct > 0) "${hp.rainPct}%" else " "),
                LinearLayout.LayoutParams(0, -1, 1f)
            )
            if (w.hourly.isNotEmpty()) body.addView(titled("Next hours", 0.95f, hours))

            // The coming days.
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH)
            val dayFmt = java.text.SimpleDateFormat("EEE", L.locale())
            val days = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            for ((i, d) in w.days.take(5).withIndex()) {
                val name = if (i == 0) L.t("Today") else try { dayFmt.format(fmt.parse(d.date)!!) } catch (_: Exception) { d.date }
                days.addView(cell(name, d.code, false, "${d.maxC}° / ${d.minC}°", L.t("Rain") + " ${d.rainPct}%"), LinearLayout.LayoutParams(0, -1, 1f))
            }
            if (w.days.isNotEmpty()) body.addView(titled("5-day forecast", 1.05f, days, 0))
            else {
                val two = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                two.addView(dayCard("Today", w.today), LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = u.i(14f) })
                two.addView(dayCard("Tomorrow", w.tomorrow), LinearLayout.LayoutParams(0, -1, 1f))
                body.addView(two, LinearLayout.LayoutParams(-1, 0, 1f))
            }
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
        Toast.makeText(a, L.t("Downloading…"), Toast.LENGTH_LONG).show()
        Thread {
            val bytes = Net2.download(w.full, 40_000_000)
            val bmp = bytes?.let { b -> WallpaperPicker.decodeStream({ java.io.ByteArrayInputStream(b) }, 1280) }
            ui.post {
                if (a.isDestroyed) return@post
                if (bmp == null) Toast.makeText(a, L.t("Download failed (check internet)"), Toast.LENGTH_LONG).show()
                else WallpaperPicker.previewBitmap(a, w.title, bmp, bytes) { a.applyOptions() }
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
        col.addView(text(L.t("Online wallpapers (4K) — press one to download it"), 28f, 0x99FFFFFF.toInt()), LinearLayout.LayoutParams(-2, -2).apply { topMargin = u.i(22f) })
        val items = ArrayList<OnlineWp>()
        val ad = WpAdapter(items)
        val rv = RecyclerView(a).apply {
            layoutManager = RowScrollGrid(a, 4)
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
