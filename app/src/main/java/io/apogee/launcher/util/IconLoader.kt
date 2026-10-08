package io.apogee.launcher.util

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import android.util.LruCache
import androidx.palette.graphics.Palette

/**
 * Loads launcher icons and works out a tile colour for each one.
 *
 * Metro tiles want a flat glyph on a flat colour, so for adaptive icons we keep only the
 * foreground layer (or the monochrome layer where the platform offers one) and throw the
 * background away — that is what lets a colourful Android icon sit on an accent tile without
 * looking like a sticker.
 */
class IconLoader(private val context: Context) {

    data class LoadedIcon(
        val drawable: Drawable,
        val dominantColor: Int,
        /** Bumped whenever the cache is cleared, so stale icons compare unequal. */
        val generation: Long,
    )

    private val cache = LruCache<String, LoadedIcon>(192)
    private val density = context.resources.displayMetrics.densityDpi

    private var generation = 0L

    /** Drops cached icons so an app update's new icon is picked up. */
    fun clear() {
        generation++
        cache.evictAll()
    }

    fun loadIcon(activity: LauncherActivityInfo, user: UserHandle): LoadedIcon {
        val key = "${user.hashCode()}|${activity.componentName.flattenToString()}"
        cache.get(key)?.let { return it }
        val raw = runCatching { activity.getIcon(density) }.getOrNull()
            ?: context.packageManager.defaultActivityIcon
        val loaded = LoadedIcon(raw, dominantColorOf(raw), generation)
        cache.put(key, loaded)
        return loaded
    }

    /**
     * The drawable to paint on a tile: the glyph layer of an adaptive icon, or the icon
     * itself for legacy icons.
     */
    fun tileGlyph(icon: Drawable, monochrome: Boolean): Drawable {
        glyphLayer(icon, monochrome)?.let { return it }
        // Legacy icons have no layer to peel off. Copy before returning: tiles tint the
        // glyph they are given, and the original is the same instance the app list shows.
        return icon.constantState?.newDrawable()?.mutate() ?: icon
    }

    private fun glyphLayer(icon: Drawable, monochrome: Boolean): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val adaptive = icon as? AdaptiveIconDrawable ?: return null
        if (monochrome && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            adaptive.monochrome?.let { return it.constantState?.newDrawable()?.mutate() ?: it }
        }
        return adaptive.foreground?.constantState?.newDrawable()?.mutate() ?: adaptive.foreground
    }

    private fun dominantColorOf(drawable: Drawable): Int {
        val bitmap = toSmallBitmap(drawable) ?: return AccentPalette.DEFAULT
        val palette = runCatching {
            Palette.from(bitmap).clearFilters().maximumColorCount(12).generate()
        }.getOrNull()
        bitmap.recycle()
        val swatch = palette?.vibrantSwatch
            ?: palette?.dominantSwatch
            ?: palette?.mutedSwatch
            ?: return AccentPalette.DEFAULT
        return deepen(swatch.rgb)
    }

    /** Pushes a colour towards the saturated, mid-value tones metro tiles use. */
    private fun deepen(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.55f)
        hsv[2] = hsv[2].coerceIn(0.45f, 0.85f)
        return Color.HSVToColor(hsv)
    }

    private fun toSmallBitmap(drawable: Drawable): Bitmap? {
        (drawable as? BitmapDrawable)?.bitmap?.let { source ->
            if (source.isRecycled) return null
            val scaled = runCatching {
                Bitmap.createScaledBitmap(source, SAMPLE, SAMPLE, true)
            }.getOrNull()
            if (scaled != null) return scaled
        }
        val width = SAMPLE
        val height = SAMPLE
        return runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
            }
        }.getOrNull()
    }

    private companion object {
        const val SAMPLE = 32
    }
}
