package com.mytv.launcher

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/**
 * A remote-friendly photo grid built on MediaStore, for TVs that have no system file picker.
 * Needs the photo-read permission (requested by the caller).
 */
object PhotoBrowser {

    enum class Kind { IMAGE, VIDEO, AUDIO }

    fun permission(kind: Kind = Kind.IMAGE): String =
        if (Build.VERSION.SDK_INT >= 33) when (kind) {
            Kind.IMAGE -> Manifest.permission.READ_MEDIA_IMAGES
            Kind.VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
            Kind.AUDIO -> Manifest.permission.READ_MEDIA_AUDIO
        } else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context, kind: Kind = Kind.IMAGE): Boolean =
        ContextCompat.checkSelfPermission(context, permission(kind)) == PackageManager.PERMISSION_GRANTED

    data class AudioItem(val uri: Uri, val title: String)

    /** Audio files on the device, for choosing an Adhan sound without a file manager. */
    fun queryAudio(context: Context): List<AudioItem> {
        val out = ArrayList<AudioItem>()
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DURATION),
                MediaStore.Audio.Media.DURATION + " > 3000", null,
                MediaStore.Audio.Media.DATE_ADDED + " DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < 300) {
                    out.add(AudioItem(
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0)),
                        c.getString(1) ?: "audio"
                    ))
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    private val pool = Executors.newFixedThreadPool(2)
    private val cache = object : LruCache<Long, Bitmap>(48) {}

    private fun queryPhotos(context: Context, kind: Kind): List<Uri> {
        val out = ArrayList<Uri>()
        val base = if (kind == Kind.VIDEO) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        try {
            context.contentResolver.query(
                base,
                arrayOf(MediaStore.MediaColumns._ID),
                null, null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < 400) {
                    out.add(ContentUris.withAppendedId(base, c.getLong(0)))
                }
            }
        } catch (e: Exception) {
            // Permission revoked or provider unavailable: show the empty state.
        }
        return out
    }

    private fun decodeVideoThumb(context: Context, uri: Uri): Bitmap? = try {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            r.getFrameAtTime(1_000_000L)?.let { Bitmap.createScaledBitmap(it, 320, 180, true) }
        } finally {
            r.release()
        }
    } catch (e: Exception) {
        null
    }

    private fun decodeThumb(context: Context, uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 360) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }

    fun show(
        activity: Activity, title: String, emptyText: String,
        kind: Kind = Kind.IMAGE, onPick: (Uri) -> Unit
    ) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val dialog = Dialog(activity, R.style.LauncherPlainDialog)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog)
            setPadding(dp(24), dp(20), dp(24), dp(16))
        }
        root.addView(TextView(activity).apply {
            text = title
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), 0, dp(8), dp(12))
        })
        val message = TextView(activity).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            visibility = View.GONE
        }
        val grid = RecyclerView(activity).apply {
            layoutManager = GridLayoutManager(activity, 4)
            clipToPadding = false
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        val screenH = activity.resources.displayMetrics.heightPixels
        root.addView(message)
        root.addView(grid, LinearLayout.LayoutParams(dp(820), (screenH * 0.62f).toInt()))

        val photos = ArrayList<Uri>()
        grid.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = photos.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val iv = ImageView(activity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    isFocusable = true
                    isClickable = true
                    setBackgroundColor(0x33FFFFFF)
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110))
                    (layoutParams as RecyclerView.LayoutParams).setMargins(dp(5), dp(5), dp(5), dp(5))
                    setOnFocusChangeListener { v, focused ->
                        v.animate().scaleX(if (focused) 1.08f else 1f).scaleY(if (focused) 1.08f else 1f)
                            .setDuration(120).start()
                        v.foreground = if (focused) GradientDrawable().apply {
                            setStroke(dp(3), Color.WHITE)
                        } else null
                    }
                }
                return object : RecyclerView.ViewHolder(iv) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val iv = holder.itemView as ImageView
                val uri = photos[position]
                val id = ContentUris.parseId(uri)
                iv.tag = id
                iv.setImageBitmap(cache.get(id))
                iv.setOnClickListener {
                    dialog.dismiss()
                    onPick(uri)
                }
                if (cache.get(id) == null) {
                    pool.execute {
                        val bmp = (if (kind == Kind.VIDEO) decodeVideoThumb(activity, uri) else decodeThumb(activity, uri))
                            ?: return@execute
                        cache.put(id, bmp)
                        activity.runOnUiThread { if (iv.tag == id) iv.setImageBitmap(bmp) }
                    }
                }
            }
        }

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        pool.execute {
            val found = queryPhotos(activity, kind)
            activity.runOnUiThread {
                if (found.isEmpty()) {
                    message.text = emptyText
                    message.visibility = View.VISIBLE
                    grid.visibility = View.GONE
                } else {
                    photos.addAll(found)
                    grid.adapter?.notifyDataSetChanged()
                    grid.post { grid.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
                }
            }
        }
    }
}
