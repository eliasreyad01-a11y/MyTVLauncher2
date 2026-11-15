package com.mytv.tvos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioManager
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View

/** Vertical volume pill: focus it, then Up/Down changes the volume. */
class VolumePill(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init { isFocusable = true }

    override fun onDraw(c: Canvas) {
        val r = width / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = if (isFocused) 0x99FFFFFF.toInt() else 0x59FFFFFF
        c.drawRoundRect(rect, r, r, paint)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val frac = am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        val top = height * (1f - frac)
        c.save()
        c.clipRect(0f, top, width.toFloat(), height.toFloat())
        paint.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(rect, r, r, paint)
        c.restore()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val d = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> 1
            KeyEvent.KEYCODE_DPAD_DOWN -> -1
            else -> return super.onKeyDown(keyCode, event)
        }
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val v = (am.getStreamVolume(AudioManager.STREAM_MUSIC) + d).coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
        invalidate()
        return true
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }
}
