package io.apogee.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * The pixel geometry TileGrid lays tiles out with, kept in step with the real thing:
 * `edge(i) = round(i * step)` where `step = (usable + gap) / columns`, a tile spanning
 * columns [c, c+n) occupies `edge(c) .. edge(c + n) - gap`.
 *
 * The property that matters is symmetry — the grid must start flush with the left padding
 * and end flush with the right, with gaps only ever *between* tiles.
 */
class TileGeometryTest {

    private fun step(usable: Int, gap: Int, columns: Int) = (usable + gap).toFloat() / columns

    private fun edge(index: Int, step: Float) = (index * step).roundToInt()

    @Test
    fun `the last column ends exactly on the content edge`() {
        for (usable in listOf(1000, 1014, 1080, 1081, 1234, 999)) {
            for (columns in listOf(6, 8)) {
                val gap = 16
                val s = step(usable, gap, columns)
                val right = edge(columns, s) - gap
                // Flush with the right content edge: the trailing gap the step carries is
                // exactly what gets subtracted back off the final tile.
                assertEquals(
                    "usable=$usable columns=$columns should end flush",
                    usable,
                    right,
                )
            }
        }
    }

    @Test
    fun `the first column starts at zero, so margins match`() {
        val s = step(1014, 16, 6)
        assertEquals(0, edge(0, s))
    }

    @Test
    fun `gaps between neighbours are the gap, never doubled or dropped`() {
        val gap = 16
        val columns = 6
        val s = step(1014, gap, columns)
        for (col in 0 until columns - 1) {
            val thisRight = edge(col + 1, s) - gap
            val nextLeft = edge(col + 1, s)
            assertEquals("gap after column $col", gap, nextLeft - thisRight)
        }
    }

    @Test
    fun `leftover pixels are spread, so no tile is more than one pixel off`() {
        // 1001 does not divide by 6; the error must not pile up on the last column.
        val gap = 16
        val columns = 6
        val usable = 1001
        val s = step(usable, gap, columns)
        val widths = (0 until columns).map { edge(it + 1, s) - edge(it, s) }
        val smallest = widths.min()
        val largest = widths.max()
        assertTrue("unit widths $widths should differ by at most a pixel", largest - smallest <= 1)
    }

    @Test
    fun `a full width tile spans every column of either grid`() {
        assertEquals(6, TileSize.FULL.cols(6))
        assertEquals(8, TileSize.FULL.cols(8))
        assertEquals(6, TileSize.FULL_TALL.cols(6))
        assertTrue(TileSize.FULL.spansGrid)
    }

    @Test
    fun `fixed width tiles are clamped to a narrower grid rather than overflowing`() {
        assertEquals(4, TileSize.WIDE.cols(6))
        assertEquals(2, TileSize.WIDE.cols(2))
        assertEquals(1, TileSize.SMALL.cols(6))
    }

    @Test
    fun `a full width tile occupies the whole row and nothing sits beside it`() {
        val tiles = listOf(
            Tile("full", TileKind.CLOCK, TileSize.FULL),
            Tile("small", TileKind.CLOCK, TileSize.SMALL),
        )
        val result = TilePacker.pack(tiles, columns = 6)

        val full = result.slotFor("full")!!
        assertEquals(0, full.row)
        assertEquals(0, full.col)
        assertEquals(6, full.cols)
        // The small tile cannot squeeze in beside it, so it lands on the row below.
        assertEquals(2, result.slotFor("small")!!.row)
    }

    @Test
    fun `resizing cycles through every footprint and returns to the start`() {
        var size = TileSize.SMALL
        val seen = mutableListOf(size)
        repeat(TileSize.entries.size) {
            size = size.next()
            seen += size
        }
        assertEquals(TileSize.entries.size + 1, seen.size)
        assertEquals(TileSize.SMALL, seen.last())
        assertEquals(TileSize.entries.toSet(), seen.toSet())
    }
}
