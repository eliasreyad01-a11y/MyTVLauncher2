package com.mytv.launcher

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.shadows.ShadowToast

/**
 * Follow-up checks for the things the first QA round could not settle: control-center Back and focus, key-repeat pacing,
 * the system-settings rows (intent + fallback chain), tile rounding and blur configuration. Simulated on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1440dp-h810dp-land-tvdpi")
class VerificationTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

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

    private fun home(): MainActivity {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        try { c.javaClass.getMethod("windowFocusChanged", Boolean::class.javaPrimitiveType).invoke(c, true) } catch (_: Throwable) {}
        val a = c.get()
        val adapter: AppAdapter = field(a, "adapter")
        waitUntil { adapter.itemCount > 0 }
        val apps = (0 until 10).map { i ->
            val d = ColorDrawable(Color.GRAY)
            AppItem("com.fake.app$i", "App %02d".format(i), d, d, Intent())
        }
        Prefs(ctx).favorites = apps.take(6).map { it.packageName }
        setField(a, "loadedApps", apps)
        call(a, "applyLists")
        TestUi.idle()
        TestUi.leaveTouchMode(a.window.decorView)
        TestUi.layoutAt1080p(a.window.decorView)
        TestUi.idle()
        return a
    }

    // ------------------------------------------------------------------ control center

    @Test
    fun controlCenterIsAFocusablePopupThatBackClosesAndEveryControlIsReachable() {
        val a = home()
        call(a, "showQuickSettings")
        TestUi.idle()
        val popup: PopupWindow = field(a, "menuPopup")
        assertTrue("popup shows", popup.isShowing)
        assertTrue("a focusable popup takes Back itself (the activity never sees it)", popup.isFocusable)

        // Focus graph inside the panel.
        val content = popup.contentView
        TestUi.leaveTouchMode(content.rootView)
        content.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
        val focusables = TestUi.allViews(content).filter { it.isFocusable && it.visibility == View.VISIBLE }
        assertTrue("expected 2 left tiles + power + volume + 4 round buttons, found ${focusables.size}", focusables.size >= 8)
        val start = focusables.first()
        start.requestFocus()
        val seen = HashSet<View>()
        val queue = ArrayDeque<View>()
        seen.add(start); queue.add(start)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (dir in intArrayOf(View.FOCUS_LEFT, View.FOCUS_RIGHT, View.FOCUS_UP, View.FOCUS_DOWN)) {
                val n = cur.focusSearch(dir) ?: continue
                if (n in focusables && seen.add(n)) queue.add(n)
            }
        }
        val unreachable = focusables.filter { it !in seen }
        assertTrue("controls the D-pad cannot reach: ${unreachable.map { it.javaClass.simpleName + "/" + it.contentDescription }}", unreachable.isEmpty())

        // Back.
        val decor = content.rootView
        val now = System.currentTimeMillis()
        decor.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))
        decor.dispatchKeyEvent(KeyEvent(now, now + 10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0))
        TestUi.idle()
        assertFalse("Back should close the control center", popup.isShowing)
        assertFalse("and must not close the launcher", a.isFinishing)
    }

    // ------------------------------------------------------------------ held D-pad key

    @Test
    fun heldDpadKeyIsPacedAndOtherKeysAreNot() {
        val a = home()
        fun hold(hz: Int, millis: Long): Int {
            setField(a, "lastRepeatMove", 0L)
            val down = 1_000_000L
            val step = 1000L / hz
            var accepted = 0
            var t = down
            var repeat = 1
            while (t - down < millis) {
                t += step
                val handled = a.dispatchKeyEvent(KeyEvent(down, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, repeat++))
                if (field<Long>(a, "lastRepeatMove") == t) accepted++ else assertTrue("a swallowed repeat must report 'handled'", handled)
            }
            return accepted
        }
        val at50 = hold(50, 2000)
        val at30 = hold(30, 2000)
        val at100 = hold(100, 2000)
        // Moves are at most one per 40 ms: a 50 Hz remote gets 25 a second, a 30 Hz remote 15, never more than 25.
        assertEquals("50 Hz over 2 s", 50, at50, 2.0)
        assertEquals("30 Hz over 2 s", 30, at30, 2.0)
        assertEquals("100 Hz over 2 s", 50, at100, 2.0)

        setField(a, "lastRepeatMove", 0L)
        val down = 2_000_000L
        for (i in 1..5) a.dispatchKeyEvent(KeyEvent(down, down + i * 5L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, i))
        assertEquals("OK / other keys are never paced", 0L, field<Long>(a, "lastRepeatMove"))
    }

    // ------------------------------------------------------------------ system settings rows

    private fun resolvable(action: String) {
        val ai = ApplicationInfo().apply { packageName = "com.android.tv.settings" }
        val ri = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = "com.android.tv.settings"; name = "Screen"; applicationInfo = ai }
        }
        @Suppress("DEPRECATION")
        shadowOf(ctx.packageManager).addResolveInfoForIntent(Intent(action), ri)
    }

    private fun settingsMain(): SettingsActivity =
        Robolectric.buildActivity(SettingsActivity::class.java, Intent(ctx, SettingsActivity::class.java)).setup().get()

    private val systemRows = mapOf(
        "Video" to listOf(android.provider.Settings.ACTION_DISPLAY_SETTINGS),
        "Audio" to listOf(android.provider.Settings.ACTION_SOUND_SETTINGS),
        "Screen Saver" to listOf(android.provider.Settings.ACTION_DREAM_SETTINGS, android.provider.Settings.ACTION_DISPLAY_SETTINGS),
        "Accounts" to listOf(android.provider.Settings.ACTION_SYNC_SETTINGS, android.provider.Settings.ACTION_ADD_ACCOUNT),
        "Remotes and Devices" to listOf(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),
        "Accessibility" to listOf(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS),
        "Apps" to listOf(android.provider.Settings.ACTION_APPLICATION_SETTINGS, android.provider.Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS),
        "Network" to listOf(android.provider.Settings.ACTION_WIFI_SETTINGS),
        "System" to listOf(android.provider.Settings.ACTION_SETTINGS)
    )

    @Test
    fun systemRowsStartTheirScreenAndFallBackOrSayNotAvailable() {
        shadowOf(ctx as android.app.Application).checkActivities(true)

        // 1. A firmware with none of these screens: no crash, an honest toast, nothing started.
        for ((title, _) in systemRows) {
            ShadowToast.reset()
            val a = settingsMain()
            TestUi.click(a.window.decorView, title)
            assertEquals("row $title with no handler", "Not available on this device", ShadowToast.getTextOfLatestToast())
            assertNull("row $title started something that cannot exist", shadowOf(a).nextStartedActivity)
        }

        // 2. Only the LAST action of each chain exists: the row must fall back to it.
        for ((title, actions) in systemRows) {
            shadowOf(ctx.packageManager).clearResolveInfosForIntent(Intent(actions.first()))
        }
        for ((title, actions) in systemRows) {
            resolvable(actions.last())
            val a = settingsMain()
            TestUi.click(a.window.decorView, title)
            val started = shadowOf(a).nextStartedActivity
            assertNotNull("row $title did not start its fallback ${actions.last()}", started)
            assertEquals("row $title", actions.last(), started.action)
            assertTrue("new task flag", started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }

        // 3. The first action exists too: it wins.
        for ((title, actions) in systemRows) {
            if (actions.size < 2) continue
            resolvable(actions.first())
            val a = settingsMain()
            TestUi.click(a.window.decorView, title)
            assertEquals("row $title prefers its first action", actions.first(), shadowOf(a).nextStartedActivity.action)
        }
    }

    // ------------------------------------------------------------------ rounding and blur configuration

    @Test
    fun appTilesClipToARoundedOutline() {
        val tile = RatioFrameLayout(ctx)
        tile.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        tile.layout(0, 0, tile.measuredWidth, tile.measuredHeight)
        assertTrue("tile must clip its content to its outline", tile.clipToOutline)
        val o = Outline()
        tile.outlineProvider.getOutline(tile, o)
        assertFalse("outline is empty", o.isEmpty)
        assertTrue("outline must be clippable", o.canClip())
        val radius = try { Outline::class.java.getMethod("getRadius").invoke(o) as Float } catch (_: Throwable) {
            Outline::class.java.getDeclaredField("mRadius").apply { isAccessible = true }.getFloat(o)
        }
        assertEquals("24dp corner radius", 24f * ctx.resources.displayMetrics.density, radius, 0.5f)
        assertEquals("16:9 tile", 270, tile.measuredHeight)
    }

    @Test
    fun dialogsAskTheSystemToBlurBehindThem() {
        val a = settingsMain()
        val d = Dialogs.track(Dialog(a, R.style.LauncherPlainDialog))
        val attrs = d.window!!.attributes
        assertTrue("FLAG_BLUR_BEHIND", attrs.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND != 0)
        assertEquals(30, attrs.blurBehindRadius)
        assertEquals(0.3f, attrs.dimAmount, 0.001f)
    }
}
