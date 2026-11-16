package com.mytv.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.View
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
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Phase 6 (remote control), simulated on the JVM: the real layouts at 1920x1080 with the system out of touch mode, so
 * the focus search is the one a TV remote uses. Checks the D-pad graph (every tile / row reachable, no dead ends),
 * OK and Back behaviour on each screen. It does not replace trying the real remote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1440dp-h810dp-land-tvdpi")
class RemoteControlTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    // ------------------------------------------------------------------ helpers

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

    @Suppress("UNCHECKED_CAST")
    private fun <T> call(o: Any, name: String, vararg args: Any): T? {
        val m = o.javaClass.declaredMethods.first { it.name == name && it.parameterCount == args.size }
        m.isAccessible = true
        return m.invoke(o, *args) as T?
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

    private fun prepare(decor: View) {
        TestUi.leaveTouchMode(decor)
        TestUi.layoutAt1080p(decor)
        TestUi.idle()
        TestUi.layoutAt1080p(decor)
    }

    /** Activity.getCurrentFocus() (used by the launcher's own key handling) only reports a view once the window has focus. */
    private fun windowFocus(c: Any) {
        try {
            c.javaClass.getMethod("windowFocusChanged", Boolean::class.javaPrimitiveType).invoke(c, true)
        } catch (_: Throwable) {
        }
    }

    private fun fakeApps(n: Int): List<AppItem> = (0 until n).map { i ->
        val d = ColorDrawable(Color.GRAY)
        AppItem("com.fake.app$i", "App %02d".format(i), d, d, Intent())
    }

    private fun home(n: Int = 14): MainActivity {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        windowFocus(c)
        val a = c.get()
        val adapter: AppAdapter = field(a, "adapter")
        waitUntil { adapter.itemCount > 0 }
        val apps = fakeApps(n)
        Prefs(ctx).favorites = apps.take(6).map { it.packageName }
        setField(a, "loadedApps", apps)
        call<Any>(a, "applyLists")
        TestUi.idle()
        prepare(a.window.decorView)
        return a
    }

    private fun key(a: android.app.Activity, code: Int) {
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        TestUi.idle()
    }

    private fun gridOf(a: MainActivity) = a.findViewById<RecyclerView>(R.id.grid)
    private fun tile(a: MainActivity, pos: Int): View? =
        gridOf(a).findViewHolderForAdapterPosition(pos)?.itemView?.findViewById(R.id.tile)

    private fun posOf(a: MainActivity, v: View?): Int {
        v ?: return -1
        val g = gridOf(a)
        val item = g.findContainingItemView(v) ?: return -1
        return g.getChildAdapterPosition(item)
    }

    // ------------------------------------------------------------------ home: D-pad graph

    @Test
    fun dockRowIsWalkableWithRightAndLeft() {
        val a = home()
        assertFalse("still in touch mode: ${a.window.decorView.isInTouchMode}", a.window.decorView.isInTouchMode)
        val first = tile(a, 0)
        assertNotNull("dock tile 0 not laid out", first)
        assertTrue("tile 0 cannot take focus", first!!.requestFocus())
        var cur: View = first
        for (i in 1..6) {
            val n = cur.focusSearch(View.FOCUS_RIGHT)
            assertEquals("RIGHT from dock tile ${i - 1}", i, posOf(a, n))
            n!!.requestFocus()
            cur = n
        }
        for (i in 5 downTo 0) {
            val n = cur.focusSearch(View.FOCUS_LEFT)
            assertEquals("LEFT from dock tile ${i + 1}", i, posOf(a, n))
            n!!.requestFocus()
            cur = n
        }
    }

    @Test
    fun downAndUpMoveBetweenTheDockAndTheGrid() {
        val a = home()
        val adapter: AppAdapter = field(a, "adapter")
        val span = adapter.dockCount
        for (col in listOf(0, 3, 6)) {
            val top = tile(a, col)!!
            top.requestFocus()
            val down = top.focusSearch(View.FOCUS_DOWN)
            assertEquals("DOWN from dock column $col", span + col, posOf(a, down))
            down!!.requestFocus()
            val up = down.focusSearch(View.FOCUS_UP)
            assertEquals("UP from grid column $col", col, posOf(a, up))
        }
    }

    @Test
    fun upFromTheDockReachesAHeaderButtonAndDownComesBack() {
        val a = home()
        val top = tile(a, 2)!!
        top.requestFocus()
        val up = top.focusSearch(View.FOCUS_UP)
        assertNotNull("UP from the dock goes nowhere", up)
        assertEquals("UP from the dock should leave the grid", -1, posOf(a, up))
        assertTrue("target is focusable", up!!.isFocusable)
        up.requestFocus()
        val back = up.focusSearch(View.FOCUS_DOWN)
        assertNotNull("DOWN from the header goes nowhere", back)
        assertTrue("DOWN from the header should land on a tile, landed on ${back!!.javaClass.simpleName}#${back.id}", posOf(a, back) >= 0)
    }

    @Test
    fun everyFocusableHeaderButtonCanGoDown() {
        val a = home()
        for (id in listOf(R.id.btnSettings, R.id.btnWifi, R.id.btnHome, R.id.btnWave, R.id.btnBt)) {
            val v = a.findViewById<View>(id)
            if (v.visibility != View.VISIBLE || !v.isFocusable) continue
            v.requestFocus()
            val n = v.focusSearch(View.FOCUS_DOWN)
            assertNotNull("DOWN from header button $id goes nowhere", n)
        }
    }

    @Test
    fun everyAppTileIsReachableFromTheFirstOne() {
        val a = home()
        val adapter: AppAdapter = field(a, "adapter")
        val reachable = HashSet<Int>()
        // Breadth-first over the four D-pad directions, the way someone exploring with the remote would.
        val queue = ArrayDeque<View>()
        val start = tile(a, 0)!!
        start.requestFocus()
        queue.add(start)
        reachable.add(0)
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 400) {
            val cur = queue.removeFirst()
            for (dir in intArrayOf(View.FOCUS_RIGHT, View.FOCUS_LEFT, View.FOCUS_DOWN, View.FOCUS_UP)) {
                val n = cur.focusSearch(dir) ?: continue
                val p = posOf(a, n)
                if (p >= 0 && reachable.add(p)) queue.add(n)
            }
        }
        val expected = adapter.items.indices.filter { !adapter.items[it].packageName.startsWith(AppAdapter.PAD_PREFIX) }
        val missing = expected.filter { it !in reachable }
        assertTrue("tiles the D-pad cannot reach (positions): $missing of ${expected.size}", missing.isEmpty())
    }

    @Test
    fun leftAndRightWrapAtTheEndsOfARow() {
        val a = home()
        val adapter: AppAdapter = field(a, "adapter")
        val last = adapter.dockCount - 1
        val lastTile = tile(a, last)!!
        assertTrue("last dock tile takes focus", lastTile.requestFocus())
        fun focusInfo(): String { val f = a.currentFocus; return "${f?.javaClass?.simpleName}#${f?.id} pos=${posOf(a, f)}" }
        val diag = "tile focused=${lastTile.isFocused} hasFocus=${lastTile.hasFocus()} attached=${lastTile.isAttachedToWindow} " +
            "rootIsDecor=${lastTile.rootView === a.window.decorView} rootFocus=${lastTile.rootView.findFocus()?.javaClass?.simpleName} " +
            "touch=${lastTile.isInTouchMode} windowFocus=${a.window.decorView.hasWindowFocus()} " +
            "windowCurrentFocus=${a.window.currentFocus?.javaClass?.simpleName} decorFind=${a.window.decorView.findFocus()?.javaClass?.simpleName} " +
            "activityClass=${a.javaClass.simpleName} sameWindow=${lastTile.context.let { (it as? android.content.ContextWrapper)?.baseContext != null }}"
        assertEquals("before: " + focusInfo() + " | " + diag, last, posOf(a, a.currentFocus))
        val consumed = a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue("RIGHT at the end of the dock is handled (wraps); focus now " + focusInfo(), consumed)
        assertEquals("RIGHT at the end of the dock wraps to its start; focus now " + focusInfo(), 0, posOf(a, a.currentFocus))
        val consumedLeft = a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        assertTrue("LEFT at the start of the dock is handled (wraps); focus now " + focusInfo(), consumedLeft)
        assertEquals("LEFT at the start of the dock wraps to its end; focus now " + focusInfo(), last, posOf(a, a.currentFocus))
    }

    // ------------------------------------------------------------------ home: OK / Back / move mode / screensaver

    @Test
    fun backOnTheHomeNeverLeavesTheLauncher() {
        val a = home()
        a.onBackPressedDispatcher.onBackPressed()
        TestUi.idle()
        assertFalse(a.isFinishing)
    }

    @Test
    fun moveModeUsesTheDpadOkSavesAndBackCancels() {
        val a = home()
        val adapter: AppAdapter = field(a, "adapter")
        val before = adapter.items.take(6).map { it.packageName }
        val item = adapter.items[2]

        // Back cancels.
        call<Any>(a, "startMove", item, adapter)
        TestUi.idle()
        key(a, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("moved one step right", item.packageName, adapter.items[3].packageName)
        a.onBackPressedDispatcher.onBackPressed()
        TestUi.idle()
        assertEquals("Back restores the original order", before, adapter.items.take(6).map { it.packageName })

        // OK saves.
        call<Any>(a, "startMove", item, adapter)
        TestUi.idle()
        key(a, KeyEvent.KEYCODE_DPAD_RIGHT)
        key(a, KeyEvent.KEYCODE_DPAD_CENTER)
        TestUi.idle()
        val saved = Prefs(ctx).favorites
        assertEquals("OK keeps the new order", item.packageName, saved[3])
        assertEquals(before.size, saved.size)
    }

    @Test
    fun anyKeyEndsTheScreensaver() {
        val a = home()
        for (code in listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_DOWN)) {
            call<Any>(a, "startScreensaver")
            assertTrue("screensaver should be on", field<Boolean>(a, "screensaverOn"))
            key(a, code)
            assertFalse("key $code should end the screensaver", field<Boolean>(a, "screensaverOn"))
        }
    }

    @Test
    fun backClosesTheFolderAndStaysOnTheHome() {
        val a = home()
        call<Any>(a, "addToFolder", "Games", "com.fake.app9")
        call<Any>(a, "addToFolder", "Games", "com.fake.app10")
        TestUi.idle()
        call<Any>(a, "showFolder", "Games")
        TestUi.idle()
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog.isShowing)
        dialog.onBackPressed()
        TestUi.idle()
        assertFalse("Back should close the folder", dialog.isShowing)
        assertFalse(a.isFinishing)
    }

    // ------------------------------------------------------------------ settings

    private fun openSettings(section: String? = null): SettingsActivity {
        val intent = Intent(ctx, SettingsActivity::class.java)
        if (section != null) intent.putExtra("section", section)
        val a = Robolectric.buildActivity(SettingsActivity::class.java, intent).setup().get()
        prepare(a.window.decorView)
        return a
    }

    private fun assertRowsChain(root: View, what: String) {
        val rows = TestUi.rowsIn(root)
        assertTrue("$what has rows", rows.size >= 3)
        rows[0].requestFocus()
        for (i in 1 until rows.size) {
            val n = rows[i - 1].focusSearch(View.FOCUS_DOWN)
            assertTrue("$what: DOWN from \"${TestUi.title(rows[i - 1])}\" should reach \"${TestUi.title(rows[i])}\" but reached ${n?.javaClass?.simpleName}", n === rows[i])
            n!!.requestFocus()
        }
        for (i in rows.size - 2 downTo 0) {
            val n = rows[i + 1].focusSearch(View.FOCUS_UP)
            assertTrue("$what: UP from \"${TestUi.title(rows[i + 1])}\" should reach \"${TestUi.title(rows[i])}\"", n === rows[i])
        }
    }

    @Test
    fun everySettingsPageIsWalkableWithUpAndDown() {
        for (section in listOf<String?>(null, "prayer", "wallpaper", "home", "general")) {
            assertRowsChain(openSettings(section).window.decorView, "settings ${section ?: "main"}")
        }
    }

    @Test
    fun backLeavesTheSettings() {
        val a = openSettings()
        a.onBackPressedDispatcher.onBackPressed()
        TestUi.idle()
        assertTrue("Back should close the settings", a.isFinishing)
    }

    @Test
    fun choicePageIsWalkableAndBackClosesOnlyIt() {
        val a = openSettings()
        Dialogs.showChoice(a, "Pick", listOf("One", "Two", "Three", "Four"), 1) { }
        TestUi.idle()
        val dialog = ShadowDialog.getLatestDialog()
        prepare(dialog.window!!.decorView)
        assertRowsChain(dialog.window!!.decorView, "choice page")
        dialog.onBackPressed()
        TestUi.idle()
        assertFalse("Back should close the choice page", dialog.isShowing)
        assertFalse("Back on a choice page must not close the settings", a.isFinishing)
    }
}
