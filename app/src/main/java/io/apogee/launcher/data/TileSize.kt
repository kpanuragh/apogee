package io.apogee.launcher.data

/** Marks a footprint whose width is the grid's, resolved at layout time. */
private const val SPANS_GRID = -1

/**
 * Tile footprints in grid units, where a medium tile is 2x2 — the unit system Windows 10
 * Mobile uses for its six-unit-wide Start screen.
 *
 * The full-width footprints have no fixed column count: they stretch to whatever the grid
 * is, so they stay edge to edge whether "show more tiles" is on (eight units) or off (six).
 */
enum class TileSize(private val fixedCols: Int, val rows: Int) {
    SMALL(1, 1),
    MEDIUM(2, 2),
    WIDE(4, 2),
    LARGE(4, 4),
    FULL(SPANS_GRID, 2),
    FULL_TALL(SPANS_GRID, 4),
    ;

    /** How many units wide this tile is on a grid [gridColumns] units across. */
    fun cols(gridColumns: Int): Int =
        if (fixedCols == SPANS_GRID) gridColumns else fixedCols.coerceAtMost(gridColumns)

    val spansGrid: Boolean get() = fixedCols == SPANS_GRID

    /** The size the resize chevron moves to next, cycling through every footprint. */
    fun next(): TileSize = entries[(ordinal + 1) % entries.size]

    /** Small tiles are icon-only; everything larger has room for a label. */
    val showsLabel: Boolean get() = this != SMALL

    /** Wide and full tiles are short and broad, so their glyph sits beside the label. */
    val isBanner: Boolean get() = this == WIDE || this == FULL

    companion object {
        fun from(name: String?): TileSize = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}
