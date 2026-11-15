package com.mytv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.view.KeyEvent
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog

/** Drives the real MainActivity (home screen) with a handful of fake installed apps. */
@RunWith(RobolectricTestRunner::class)
class MainScreenTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun installFakeApps(n: Int) {
        val spm = shadowOf(ctx.packageManager)
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        for (i in 0 until n) {
            val pkg = "com.fake.app$i"
            val ai = ApplicationInfo().apply { packageName = pkg }
            val ri = ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = pkg
                    name = "$pkg.Main"
                    applicationInfo = ai
                }
                nonLocalizedLabel = "App %02d".format(i)
            }
            spm.addResolveInfoForIntent(query, ri)
            spm.addActivityIfNotPresent(ComponentName(pkg, "$pkg.Main"))
        }
    }

    private fun launch(): Pair<ActivityController<MainActivity>, MainActivity> {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        return c to c.get()
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

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(o: Any, name: String): T {
        val f = o.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(o) as T
    }

    private fun call(o: Any, name: String, vararg args: Any) {
        val m = o.javaClass.declaredMethods.first { it.name == name && it.parameterCount == args.size }
        m.isAccessible = true
        m.invoke(o, *args)
    }

    private fun adapterOf(a: MainActivity): AppAdapter = field(a, "adapter")

    @Test
    fun homeScreenBuildsDockAndRows() {
        installFakeApps(14)
        val (_, a) = launch()
        val adapter = adapterOf(a)
        assertTrue("apps never loaded", waitUntil { adapter.itemCount > 0 })
        // 7 per row: six favourites + the add tile are the dock; the remaining apps follow; the library tile is last.
        assertEquals(7, adapter.dockCount)
        assertEquals("__add__", adapter.items[6].packageName)
        assertEquals("__library__", adapter.items.last().packageName)
        assertEquals(6, Prefs(ctx).favorites.size)
        assertTrue(adapter.items.any { it.packageName == "com.fake.app13" })
    }

    @Test
    fun dpadKeysDoNotCrashTheHome() {
        installFakeApps(10)
        val (_, a) = launch()
        assertTrue(waitUntil { adapterOf(a).itemCount > 0 })
        val keys = listOf(
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT
        )
        for (k in keys) {
            a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, k))
            a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, k))
            TestUi.idle()
        }
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        TestUi.idle()
    }

    @Test
    fun controlCenterOpens() {
        installFakeApps(6)
        val (_, a) = launch()
        assertTrue(waitUntil { adapterOf(a).itemCount > 0 })
        a.findViewById<View>(R.id.btnSettings).performClick()
        TestUi.idle()
        val popup: PopupWindow? = field(a, "menuPopup")
        assertNotNull("control center did not open", popup)
        assertTrue(popup!!.isShowing)
        popup.dismiss()
    }

    @Test
    fun headerButtonsOpenSystemScreens() {
        installFakeApps(3)
        val (_, a) = launch()
        for (id in listOf(R.id.btnWifi, R.id.btnWave, R.id.btnBt)) {
            a.findViewById<View>(id).performClick()
            TestUi.idle()
        }
        val started = shadowOf(a).nextStartedActivity
        assertNotNull(started)
        assertEquals("Wi-Fi", a.findViewById<View>(R.id.btnWifi).contentDescription)
        assertEquals("Home", a.findViewById<View>(R.id.btnHome).contentDescription)
    }

    @Test
    fun foldersCanBeCreatedOpenedAndRemoved() {
        installFakeApps(14)
        val (_, a) = launch()
        val adapter = adapterOf(a)
        assertTrue(waitUntil { adapter.itemCount > 0 })
        call(a, "addToFolder", "Games", "com.fake.app9")
        call(a, "addToFolder", "Games", "com.fake.app10")
        assertTrue("folder tile missing", waitUntil { adapter.items.any { it.packageName.startsWith("__folder:Games") } })
        assertEquals(listOf("com.fake.app9", "com.fake.app10"), Prefs(ctx).folders["Games"])
        // The two apps left the plain grid; they live in the folder.
        assertFalse(adapter.items.any { it.packageName == "com.fake.app9" })
        call(a, "showFolder", "Games")
        TestUi.idle()
        assertNotNull(ShadowDialog.getLatestDialog())
        assertTrue(ShadowDialog.getLatestDialog().isShowing)
        Dialogs.dismissAll()
        call(a, "removeFromFolder", "Games", "com.fake.app9")
        call(a, "removeFromFolder", "Games", "com.fake.app10")
        assertTrue(waitUntil { adapter.items.none { it.packageName.startsWith("__folder:") } })
        assertTrue(Prefs(ctx).folders.isEmpty())
    }

    /** Phase 2 fix: the screensaver shows the dates the user asked for. */
    @Test
    fun screensaverFollowsTheDateSetting() {
        installFakeApps(3)
        val (_, a) = launch()
        val sub = a.findViewById<TextView>(R.id.ssSub)
        val prefs = Prefs(ctx)

        prefs.dateMode = 1
        call(a, "startScreensaver")
        assertEquals(View.GONE, sub.visibility)
        call(a, "stopScreensaver")

        prefs.dateMode = 0
        call(a, "startScreensaver")
        assertEquals(View.VISIBLE, sub.visibility)
        call(a, "stopScreensaver")
    }

    @Test
    fun settingsChangesShowOnReturningHome() {
        installFakeApps(8)
        val prefs = Prefs(ctx)
        prefs.dateMode = 2
        prefs.showGreeting = false
        prefs.glassTheme = 5
        prefs.glassOpacity = 60
        val (c, a) = launch()
        TestUi.idle()
        assertEquals(View.GONE, a.findViewById<View>(R.id.dateGreg).visibility)
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.dateText).visibility)
        assertEquals(View.GONE, a.findViewById<View>(R.id.greeting).visibility)
        prefs.dateMode = 1
        prefs.showGreeting = true
        c.pause().resume()
        TestUi.idle()
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.dateGreg).visibility)
        assertEquals(View.GONE, a.findViewById<View>(R.id.dateText).visibility)
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.greeting).visibility)
    }

    @Test
    fun gridUsesTheChosenAppsPerRow() {
        installFakeApps(12)
        Prefs(ctx).gridSpan = 5
        val (_, a) = launch()
        val adapter = adapterOf(a)
        assertTrue(waitUntil { adapter.itemCount > 0 })
        assertEquals(5, adapter.dockCount)
        val grid = a.findViewById<RecyclerView>(R.id.grid)
        assertEquals(5, (grid.layoutManager as androidx.recyclerview.widget.GridLayoutManager).spanCount)
    }
}
