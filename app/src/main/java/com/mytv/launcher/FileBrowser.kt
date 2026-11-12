package com.mytv.launcher

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Environment
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * A remote-friendly file browser for the TV's own storage (internal storage, USB drives, app folders).
 * Shows folders and only the files with the wanted extensions; Back goes up one folder.
 */
class FileBrowser private constructor(
    private val activity: Activity,
    private val title: String,
    private val extensions: Set<String>,
    private val onPick: (File) -> Unit
) {
    private class Entry(val label: String, val file: File?, val isDir: Boolean, val isUp: Boolean = false)

    private val density = activity.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private val dialog = Dialogs.track(Dialog(activity, R.style.LauncherPlainDialog))
    private val entries = ArrayList<Entry>()
    private var current: File? = null // null = list of storage locations
    private lateinit var pathView: TextView
    private lateinit var rv: RecyclerView

    private val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val tv = TextView(activity).apply {
                textSize = 21f
                setTextColor(Color.WHITE)
                setPadding(dp(20), dp(13), dp(20), dp(13))
                isFocusable = true
                isClickable = true
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                setBackgroundResource(R.drawable.bg_choice)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(3), 0, dp(3)) }
            }
            return object : RecyclerView.ViewHolder(tv) {}
        }

        override fun getItemCount() = entries.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val e = entries[position]
            val tv = holder.itemView as TextView
            tv.text = when {
                e.isUp -> "⬆  " + e.label
                e.isDir -> "📁  " + e.label
                else -> "📄  " + e.label
            }
            tv.setOnClickListener {
                val f = e.file
                when {
                    e.isUp -> goUp()
                    e.isDir -> open(f)
                    f != null -> {
                        dialog.dismiss()
                        onPick(f)
                    }
                }
            }
        }
    }

    private fun build() {
        val dm = activity.resources.displayMetrics
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog)
            setPadding(dp(28), dp(22), dp(28), dp(14))
        }
        root.addView(TextView(activity).apply {
            text = title
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        pathView = TextView(activity).apply {
            textSize = 14f
            alpha = 0.8f
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(10))
            maxLines = 2
        }
        root.addView(pathView)
        rv = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = this@FileBrowser.adapter
            clipToPadding = false
        }
        root.addView(rv, LinearLayout.LayoutParams((dm.widthPixels * 0.62f).toInt(), (dm.heightPixels * 0.66f).toInt()))

        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) goUp()
                true
            } else {
                false
            }
        }
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        open(null)
    }

    private fun goUp() {
        val c = current
        if (c == null) {
            dialog.dismiss()
            return
        }
        val topLevel = roots().any { it.second.absolutePath == c.absolutePath }
        open(if (topLevel) null else c.parentFile)
    }

    private fun open(dir: File?) {
        current = dir
        entries.clear()
        if (dir == null) {
            for ((label, f) in roots()) entries.add(Entry(label, f, true))
            pathView.text = activity.getString(R.string.browse_pick_location)
        } else {
            entries.add(Entry(activity.getString(R.string.browse_up), null, true, isUp = true))
            val list = try { dir.listFiles() } catch (e: Exception) { null }
            pathView.text = dir.absolutePath
            if (list == null) {
                Toast.makeText(activity, R.string.browse_cannot_read, Toast.LENGTH_LONG).show()
            } else {
                list.filter { it.isDirectory && !it.name.startsWith(".") }
                    .sortedBy { it.name.lowercase() }
                    .forEach { entries.add(Entry(it.name, it, true)) }
                list.filter { it.isFile && it.extension.lowercase() in extensions }
                    .sortedBy { it.name.lowercase() }
                    .forEach { entries.add(Entry(it.name, it, false)) }
            }
        }
        adapter.notifyDataSetChanged()
        rv.scrollToPosition(0)
        rv.post {
            val idx = if (dir != null && entries.size > 1) 1 else 0
            (rv.findViewHolderForAdapterPosition(idx) ?: rv.findViewHolderForAdapterPosition(0))
                ?.itemView?.requestFocus()
        }
    }

    /** Storage locations: internal storage, other volumes (USB / SD card) and the app's own folder. */
    private fun roots(): List<Pair<String, File>> {
        val out = ArrayList<Pair<String, File>>()
        val seen = HashSet<String>()
        fun add(label: String, f: File?) {
            if (f != null && f.exists() && seen.add(f.absolutePath)) out.add(label to f)
        }
        add(activity.getString(R.string.browse_internal), Environment.getExternalStorageDirectory())
        try {
            File("/storage").listFiles()?.forEach { v ->
                if (v.isDirectory && v.name != "emulated" && v.name != "self" && v.canRead()) {
                    add(activity.getString(R.string.browse_usb_fmt, v.name), v)
                }
            }
        } catch (e: Exception) {
        }
        add(activity.getString(R.string.browse_app_folder), activity.getExternalFilesDir(null))
        return out
    }

    companion object {
        fun show(activity: Activity, title: String, extensions: Set<String>, onPick: (File) -> Unit) {
            FileBrowser(activity, title, extensions, onPick).build()
        }
    }
}
