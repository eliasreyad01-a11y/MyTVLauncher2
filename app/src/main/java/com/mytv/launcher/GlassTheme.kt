package com.mytv.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View

/** Eight glass colour themes. Only the tint of the existing glass drawables (and the dock frost) changes. */
object GlassTheme {
    class T(val name: String, val tint: Int, val frostAlpha: Int)

    val all = listOf(
        T("Ice Blue", 0xFFBFE6FF.toInt(), 0x44),
        T("Glass Purple", 0xFFC9B3FF.toInt(), 0x48),
        T("Pearl Silver", 0xFFF2F4F8.toInt(), 0x40),
        T("Mint Green", 0xFFBDF5DC.toInt(), 0x44),
        T("Rose Pink", 0xFFFFC4D8.toInt(), 0x46),
        T("Steel Blue", 0xFF8FB0D6.toInt(), 0x50),
        T("Champagne Gold", 0xFFF3DDA8.toInt(), 0x48),
        T("Smoke Gray", 0xFF8A8F98.toInt(), 0x5A)
    )

    fun opacity(ctx: Context): Float = Prefs(ctx).glassOpacity / 100f
    /** Changes whenever theme or transparency changes. */
    fun key(ctx: Context): Int = index(ctx) * 1000 + Prefs(ctx).glassOpacity

    fun index(ctx: Context): Int = Prefs(ctx).glassTheme.coerceIn(0, all.size - 1)
    fun current(ctx: Context): T = all[index(ctx)]

    /** Mixes ~35% of the theme colour into the glass (keeps the glass's own transparency, darkness and rim). */
    private fun apply(ctx: Context, d: Drawable, tint: Int) {
        d.alpha = (255 * opacity(ctx)).toInt()
        d.setTintMode(android.graphics.PorterDuff.Mode.SRC_ATOP)
        d.setTintList(android.content.res.ColorStateList.valueOf((0x5A shl 24) or (tint and 0xFFFFFF)))
    }

    /** A private copy of a glass drawable, tinted with the current theme (alpha of every layer is kept). */
    fun drawable(ctx: Context, res: Int): Drawable? =
        ctx.getDrawable(res)?.mutate()?.also { apply(ctx, it, current(ctx).tint) }

    /** Re-tints a view's existing glass background without touching shape, size or state selectors. */
    fun retint(ctx: Context, v: View?) {
        val bg = v?.background ?: return
        v.background = bg.mutate().also { apply(ctx, it, current(ctx).tint) }
    }
}
