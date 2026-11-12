package com.mytv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/** Folders used for user media, shared by the home screen and the settings screen. */
object MediaDirs {
    val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp")

    fun slideDir(context: Context): File {
        val dir = context.getExternalFilesDir("Slideshow") ?: File(context.filesDir, "Slideshow")
        dir.mkdirs()
        return dir
    }

    fun slidePhotos(context: Context): List<File> =
        slideDir(context).listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTS }
            ?.sortedBy { it.name }
            ?: emptyList()
}

/**
 * Home background: a built-in gradient preset, or a user image, a looping muted video,
 * or a rotating photo slideshow.
 */
class WallpaperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = GONE
    }
    private val video = VideoBackground(context).apply { visibility = GONE }
    private val dimView = View(context)
    private val handler = Handler(Looper.getMainLooper())

    private var key: String? = null
    private var slideshowActive = false
    private var slideFiles: List<File> = emptyList()
    private var slideIndex = 0
    private var slideRes: IntArray? = null
    private var slideMs = 30_000L

    private val slideTick = object : Runnable {
        override fun run() {
            showNextSlide()
            handler.postDelayed(this, slideMs)
        }
    }

    init {
        setBackgroundResource(R.drawable.bg_ocean)
        val match = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(image, match)
        addView(video, LayoutParams(match))
        addView(dimView, LayoutParams(match))
    }

    fun apply(prefs: Prefs) {
        dimView.setBackgroundColor(Color.argb(prefs.dim.coerceIn(0, 90) * 255 / 100, 0, 0, 0))

        val type = prefs.wallpaperType
        val uriStr = prefs.wallpaperUri
        slideMs = prefs.slideSeconds.coerceIn(5, 3600) * 1000L
        val newKey = "$type|$uriStr|${prefs.wallpaperPreset}|${prefs.wallpaperStamp}|${prefs.slideSeconds}"
        if (newKey == key) return
        key = newKey

        stopSlideshow()
        slideRes = null
        image.visibility = GONE
        image.alpha = 1f
        image.setImageDrawable(null)
        video.visibility = GONE
        video.setSource(null)
        setBackgroundResource(presetRes(prefs.wallpaperPreset))

        when (type) {
            "video" -> {
                if (uriStr == null) return
                video.visibility = VISIBLE
                video.setSource(Uri.parse(uriStr))
            }
            "image" -> {
                if (uriStr == null) return
                val uri = Uri.parse(uriStr)
                val dm = resources.displayMetrics
                Thread {
                    val bmp = decodeSampled(uri, dm.widthPixels, dm.heightPixels)
                    post {
                        if (key == newKey && bmp != null) {
                            image.setImageBitmap(bmp)
                            image.visibility = VISIBLE
                        }
                    }
                }.start()
            }
            "slideshow" -> startSlideshow()
            "slideshow_builtin" -> {
                slideRes = PHOTOS
                startSlideshow()
            }
            else -> {
                val res = photoRes(prefs.wallpaperPreset)
                if (res != 0) {
                    image.setImageResource(res)
                    image.visibility = VISIBLE
                }
            }
        }
    }

    fun onResume() {
        video.resume()
        if (slideshowActive) {
            handler.removeCallbacks(slideTick)
            handler.post(slideTick)
        }
    }

    fun onPause() {
        video.pause()
        handler.removeCallbacks(slideTick)
    }

    /** Only the Galata Tower / seagull photos are bundled; a removed preset falls back to the first Galata photo. */
    private fun photoRes(preset: String): Int = when (preset) {
        "kirkjufell" -> R.drawable.wp_kirkjufell
        "photo1" -> R.drawable.wp_photo1
        "photo3" -> R.drawable.wp_photo3
        "photo6" -> R.drawable.wp_photo6
        else -> R.drawable.wp_kirkjufell
    }

    private fun presetRes(@Suppress("UNUSED_PARAMETER") preset: String): Int = R.drawable.bg_ocean

    // ------------------------------------------------------------ slideshow

    private fun startSlideshow() {
        slideFiles = if (slideRes == null) MediaDirs.slidePhotos(context) else emptyList()
        slideIndex = 0
        slideshowActive = slideRes != null || slideFiles.isNotEmpty()
        if (slideshowActive) {
            handler.removeCallbacks(slideTick)
            handler.post(slideTick)
        }
    }

    private fun stopSlideshow() {
        slideshowActive = false
        handler.removeCallbacks(slideTick)
        image.animate().cancel()
    }

    private fun showNextSlide() {
        val res = slideRes
        if (res == null && slideFiles.isEmpty()) return
        val dm = resources.displayMetrics
        val slideKey = key
        val resId = res?.let { it[slideIndex % it.size] }
        val file = if (res == null) slideFiles[slideIndex % slideFiles.size] else null
        slideIndex++
        Thread {
            val bmp = if (resId != null) decodeRes(resId, dm.widthPixels, dm.heightPixels)
            else decodeSampled(Uri.fromFile(file), dm.widthPixels, dm.heightPixels)
            post {
                if (bmp == null || key != slideKey) return@post
                if (image.drawable == null) {
                    image.setImageBitmap(bmp)
                    image.visibility = VISIBLE
                } else {
                    image.animate().alpha(0f).setDuration(400).withEndAction {
                        image.setImageBitmap(bmp)
                        image.animate().alpha(1f).setDuration(600).start()
                    }.start()
                }
            }
        }.start()
    }

    // ------------------------------------------------------------ images

    private fun openStream(uri: Uri): InputStream? = try {
        if (uri.scheme == "file") FileInputStream(uri.path!!) else context.contentResolver.openInputStream(uri)
    } catch (e: Exception) {
        null
    }

    private fun decodeRes(id: Int, reqW: Int, reqH: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, id, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) sample *= 2
        BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) {
        null
    }

    private fun decodeSampled(uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            openStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private val PHOTOS = intArrayOf(
            R.drawable.wp_kirkjufell, R.drawable.wp_photo1, R.drawable.wp_photo3, R.drawable.wp_photo6
        )
    }
}

