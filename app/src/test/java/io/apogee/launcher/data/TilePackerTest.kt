package io.apogee.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TilePackerTest {

    private fun tile(id: String, size: TileSize) =
        Tile(id = id, kind = TileKind.CLOCK, size = size)

    @Test
    fun `three medium tiles fill one row of a six column grid`() {
        val tiles = listOf(
            tile("a", TileSize.MEDIUM),
            tile("b", TileSize.MEDIUM),
            tile("c", TileSize.MEDIUM),
        )
        val result = TilePacker.pack(tiles, columns = 6)

        assertEquals(2, result.rows)
        assertEquals(0 to 0, result.slotFor("a")!!.let { it.row to it.col })
        assertEquals(0 to 2, result.slotFor("b")!!.let { it.row to it.col })
        assertEquals(0 to 4, result.slotFor("c")!!.let { it.row to it.col })
    }

    @Test
    fun `a wide tile leaves a two unit gap that a later small tile backfills`() {
        val tiles = listOf(
            tile("wide", TileSize.WIDE),
            tile("large", TileSize.LARGE),
            tile("small", TileSize.SMALL),
        )
        val result = TilePacker.pack(tiles, columns = 6)

        // The wide tile takes columns 0..3 of rows 0..1, so the large tile cannot fit beside
        // it and drops to row 2 — leaving columns 4..5 of rows 0..1 free for the small tile.
        assertEquals(0 to 0, result.slotFor("wide")!!.let { it.row to it.col })
        assertEquals(2 to 0, result.slotFor("large")!!.let { it.row to it.col })
        assertEquals(0 to 4, result.slotFor("small")!!.let { it.row to it.col })
    }

    @Test
    fun `no two tiles ever overlap`() {
        val sizes = TileSize.entries
        val tiles = (0 until 40).map { tile("t$it", sizes[it % sizes.size]) }
        for (columns in listOf(4, 6, 8)) {
            val result = TilePacker.pack(tiles, columns)
            val taken = HashSet<Pair<Int, Int>>()
            for (slot in result.slots) {
                for (r in slot.row until slot.row + slot.rows) {
                    for (c in slot.col until slot.col + slot.cols) {
                        assertTrue(
                            "overlap at $r,$c with $columns columns",
                            taken.add(r to c),
                        )
                        assertTrue("column $c out of bounds", c < columns)
                    }
                }
            }
            assertEquals(tiles.size, result.slots.size)
        }
    }

    @Test
    fun `tiles wider than the grid are clamped instead of dropped`() {
        val tiles = listOf(tile("wide", TileSize.WIDE), tile("large", TileSize.LARGE))
        val result = TilePacker.pack(tiles, columns = 2)

        assertNotNull(result.slotFor("wide"))
        assertNotNull(result.slotFor("large"))
        assertEquals(0, result.slotFor("wide")!!.col)
        assertEquals(2, result.slotFor("wide")!!.cols)
    }

    @Test
    fun `an empty layout occupies no rows`() {
        assertEquals(0, TilePacker.pack(emptyList(), columns = 6).rows)
    }

    @Test
    fun `the eight column grid fits four medium tiles per row`() {
        val tiles = (0 until 4).map { tile("t$it", TileSize.MEDIUM) }
        val result = TilePacker.pack(tiles, columns = 8)

        assertEquals(2, result.rows)
        assertTrue(result.slots.all { it.row == 0 })
    }
}
