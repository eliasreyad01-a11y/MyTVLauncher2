package com.mytv.launcher

import android.app.Dialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/** Helpers shared by the screen tests: find rows by title, click them, look into the latest dialog. */
object TestUi {

    fun idle() = shadowOf(Looper.getMainLooper()).idle()

    fun allViews(root: View): List<View> {
        val out = ArrayList<View>()
        fun walk(v: View) {
            out.add(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return out
    }

    /** A settings / choice row: a clickable, focusable LinearLayout whose first child is its title. */
    fun rowsIn(root: View): List<LinearLayout> = allViews(root).filterIsInstance<LinearLayout>().filter {
        it.isClickable && it.isFocusable && it.childCount >= 1 && it.getChildAt(0) is TextView
    }

    fun title(row: LinearLayout): String = (row.getChildAt(0) as TextView).text.toString()

    fun subtitle(row: LinearLayout): String =
        if (row.childCount > 1) (row.getChildAt(1) as? TextView)?.text?.toString().orEmpty() else ""

    fun row(root: View, title: String): LinearLayout =
        rowsIn(root).firstOrNull { title(it) == title }
            ?: throw AssertionError("no row \"$title\"; rows = " + rowsIn(root).map { title(it) })

    fun click(root: View, title: String) {
        row(root, title).performClick()
        idle()
    }

    fun latestDialogRoot(): View {
        val d: Dialog = ShadowDialog.getLatestDialog() ?: throw AssertionError("no dialog is open")
        return d.window!!.decorView
    }

    /** Clicks a row in the dialog that was opened last (the choice pages use the same row layout). */
    fun pick(label: String) {
        row(latestDialogRoot(), label).performClick()
        idle()
    }

    /** A TV remote works outside touch mode; Robolectric starts inside it, where only touch-focusable views can focus. */
    fun leaveTouchMode(decor: View) {
        try {
            val vri = View::class.java.getMethod("getViewRootImpl").invoke(decor) ?: return
            for (name in listOf("ensureTouchModeLocally", "ensureTouchMode")) {
                try {
                    val m = vri.javaClass.getDeclaredMethod(name, Boolean::class.javaPrimitiveType)
                    m.isAccessible = true
                    m.invoke(vri, false)
                    return
                } catch (_: NoSuchMethodException) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun layoutAt1080p(v: View) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1920, 1080)
    }
}
