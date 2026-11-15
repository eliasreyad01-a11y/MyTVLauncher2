package com.mytv.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Every stored setting: default value, value surviving a new Prefs instance, clamping, and the file written to disk. */
@RunWith(RobolectricTestRunner::class)
class PrefsPersistenceTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun defaults() {
        val p = Prefs(ctx)
        assertEquals(2, p.glassTheme)
        assertEquals(100, p.glassOpacity)
        assertEquals(0, p.dateMode)
        assertEquals(7, p.gridSpan)
        assertEquals(2, p.homeRows)
        assertTrue(p.glossy)
        assertTrue(p.animations)
        assertTrue(p.showGreeting)
        assertTrue(p.showPrayerCard)
        assertTrue(p.showWeather)
        assertFalse(p.adhanEnabled)
        assertEquals(23, p.method)
        assertEquals(100, p.adhanVolume)
        assertEquals("24", p.timeFormat)
        assertEquals("system", p.language)
        assertEquals("auto", p.tzMode)
        assertEquals(10, p.screensaverMinutes)
        assertEquals(30, p.slideSeconds)
        assertEquals(0, p.dim)
        assertEquals("none", p.wallpaperType)
        assertNull(p.wallpaperUri)
        assertTrue(p.favorites.isEmpty())
        assertTrue(p.folders.isEmpty())
        PrayerRepository.PRAYERS.forEach { assertTrue(p.isPrayerEnabled(it)); assertEquals(0, p.prayerOffset(it)) }
    }

    @Test
    fun everySettingRoundTripsThroughANewPrefsInstance() {
        val a = Prefs(ctx)
        a.glassTheme = 5
        a.glassOpacity = 60
        a.dateMode = 2
        a.gridSpan = 5
        a.homeRows = 3
        a.glossy = false
        a.animations = false
        a.showGreeting = false
        a.showPrayerCard = false
        a.showWeather = false
        a.adhanEnabled = true
        a.setPrayerEnabled("Asr", false)
        a.cityName = "Irbid, Jordan"
        a.latitude = 32.5556
        a.longitude = 35.85
        a.method = 4
        a.adhanVolume = 60
        a.setPrayerOffset("Fajr", 3)
        a.tzMode = "10800"
        a.hijriAdjust = -1
        a.screensaverMinutes = 30
        a.slideSeconds = 60
        a.wallpaperType = "slideshow"
        a.wallpaperUri = "file:///sdcard/x.jpg"
        a.wallpaperPreset = "kirkjufell"
        a.dim = 40
        a.timeFormat = "12"
        a.language = "ar"
        a.adhanUri = "file:///sdcard/adhan.mp3"
        a.adhanName = "adhan.mp3"
        a.favorites = listOf("pkg.a", "pkg.b")
        a.hidden = listOf("pkg.c")
        a.appOrder = listOf("pkg.z", "pkg.y")
        val folders = LinkedHashMap<String, List<String>>()
        folders["Games"] = listOf("g.1", "g.2")
        folders["Video"] = listOf("v.1")
        a.folders = folders

        val b = Prefs(ctx)
        assertEquals(5, b.glassTheme)
        assertEquals(60, b.glassOpacity)
        assertEquals(2, b.dateMode)
        assertEquals(5, b.gridSpan)
        assertEquals(3, b.homeRows)
        assertFalse(b.glossy)
        assertFalse(b.animations)
        assertFalse(b.showGreeting)
        assertFalse(b.showPrayerCard)
        assertFalse(b.showWeather)
        assertTrue(b.adhanEnabled)
        assertFalse(b.isPrayerEnabled("Asr"))
        assertTrue(b.isPrayerEnabled("Fajr"))
        assertEquals("Irbid, Jordan", b.cityName)
        assertEquals(32.5556, b.latitude, 0.00001)
        assertEquals(35.85, b.longitude, 0.00001)
        assertEquals(4, b.method)
        assertEquals(60, b.adhanVolume)
        assertEquals(3, b.prayerOffset("Fajr"))
        assertEquals("10800", b.tzMode)
        assertEquals(-1, b.hijriAdjust)
        assertEquals(30, b.screensaverMinutes)
        assertEquals(60, b.slideSeconds)
        assertEquals("slideshow", b.wallpaperType)
        assertEquals("file:///sdcard/x.jpg", b.wallpaperUri)
        assertEquals(40, b.dim)
        assertEquals("12", b.timeFormat)
        assertEquals("ar", b.language)
        assertEquals("file:///sdcard/adhan.mp3", b.adhanUri)
        assertEquals("adhan.mp3", b.adhanName)
        assertEquals(listOf("pkg.a", "pkg.b"), b.favorites)
        assertEquals(listOf("pkg.c"), b.hidden)
        assertEquals(listOf("pkg.z", "pkg.y"), b.appOrder)
        assertEquals(listOf("Games", "Video"), b.folders.keys.toList())
        assertEquals(listOf("g.1", "g.2"), b.folders["Games"])
    }

    @Test
    fun valuesAreClamped() {
        val p = Prefs(ctx)
        p.glassOpacity = 5
        assertEquals(30, p.glassOpacity)
        p.glassOpacity = 500
        assertEquals(100, p.glassOpacity)
        p.adhanVolume = 1
        assertEquals(10, p.adhanVolume)
        p.setPrayerOffset("Dhuhr", 99)
        assertEquals(15, p.prayerOffset("Dhuhr"))
        p.setPrayerOffset("Dhuhr", -99)
        assertEquals(-15, p.prayerOffset("Dhuhr"))
        p.hijriAdjust = 9
        assertEquals(2, p.hijriAdjust)
    }

    @Test
    fun settingsAreWrittenToTheFileOnDisk() {
        val p = Prefs(ctx)
        p.glassTheme = 6
        p.dateMode = 1
        p.gridSpan = 4
        p.language = "en"
        val file = File(ctx.applicationInfo.dataDir, "shared_prefs/launcher_prefs.xml")
        var text = ""
        val end = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < end) {
            if (file.exists()) {
                text = file.readText()
                if (text.contains("\"grid_span\"") && text.contains("\"language\"")) break
            }
            Thread.sleep(50)
        }
        assertTrue("prefs file missing or incomplete: $text", text.contains("name=\"glass_theme\" value=\"6\""))
        assertTrue(text, text.contains("name=\"date_mode\" value=\"1\""))
        assertTrue(text, text.contains("name=\"grid_span\" value=\"4\""))
        assertTrue(text, text.contains(">en</string>"))
    }
}
