package com.mytv.tvos

import android.app.Dialog
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
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.io.FileOutputStream

/** Logic and screens of the 2.5 features, simulated on the JVM (Robolectric). Nothing here runs on a real TV. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1440dp-h810dp-land-tvdpi")
class FeatureTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun sp() = ctx.getSharedPreferences("tvos_settings", Context.MODE_PRIVATE)

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(o: Any, n: String): T { val f = o.javaClass.getDeclaredField(n); f.isAccessible = true; return f.get(o) as T }
    private fun setField(o: Any, n: String, v: Any?) { val f = o.javaClass.getDeclaredField(n); f.isAccessible = true; f.set(o, v) }
    private fun call(o: Any, n: String, vararg a: Any) { val m = o.javaClass.declaredMethods.first { (it.name == n || it.name.startsWith("$n$")) && it.parameterCount == a.size }; m.isAccessible = true; m.invoke(o, *a) }

    private fun allViews(root: View): List<View> {
        val out = ArrayList<View>(); fun walk(v: View) { out.add(v); if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }; walk(root); return out
    }
    private fun texts(root: View) = allViews(root).filterIsInstance<TextView>().map { it.text.toString() }

    private fun leaveTouch(a: android.app.Activity) {
        try {
            val vri = View::class.java.getMethod("getViewRootImpl").invoke(a.window.decorView)
            for (n in listOf("ensureTouchModeLocally", "ensureTouchMode")) try {
                val m = vri.javaClass.getDeclaredMethod(n, Boolean::class.javaPrimitiveType); m.isAccessible = true; m.invoke(vri, false); break
            } catch (_: NoSuchMethodException) {}
        } catch (_: Throwable) {}
    }

    private fun layout(v: View) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1920, 1080)
    }

    private fun fake(n: Int = 14): List<AppItem> = (0 until n).map { i ->
        val bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 320f, 180f, Color.HSVToColor(floatArrayOf(i * 25f, 0.7f, 0.8f)), 0xFF101018.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 320f, 180f, p)
        val d = BitmapDrawable(ctx.resources, bmp)
        AppItem("com.fake.$i", "App %02d".format(i), d, d, Intent())
    }

    private fun home(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        a.setApps(fake()); idle(); leaveTouch(a); layout(a.window.decorView); idle(); layout(a.window.decorView)
        return a
    }

    // ---------------------------------------------------------------- network

    @Test fun networkDecisionTable() {
        val wifiOk = NetInfo(NetKind.WIFI, true, true)
        val wifiUnval = NetInfo(NetKind.WIFI, true, false)
        val eth = NetInfo(NetKind.ETHERNET, true, true)
        assertEquals(NetKind.WIFI to Internet.YES, Net.decide(listOf(wifiOk), null, false))
        assertEquals("not validated and not probed yet: undecided, never 'no internet'", NetKind.WIFI to Internet.CHECKING, Net.decide(listOf(wifiUnval), null, false))
        assertEquals(NetKind.WIFI to Internet.NO, Net.decide(listOf(wifiUnval), false, false))
        assertEquals("the probe reaches the internet although Android never validated the network", NetKind.WIFI to Internet.YES, Net.decide(listOf(wifiUnval), true, false))
        assertEquals("a successful weather download is proof", NetKind.WIFI to Internet.YES, Net.decide(listOf(wifiUnval), false, true))
        assertEquals(NetKind.NONE to Internet.NO, Net.decide(emptyList(), null, false))
        assertEquals("boxes that report no network while the internet works", NetKind.OTHER to Internet.YES, Net.decide(emptyList(), null, true))
        assertEquals(NetKind.WIFI to Internet.YES, Net.decide(listOf(eth, wifiUnval), null, false))
        assertEquals(NetKind.ETHERNET to Internet.YES, Net.decide(listOf(eth), null, false))
        assertEquals("a network without the INTERNET capability does not count", NetKind.NONE to Internet.NO, Net.decide(listOf(NetInfo(NetKind.WIFI, false, false)), null, false))
    }

    @Test fun ssidIsNeverWrong() {
        assertEquals("Home", Net.clean("\"Home\""))
        assertEquals("My WiFi", Net.clean("  \"My WiFi\" "))
        assertNull(Net.clean("<unknown ssid>"))
        assertNull(Net.clean("<UNKNOWN SSID>"))
        assertNull(Net.clean("\"\""))
        assertNull(Net.clean(null))
        val hidden = Net.describe(NetState(NetKind.WIFI, Internet.YES, null, true))
        assertTrue(hidden.second, hidden.second.contains("hidden") && hidden.second.contains("Internet OK"))
        assertEquals("Home\nInternet OK", Net.describe(NetState(NetKind.WIFI, Internet.YES, "Home", false)).second)
        assertTrue(Net.describe(NetState(NetKind.WIFI, Internet.NO, "Home", false)).second.contains("no internet"))
        assertEquals("Not connected", Net.describe(NetState(NetKind.NONE, Internet.NO, null, false)).second)
    }

    @Test fun controlCenterShowsTheNetworkLines() {
        val a = home()
        call(a, "openCC"); idle()
        val cc: View = field(a, "ccView")
        val t = texts(cc)
        assertTrue(t.toString(), t.any { it == "Wi-Fi" || it == "Network" || it == "Ethernet" })
        assertTrue("a status line is shown: $t", t.any { it.contains("internet", true) || it.contains("Not connected") })
        call(a, "closeCC")
    }

    // ---------------------------------------------------------------- clock

    @Test fun clockSettingsRoundTripAndMigrate() {
        val s = sp()
        val d = ClockCfg.load(s)
        assertEquals("digital_thin", d.kind); assertEquals(100, d.pct); assertTrue(d.date); assertFalse(d.seconds); assertFalse(d.glass); assertEquals("left", d.pos)
        s.edit().putString("clock_style", "bold").putString("clock_size", "xl").apply()
        val m = ClockCfg.load(s)
        assertEquals("old font setting becomes the default font", "bold", m.font); assertEquals(130, m.pct)
        val c = ClockCfg("flip", 115, 0xFFF5C76B.toInt(), "serif", true, false, true, "center")
        c.save(s)
        val r = ClockCfg.load(ctx.getSharedPreferences("tvos_settings", Context.MODE_PRIVATE))
        assertEquals("flip", r.kind); assertEquals(115, r.pct); assertEquals(0xFFF5C76B.toInt(), r.color); assertEquals("serif", r.font)
        assertTrue(r.seconds); assertFalse(r.date); assertTrue(r.glass); assertEquals("center", r.pos)
        s.edit().putString("clock_kind", "nonsense").apply()
        assertEquals("digital_thin", ClockCfg.load(s).kind)
        assertEquals(7, ClockCfg.KINDS.size)
    }

    @Test fun everyClockDesignMeasuresAndDraws() {
        val u = U(ctx)
        val sheet = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val cv = Canvas(sheet)
        val bg = Paint(); bg.shader = LinearGradient(0f, 0f, 1920f, 1080f, 0xFF2B6CB0.toInt(), 0xFF7B2CBF.toInt(), Shader.TileMode.CLAMP); cv.drawRect(0f, 0f, 1920f, 1080f, bg)
        var x = 30f; var y = 30f; var rowH = 0f
        for ((i, kind) in ClockCfg.KINDS.withIndex()) {
            for (glass in listOf(false, true)) {
                val v = ClockView(ctx, u)
                v.is24 = i % 2 == 0
                v.cfg = ClockCfg(kind, 70, Color.WHITE, "light", i == 2, true, glass, "left")
                v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST))
                assertTrue("$kind measures", v.measuredWidth > 20 && v.measuredHeight > 20)
                assertTrue("$kind stays on the screen at 150%: ${v.measuredWidth}", v.measuredWidth < 1920)
                v.layout(0, 0, v.measuredWidth, v.measuredHeight)
                if (x + v.measuredWidth > 1900) { x = 30f; y += rowH + 30f; rowH = 0f }
                cv.save(); cv.translate(x, y); v.draw(cv); cv.restore()
                x += v.measuredWidth + 30f; rowH = maxOf(rowH, v.measuredHeight.toFloat())
            }
        }
        // every design at the largest size still fits the screen width
        for (kind in ClockCfg.KINDS) {
            val v = ClockView(ctx, u); v.cfg = ClockCfg(kind, 150, Color.WHITE, "bold", true, true, true, "left")
            v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST))
            assertTrue("$kind at 150% is ${v.measuredWidth}x${v.measuredHeight}", v.measuredWidth < 1500 && v.measuredHeight < 700)
        }
        save("f10_clock_gallery", sheet)
    }

    @Test fun clockCustomiserPreviewsSavesAndCancels() {
        val a = home()
        sp().edit().clear().apply()
        ClockCustomizer.show(a, sp(), onSaved = { })
        idle()
        var dlg: Dialog = ShadowDialog.getLatestDialog()
        assertTrue(dlg.isShowing)
        val rows = allViews(dlg.window!!.decorView).filter { it.isFocusable && it.isClickable }
        assertTrue("9 setting rows and 3 buttons, found ${rows.size}", rows.size >= 12)
        rows[0].performClick(); idle() // Style: next design
        assertEquals("nothing is stored before Save", "digital_thin", ClockCfg.load(sp()).kind)
        rows.first { (it as? TextView)?.text?.toString() == "Cancel" }.performClick(); idle()
        assertEquals("Cancel keeps the old clock", "digital_thin", ClockCfg.load(sp()).kind)
        assertFalse(dlg.isShowing)

        var saved = false
        ClockCustomizer.show(a, sp(), onSaved = { saved = true })
        idle(); dlg = ShadowDialog.getLatestDialog()
        val rows2 = allViews(dlg.window!!.decorView).filter { it.isFocusable && it.isClickable }
        rows2[0].performClick(); rows2[1].performClick(); rows2[5].performClick(); rows2[7].performClick(); idle()
        rows2.first { (it as? TextView)?.text?.toString() == "Save" }.performClick(); idle()
        val c = ClockCfg.load(ctx.getSharedPreferences("tvos_settings", Context.MODE_PRIVATE))
        assertTrue(saved)
        assertEquals("digital_big", c.kind); assertEquals(115, c.pct); assertTrue(c.seconds); assertTrue(c.glass)
        save("f11_clock_customiser", renderDialogOver(a, dlg))
    }

    @Test fun homeClockFollowsTheSavedDesign() {
        sp().edit().clear().apply()
        ClockCfg("analog_hands", 100, Color.WHITE, "light", false, false, true, "center").save(sp())
        val a = home()
        val cv: ClockView = field(a, "bigClock")
        assertEquals("analog_hands", cv.cfg.kind)
        assertEquals("date line hidden when switched off", View.GONE, field<TextView>(a, "dateText").visibility)
        assertTrue(cv.measuredWidth > 100)
        save("f12_home_analog", render(a.window.decorView))
    }

    // ---------------------------------------------------------------- pages bar

    @Test fun pagesBarHasTheSixPagesAndWorksWithTheRemote() {
        val a = home()
        val bar: LinearLayout = field(a, "pagesBar")
        assertEquals(6, bar.childCount)
        assertEquals(listOf("Home", "Apps", "Weather", "Wallpapers", "Clock", "Settings"), (0 until 6).map { bar.getChildAt(it).contentDescription.toString() })
        assertTrue((0 until 6).all { bar.getChildAt(it).isFocusable })
        assertTrue("Home is the selected page at start", bar.getChildAt(0).isSelected)
        // Left / Right walk the bar.
        bar.getChildAt(0).requestFocus()
        var cur = bar.getChildAt(0)
        for (i in 1..5) { val n = cur.focusSearch(View.FOCUS_RIGHT); assertTrue("RIGHT from item ${i - 1}", n === bar.getChildAt(i)); n!!.requestFocus(); cur = n }
        // Up from a dock tile reaches the top row; Down from the bar returns to the tiles.
        val grid: RecyclerView = field(a, "grid")
        val tile = grid.findViewHolderForAdapterPosition(2)!!.itemView
        tile.requestFocus()
        val up = tile.focusSearch(View.FOCUS_UP)
        assertNotNull("UP from the dock goes somewhere", up)
        assertTrue("it is the bar or the capsule", up!!.parent === bar || up === field<View>(a, "capsule"))
        bar.getChildAt(3).requestFocus()
        val handled = a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
        assertTrue("DOWN on the bar is handled", handled)
        idle()
        assertTrue("and focus is back on the tiles", grid.findContainingViewHolder(a.window.currentFocus ?: grid) != null)
    }

    @Test fun pagesOpenTheirScreens() {
        val a = home()
        val bar: LinearLayout = field(a, "pagesBar")
        val grid: RecyclerView = field(a, "grid")
        // Apps: a full page with every installed app.
        bar.getChildAt(1).performClick(); idle()
        var page: View? = field(a, "pageView")
        assertNotNull("Apps page is open", page)
        val appsRv = allViews(page!!).filterIsInstance<RecyclerView>().first()
        assertEquals("all 14 apps plus Settings", 15, appsRv.adapter!!.itemCount)
        assertEquals("the home grid steps aside", View.INVISIBLE, grid.visibility)
        assertTrue(bar.getChildAt(1).isSelected)
        save("f16_apps_page", render(a.window.decorView))
        a.onBackPressed(); idle()
        assertNull("Back closes the page", field<View?>(a, "pageView"))
        assertEquals(View.VISIBLE, grid.visibility)
        assertTrue("Home is selected again", bar.getChildAt(0).isSelected)

        // Weather: a full page, not a small dialog.
        bar.getChildAt(2).performClick(); idle()
        page = field(a, "pageView")
        assertNotNull("Weather page is open", page)
        assertTrue(texts(page!!).contains("Refresh"))
        save("f13_weather_page", render(a.window.decorView))
        bar.getChildAt(0).performClick(); idle()
        assertNull(field<View?>(a, "pageView"))

        // Wallpapers: choices from the TV and an online gallery.
        bar.getChildAt(3).performClick(); idle()
        page = field(a, "pageView")
        val t = texts(page!!)
        assertTrue("wallpaper choices: $t", t.contains("Choose from TV storage") && t.contains("Default") && t.contains("None"))
        assertTrue("online gallery list is present", allViews(page).filterIsInstance<RecyclerView>().isNotEmpty())
        save("f17_wallpapers_page", render(a.window.decorView))
        bar.getChildAt(0).performClick(); idle()

        // Clock: the customiser.
        bar.getChildAt(4).performClick(); idle()
        var d: Dialog = ShadowDialog.getLatestDialog()
        assertTrue("Clock page is open", d.isShowing && texts(d.window!!.decorView).contains("Save"))
        d.dismiss(); idle()
        bar.getChildAt(5).performClick(); idle()
        assertNotNull("Settings starts", shadowOf(a).nextStartedActivity)
        save("f14_home_with_bar", render(a.window.decorView))
    }

    @Test fun numeralsCanBeEnglishArabicOrAutomatic() {
        try {
            assertEquals("٠١٢:٣٤", Digits.convert("012:34", "arabic"))
            assertEquals("12:30", Digits.convert("١٢:٣٠", "latin"))
            assertEquals("12:30", Digits.convert("12:30", "auto"))
            Digits.mode = "arabic"
            assertEquals("٠٥:٣٠", TimeFmt.display("05:30", true))
            Digits.mode = "auto"
            // The clock page has the row, and Save keeps it.
            val a = home()
            ClockCustomizer.show(a, sp(), onSaved = { })
            idle()
            val d = ShadowDialog.getLatestDialog()
            val row = allViews(d.window!!.decorView).first { v -> v is LinearLayout && v.isClickable && (v.getChildAt(0) as? TextView)?.text?.toString() == "Numerals" }
            row.performClick(); idle()
            row.performClick(); idle()
            assertEquals("arabic", Digits.mode)
            allViews(d.window!!.decorView).first { (it as? TextView)?.text?.toString() == "Save" }.performClick(); idle()
            assertEquals("arabic", Prefs(ctx).numerals)
        } finally { Digits.mode = "auto" }
    }

    @Test fun paleGlassTintGetsEnoughShadeToStayReadable() {
        val s = sp()
        s.edit().putInt("glass_tint", 0).putInt("glass_level", 3).putInt("glass_shade", 0).apply(); GlassStyle.load(s)
        assertTrue("Frost White, Strong, shade None still darkened", GlassStyle.shadeAlpha(false) >= GlassStyle.bodyAlpha())
        val graphite = GlassStyle.TINTS.indexOfFirst { it.first == "Graphite" }
        s.edit().putInt("glass_tint", graphite).apply(); GlassStyle.load(s)
        assertEquals("a dark tint keeps the chosen shade", 0, GlassStyle.shadeAlpha(false))
        s.edit().clear().apply(); GlassStyle.load(s)
    }

    @Test fun focusStaysInsideControlCenter() {
        val a = home()
        call(a, "openCC"); idle()
        val cc: View = field(a, "ccView")
        for (k in listOf(android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) {
            a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, k)); idle()
            var c: View? = a.window.currentFocus; var inside = false
            while (c != null) { if (c === cc) inside = true; c = c.parent as? View }
            assertTrue("focus left Control Center on key $k", inside)
        }
    }

    @Test fun focusStaysInsideAPageAndWrapsAround() {
        val a = home()
        val bar: LinearLayout = field(a, "pagesBar")
        val grid: RecyclerView = field(a, "grid")
        // Wallpapers page: Down never reaches the home tiles behind it.
        bar.getChildAt(3).performClick(); idle(); layout(a.window.decorView); idle()
        val page: View = field(a, "pageView")
        a.window.decorView.requestFocus()
        allViews(page).first { it.isFocusable }.requestFocus()
        repeat(12) {
            a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN)); idle()
            assertNull("focus reached the hidden home grid", grid.findContainingViewHolder(a.window.currentFocus ?: grid))
        }
        // The pages bar wraps: last -> first and first -> last.
        val got = bar.getChildAt(5).requestFocus()
        assertTrue("bar item 5 took focus (requestFocus=$got, focus=${a.window.currentFocus}, touch=${bar.isInTouchMode})", a.window.currentFocus === bar.getChildAt(5))
        val ms = a.javaClass.declaredMethods.filter { it.name.startsWith("wrapRow") }
        val m = ms.first { it.parameterCount == 1 }; m.isAccessible = true
        val f0 = a.window.currentFocus!!; val par = f0.parent
        val dbg = "methods=${ms.map { it.name + it.parameterCount }} parent=${par?.javaClass?.simpleName} same=${par === bar} orient=${(par as? LinearLayout)?.orientation} idx=${bar.indexOfChild(f0)}"
        val direct = m.invoke(a, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        val info = (0 until bar.childCount).map { val c = bar.getChildAt(it); "${c.isFocusable}/${c.visibility}/${c.hasFocus()}" }
        assertTrue("$dbg direct wrapRow=$direct children=$info focus=${a.window.currentFocus}", a.window.currentFocus === bar.getChildAt(0))
        bar.getChildAt(5).requestFocus()
        a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)); idle()
        assertTrue("RIGHT on the last item wraps to Home (focus=${a.window.currentFocus})", a.window.currentFocus === bar.getChildAt(0))
        a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT)); idle()
        assertTrue("LEFT on the first item wraps to Settings", a.window.currentFocus === bar.getChildAt(5))
    }

    @Test fun cursorStaysNearWhereItWasWhenTheListChanges() {
        val a = home()
        val grid: RecyclerView = field(a, "grid")
        val tile = grid.findViewHolderForAdapterPosition(6)!!.itemView
        tile.requestFocus(); idle()
        a.setApps(fake(13)); idle(); layout(a.window.decorView); idle()
        val f = a.window.currentFocus
        assertNotNull("something still has focus", f)
        val pos = grid.findContainingViewHolder(f!!)?.bindingAdapterPosition ?: -1
        assertTrue("the cursor stayed in the grid near position 6, not at the start (was $pos)", pos >= 4)
    }

    @Test fun glassStyleIsOneColourForEverything() {
        val s = sp()
        s.edit().clear().apply()
        GlassStyle.load(s)
        assertEquals(0, GlassStyle.tint); assertEquals(2, GlassStyle.level); assertEquals(1, GlassStyle.shade)
        s.edit().putInt("glass_tint", 99).putInt("glass_level", -5).putInt("glass_shade", 7).apply()
        GlassStyle.load(s)
        assertEquals("values are clamped", GlassStyle.TINTS.size - 1, GlassStyle.tint); assertEquals(0, GlassStyle.level); assertEquals(2, GlassStyle.shade)
        assertTrue(GlassStyle.bodyAlpha() < 0x30)
        s.edit().putInt("glass_level", 3).apply(); GlassStyle.load(s)
        assertTrue("Strong is more opaque than Clear", GlassStyle.bodyAlpha() > 0x50)
        assertTrue(GlassStyle.isLightBase(0x38FFFFFF) && !GlassStyle.isLightBase(0xE62A2A2C.toInt()))
        // The settings page changes it for real.
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent(ctx, SettingsActivity::class.java)).setup().get()
        idle()
        val row = allViews(a.window.decorView).first { v -> v is LinearLayout && v.isClickable && (v.getChildAt(0) as? TextView)?.text?.toString() == "Glass" }
        row.performClick(); idle()
        val tints = allViews(a.window.decorView).first { v -> v is LinearLayout && v.isClickable && (v.getChildAt(0) as? TextView)?.text?.toString() == "Glass Color" }
        tints.performClick(); idle()
        val dlg = ShadowDialog.getLatestDialog()
        allViews(dlg.window!!.decorView).first { (it as? TextView)?.text?.toString() == "Mint" }.performClick(); idle()
        assertEquals(GlassStyle.TINTS.indexOfFirst { it.first == "Mint" }, sp().getInt("glass_tint", -1))
        assertEquals(GlassStyle.TINTS.indexOfFirst { it.first == "Mint" }, GlassStyle.tint)
    }

    @Test fun sleepScreenShowsGreetingTimeAndDate() {
        val a = home()
        call(a, "startScreensaver"); idle()
        val f: View? = field(a, "screensaver")
        assertNotNull(f)
        val t = texts(f!!)
        assertTrue("greeting in capitals: $t", t.any { it.startsWith("GOOD ") })
        assertTrue("date in capitals: $t", t.any { it.contains(java.text.SimpleDateFormat("MMMM", java.util.Locale.ENGLISH).format(java.util.Date()).uppercase()) })
        assertTrue("clock view present", allViews(f).any { it is ClockView })
        save("f18_sleep", render(a.window.decorView))
        call(a, "stopScreensaver")
    }

    @Test fun thumbTilesDrawWithAndWithoutPictures() {
        val u = U(ctx)
        val v = ThumbView(ctx, u)
        v.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(225, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 400, 225)
        val c = Canvas(Bitmap.createBitmap(400, 225, Bitmap.Config.ARGB_8888))
        v.draw(c)
        v.failed = true; v.draw(c)
        v.bmp = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.CYAN) }
        v.draw(c)
        v.requestFocus(); v.draw(c)
    }

    @Test fun weatherShownWithCityAndConditionIcon() {
        val a = home()
        val w = Weather(31, 95, false, 33, 40, 12, DayForecast(95, 33, 20, 70), DayForecast(1, 30, 19, 0))
        setField(a, "weather", w)
        call(a, "updateInfo"); idle()
        val g: WeatherGlyph = field(a, "wxGlyph")
        assertEquals(95, g.code); assertTrue("night flag follows the data", g.night)
        val city = Prefs(ctx).cityName.substringBefore(",").trim()
        assertTrue("city '$city' is in the weather pill", texts(a.window.decorView).any { it.contains(city) && it.contains("31°") })
        save("f15_home_weather", render(a.window.decorView))
    }

    // ---------------------------------------------------------------- prayer

    private fun seedTimings(day: String) {
        val p = Prefs(ctx)
        val date = PrayerRepository.cityToday(ctx)
        val body = JSONObject().put("data", JSONObject()
            .put("timings", JSONObject().put("Fajr", "05:00 (EET)").put("Sunrise", "06:30").put("Dhuhr", "12:00").put("Asr", "15:00").put("Maghrib", "17:30").put("Isha", day))
            .put("meta", JSONObject().put("timezone", "Asia/Amman")))
        p.timingsCache = JSONObject().put("$date|${p.latitude}|${p.longitude}|${p.method}", body.toString()).toString()
    }

    @Test fun prayerCorrectionsAreAppliedOnceAndCanBeReset() {
        seedTimings("19:00")
        val p = Prefs(ctx)
        val date = PrayerRepository.cityToday(ctx)
        assertEquals("05:00", PrayerRepository.getTimings(ctx, date)!!.times["Fajr"])
        for (v in listOf(1, 2, 3, 4, -1, -2, -3, -4)) {
            p.setPrayerOffset("Fajr", v)
            val expect = PrayerRepository.shifted("05:00", v)
            assertEquals("offset $v", expect, PrayerRepository.getTimings(ctx, date)!!.times["Fajr"])
            assertEquals("asking again does not add the offset twice", expect, PrayerRepository.getTimings(ctx, date)!!.times["Fajr"])
            assertEquals("the original stays available", "05:00", PrayerRepository.cachedRaw(ctx, date)!!.times["Fajr"])
        }
        p.setPrayerOffset("Fajr", 3)
        assertEquals("a new Prefs object (app restart) reads the same", 3, Prefs(ctx).prayerOffset("Fajr"))
        assertEquals("05:03", PrayerRepository.getTimings(ctx, date)!!.times["Fajr"])
        p.setPrayerOffset("Fajr", 0)
        assertEquals("05:00", PrayerRepository.getTimings(ctx, date)!!.times["Fajr"])
        assertEquals("00:02", PrayerRepository.shifted("23:58", 4))
        assertEquals("05:08", PrayerRepository.shifted("05:12", -4))
    }

    @Test fun prayerSettingsShowOriginalAndAdjustedTimes() {
        seedTimings("19:00")
        Prefs(ctx).setPrayerOffset("Asr", 2)
        Prefs(ctx).timeFormat = "24"
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent(ctx, SettingsActivity::class.java).putExtra("page", "prayer")).setup().get()
        idle()
        val t = texts(a.window.decorView)
        assertTrue("original → adjusted shown: $t", t.any { it.contains("15:00") && it.contains("15:02") && it.contains("+2") })
        assertTrue("reset row exists: $t", t.contains("Reset all corrections"))
        assertTrue("media pause switch exists: $t", t.contains("Pause Playback at Adhan"))
        val resetRow = allViews(a.window.decorView).first { v -> v is LinearLayout && v.isClickable && (v.getChildAt(0) as? TextView)?.text?.toString() == "Reset all corrections" }
        resetRow.performClick(); idle()
        assertEquals(0, Prefs(ctx).prayerOffset("Asr"))
    }

    @Test fun stopButtonOnlyWhileTheAdhanPlays() {
        val a = home()
        val pill: View = field(a, "stopPill")
        call(a, "updateStopPill")
        assertEquals("hidden when nothing plays", View.GONE, pill.visibility)
        assertFalse(AdhanService.isPlaying)
    }

    // ---------------------------------------------------------------- wallpaper

    private fun png(w: Int, h: Int, color: Int): File {
        val f = File(ctx.cacheDir, "src_${w}x$h.png")
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); b.eraseColor(color)
        FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    @Test fun wallpaperIsDecodedSmallCopiedAndSurvives() {
        val src = png(4000, 2000, Color.rgb(200, 80, 20))
        val bmp = WallpaperPicker.decodeStream({ java.io.FileInputStream(src) })
        assertNotNull(bmp)
        assertTrue("never the full-size picture: ${bmp!!.width}", bmp.width <= 2000)
        assertEquals("keeps the proportions", 2.0f, bmp.width.toFloat() / bmp.height, 0.02f)
        assertTrue(WallpaperPicker.saveAsWallpaper(ctx, bmp))
        val path = sp().getString("wall_path", null)!!
        assertEquals("custom", sp().getString("wall_mode", null))
        assertTrue("a private copy", path.startsWith(ctx.filesDir.absolutePath))
        src.delete() // the original disappears (a removed USB stick): the wallpaper must stay
        val again = Wallpapers.decode(ctx, sp())
        assertNotNull(again)
        val px = again!!.getPixel(again.width / 2, again.height / 2)
        assertTrue("same colours", Color.red(px) > 150 && Color.blue(px) < 80)
        val second = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.BLUE) }
        assertTrue(WallpaperPicker.saveAsWallpaper(ctx, second))
        assertFalse("the previous private copy is removed", File(path).exists())
    }

    @Test fun unreadableWallpaperFilesAreHandledSafely() {
        assertNull(WallpaperPicker.decodeStream({ null }))
        assertNull(WallpaperPicker.decodeStream({ java.io.ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }))
        sp().edit().putString("wall_mode", "custom").putString("wall_path", "/nonexistent/x.jpg").apply()
        assertNull("a missing file gives no picture, not a crash", Wallpapers.decode(ctx, sp()))
        val a = home()
        a.onWindowFocusChanged(true); idle()
    }

    @Test fun wallpaperMenuOffersTheTvFiles() {
        val a = home()
        WallpaperPicker.show(a) { }
        idle()
        val t = texts(ShadowDialog.getLatestDialog().window!!.decorView)
        assertTrue(t.toString(), t.contains("Choose from TV storage") && t.contains("None") && t.contains("Default"))
    }

    // ---------------------------------------------------------------- language

    @Test fun arabicLabelsExistForTheNewScreens() {
        L.ar = true
        try {
            for (k in listOf("Home", "Wallpapers", "Clock", "Style", "Seconds", "Preview", "Apply", "Refresh", "Internet OK", "Reset all corrections", "Pause Playback at Adhan", "Thin Digital", "Flip"))
                assertTrue("missing Arabic for $k", L.t(k) != k || k == "Flip")
        } finally { L.ar = false }
    }

    // ---------------------------------------------------------------- helpers for pictures

    private fun render(v: View): Bitmap { layout(v); val b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888); v.draw(Canvas(b)); return b }

    private fun renderDialogOver(a: android.app.Activity, d: Dialog): Bitmap {
        val base = render(a.window.decorView)
        val c = Canvas(base); c.drawColor(0x66000000)
        val v = d.window!!.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST))
        val w = v.measuredWidth.coerceAtLeast(1); val h = v.measuredHeight.coerceAtLeast(1)
        v.layout(0, 0, w, h)
        c.save(); c.translate(((1920 - w) / 2).toFloat(), ((1080 - h) / 2).toFloat()); v.draw(c); c.restore()
        return base
    }

    private fun save(n: String, b: Bitmap) {
        val f = File("build/screens/$n.jpg"); f.parentFile!!.mkdirs()
        f.outputStream().use { Bitmap.createScaledBitmap(b, 1280, 720, true).compress(Bitmap.CompressFormat.JPEG, 82, it) }
    }
}
