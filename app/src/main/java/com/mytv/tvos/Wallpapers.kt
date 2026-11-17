package com.mytv.tvos

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Environment
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/** Which picture sits behind the home screen: none, the built-in one, or a file picked from the TV's own storage. */
object Wallpapers {
    fun mode(sp: android.content.SharedPreferences): String =
        sp.getString("wall_mode", null) ?: if (sp.getBoolean("wall", false)) "default" else "none"

    fun key(sp: android.content.SharedPreferences): String = mode(sp) + ":" + (sp.getString("wall_path", "") ?: "") + ":" + (sp.getLong("wall_stamp", 0L))

    /** Decodes the wallpaper at roughly [maxW] pixels wide (never the full-size image: that would waste memory). Blocking. */
    fun decode(ctx: Context, sp: android.content.SharedPreferences, maxW: Int = 1920): Bitmap? {
        return try {
            when (mode(sp)) {
                "default" -> sample(maxW) { o -> BitmapFactory.decodeResource(ctx.resources, R.drawable.wp_kirkjufell, o) }
                "custom" -> {
                    val path = sp.getString("wall_path", null) ?: return null
                    if (!File(path).canRead()) null else sample(maxW) { o -> BitmapFactory.decodeFile(path, o) }
                }
                else -> null
            }
        } catch (_: Throwable) { null }
    }

    private fun sample(maxW: Int, decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (o.outWidth / (s * 2) >= maxW) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 }
        return decode(o2)
    }
}

/** A remote-friendly picker for the TV's storage (internal, USB, SD): folders plus the files with the wanted extensions. */
class FileBrowser private constructor(
    private val activity: Activity,
    private val title: String,
    private val extensions: Set<String>,
    private val onPick: (File) -> Unit
) {
    private class Item(val label: String, val file: File?, val isDir: Boolean, val isUp: Boolean = false)

    private val u = U(activity)
    private val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
    private val items = ArrayList<Item>()
    private var current: File? = null
    private lateinit var pathView: TextView
    private lateinit var rv: RecyclerView

    private val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val tv = TextView(activity).apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(30f))
                setTextColor(Glass.text())
                gravity = Gravity.CENTER_VERTICAL
                setPadding(u.i(26f), 0, u.i(26f), 0)
                isFocusable = true
                isClickable = true
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                background = Glass.rowBg(u, 30f, 0x14FFFFFF)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.i(66f)).apply { setMargins(0, u.i(4f), 0, u.i(4f)) }
            }
            return object : RecyclerView.ViewHolder(tv) {}
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val e = items[position]
            val tv = holder.itemView as TextView
            tv.text = when { e.isUp -> "..  " + L.t("Up"); e.isDir -> "▸  " + e.label; else -> e.label }
            tv.setOnClickListener {
                val f = e.file
                when {
                    e.isUp -> goUp()
                    e.isDir -> open(f)
                    f != null -> { dialog.dismiss(); onPick(f) }
                }
            }
        }
    }

    private fun build() {
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = Glass.glass(0xE62A2A2C.toInt(), u.px(48f), 0.7f)
            setPadding(u.i(36f), u.i(28f), u.i(36f), u.i(20f))
        }
        panel.addView(TextView(activity).apply {
            text = L.t(title)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(36f))
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        pathView = TextView(activity).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(24f))
            setTextColor(0x99FFFFFF.toInt())
            setPadding(0, u.i(6f), 0, u.i(12f))
            maxLines = 2
        }
        panel.addView(pathView)
        rv = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = this@FileBrowser.adapter
            itemAnimator = null
            clipToPadding = false
        }
        panel.addView(rv, LinearLayout.LayoutParams(u.i(1100f), u.i(640f)))
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK) { if (event.action == KeyEvent.ACTION_UP) goUp(); true } else false
        }
        dialog.setContentView(panel)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.55f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(R.style.DialogFade)
        }
        dialog.show()
        open(null)
    }

    private fun goUp() {
        val c = current
        if (c == null) { dialog.dismiss(); return }
        val topLevel = roots().any { it.second.absolutePath == c.absolutePath }
        open(if (topLevel) null else c.parentFile)
    }

    private fun open(dir: File?) {
        current = dir
        items.clear()
        if (dir == null) {
            for ((label, f) in roots()) items.add(Item(label, f, true))
            pathView.text = L.t("Choose a location")
        } else {
            items.add(Item("", null, true, isUp = true))
            val list = try { dir.listFiles() } catch (_: Exception) { null }
            pathView.text = dir.absolutePath
            if (list == null) Toast.makeText(activity, L.t("Cannot read this folder"), Toast.LENGTH_LONG).show()
            else {
                list.filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }.forEach { items.add(Item(it.name, it, true)) }
                list.filter { it.isFile && it.extension.lowercase() in extensions }.sortedBy { it.name.lowercase() }.forEach { items.add(Item(it.name, it, false)) }
            }
        }
        adapter.notifyDataSetChanged()
        rv.scrollToPosition(0)
        rv.post {
            val idx = if (dir != null && items.size > 1) 1 else 0
            (rv.findViewHolderForAdapterPosition(idx) ?: rv.findViewHolderForAdapterPosition(0))?.itemView?.requestFocus()
        }
    }

    private fun roots(): List<Pair<String, File>> {
        val out = ArrayList<Pair<String, File>>()
        val seen = HashSet<String>()
        fun add(label: String, f: File?) { if (f != null && f.exists() && seen.add(f.absolutePath)) out.add(label to f) }
        @Suppress("DEPRECATION") add(L.t("Internal storage"), Environment.getExternalStorageDirectory())
        try {
            File("/storage").listFiles()?.forEach { v ->
                if (v.isDirectory && v.name != "emulated" && v.name != "self" && v.canRead()) add("USB / SD: " + v.name, v)
            }
        } catch (_: Exception) {}
        add(L.t("App folder"), activity.getExternalFilesDir(null))
        return out
    }

    companion object {
        fun show(activity: Activity, title: String, extensions: Set<String>, onPick: (File) -> Unit) { FileBrowser(activity, title, extensions, onPick).build() }
    }
}
