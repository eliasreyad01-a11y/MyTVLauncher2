package com.mytv.launcher

import android.content.Context
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/** Draws the soft band behind the dock row (the first row of the home grid). */
class DockBand(context: Context, private val adapter: AppAdapter) : RecyclerView.ItemDecoration() {
    private val band = ContextCompat.getDrawable(context, R.drawable.bg_dock)
    private val pad = (6 * context.resources.displayMetrics.density).toInt()

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (adapter.dockCount == 0) return
        val first = parent.layoutManager?.findViewByPosition(0) ?: return
        val drawable = band ?: return
        drawable.setBounds(
            parent.paddingLeft - pad,
            first.top - pad,
            parent.width - parent.paddingRight + pad,
            first.bottom
        )
        drawable.draw(c)
    }
}
