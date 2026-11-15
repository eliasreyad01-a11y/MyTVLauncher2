package com.mytv.launcher

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog

/** Drives the real SettingsActivity: opens every page, clicks every row, and changes settings through the real dialogs. */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun open(section: String? = null): SettingsActivity {
        val intent = Intent(ctx, SettingsActivity::class.java)
        if (section != null) intent.putExtra("section", section)
        return Robolectric.buildActivity(SettingsActivity::class.java, intent).setup().get()
    }

    private fun root(a: SettingsActivity): View = a.findViewById(android.R.id.content)

    @Test
    fun everyPageBuildsAndEveryRowCanBeClickedWithoutCrashing() {
        for (section in listOf<String?>(null, "prayer", "wallpaper", "home", "general")) {
            val a = open(section)
            val rows = TestUi.rowsIn(root(a))
            assertTrue("page $section has rows (${rows.size})", rows.size >= 4)
            for (i in rows.indices) {
                // Rows are looked up again each time: some clicks rebuild or recreate the page.
                val current = TestUi.rowsIn(root(a))
                if (i < current.size) current[i].performClick()
                TestUi.idle()
                Dialogs.dismissAll()
                ShadowDialog.reset()
            }
        }
    }

    @Test
    fun mainPageListsTheRealSections() {
        val titles = TestUi.rowsIn(root(open())).map { TestUi.title(it) }
        for (t in listOf("Glass Theme", "Glass Transparency", "Date", "Adhan and Prayer Times", "Wallpaper", "Home Screen", "General")) {
            assertTrue("missing row $t in $titles", t in titles)
        }
    }

    @Test
    fun glassThemeChoiceIsSaved() {
        val a = open()
        TestUi.click(root(a), "Glass Theme")
        TestUi.pick("Icy Blue")
        assertEquals(5, Prefs(ctx).glassTheme)
    }

    @Test
    fun glassTransparencyChoiceIsSaved() {
        val a = open()
        TestUi.click(root(a), "Glass Transparency")
        TestUi.pick("60%")
        assertEquals(60, Prefs(ctx).glassOpacity)
    }

    @Test
    fun dateModeChoiceIsSaved() {
        val a = open()
        TestUi.click(root(a), "Date")
        TestUi.pick("Hijri")
        assertEquals(2, Prefs(ctx).dateMode)
    }

    @Test
    fun homeToggleAndChoicesAreSaved() {
        val a = open("home")
        val r = root(a)
        assertEquals("On", TestUi.subtitle(TestUi.row(r, a.getString(R.string.glossy_tiles))))
        TestUi.click(r, a.getString(R.string.glossy_tiles))
        assertFalse(Prefs(ctx).glossy)
        assertEquals("Off", TestUi.subtitle(TestUi.row(r, a.getString(R.string.glossy_tiles))))

        TestUi.click(r, a.getString(R.string.focus_animation))
        assertFalse(Prefs(ctx).animations)
        TestUi.click(r, a.getString(R.string.show_greeting))
        assertFalse(Prefs(ctx).showGreeting)
        TestUi.click(r, a.getString(R.string.card_prayer))
        assertFalse(Prefs(ctx).showPrayerCard)
        TestUi.click(r, a.getString(R.string.card_weather))
        assertFalse(Prefs(ctx).showWeather)

        TestUi.click(r, a.getString(R.string.apps_per_row))
        TestUi.pick("5")
        assertEquals(5, Prefs(ctx).gridSpan)
        assertEquals("5", TestUi.subtitle(TestUi.row(r, a.getString(R.string.apps_per_row))))

        TestUi.click(r, a.getString(R.string.screensaver))
        TestUi.pick(a.getString(R.string.minutes_fmt, 30))
        assertEquals(30, Prefs(ctx).screensaverMinutes)

        TestUi.click(r, a.getString(R.string.time_format))
        TestUi.pick(a.getString(R.string.time_12))
        assertEquals("12", Prefs(ctx).timeFormat)
    }

    @Test
    fun prayerPageSettingsAreSaved() {
        val a = open("prayer")
        val r = root(a)
        TestUi.click(r, a.getString(R.string.adhan_auto))
        assertTrue(Prefs(ctx).adhanEnabled)
        TestUi.click(r, a.getString(R.string.prayer_asr))
        assertFalse(Prefs(ctx).isPrayerEnabled("Asr"))

        TestUi.click(r, a.getString(R.string.adhan_volume))
        TestUi.pick("60%")
        assertEquals(60, Prefs(ctx).adhanVolume)

        TestUi.click(r, a.getString(R.string.calc_method))
        TestUi.pick("مكة – أم القرى | Umm Al-Qura, Makkah")
        assertEquals(4, Prefs(ctx).method)

        TestUi.click(r, a.getString(R.string.prayer_adjust))
        TestUi.pick(a.getString(R.string.prayer_fajr) + ": 0")
        TestUi.pick("+3")
        assertEquals(3, Prefs(ctx).prayerOffset("Fajr"))
    }

    @Test
    fun wallpaperAndGeneralSettingsAreSaved() {
        val w = open("wallpaper")
        TestUi.click(root(w), w.getString(R.string.slide_interval))
        TestUi.pick(w.getString(R.string.minutes_fmt, 5))
        assertEquals(300, Prefs(ctx).slideSeconds)

        // "Reset" returns to the built-in wallpaper.
        Prefs(ctx).wallpaperType = "image"
        Prefs(ctx).wallpaperUri = "file:///x.jpg"
        TestUi.click(root(w), w.getString(R.string.wallpaper_reset))
        assertEquals("none", Prefs(ctx).wallpaperType)

        val g = open("general")
        TestUi.click(root(g), g.getString(R.string.hijri_adjust))
        TestUi.pick(g.getString(R.string.days_fmt, "+1"))
        assertEquals(1, Prefs(ctx).hijriAdjust)
        TestUi.click(root(g), g.getString(R.string.time_zone))
        TestUi.pick("UTC+3")
        assertEquals("10800", Prefs(ctx).tzMode)
    }

    /** Phase 2 fix: the same language rule for the hand-built pages and for the string resources. */
    @Test
    fun arabicIsUsedWhenChosen() {
        Prefs(ctx).language = "ar"
        val titles = TestUi.rowsIn(root(open())).map { TestUi.title(it) }
        assertTrue(titles.toString(), "الثيم الزجاجي" in titles)
        assertTrue(titles.toString(), "التاريخ" in titles)
    }

    @Test
    fun englishIsUsedWhenChosen() {
        Prefs(ctx).language = "en"
        val a = open()
        val texts = TestUi.allViews(root(a)).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.toString(), "Settings" in texts)
    }
}
