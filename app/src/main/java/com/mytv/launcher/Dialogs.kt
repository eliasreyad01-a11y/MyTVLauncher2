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

    /** Blurred, darkened mountain backdrop shared by the Apple-style full pages. */
    fun backdrop(activity: Activity): android.graphics.drawable.Drawable? = try {
        val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 24 }
        val small = android.graphics.BitmapFactory.decodeResource(activity.resources, R.drawable.wp_kirkjufell, o)
        val bd = android.graphics.drawable.BitmapDrawable(activity.resources, small).apply {
            isFilterBitmap = true; gravity = android.view.Gravity.FILL
        }
        android.graphics.drawable.LayerDrawable(arrayOf(bd, android.graphics.drawable.ColorDrawable(Color.parseColor("#8C0E1A1F"))))
    } catch (e: Throwable) { null }

    /** tvOS-style choice page: dimmed centred title, squircle on the left, pill rows on the right, tick on the current value. */
    private fun rowBg(focused: Boolean): android.graphics.drawable.Drawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(if (focused) "#F5FFFFFF" else "#38FFFFFF"))
            cornerRadius = 10f * android.content.res.Resources.getSystem().displayMetrics.density
        }

    fun showChoice(
        activity: Activity,
        title: String?,
        labels: List<String>,
        selected: Int = -1,
        onPick: (Int) -> Unit
    ): Dialog {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val dialog = Dialog(activity, R.style.LauncherPlainDialog)

        val titleView = TextView(activity).apply {
            text = title ?: ""
            textSize = 32f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#B3F4F8FF"))
            setTypeface(typeface, Typeface.BOLD)
        }
        val tile = android.widget.FrameLayout(activity).apply {
            setBackgroundResource(R.drawable.bg_settings_tile)
            addView(android.widget.ImageView(activity).apply {
                setImageResource(R.drawable.ic_tune)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#66FFFFFF"))
            }, android.widget.FrameLayout.LayoutParams(dp(110), dp(110), android.view.Gravity.CENTER))
        }
        val left = android.widget.FrameLayout(activity).apply {
            addView(tile, android.widget.FrameLayout.LayoutParams(dp(210), dp(210), android.view.Gravity.CENTER))
        }
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), dp(8), dp(40))
        }
        labels.forEachIndexed { index, label ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                minimumHeight = dp(56)
                background = rowBg(false)
                setPadding(dp(18), dp(10), dp(18), dp(10))
            }
            val t = TextView(activity).apply {
                text = label
                textSize = 19f
                textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
                setTextColor(Color.parseColor("#F4F8FF"))
            }
            val tick = TextView(activity).apply {
                text = "\u2713"
                textSize = 22f
                setTextColor(Color.parseColor("#F4F8FF"))
                visibility = if (index == selected) android.view.View.VISIBLE else android.view.View.INVISIBLE
            }
            row.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(tick)
            row.setOnClickListener { dialog.dismiss(); onPick(index) }
            row.setOnFocusChangeListener { v, f ->
                v.background = rowBg(f)
                v.animate().scaleX(if (f) 1.03f else 1f).scaleY(if (f) 1.03f else 1f).setDuration(140).start()
                t.setTextColor(Color.parseColor(if (f) "#10151B" else "#F4F8FF"))
                tick.setTextColor(Color.parseColor(if (f) "#10151B" else "#F4F8FF"))
            }
            list.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(3)) })
        }
        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.25f))
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
            background = backdrop(activity)
            setPadding(dp(64), dp(28), dp(64), 0)
            addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(18) })
            addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        body.alpha = 0f; body.translationX = dp(40).toFloat()
        body.animate().alpha(1f).translationX(0f).setDuration(300).start()

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        track(dialog).show()
        (if (selected in 0 until list.childCount) list.getChildAt(selected) else list.getChildAt(0))?.requestFocus()
        return dialog
    }
}
