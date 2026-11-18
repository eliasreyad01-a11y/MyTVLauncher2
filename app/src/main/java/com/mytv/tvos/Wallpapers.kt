package com.mytv.tvos

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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

/**
 * Choosing the wallpaper from the TV's own files. Two ways are offered: the launcher's remote-friendly file browser (works on
 * every TV, needs the storage permission) and the system picker (only where the TV has one). Whatever is chosen is shown in a
 * preview first and, once applied, copied into the launcher's private storage: the wallpaper then survives a restart, a
 * removed USB stick, and a revoked permission.
 */
object WallpaperPicker {
    private const val REQ_PERM = 21
    private const val REQ_DOC = 22
    private val EXT = setOf("jpg", "jpeg", "png", "webp", "bmp")
    private var afterPermission: (() -> Unit)? = null
    private var done: (() -> Unit)? = null

    private fun sp(a: Activity) = a.getSharedPreferences("tvos_settings", Context.MODE_PRIVATE)

    fun show(a: Activity, onDone: () -> Unit) {
        done = onDone
        val s = sp(a)
        val items = ArrayList<Pair<String, () -> Unit>>()
        items.add("None" to { s.edit().putString("wall_mode", "none").apply(); onDone() })
        items.add("Default" to { s.edit().putString("wall_mode", "default").apply(); onDone() })
        items.add("Choose from TV storage" to { pickFromStorage(a) })
        if (systemPickerIntent().resolveActivity(a.packageManager) != null) items.add("Choose with the system picker" to { pickWithSystem(a) })
        Menus.show(a, "Wallpaper", items)
    }

    private fun systemPickerIntent() = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

    private fun pickWithSystem(a: Activity) {
        try { a.startActivityForResult(systemPickerIntent(), REQ_DOC) } catch (_: Exception) { toast(a, "Not available on this device") }
    }

    private fun pickFromStorage(a: Activity) {
        val needed = if (Build.VERSION.SDK_INT >= 33) arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        val open = {
            FileBrowser.show(a, "Choose a picture", EXT) { f -> preview(a, f.name) { openFile(f) } }
        }
        if (Build.VERSION.SDK_INT < 23 || needed.all { a.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) open()
        else { afterPermission = open; try { a.requestPermissions(needed, REQ_PERM) } catch (_: Exception) { afterPermission = null; open() } }
    }

    /** Forward from the activity's onRequestPermissionsResult. The browser opens even when refused: the app's own folders still work. */
    fun onPermission(requestCode: Int) {
        if (requestCode != REQ_PERM) return
        val then = afterPermission; afterPermission = null; then?.invoke()
    }

    /** Forward from the activity's onActivityResult. */
    fun onActivityResult(a: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_DOC || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        try { a.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        preview(a, uri.lastPathSegment ?: "") { try { a.contentResolver.openInputStream(uri) } catch (_: Exception) { null } }
    }

    private fun openFile(f: File): java.io.InputStream? = try { java.io.FileInputStream(f) } catch (_: Exception) { null }

    private fun toast(a: Activity, s: String) = Toast.makeText(a, L.t(s), Toast.LENGTH_SHORT).show()

    /** Decodes at most [maxW] pixels wide (never the full-size picture) and turns it upright using its EXIF rotation. Blocking. */
    fun decodeStream(open: () -> java.io.InputStream?, maxW: Int = 1920): Bitmap? {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, o) }
            if (o.outWidth <= 0 || o.outHeight <= 0) return null
            var s = 1
            while (o.outWidth / (s * 2) >= maxW) s *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = s }
            val bmp = open()?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
            val deg = try {
                open()?.use { st ->
                    when (android.media.ExifInterface(st).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } ?: 0
            } catch (_: Exception) { 0 }
            if (deg == 0) bmp else {
                val m = android.graphics.Matrix().apply { postRotate(deg.toFloat()) }
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
        } catch (_: Throwable) { null }
    }

    /** Saves the bitmap as the wallpaper: a private copy, so it never depends on the original file again. Blocking. */
    fun saveAsWallpaper(ctx: Context, bmp: Bitmap): Boolean {
        return try {
            val s = ctx.getSharedPreferences("tvos_settings", Context.MODE_PRIVATE)
            val dir = ctx.filesDir
            val out = File(dir, "wallpaper_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            val old = s.getString("wall_path", null)
            s.edit().putString("wall_mode", "custom").putString("wall_path", out.absolutePath).putLong("wall_stamp", System.currentTimeMillis()).apply()
            if (old != null && old != out.absolutePath && old.startsWith(dir.absolutePath)) File(old).delete()
            true
        } catch (_: Throwable) { false }
    }

    private fun preview(a: Activity, name: String, open: () -> java.io.InputStream?) {
        Thread {
            val bmp = decodeStream(open)
            a.runOnUiThread {
                if (a.isDestroyed) return@runOnUiThread
                if (bmp == null) { toast(a, "This picture can't be opened"); return@runOnUiThread }
                showPreview(a, name, bmp)
            }
        }.start()
    }

    private fun showPreview(a: Activity, name: String, bmp: Bitmap) {
        val u = U(a)
        val d = Dialog(a, android.R.style.Theme_Translucent_NoTitleBar)
        val panel = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = Glass.glass(0xE62A2A2C.toInt(), u.px(48f), 0.7f)
            setPadding(u.i(36f), u.i(30f), u.i(36f), u.i(30f))
        }
        panel.addView(TextView(a).apply {
            text = L.t("Preview") + " · " + name
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(30f)); setTextColor(0xB3FFFFFF.toInt()); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        })
        val img = android.widget.ImageView(a).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP; setImageBitmap(bmp) }
        panel.addView(img, LinearLayout.LayoutParams(u.i(960f), u.i(540f)).apply { topMargin = u.i(14f); bottomMargin = u.i(16f) })
        val bar = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        fun btn(label: String, act: () -> Unit) = TextView(a).apply {
            text = L.t(label); setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(32f)); setTextColor(Glass.text()); gravity = Gravity.CENTER
            isFocusable = true; isClickable = true; background = Glass.rowBg(u, 33f, 0x24FFFFFF)
            setOnClickListener { act() }
        }
        val applyBtn = btn("Apply") {
            d.dismiss()
            Thread {
                val ok = saveAsWallpaper(a, bmp)
                a.runOnUiThread { if (ok) { toast(a, "Wallpaper set"); done?.invoke() } else toast(a, "Could not save the wallpaper") }
            }.start()
        }
        bar.addView(applyBtn, LinearLayout.LayoutParams(0, u.i(70f), 1f).apply { marginEnd = u.i(12f) })
        bar.addView(btn("Cancel") { d.dismiss() }, LinearLayout.LayoutParams(0, u.i(70f), 1f))
        panel.addView(bar)
        d.setContentView(panel, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        d.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER); setDimAmount(0.6f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        d.setOnShowListener { applyBtn.requestFocus() }
        d.show()
    }
}
