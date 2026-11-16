package com.mytv.launcher

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Phase 4: the focused settings / choice row grows 3 %. The scroll area clips its children, so the list needs side room
 * for that growth; otherwise the focused row is cut flat on both ends (it looked square instead of rounded).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1440dp-h810dp-land-tvdpi")
class FocusRoomTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun layoutAt1080p(v: View) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1920, 1080)
    }

    private fun assertRoomToGrow(root: View, what: String) {
        layoutAt1080p(root)
        val rows = TestUi.rowsIn(root)
        assertTrue("$what has rows", rows.isNotEmpty())
        for (row in rows) {
            val list = row.parent as ViewGroup
            val grow = row.width * 0.03f / 2f
            assertTrue("$what: the list must not clip its children to its padding, or the focused row is cut flat", !list.clipToPadding)
            assertTrue("$what: ${TestUi.title(row)} grows ${grow}px, left room ${list.paddingLeft}px", list.paddingLeft >= grow)
            assertTrue("$what: ${TestUi.title(row)} grows ${grow}px, right room ${list.paddingRight}px", list.paddingRight >= grow)
        }
    }

    @Test
    fun settingsRowsHaveRoomToGrow() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent(ctx, SettingsActivity::class.java)).setup().get()
        assertRoomToGrow(a.window.decorView, "settings")
    }

    @Test
    fun choiceRowsHaveRoomToGrow() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent(ctx, SettingsActivity::class.java)).setup().get()
        Dialogs.showChoice(a, "Pick", listOf("One", "Two", "Three"), 1) { }
        TestUi.idle()
        assertRoomToGrow(ShadowDialog.getLatestDialog().window!!.decorView, "choice dialog")
    }
}
