package io.apogee.launcher.data

/**
 * Tile footprints measured in grid units, where a medium tile is 2x2 — the same unit system
 * Windows 10 Mobile uses for its six-unit-wide Start screen.
 */
enum class TileSize(val cols: Int, val rows: Int) {
    SMALL(1, 1),
    MEDIUM(2, 2),
    WIDE(4, 2),
    LARGE(4, 4),
    ;

    /** The size the resize chevron moves to next, cycling through the four footprints. */
    fun next(): TileSize = when (this) {
        SMALL -> MEDIUM
        MEDIUM -> WIDE
        WIDE -> LARGE
        LARGE -> SMALL
    }

    /** Small tiles are icon-only; everything larger has room for a label. */
    val showsLabel: Boolean get() = this != SMALL

    companion object {
        fun from(name: String?): TileSize =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}
