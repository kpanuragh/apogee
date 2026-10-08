package io.apogee.launcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import io.apogee.launcher.util.AccentPalette

/** Every user-facing setting, backed by a single SharedPreferences file. */
class Prefs private constructor(private val sp: SharedPreferences) {

    fun interface Listener {
        fun onPrefsChanged(key: String)
    }

    private val listeners = mutableSetOf<Listener>()

    private val spListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        key?.let { k -> listeners.toList().forEach { it.onPrefsChanged(k) } }
    }

    init {
        sp.registerOnSharedPreferenceChangeListener(spListener)
    }

    fun addListener(l: Listener) = listeners.add(l)

    fun removeListener(l: Listener) = listeners.remove(l)

    var accent: Int
        get() = sp.getInt(KEY_ACCENT, AccentPalette.DEFAULT)
        set(v) = sp.edit { putInt(KEY_ACCENT, v) }

    /** One of [AppCompatDelegate.MODE_NIGHT_YES], `MODE_NIGHT_NO`, `MODE_NIGHT_FOLLOW_SYSTEM`. */
    var nightMode: Int
        get() = sp.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_YES)
        set(v) = sp.edit { putInt(KEY_NIGHT_MODE, v) }

    var transparentTiles: Boolean
        get() = sp.getBoolean(KEY_TRANSPARENT, false)
        set(v) = sp.edit { putBoolean(KEY_TRANSPARENT, v) }

    var monochromeIcons: Boolean
        get() = sp.getBoolean(KEY_MONOCHROME, true)
        set(v) = sp.edit { putBoolean(KEY_MONOCHROME, v) }

    var colorFromIcon: Boolean
        get() = sp.getBoolean(KEY_APP_COLORS, false)
        set(v) = sp.edit { putBoolean(KEY_APP_COLORS, v) }

    /** Windows 10 Mobile's "show more tiles" doubles the column count from six to eight. */
    var showMoreTiles: Boolean
        get() = sp.getBoolean(KEY_MORE_TILES, false)
        set(v) = sp.edit { putBoolean(KEY_MORE_TILES, v) }

    val columns: Int get() = if (showMoreTiles) 8 else 6

    var badgesEnabled: Boolean
        get() = sp.getBoolean(KEY_BADGES, false)
        set(v) = sp.edit { putBoolean(KEY_BADGES, v) }

    /** Show the next calendar event on the calendar tile; needs READ_CALENDAR. */
    var calendarOnTiles: Boolean
        get() = sp.getBoolean(KEY_CALENDAR, false)
        set(v) = sp.edit { putBoolean(KEY_CALENDAR, v) }

    /** Show the latest notification's text on a tile, not just a count. */
    var notificationText: Boolean
        get() = sp.getBoolean(KEY_NOTIFICATION_TEXT, false)
        set(v) = sp.edit { putBoolean(KEY_NOTIFICATION_TEXT, v) }

    var liveTiles: Boolean
        get() = sp.getBoolean(KEY_LIVE_TILES, true)
        set(v) = sp.edit { putBoolean(KEY_LIVE_TILES, v) }

    var tiltOnPress: Boolean
        get() = sp.getBoolean(KEY_TILT, true)
        set(v) = sp.edit { putBoolean(KEY_TILT, v) }

    var hiddenApps: Set<String>
        get() = sp.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()
        set(v) = sp.edit { putStringSet(KEY_HIDDEN, v) }

    fun setHidden(key: String, hidden: Boolean) {
        hiddenApps = hiddenApps.toMutableSet().apply { if (hidden) add(key) else remove(key) }
    }

    /** Raw tile layout JSON; [TileStore] owns the encoding. */
    var layout: String?
        get() = sp.getString(KEY_LAYOUT, null)
        set(v) = sp.edit { putString(KEY_LAYOUT, v) }

    companion object {
        const val KEY_ACCENT = "accent"
        const val KEY_NIGHT_MODE = "night_mode"
        const val KEY_TRANSPARENT = "transparent_tiles"
        const val KEY_MONOCHROME = "monochrome_icons"
        const val KEY_APP_COLORS = "color_from_icon"
        const val KEY_MORE_TILES = "show_more_tiles"
        const val KEY_BADGES = "badges"
        const val KEY_LIVE_TILES = "live_tiles"
        const val KEY_CALENDAR = "calendar_on_tiles"
        const val KEY_NOTIFICATION_TEXT = "notification_text"
        const val KEY_TILT = "tilt"
        const val KEY_HIDDEN = "hidden_apps"
        const val KEY_LAYOUT = "layout"

        /** Keys that change how a tile paints, so the Start screen must redraw. */
        val VISUAL_KEYS = setOf(
            KEY_ACCENT, KEY_TRANSPARENT, KEY_MONOCHROME, KEY_APP_COLORS,
            KEY_MORE_TILES, KEY_BADGES, KEY_LIVE_TILES, KEY_TILT,
        )

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(
                context.applicationContext
                    .getSharedPreferences("apogee", Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}
