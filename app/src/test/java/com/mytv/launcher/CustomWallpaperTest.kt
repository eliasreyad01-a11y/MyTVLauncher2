package com.mytv.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The glass (Frost / dock band / settings backdrop) must follow the wallpaper that is really on screen. Uses a loud
 * orange-to-teal test picture so the difference from the built-in photo cannot be missed. Writes a render to build/screens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1440dp-h810dp-land-tvdpi")
class CustomWallpaperTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun reset() = Frost.setSource(null)

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

    private fun waitUntil(timeoutMs: Long = 15_000, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            TestUi.idle()
            if (cond()) return true
            Thread.sleep(50)
        }
        TestUi.idle()
        return cond()
    }

    private fun testPicture(): File {
        val bmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, 1920f, 0f, Color.rgb(235, 110, 10), Color.rgb(10, 170, 175), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 1920f, 1080f, p)
        val f = File(ctx.filesDir, "test_wallpaper.png")
        f.parentFile!!.mkdirs()
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    private fun fakeApps() = (0 until 14).map { i ->
        val d = ColorDrawable(Color.argb(255, 60 + i * 8, 70, 90))
        AppItem("com.fake.app$i", "App %02d".format(i), d, d, Intent())
    }

    private fun startHome() = Robolectric.buildActivity(MainActivity::class.java).setup().also { c ->
        val a = c.get()
        val adapter: AppAdapter = field(a, "adapter")
        waitUntil { adapter.itemCount > 0 }
        val apps = fakeApps()
        Prefs(ctx).favorites = apps.take(6).map { it.packageName }
        setField(a, "loadedApps", apps)
        call(a, "applyLists")
        TestUi.idle()
    }

    private fun render(view: View): Bitmap {
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1920, 1080)
        val bmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        return bmp
    }

    private fun save(name: String, bmp: Bitmap) {
        val f = File("build/screens/$name.jpg")
        f.parentFile!!.mkdirs()
        val small = Bitmap.createScaledBitmap(bmp, 1280, 720, true)
        f.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    }

    private fun px(b: Bitmap, fx: Float, fy: Float): Int = b.getPixel((b.width * fx).toInt(), (b.height * fy).toInt())

    @Test
    fun glassFollowsACustomWallpaperAndGoesBackToTheBuiltInPhoto() {
        val log = StringBuilder()
        val builtIn = Frost.bitmap(ctx)!!
        val builtInLeft = px(builtIn, 0.12f, 0.15f)
        log.append("built-in sky (left) = #${Integer.toHexString(builtInLeft)}\n")

        val v0 = Frost.version
        val prefs = Prefs(ctx)
        prefs.wallpaperType = "image"
        prefs.wallpaperUri = Uri.fromFile(testPicture()).toString()
        val c = startHome()
        val a = c.get()
        assertTrue("custom wallpaper never reached the glass (version $v0 -> ${Frost.version})", waitUntil { Frost.version > v0 })

        val glass = Frost.bitmap(ctx)!!
        val left = px(glass, 0.12f, 0.5f)
        val right = px(glass, 0.88f, 0.5f)
        log.append("custom: glass left = #${Integer.toHexString(left)} right = #${Integer.toHexString(right)} (${glass.width}x${glass.height})\n")
        assertTrue("left of the glass should be orange", Color.red(left) > Color.blue(left) + 80)
        assertTrue("right of the glass should be teal", Color.blue(right) > Color.red(right) + 80)

        // The dock band, the cards and the header buttons all rebuild from it: render the real home screen.
        val shot = render(a.window.decorView)
        save("20_home_custom_wallpaper", shot)

        // The settings pages open afterwards use the same picture.
        val s = Robolectric.buildActivity(SettingsActivity::class.java, Intent(ctx, SettingsActivity::class.java)).setup().get()
        val back = Dialogs.backdrop(s)
        assertNotNull("settings backdrop", back)

        // Back to the built-in photo: the glass must follow again.
        val v1 = Frost.version
        prefs.wallpaperType = "none"
        prefs.wallpaperUri = null
        c.pause().resume()
        assertTrue("resetting the wallpaper never reached the glass", waitUntil { Frost.version > v1 })
        val after = px(Frost.bitmap(ctx)!!, 0.12f, 0.15f)
        log.append("after reset: sky (left) = #${Integer.toHexString(after)}\n")
        assertTrue("glass is orange again after reset", !(Color.red(after) > Color.blue(after) + 80))

        val f = File("build/screens/debug2.txt")
        f.parentFile!!.mkdirs()
        f.writeText(log.toString())
    }
}
