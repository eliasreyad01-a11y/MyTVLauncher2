package com.mytv.launcher

import android.content.Context
import android.graphics.Canvas
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/** Draws the glass band behind the dock tiles (the first row of the home grid), hugging the tiles that exist. */
class DockBand(context: Context, private val adapter: AppAdapter) : RecyclerView.ItemDecoration() {
    private val band = ContextCompat.getDrawable(context, R.drawable.bg_dock)
    private val pad = (10 * context.resources.displayMetrics.density).toInt()

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (adapter.dockCount == 0) return
        val lm = parent.layoutManager ?: return
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var top = 0
        var bottom = 0
        for (p in 0 until adapter.dockCount) {
            if (adapter.items.getOrNull(p)?.packageName?.startsWith(AppAdapter.PAD_PREFIX) == true) continue
            val item = lm.findViewByPosition(p) ?: continue
            val tile: View = item.findViewById(R.id.tile) ?: continue
            val ox = item.left + item.translationX.toInt()
            left = minOf(left, ox + tile.left)
            right = maxOf(right, ox + tile.right)
            top = item.top + tile.top
            bottom = item.top + tile.bottom
        }
        if (left == Int.MAX_VALUE) return
        val drawable = band ?: return
        drawable.setBounds(left - pad, top - pad, right + pad, bottom + pad)
        drawable.draw(c)
    }
}
