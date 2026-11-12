package com.mytv.launcher

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object Dialogs {

    private val open = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Dialog, Boolean>())

    /** Remembers a dialog so Home can close it. */
    fun <T : Dialog> track(dialog: T): T {
        open.add(dialog)
        dialog.window?.let { w ->
            // Glass: the home screen shows through, blurred where the TV supports it (Android 12+).
            w.setDimAmount(0.3f)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply { blurBehindRadius = 30 }
                } catch (e: Exception) { }
            }
        }
        return dialog
    }

    fun anyOpen(): Boolean = open.any { it.isShowing }

    fun dismissAll() {
        for (d in open.toList()) {
            try { if (d.isShowing) d.dismiss() } catch (e: Exception) { }
        }
        open.clear()
    }

    /** A remote-friendly list dialog: dark card, focused row highlighted. */
    fun showChoice(
        activity: Activity,
        title: String?,
        labels: List<String>,
        onPick: (Int) -> Unit
    ): Dialog {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val dialog = Dialog(activity, R.style.LauncherPlainDialog)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog)
            setPadding(dp(28), dp(24), dp(28), dp(16))
            minimumWidth = dp(560)
        }
        if (title != null) {
            root.addView(TextView(activity).apply {
                text = title
                textSize = 28f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(8), 0, dp(8), dp(14))
            })
        }

        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        labels.forEachIndexed { index, label ->
            list.addView(TextView(activity).apply {
                text = label
                textSize = 22f
                setTextColor(Color.WHITE)
                setPadding(dp(20), dp(16), dp(20), dp(16))
                isFocusable = true
                isClickable = true
                setBackgroundResource(R.drawable.bg_choice)
                setOnClickListener {
                    dialog.dismiss()
                    onPick(index)
                }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(3)) })
        }
        root.addView(ScrollView(activity).apply { addView(list) })

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        track(dialog).show()
        list.getChildAt(0)?.requestFocus()
        return dialog
    }
}
