package com.mytv.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * Renders the real screens (1920x1080, TV density) with Robolectric's native graphics and writes JPEGs to
 * app/build/screens. It is an approximation of the TV: no GPU-only effects (live blur, elevation shadows).
 * It exists so layout problems (clipping, overlap, alignment, colours) can be looked at instead of guessed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1440dp-h810dp-land-tvdpi")
class ScreenshotTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val W = 1920
    private val H = 1080

    private val names = listOf(
        "Netflix", "YouTube", "Prime Video", "Disney+", "Spotify", "Plex", "Kodi", "Browser",
        "Settings", "Files", "Games", "Music", "Live TV", "Photos"
    )
    private val colors = listOf(
        0xFFB20710.toInt() to 0xFF221F1F.toInt(), 0xFFFF0000.toInt() to 0xFF7A0000.toInt(),
        0xFF00A8E1.toInt() to 0xFF0B2A44.toInt(), 0xFF113CCF.toInt() to 0xFF06143F.toInt(),
        0xFF1DB954.toInt() to 0xFF0B3D1E.toInt(), 0xFFE5A00D.toInt() to 0xFF3A2A03.toInt(),
        0xFF31A9D6.toInt() to 0xFF123344.toInt(), 0xFF4285F4.toInt() to 0xFF1A2F5A.toInt(),
        0xFF8E8E93.toInt() to 0xFF2C2C2E.toInt(), 0xFFFFB300.toInt() to 0xFF4A3300.toInt(),
        0xFF7E57C2.toInt() to 0xFF2A1B4D.toInt(), 0xFFEC407A.toInt() to 0xFF4A1426.toInt(),
        0xFF26A69A.toInt() to 0xFF0C3430.toInt(), 0xFFFF7043.toInt() to 0xFF4A210F.toInt()
    )

    private fun fakeApps(): List<AppItem> = names.mapIndexed { i, n ->
        val bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 320f, 180f, colors[i].first, colors[i].second, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, 320f, 180f, p)
        p.shader = null
        p.color = Color.WHITE
        p.textSize = 30f
        p.textAlign = Paint.Align.CENTER
        cv.drawText(n, 160f, 100f, p)
        val d = BitmapDrawable(ctx.resources, bmp)
        AppItem("com.fake.${n.replace(" ", "").replace("+", "")}", n, d, d, Intent())
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(o: Any, name: String): T {
        val f = o.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(o) as T
    }

    private fun setField(o: Any, name: String, v: Any?) {
        val f = o.javaClass.getDeclaredField(name)
        f.isAccessible = true
        f.set(o, v)
    }

    private fun call(o: Any, name: String, vararg args: Any) {
        val m = o.javaClass.declaredMethods.first { it.name == name && it.parameterCount == args.size }
        m.isAccessible = true
        m.invoke(o, *args)
    }

    private fun waitUntil(timeoutMs: Long = 10_000, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            TestUi.idle()
            if (cond()) return true
            Thread.sleep(50)
        }
        TestUi.idle()
        return cond()
    }

    private fun render(view: View, w: Int = W, h: Int = H): Bitmap {
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        return bmp
    }

    private fun overlay(base: Bitmap, v: View, x: Int?, y: Int?, dim: Boolean) {
        val cv = Canvas(base)
        if (dim) cv.drawColor(0x59000000)
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.AT_MOST))
        val w = v.measuredWidth
        val h = v.measuredHeight
        v.layout(0, 0, w, h)
        cv.save()
        cv.translate((x ?: (W - w) / 2).toFloat(), (y ?: (H - h) / 2).toFloat())
        v.draw(cv)
        cv.restore()
    }

    private fun save(name: String, bmp: Bitmap) {
        val f = File("build/screens/$name.jpg")
        f.parentFile!!.mkdirs()
        val small = Bitmap.createScaledBitmap(bmp, 1280, 720, true)
        f.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun openHome(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val adapter: AppAdapter = field(a, "adapter")
        waitUntil { adapter.itemCount > 0 }
        val apps = fakeApps()
        Prefs(ctx).favorites = apps.take(6).map { it.packageName }
        setField(a, "loadedApps", apps)
        call(a, "applyLists")
        TestUi.idle()
        return a
    }

    @Test
    fun homeScreens() {
        val a = openHome()
        val adapter: AppAdapter = field(a, "adapter")
        assertNotNull(adapter)
        save("01_home", render(a.window.decorView))

        // Focus a tile in the dock and one in the grid.
        val grid = a.findViewById<RecyclerView>(R.id.grid)
        grid.findViewHolderForAdapterPosition(1)?.itemView?.requestFocus()
        TestUi.idle()
        grid.findViewHolderForAdapterPosition(1)?.itemView?.let { call(a, "placeGlow", it, false) }
        save("02_home_focus_dock", render(a.window.decorView))

        grid.findViewHolderForAdapterPosition(10)?.itemView?.requestFocus()
        TestUi.idle()
        grid.findViewHolderForAdapterPosition(10)?.itemView?.let { call(a, "placeGlow", it, false) }
        save("03_home_focus_grid", render(a.window.decorView))
    }

    @Test
    fun controlCenterAndScreensaver() {
        val a = openHome()
        val base = render(a.window.decorView)
        call(a, "showQuickSettings")
        TestUi.idle()
        val popup: PopupWindow = field(a, "menuPopup")
        overlay(base, popup.contentView, dp(60), dp(88), false)
        save("04_control_center", base)
        popup.dismiss()

        call(a, "startScreensaver")
        TestUi.idle()
        save("05_screensaver", render(a.window.decorView))
    }

    @Test
    fun folderScreens() {
        val a = openHome()
        for (p in listOf("com.fake.Plex", "com.fake.Kodi", "com.fake.Music", "com.fake.LiveTV", "com.fake.Photos")) {
            call(a, "addToFolder", "Entertainment", p)
        }
        TestUi.idle()
        save("06_home_with_folder", render(a.window.decorView))
        val base = render(a.window.decorView)
        call(a, "showFolder", "Entertainment")
        TestUi.idle()
        overlay(base, ShadowDialog.getLatestDialog().window!!.decorView, null, null, true)
        save("07_folder_open", base)
    }

    /** Looks at how a focused settings row is really painted (numbers, not guesses); written to build/screens/debug.txt. */
    @Test
    fun focusedRowProbe() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent(ctx, SettingsActivity::class.java)).setup().get()
        val row = TestUi.rowsIn(a.window.decorView)[1]
        row.requestFocus()
        TestUi.idle()
        fun absLeft(v: View): Int { var x = 0; var c: View? = v; while (c != null) { x += c.left; c = c.parent as? View }; return x }
        fun absTop(v: View): Int { var y = 0; var c: View? = v; while (c != null) { y += c.top; c = c.parent as? View }; return y }
        val sb = StringBuilder()
        fun probe(tag: String) {
            val bmp = render(a.window.decorView)
            val l = absLeft(row); val t = absTop(row); val cy = t + row.height / 2
            val p1 = row.parent as ViewGroup
            sb.append("$tag scaleX=${row.scaleX} left=$l top=$t w=${row.width} h=${row.height} parentPadL=${p1.paddingLeft} parentLeft=${absLeft(p1)} clipChildren=${p1.clipChildren} anim=${row.animation}\n")
            sb.append("  hline@cy:")
            for (dx in -24..24 step 6) sb.append(" ${l + dx}=${Integer.toHexString(bmp.getPixel(l + dx, cy))}")
            sb.append("\n  corner:")
            for (d in intArrayOf(1, 4, 8, 14)) sb.append(" (${l + d},${t + 1})=${Integer.toHexString(bmp.getPixel(l + d, t + 1))}")
            sb.append("\n")
        }
        probe("A-after-focus")
        row.animate().cancel(); row.scaleX = 1.03f; row.scaleY = 1.03f
        probe("B-forced-1.03")
        row.scaleX = 1f; row.scaleY = 1f
        probe("C-forced-1.0")
        val f = File("build/screens/debug.txt")
        f.parentFile!!.mkdirs()
        f.writeText(sb.toString())
    }

    @Test
    fun settingsScreens() {
        for ((i, s) in listOf<String?>(null, "prayer", "wallpaper", "home", "general").withIndex()) {
            val intent = Intent(ctx, SettingsActivity::class.java)
            if (s != null) intent.putExtra("section", s)
            val a = Robolectric.buildActivity(SettingsActivity::class.java, intent).setup().get()
            // Focus the second row so the focused style shows.
            TestUi.rowsIn(a.window.decorView).getOrNull(1)?.let { it.requestFocus(); it.onFocusChangeListener?.onFocusChange(it, true) }
            TestUi.idle()
            save("1${i}_settings_${s ?: "main"}", render(a.window.decorView))
        }
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent(ctx, SettingsActivity::class.java)).setup().get()
        TestUi.click(a.window.decorView, "Glass Theme")
        val base = render(a.window.decorView)
        overlay(base, ShadowDialog.getLatestDialog().window!!.decorView, null, null, true)
        save("15_settings_theme_dialog", base)
    }
}
