package com.mytv.launcher

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onClick: (AppItem) -> Unit,
    private val onLongClick: (AppItem, AppAdapter) -> Unit,
    private val onFocus: ((AppItem) -> Unit)? = null
) : RecyclerView.Adapter<AppAdapter.VH>() {

    val items = ArrayList<AppItem>()

    /** The first [dockCount] positions are the dock row (favourites, the add tile, then invisible pads). */
    var dockCount = 0
    fun isDockAt(position: Int) = position in 0 until dockCount
    fun isDock(pkg: String) = isDockAt(items.indexOfFirst { it.packageName == pkg })

    /** Package currently being moved (highlighted); null when not moving. */
    var movingPackage: String? = null

    /** Visual options, set by the home screen from the user's settings. */
    var glossy = true
    var animate = true

    /** Bright focus ring for tiles inside dialogs (where the home screen's glow is not drawn). */
    var focusRing = false

    /** Replaces the list with a diff, so added/removed/moved tiles animate instead of flashing. */
    fun submit(newItems: List<AppItem>) {
        val old = ArrayList(items)
        val result = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].packageName == newItems[n].packageName
            override fun areContentsTheSame(o: Int, n: Int) =
                old[o].label == newItems[n].label && old[o].packageName == newItems[n].packageName
        }, false)
        items.clear()
        items.addAll(newItems)
        result.dispatchUpdatesTo(this)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        var fgRes = 0
        val tile: View = view.findViewById(R.id.tile)
        val banner: ImageView = view.findViewById(R.id.banner)
        val iconBox: View = view.findViewById(R.id.iconBox)
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    /** The same Drawable must not be shared between two ImageViews of different sizes. */
    private fun copyOf(d: Drawable): Drawable = d.constantState?.newDrawable()?.mutate() ?: d

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val pad = item.packageName.startsWith(PAD_PREFIX)
        holder.itemView.visibility = if (pad) View.INVISIBLE else View.VISIBLE
        holder.tile.isFocusable = !pad
        holder.tile.isClickable = !pad
        val banner = item.banner
        if (banner != null) {
            holder.banner.setImageDrawable(copyOf(banner))
            holder.banner.visibility = View.VISIBLE
            holder.iconBox.visibility = View.GONE
        } else {
            holder.banner.setImageDrawable(null)
            holder.banner.visibility = View.GONE
            holder.iconBox.visibility = View.VISIBLE
            holder.icon.setImageDrawable(copyOf(item.icon))
            holder.label.text = item.label
        }
        holder.tile.tag = item.label
        holder.tile.setTag(R.id.tile, item)
        holder.tile.isActivated = item.packageName == movingPackage
        val fgRes = if (focusRing) R.drawable.fg_tile_ring else if (glossy) R.drawable.fg_tile else R.drawable.fg_tile_flat
        if (holder.fgRes != fgRes) {
            holder.fgRes = fgRes
            holder.tile.foreground = androidx.core.content.ContextCompat.getDrawable(holder.tile.context, fgRes)
        }

        holder.tile.setOnClickListener {
            if (movingPackage == null) onClick(item)
        }
        holder.tile.setOnLongClickListener {
            if (movingPackage == null) onLongClick(item, this)
            true
        }
        holder.tile.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) onFocus?.invoke(item)
        }
    }

    companion object {
        /** Tiles keep their size on focus (like Secreto): only the glass highlight marks the focus, so nothing re-scales while the page moves. */
        const val FOCUS_SCALE = 1.0f

        /** Invisible filler tiles that complete the dock row so grid apps never slip into it. */
        const val PAD_PREFIX = "__pad"
    }
}
