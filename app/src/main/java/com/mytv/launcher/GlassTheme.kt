package com.mytv.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View

/** Eight glass colour themes. Only the tint of the existing glass drawables (and the dock frost) changes. */
object GlassTheme {
    class T(val name: String, val tint: Int, val frostAlpha: Int)

    /** The Liquid Glass material, exactly as specified. */
    const val MAIN = 0x38F5F9FF        // rgba(245,249,255,0.22)  main glass background
    const val SECOND = 0x29E1EBF8      // rgba(225,235,248,0.16)  secondary glass layer
    const val BORDER = 0xA6FFFFFF.toInt()  // rgba(255,255,255,0.65)  glass border
    const val HIGHLIGHT = 0xCCFFFFFF.toInt() // rgba(255,255,255,0.80)  inner highlight
    const val SHADOW = 0x1F415578      // rgba(65,85,120,0.12)    soft shadow

    val all = listOf(
        T("Crystal White", 0xFFF8FAFF.toInt(), 0x44),
        T("Pearl Silver", 0xFFE8EDF5.toInt(), 0x44),
        T("Arctic Ice Blue", 0xFFDCEBFA.toInt(), 0x44),
        T("Platinum Silver", 0xFFC9D2DF.toInt(), 0x46),
        T("Soft Sky Blue", 0xFFB9D9F5.toInt(), 0x46),
        T("Icy Blue", 0xFFA8CFF2.toInt(), 0x46),
        T("Aurora Lavender", 0xFFDCD9F5.toInt(), 0x44),
        T("Champagne Silver", 0xFFE8E3DA.toInt(), 0x44)
    )

    /** Chosen opacity, nudged by the wallpaper: a darker picture gets slightly lighter glass so it never turns muddy. */
    fun opacity(ctx: Context): Float = (Prefs(ctx).glassOpacity / 100f * (0.92f + 0.12f * Frost.luminance(ctx))).coerceIn(0.2f, 1f)
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
