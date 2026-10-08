package io.apogee.launcher.data

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TileStoreTest {

    private lateinit var prefs: Prefs
    private lateinit var store: TileStore

    private fun app(name: String, pkg: String = "com.example.$name") = AppInfo(
        component = ComponentName(pkg, "$pkg.Main"),
        userSerial = 0L,
        label = name,
        icon = ColorDrawable(0),
        iconColor = 0,
        iconGeneration = 0L,
        firstInstallTime = 0L,
        isWorkProfile = false,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("apogee", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = Prefs.get(context)
        prefs.layout = null
        store = TileStore(prefs)
    }

    @Test
    fun `a fresh install seeds a default layout once`() {
        assertTrue(store.isUnseeded())

        store.seed(listOf(app("Alpha"), app("Beta")))
        val seeded = store.tiles.value
        assertTrue(seeded.isNotEmpty())
        assertFalse(store.isUnseeded())

        // Seeding again must not duplicate tiles.
        store.seed(listOf(app("Gamma")))
        assertEquals(seeded, store.tiles.value)
    }

    @Test
    fun `seeding before the app list has loaded is deferred, not wasted`() {
        // The repository emits an empty list before its first load completes.
        store.seed(emptyList())

        assertTrue("an empty seed must not count as seeded", store.isUnseeded())
        assertTrue(store.tiles.value.isEmpty())

        store.seed(listOf(app("Alpha")))

        assertFalse(store.isUnseeded())
        assertTrue(store.tiles.value.any { it.label == "Alpha" })
    }

    @Test
    fun `the default layout leads with the clock and holds an app list tile`() {
        store.seed(listOf(app("Alpha")))
        val kinds = store.tiles.value.map { it.kind }

        assertEquals(TileKind.CLOCK, kinds.first())
        assertTrue(TileKind.ALL_APPS in kinds)
    }

    @Test
    fun `pinning is idempotent and unpinning removes the tile`() {
        val alpha = app("Alpha")
        assertTrue(store.pin(alpha))
        assertFalse(store.pin(alpha))
        assertEquals(1, store.tiles.value.size)
        assertTrue(store.isPinned(alpha.component, alpha.userSerial))

        store.unpinApp(alpha.component, alpha.userSerial)
        assertTrue(store.tiles.value.isEmpty())
        assertFalse(store.isPinned(alpha.component, alpha.userSerial))
    }

    @Test
    fun `the layout survives a new store over the same prefs`() {
        store.pin(app("Alpha"), TileSize.WIDE)
        store.pin(app("Beta"), TileSize.SMALL)

        val reloaded = TileStore(prefs)

        assertEquals(store.tiles.value, reloaded.tiles.value)
        assertEquals(TileSize.WIDE, reloaded.tiles.value[0].size)
        assertEquals(TileSize.SMALL, reloaded.tiles.value[1].size)
    }

    @Test
    fun `moving a tile reorders the layout`() {
        store.pin(app("Alpha"))
        store.pin(app("Beta"))
        store.pin(app("Gamma"))

        store.move(0, 2)

        assertEquals(
            listOf("Beta", "Gamma", "Alpha"),
            store.tiles.value.map { it.label },
        )
    }

    @Test
    fun `an out of range move is ignored`() {
        store.pin(app("Alpha"))
        val before = store.tiles.value

        store.move(0, 5)
        store.move(-1, 0)

        assertEquals(before, store.tiles.value)
    }

    @Test
    fun `resizing updates only the target tile`() {
        store.pin(app("Alpha"))
        store.pin(app("Beta"))
        val target = store.tiles.value.first()

        store.resize(target.id, TileSize.LARGE)

        assertEquals(TileSize.LARGE, store.tiles.value[0].size)
        assertEquals(TileSize.MEDIUM, store.tiles.value[1].size)
    }

    @Test
    fun `uninstalled apps lose their tiles but live tiles stay`() {
        store.pin(app("Alpha"))
        store.pin(app("Beta"))
        store.replaceAll(
            store.tiles.value + Tile(Tile.newId(), TileKind.CLOCK, TileSize.WIDE),
        )

        store.pruneMissing(listOf(app("Alpha")))

        val remaining = store.tiles.value
        assertEquals(2, remaining.size)
        assertEquals(listOf("Alpha", null), remaining.map { it.label })
    }

    @Test
    fun `a corrupt layout is discarded rather than crashing`() {
        prefs.layout = "{not json at all"

        assertTrue(TileStore(prefs).tiles.value.isEmpty())
    }

    @Test
    fun `an empty app list leaves tiles untouched when pruning`() {
        store.pin(app("Alpha"))
        val before = store.tiles.value

        store.pruneMissing(emptyList())

        assertEquals(before, store.tiles.value)
    }
}
