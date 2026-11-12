package com.mytv.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val banner: Drawable?,
    val intent: Intent
)

object AppRepository {

    /** Lists every launchable app (TV and regular launcher entries), without duplicates. */
    fun load(context: Context): List<AppItem> {
        val pm = context.packageManager
        val result = LinkedHashMap<String, AppItem>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName || result.containsKey(pkg)) continue
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(pkg, info.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val banner = try {
                    info.activityInfo.loadBanner(pm)
                } catch (e: Exception) {
                    null
                }
                // Pre-scaled to the size they are shown at: big original banners would cost memory and slow
                // the first scroll (the GPU has to upload each full-size image).
                val res = context.resources
                val smallBanner = banner?.let { scaled(res, it, 320, 180) }
                val smallIcon = scaled(res, info.loadIcon(pm), 96, 96)
                result[pkg] = AppItem(pkg, info.loadLabel(pm).toString(), smallIcon, smallBanner, intent)
            }
        }
        return result.values.toList()
    }

    /** Draws [d] into a w×h bitmap, centre-cropped (same as the tile's centerCrop), so the tile shows it 1:1. */
    private fun scaled(res: android.content.res.Resources, d: Drawable, w: Int, h: Int): Drawable {
        return try {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val dw = if (d.intrinsicWidth > 0) d.intrinsicWidth else w
            val dh = if (d.intrinsicHeight > 0) d.intrinsicHeight else h
            val k = maxOf(w / dw.toFloat(), h / dh.toFloat())
            val sw = dw * k
            val sh = dh * k
            d.setBounds(((w - sw) / 2f).toInt(), ((h - sh) / 2f).toInt(), ((w + sw) / 2f).toInt(), ((h + sh) / 2f).toInt())
            d.draw(c)
            BitmapDrawable(res, bmp)
        } catch (e: Throwable) {
            d
        }
    }

    /** Applies the user's saved order; apps not in the saved order go last, alphabetically. */
    fun sorted(items: List<AppItem>, order: List<String>): List<AppItem> {
        val index = HashMap<String, Int>()
        order.forEachIndexed { i, pkg -> index[pkg] = i }
        val (known, unknown) = items.partition { index.containsKey(it.packageName) }
        return known.sortedBy { index[it.packageName] } + unknown.sortedBy { it.label.lowercase() }
    }
}
