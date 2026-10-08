package io.apogee.launcher.util

import android.graphics.Color
import androidx.annotation.ColorInt

/** The twenty accent colours Windows Phone 8.1 / Windows 10 Mobile ship with. */
object AccentPalette {

    data class Accent(val name: String, @ColorInt val color: Int)

    val ALL = listOf(
        Accent("lime", 0xFFA4C400.toInt()),
        Accent("green", 0xFF60A917.toInt()),
        Accent("emerald", 0xFF008A00.toInt()),
        Accent("teal", 0xFF00ABA9.toInt()),
        Accent("cyan", 0xFF1BA1E2.toInt()),
        Accent("cobalt", 0xFF0050EF.toInt()),
        Accent("indigo", 0xFF6A00FF.toInt()),
        Accent("violet", 0xFFAA00FF.toInt()),
        Accent("pink", 0xFFF472D0.toInt()),
        Accent("magenta", 0xFFD80073.toInt()),
        Accent("crimson", 0xFFA20025.toInt()),
        Accent("red", 0xFFE51400.toInt()),
        Accent("orange", 0xFFFA6800.toInt()),
        Accent("amber", 0xFFF0A30A.toInt()),
        Accent("yellow", 0xFFE3C800.toInt()),
        Accent("brown", 0xFF825A2C.toInt()),
        Accent("olive", 0xFF6D8764.toInt()),
        Accent("steel", 0xFF647687.toInt()),
        Accent("mauve", 0xFF76608A.toInt()),
        Accent("taupe", 0xFF87794E.toInt()),
    )

    @ColorInt
    val DEFAULT = ALL[4].color // cyan, the Windows Phone stock accent

    fun nameOf(@ColorInt color: Int): String =
        ALL.firstOrNull { it.color == color }?.name ?: "custom"

    /** Darkens [color] for pressed states while keeping the metro flatness. */
    @ColorInt
    fun shade(@ColorInt color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    /** Black or white, whichever stays readable on [background]. */
    @ColorInt
    fun contrastOn(@ColorInt background: Int): Int {
        val luminance = (
            0.299 * Color.red(background) +
                0.587 * Color.green(background) +
                0.114 * Color.blue(background)
            ) / 255.0
        return if (luminance > 0.6) Color.BLACK else Color.WHITE
    }
}