/** TextureView + MediaPlayer that plays a muted looping video, cropped to fill the screen. */
class VideoBackground(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {

    private var uri: Uri? = null
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var videoW = 0
    private var videoH = 0

    init {
        surfaceTextureListener = this
    }

    fun setSource(newUri: Uri?) {
        uri = newUri
        if (newUri == null) release() else startIfPossible()
    }

    fun resume() = startIfPossible()

    fun pause() = release()

    private fun startIfPossible() {
        val u = uri ?: return
        val st = surfaceTexture ?: return
        if (!isAvailable) return
        release()
        try {
            val s = Surface(st)
            surface = s
            val mp = MediaPlayer()
            mp.setSurface(s)
            mp.setDataSource(context, u)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setOnVideoSizeChangedListener { _, w, h -> fit(w, h) }
            mp.setOnPreparedListener { it.start() }
            mp.setOnErrorListener { _, _, _ -> true }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            release()
        }
    }

    private fun release() {
        try {
            player?.release()
        } catch (e: Exception) {
            // ignore
        }
        player = null
        surface?.release()
        surface = null
    }

    private fun fit(vw: Int, vh: Int) {
        if (vw == 0 || vh == 0 || width == 0 || height == 0) return
        videoW = vw
        videoH = vh
        val videoAspect = vw.toFloat() / vh
        val viewAspect = width.toFloat() / height
        val m = Matrix()
        if (videoAspect > viewAspect) {
            m.setScale(videoAspect / viewAspect, 1f, width / 2f, height / 2f)
        } else {
            m.setScale(1f, viewAspect / videoAspect, width / 2f, height / 2f)
        }
        setTransform(m)
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) = startIfPossible()

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = fit(videoW, videoH)

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
}
