package com.mytv.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** tvOS top shelf: the big icon and the name above the dock follow the focused dock app, and the home has no big clock / prayer card. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1440dp-h810dp-land-tvdpi")
class ShelfTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(o: Any, name: String): T {
        val f = o.javaClass.getDeclaredField(name); f.isAccessible = true; return f.get(o) as T
    }

    private fun waitUntil(cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < end) { TestUi.idle(); if (cond()) return true; Thread.sleep(50) }
        return cond()
    }

    @Test
    fun shelfFollowsTheFocusedDockApp() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        try { c.javaClass.getMethod("windowFocusChanged", Boolean::class.javaPrimitiveType).invoke(c, true) } catch (_: Throwable) {}
        val a = c.get()
        val adapter: AppAdapter = field(a, "adapter")
        waitUntil { adapter.itemCount > 0 }
        val apps = (0 until 14).map { i ->
            val d = ColorDrawable(Color.argb(255, 60 + i * 8, 70, 90))
            AppItem("com.fake.app$i", "App %02d".format(i), d, d, Intent())
        }
        Prefs(ctx).favorites = apps.take(6).map { it.packageName }
        val f = a.javaClass.getDeclaredField("loadedApps"); f.isAccessible = true; f.set(a, apps)
        val m = a.javaClass.declaredMethods.first { it.name == "applyLists" && it.parameterCount == 0 }; m.isAccessible = true; m.invoke(a)
        TestUi.idle()
        TestUi.leaveTouchMode(a.window.decorView)
        TestUi.layoutAt1080p(a.window.decorView)
        TestUi.idle()
        TestUi.layoutAt1080p(a.window.decorView)

        val grid: RecyclerView = field(a, "grid")
        val shelf: View = field(a, "shelf")
        val shelfName: TextView = field(a, "shelfName")
        val shelfIcon: ImageView = field(a, "shelfIcon")
        val clockBox: View = field(a, "clockBox")
        val prayerCard: View = field(a, "prayerCard")
        assertEquals("no big clock on home", View.GONE, clockBox.visibility)
        assertEquals("no prayer card on home", View.GONE, prayerCard.visibility)

        fun checkShelf(pos: Int) {
            assertEquals("shelf name for dock app $pos (window focus=${a.window.currentFocus?.javaClass?.simpleName} pos=${a.window.currentFocus?.let { grid.findContainingItemView(it)?.let { v -> grid.getChildAdapterPosition(v) } }})", adapter.items[pos].label, shelfName.text.toString())
            assertEquals("shelf visible", View.VISIBLE, shelf.visibility)
            assertTrue("shelf opaque on the home page (alpha=${shelf.alpha})", shelf.alpha > 0.99f)
            assertTrue("shelf has the app's icon", shelfIcon.drawable === adapter.items[pos].icon)
        }
        grid.findViewHolderForAdapterPosition(0)!!.itemView.findViewById<View>(R.id.tile).requestFocus()
        TestUi.idle()
        checkShelf(0)
        // Move with the real remote key: the shelf must follow.
        a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT))
        a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_RIGHT))
        TestUi.idle()
        checkShelf(1)
    }
}
