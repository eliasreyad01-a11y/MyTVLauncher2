package com.mytv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.AudioManager
import android.view.KeyEvent
import android.view.View

/** Vertical glass pill that controls the media volume: D-pad up/down changes it, the white fill rises like iOS's slider. */
class VolumePill(context: Context) : View(context) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x80FFFFFF.toInt() }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3AA0FF.toInt() }
    private val rect = RectF()
    private val clip = Path()

    init {
        isFocusable = true
        isClickable = true
        isFocusableInTouchMode = false
        rim.strokeWidth = resources.displayMetrics.density * 1.5f
    }

    private fun step(d: Int) {
        val v = (am.getStreamVolume(AudioManager.STREAM_MUSIC) + d).coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> { step(1); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { step(-1); true }
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = w / 2f
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, r, r, glass)
        clip.reset(); clip.addRoundRect(rect, r, r, Path.Direction.CW)
        val level = am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        c.save(); c.clipPath(clip)
        c.drawRect(0f, h * (1f - level), w, h, fill)
        c.restore()
        // speaker glyph
        val s = w * 0.16f; val cx = w / 2f; val cy = h - w * 0.45f
        c.drawRect(cx - s * 1.2f, cy - s * 0.6f, cx - s * 0.2f, cy + s * 0.6f, icon)
        val p = Path().apply { moveTo(cx - s * 0.2f, cy - s * 0.6f); lineTo(cx + s * 0.8f, cy - s * 1.4f); lineTo(cx + s * 0.8f, cy + s * 1.4f); lineTo(cx - s * 0.2f, cy + s * 0.6f); close() }
        c.drawPath(p, icon)
        rim.color = if (isFocused) 0xFFFFFFFF.toInt() else 0x80FFFFFF.toInt()
        rim.strokeWidth = resources.displayMetrics.density * (if (isFocused) 3f else 1.5f)
        rect.inset(rim.strokeWidth / 2, rim.strokeWidth / 2)
        c.drawRoundRect(rect, r, r, rim)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect); invalidate()
    }
}
