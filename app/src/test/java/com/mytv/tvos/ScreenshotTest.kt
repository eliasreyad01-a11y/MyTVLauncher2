package com.mytv.tvos

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * SIMULATED screens (Robolectric native graphics, fake apps, no live blur): used to compare layout, sizes and colours
 * with the reference recordings. Not a test on a real TV.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1440dp-h810dp-land-tvdpi")
class ScreenshotTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private val names = listOf("Netflix", "YouTube", "Prime Video", "Disney+", "Spotify", "Plex", "Kodi", "Browser",
        "Files", "Games", "Music", "Live TV", "Photos", "Maps", "News", "Radio", "Clock", "Camera")
    private val cols = listOf(0xFFB20710, 0xFFFF0000, 0xFF00A8E1, 0xFF113CCF, 0xFF1DB954, 0xFFE5A00D, 0xFF31A9D6, 0xFF4285F4,
        0xFFFFB300, 0xFF7E57C2, 0xFFEC407A, 0xFF26A69A, 0xFFFF7043, 0xFF43A047, 0xFF6D4C41, 0xFF5C6BC0, 0xFF455A64, 0xFF8E24AA)

    private fun fake(): List<AppItem> = names.mapIndexed { i, n ->
        val bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 320f, 180f, cols[i].toInt(), 0xFF101018.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 320f, 180f, p)
        p.shader = null; p.color = Color.WHITE; p.textSize = 30f; p.textAlign = Paint.Align.CENTER
        c.drawText(n, 160f, 100f, p)
        val d = BitmapDrawable(ctx.resources, bmp)
        AppItem("com.fake.$i", n, d, d, Intent())
    }

    private fun render(v: View): Bitmap {
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1920, 1080)
        val b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b)); return b
    }

    private fun save(n: String, b: Bitmap) {
        val f = File("build/screens/$n.jpg"); f.parentFile!!.mkdirs()
        f.outputStream().use { Bitmap.createScaledBitmap(b, 1280, 720, true).compress(Bitmap.CompressFormat.JPEG, 82, it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(o: Any, n: String): T { val f = o.javaClass.getDeclaredField(n); f.isAccessible = true; return f.get(o) as T }
    private fun call(o: Any, n: String, vararg a: Any) { val m = o.javaClass.declaredMethods.first { it.name == n && it.parameterCount == a.size }; m.isAccessible = true; m.invoke(o, *a) }

    private fun home(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        a.setApps(fake()); idle()
        try {
            val vri = View::class.java.getMethod("getViewRootImpl").invoke(a.window.decorView)
            for (n in listOf("ensureTouchModeLocally", "ensureTouchMode")) try {
                val m = vri.javaClass.getDeclaredMethod(n, Boolean::class.javaPrimitiveType); m.isAccessible = true; m.invoke(vri, false); break
            } catch (_: NoSuchMethodException) {}
        } catch (_: Throwable) {}
        return a
    }

    private fun shot(a: MainActivity, n: String) { idle(); save(n, render(a.window.decorView)) }

    @Test fun homeAndLibrary() {
        val a = home()
        val grid: RecyclerView = field(a, "grid")
        shot(a, "t01_home")
        grid.findViewHolderForAdapterPosition(1)?.itemView?.requestFocus(); idle()
        shot(a, "t02_home_focus")
        grid.scrollBy(0, (633 * a.resources.displayMetrics.widthPixels / 1920f).toInt()); idle()
        grid.findViewHolderForAdapterPosition(8)?.itemView?.requestFocus(); idle()
        shot(a, "t03_library")
        assertTrue(true)
    }

    @Test fun folderAndControlCenter() {
        val a = home()
        val st = Store(ctx)
        val lay = st.layout(fake())
        val id = st.newFolder(lay, "com.fake.8")
        for (i in 9..16) st.addToFolder(st.layout(fake()), "f:$id", "com.fake.$i")
        a.setApps(fake()); idle()
        val grid: RecyclerView = field(a, "grid")
        grid.scrollBy(0, (633 * a.resources.displayMetrics.widthPixels / 1920f).toInt()); idle()
        shot(a, "t04_library_folder")
        val adapter: HomeAdapter = field(a, "adapter")
        val e = adapter.cells.first { it.isFolder }
        call(a, "openFolder", e); idle()
        shot(a, "t05_folder")
        call(a, "closeFolder"); idle()
        call(a, "toggleCC"); idle()
        shot(a, "t06_control_center")
    }

    @Test fun settings() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent(ctx, SettingsActivity::class.java)).setup().get()
        idle()
        save("t07_settings", render(a.window.decorView))
    }
}
