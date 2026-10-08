package io.apogee.launcher.data

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
class TileSerializationTest {

    @Test
    fun `an app tile survives a round trip`() {
        val original = Tile(
            id = "tile-1",
            kind = TileKind.APP,
            size = TileSize.WIDE,
            component = ComponentName("com.example", "com.example.Main"),
            userSerial = 11L,
            color = 0xFF1BA1E2.toInt(),
            label = "Example",
        )

        val restored = Tile.fromJson(JSONObject(original.toJson().toString()))

        assertEquals(original, restored)
    }

    @Test
    fun `a live tile round trips without a component`() {
        val original = Tile(id = "clock", kind = TileKind.CLOCK, size = TileSize.WIDE)

        val restored = Tile.fromJson(JSONObject(original.toJson().toString()))

        assertEquals(original, restored)
        assertNull(restored!!.component)
    }

    @Test
    fun `an app tile with no component is rejected rather than restored broken`() {
        val json = JSONObject()
            .put("id", "bad")
            .put("kind", "APP")
            .put("size", "MEDIUM")

        assertNull(Tile.fromJson(json))
    }

    @Test
    fun `an unknown kind is rejected`() {
        val json = JSONObject().put("kind", "TELEPORTER").put("size", "MEDIUM")

        assertNull(Tile.fromJson(json))
    }

    @Test
    fun `an unknown size falls back to medium`() {
        assertEquals(TileSize.MEDIUM, TileSize.from("ENORMOUS"))
        assertEquals(TileSize.MEDIUM, TileSize.from(null))
    }

    @Test
    fun `resizing cycles through all four footprints`() {
        var size = TileSize.SMALL
        val seen = mutableListOf(size)
        repeat(4) {
            size = size.next()
            seen += size
        }
        assertEquals(
            listOf(
                TileSize.SMALL,
                TileSize.MEDIUM,
                TileSize.WIDE,
                TileSize.LARGE,
                TileSize.SMALL,
            ),
            seen,
        )
    }
}
