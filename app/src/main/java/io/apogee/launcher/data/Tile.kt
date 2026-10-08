package io.apogee.launcher.data

import android.content.ComponentName
import org.json.JSONObject

/** What a tile shows. App tiles carry a component; the rest render their own live content. */
enum class TileKind { APP, CLOCK, CALENDAR, ALL_APPS, SETTINGS }

/**
 * One pinned tile on the Start screen. Tiles are stored in order and packed into the grid
 * top-to-bottom, left-to-right.
 */
data class Tile(
    val id: String,
    val kind: TileKind,
    val size: TileSize,
    /** Set for [TileKind.APP] tiles only. */
    val component: ComponentName? = null,
    val userSerial: Long = 0L,
    /** Overrides both the accent and the icon colour when non-null. */
    val color: Int? = null,
    val label: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_KIND, kind.name)
        put(KEY_SIZE, size.name)
        component?.let { put(KEY_COMPONENT, it.flattenToString()) }
        put(KEY_USER, userSerial)
        color?.let { put(KEY_COLOR, it) }
        label?.let { put(KEY_LABEL, it) }
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_KIND = "kind"
        private const val KEY_SIZE = "size"
        private const val KEY_COMPONENT = "component"
        private const val KEY_USER = "user"
        private const val KEY_COLOR = "color"
        private const val KEY_LABEL = "label"

        fun fromJson(json: JSONObject): Tile? {
            val kind = runCatching { TileKind.valueOf(json.getString(KEY_KIND)) }.getOrNull()
                ?: return null
            val component = json.optString(KEY_COMPONENT)
                .takeIf { it.isNotEmpty() }
                ?.let { ComponentName.unflattenFromString(it) }
            if (kind == TileKind.APP && component == null) return null
            return Tile(
                id = json.optString(KEY_ID).ifEmpty { newId() },
                kind = kind,
                size = TileSize.from(json.optString(KEY_SIZE)),
                component = component,
                userSerial = json.optLong(KEY_USER, 0L),
                color = if (json.has(KEY_COLOR)) json.getInt(KEY_COLOR) else null,
                label = json.optString(KEY_LABEL).takeIf { it.isNotEmpty() },
            )
        }

        private var counter = 0L

        fun newId(): String = "t${System.currentTimeMillis()}_${counter++}"

        fun forApp(app: AppInfo, size: TileSize = TileSize.MEDIUM): Tile = Tile(
            id = newId(),
            kind = TileKind.APP,
            size = size,
            component = app.component,
            userSerial = app.userSerial,
            label = app.label,
        )
    }
}
