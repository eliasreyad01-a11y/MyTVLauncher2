package com.mytv.tvos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppsAdapter(
    private val items: List<AppItem>,
    private val onFocus: (AppItem) -> Unit,
    private val onClick: (AppItem) -> Unit
) : RecyclerView.Adapter<AppsAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        val lp = RecyclerView.LayoutParams(view.layoutParams)
        lp.marginEnd = (16 * parent.resources.displayMetrics.density).toInt()
        view.layoutParams = lp
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.icon.setImageDrawable(item.icon)
        holder.label.text = item.label
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.12f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(160).start()
            v.translationZ = if (focused) 12f else 0f
            if (focused) onFocus(item)
        }
    }

    override fun getItemCount(): Int = items.size
}
