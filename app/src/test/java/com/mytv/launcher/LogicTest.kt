package com.mytv.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LogicTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun item(pkg: String, label: String) =
        AppItem(pkg, label, ColorDrawable(0), null, Intent())

    @Test
    fun glassThemeIndexAndKeyFollowTheSettings() {
        val p = Prefs(ctx)
        assertEquals(8, GlassTheme.all.size)
        p.glassTheme = 99
        assertEquals(7, GlassTheme.index(ctx))
        p.glassTheme = -4
        assertEquals(0, GlassTheme.index(ctx))
        p.glassTheme = 3
        p.glassOpacity = 80
        val k1 = GlassTheme.key(ctx)
        p.glassOpacity = 60
        assertNotEquals(k1, GlassTheme.key(ctx))
        p.glassTheme = 4
        assertNotEquals(k1, GlassTheme.key(ctx))
        assertTrue(GlassTheme.opacity(ctx) in 0.2f..1f)
    }

    @Test
    fun timeAndClockFormats() {
        assertEquals("5:05 PM", TimeFmt.display("17:05", false, Locale.US))
        assertEquals("07:05", TimeFmt.display("07:05", true, Locale.US))
        assertEquals("--:--", TimeFmt.display(null, true))
        assertEquals("UTC+3", CityClock.label(10800))
        assertEquals("UTC+5:30", CityClock.label(19800))
        assertEquals("UTC-5", CityClock.label(-18000))
        assertEquals("GMT+03:00", CityClock.gmtId(10800))
        assertEquals("GMT-05:00", CityClock.gmtId(-18000))
    }

    @Test
    fun hijriAdjustMovesTheDate() {
        val day = LocalDate.of(2026, 11, 15)
        val p = Prefs(ctx)
        p.hijriAdjust = 0
        val base = HijriDate.format(ctx, day, Locale.ENGLISH)
        assertTrue(base, base.endsWith("AH") && base.isNotEmpty())
        p.hijriAdjust = 1
        assertNotEquals(base, HijriDate.format(ctx, day, Locale.ENGLISH))
        p.hijriAdjust = 0
        assertTrue(HijriDate.format(ctx, day, Locale("ar")).endsWith("هـ"))
    }

    @Test
    fun appOrderKeepsSavedOrderThenAlphabetical() {
        val apps = listOf(item("c", "Cherry"), item("a", "Apple"), item("b", "Banana"), item("d", "Date"))
        val sorted = AppRepository.sorted(apps, listOf("b", "c"))
        assertEquals(listOf("b", "c", "a", "d"), sorted.map { it.packageName })
    }

    /** Phase 2 fix: an app list that is reloaded with the same packages but new icons/labels must rebind the tiles. */
    @Test
    fun reloadedAppsWithSamePackagesAreRebound() {
        val adapter = AppAdapter(onClick = {}, onLongClick = { _, _ -> })
        val first = listOf(item("a", "Apple"), item("b", "Banana"))
        adapter.submit(first)
        var changed = 0
        var inserted = 0
        var removed = 0
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) { changed += itemCount }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) { changed += itemCount }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { inserted += itemCount }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { removed += itemCount }
        })
        // Same list objects: nothing to do.
        adapter.submit(first)
        assertEquals(0, changed + inserted + removed)
        // Same package names, new objects (an app was updated): both tiles rebind, none is added or removed.
        adapter.submit(listOf(item("a", "Apple 2"), item("b", "Banana")))
        assertEquals(2, changed)
        assertEquals(0, inserted + removed)
        assertEquals("Apple 2", adapter.items[0].label)
    }

    /** Phase 2 fix: isPlaying is false the moment playback is finished. */
    @Test
    fun adhanServiceStopsReportingPlayingWhenFinished() {
        val controller = Robolectric.buildService(AdhanService::class.java).create()
        assertTrue(AdhanService.isPlaying)
        var notified = 0
        var playingAtNotify = true
        AdhanService.onChanged = { notified++; playingAtNotify = AdhanService.isPlaying }
        controller.get().finishPlayback()
        AdhanService.onChanged = null
        assertTrue(notified >= 1)
        assertEquals(false, playingAtNotify)
        assertEquals(false, AdhanService.isPlaying)
    }
}
