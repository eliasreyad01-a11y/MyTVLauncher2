package com.mytv.tvos

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** The grid of the home screen and of folders. Row 0 of the home grid is the dock. */
class HomeAdapter(
    private val u: U,
    private val home: Boolean,
    private val onFocus: (Entry) -> Unit,
    private val onClick: (Entry) -> Unit,
    private val onMenu: (Entry) -> Unit
) : RecyclerView.Adapter<HomeAdapter.VH>() {

    var cells: List<Entry> = emptyList()
    var alwaysLabels = false
    var reduceMotion = false
    var movingKey: String? = null

    class VH(val cell: FrameLayout, val tile: TileView, val label: TextView) : RecyclerView.ViewHolder(cell) {
        var jiggle: ObjectAnimator? = null
        var entry: Entry? = null
    }

    private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val cell = FrameLayout(parent.context).apply {
            clipChildren = false
            clipToPadding = false
            isFocusable = true
            isLongClickable = true
        }
        val tile = TileView(parent.context, u)
        cell.addView(tile, FrameLayout.LayoutParams(u.i(TileView.W), u.i(TileView.H), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        val label = TextView(parent.context).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(30f))
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            alpha = 0f
            setShadowLayer(u.px(8f), 0f, 0f, 0x99000000.toInt())
        }
        cell.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            topMargin = u.i(TileView.H + 24f)
            marginStart = u.i(8f); marginEnd = u.i(8f)
        })
        val vh = VH(cell, tile, label)
        cell.setOnFocusChangeListener { _, f -> focusChanged(vh, f) }
        cell.setOnClickListener { vh.entry?.let { if (!it.isSpacer) onClick(it) } }
        cell.setOnLongClickListener { vh.entry?.let { if (!it.isSpacer) { onMenu(it); true } else false } ?: false }
        return vh
    }

    private fun focusChanged(h: VH, f: Boolean) {
        val e = h.entry ?: return
        val scale = if (f && !reduceMotion) SCALE else 1f
        h.tile.animate().cancel()
        h.tile.animate().scaleX(scale).scaleY(scale).translationZ(if (f) u.px(30f) else 0f).setDuration(if (reduceMotion) 0 else 230).setInterpolator(ease).start()
        ObjectAnimator.ofFloat(h.tile, "sheen", if (f) 1f else 0f).setDuration(200).start()
        val showLabel = f || alwaysLabels
        val labelAllowed = !(home && h.bindingAdapterPosition in 0 until Store.COLUMNS) || alwaysLabels
        h.label.animate().alpha(if (showLabel && labelAllowed) 1f else 0f).setDuration(180).start()
        if (f) onFocus(e)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val e = cells[position]
        h.entry = e
        h.tile.entry = e
        h.label.text = e.title
        val dockRow = home && position < Store.COLUMNS
        val spacer = e.isSpacer
        h.cell.isFocusable = !spacer
        h.cell.isLongClickable = !spacer
        h.tile.visibility = if (spacer) View.INVISIBLE else View.VISIBLE
        val rowGap = if (home) (if (dockRow) 81f else 108f) else 93f
        h.cell.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.i(TileView.H + rowGap))
        h.label.alpha = if (alwaysLabels && !spacer) 1f else 0f
        h.tile.scaleX = 1f; h.tile.scaleY = 1f; h.tile.translationZ = 0f; h.tile.sheen = 0f
        h.jiggle?.cancel(); h.jiggle = null; h.tile.rotation = 0f
        if (movingKey != null && e.key == movingKey && !reduceMotion) {
            h.jiggle = ObjectAnimator.ofFloat(h.tile, "rotation", -2f, 2f).apply {
                duration = 120; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator(); start()
            }
        }
    }

    override fun getItemCount() = cells.size

    override fun onViewRecycled(h: VH) {
        h.jiggle?.cancel(); h.jiggle = null
        h.tile.animate().cancel()
        super.onViewRecycled(h)
    }

    companion object { const val SCALE = 1.14f }
}

/** Keeps the dock where it rests, and lifts the focused row of the library to the same height every time. */
class HomeLayoutManager(ctx: Context, span: Int, private val u: U) : GridLayoutManager(ctx, span) {
    override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean): Boolean {
        val row = getPosition(child) / spanCount
        val top = child.top
        val rest = u.px(REST_TOP)
        val lift = u.px(LIFT_TOP)
        val desired = when {
            row == 0 -> rest
            row == 1 -> lift
            else -> lift
        }
        val dy = (top - desired).toInt()
        if (dy == 0) return false
        if (immediate) parent.scrollBy(0, dy) else parent.smoothScrollBy(0, dy, DecelerateInterpolator(1.7f), 300)
        return true
    }

    companion object {
        const val REST_TOP = 810f
        const val LIFT_TOP = 177f
    }
}

/** The rounded translucent plate behind the dock row. It scrolls away with the dock. */
class PlateDecoration(private val u: U) : RecyclerView.ItemDecoration() {
    private val glass = GlossDrawable(u.px(58f), 0x1AFFFFFF, 1f, true)

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        var top = Int.MIN_VALUE
        for (i in 0 until parent.childCount) {
            val v = parent.getChildAt(i)
            if (parent.getChildAdapterPosition(v) in 0 until Store.COLUMNS) { top = v.top; break }
        }
        if (top == Int.MIN_VALUE) return
        val l = u.px(51f); val t = top - u.px(39f)
        val w = (parent.width - u.px(102f)).toInt(); val h = (u.px(TileView.H + 78f)).toInt()
        glass.setBounds(0, 0, w, h)
        glass.setOrigin(l, t, parent.width.toFloat(), parent.height.toFloat())
        c.save(); c.translate(l, t); glass.draw(c); c.restore()
    }
}


/**
 * A grid that scrolls the moment the focus moves to another row: the focused row is always brought to the top edge of the list,
 * so the page never looks as if it were waiting for the cursor to reach the bottom.
 */
class RowScrollGrid(ctx: Context, span: Int) : GridLayoutManager(ctx, span) {
    override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean): Boolean {
        val dy = child.top - parent.paddingTop
        if (kotlin.math.abs(dy) < 3) return false
        if (immediate) parent.scrollBy(0, dy) else parent.smoothScrollBy(0, dy, DecelerateInterpolator(1.6f), 240)
        return true
    }
}
