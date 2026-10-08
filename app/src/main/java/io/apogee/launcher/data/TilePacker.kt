package io.apogee.launcher.data

/**
 * Packs tiles into the unit grid the Start screen draws.
 *
 * Windows 10 Mobile keeps Start flush: a tile takes the first slot it fits in, scanning left
 * to right and then down, so a small tile will drop back into a gap an earlier wide tile left
 * behind. This is that rule, kept free of any view code so it can be reasoned about — and
 * tested — on its own.
 */
object TilePacker {

    /** Where one tile ended up, in grid units. */
    data class Slot(val id: String, val row: Int, val col: Int, val cols: Int, val rows: Int)

    data class Result(val slots: List<Slot>, val rows: Int) {
        private val byId = slots.associateBy { it.id }

        fun slotFor(id: String): Slot? = byId[id]
    }

    fun pack(tiles: List<Tile>, columns: Int): Result {
        require(columns > 0) { "columns must be positive" }
        val occupied = ArrayList<BooleanArray>()
        val slots = ArrayList<Slot>(tiles.size)
        var used = 0

        fun ensureRow(row: Int) {
            while (occupied.size <= row) occupied.add(BooleanArray(columns))
        }

        fun fits(row: Int, col: Int, width: Int, height: Int): Boolean {
            if (col + width > columns) return false
            ensureRow(row + height - 1)
            for (r in row until row + height) {
                for (c in col until col + width) if (occupied[r][c]) return false
            }
            return true
        }

        fun occupy(row: Int, col: Int, width: Int, height: Int) {
            ensureRow(row + height - 1)
            for (r in row until row + height) {
                for (c in col until col + width) occupied[r][c] = true
            }
        }

        for (tile in tiles) {
            // A tile wider than the grid is clamped rather than dropped, so switching to a
            // narrower grid cannot lose a pinned tile.
            val width = tile.size.cols.coerceAtMost(columns)
            val height = tile.size.rows
            var row = 0
            while (true) {
                var placed = false
                ensureRow(row)
                for (col in 0..columns - width) {
                    if (fits(row, col, width, height)) {
                        occupy(row, col, width, height)
                        slots += Slot(tile.id, row, col, width, height)
                        used = maxOf(used, row + height)
                        placed = true
                        break
                    }
                }
                if (placed) break
                row++
            }
        }
        return Result(slots, used)
    }
}
