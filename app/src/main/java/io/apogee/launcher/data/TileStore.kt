package io.apogee.launcher.data

import android.content.ComponentName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * The pinned Start screen layout. Tile order is the layout: [io.apogee.launcher.ui.start.TileGrid]
 * packs them into the grid in list order.
 */
class TileStore(private val prefs: Prefs) {

    private val _tiles = MutableStateFlow(load())
    val tiles: StateFlow<List<Tile>> = _tiles.asStateFlow()

    private fun load(): List<Tile> {
        val raw = prefs.layout ?: return emptyList()
        val parsed = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { Tile.fromJson(array.getJSONObject(it)) }
        }.getOrNull()
        return parsed ?: emptyList()
    }

    private fun persist(tiles: List<Tile>) {
        val array = JSONArray()
        tiles.forEach { array.put(it.toJson()) }
        prefs.layout = array.toString()
        _tiles.value = tiles
    }

    /** True when nothing has ever been pinned, so a default layout should be seeded. */
    fun isUnseeded(): Boolean = prefs.layout == null

    /**
     * Writes the default layout, once, on a fresh install. Ignores an empty app list: the
     * repository emits one before its first load finishes, and seeding from it would leave
     * the user with a Start screen of live tiles and no apps.
     */
    fun seed(apps: List<AppInfo>) {
        if (apps.isEmpty() || !isUnseeded()) return
        persist(DefaultLayout.build(apps))
    }

    fun reset(apps: List<AppInfo>) {
        persist(DefaultLayout.build(apps))
    }

    fun replaceAll(tiles: List<Tile>) = persist(tiles)

    fun pin(app: AppInfo, size: TileSize = TileSize.MEDIUM): Boolean {
        if (isPinned(app.component, app.userSerial)) return false
        persist(_tiles.value + Tile.forApp(app, size))
        return true
    }

    fun unpin(id: String) = persist(_tiles.value.filterNot { it.id == id })

    fun unpinApp(component: ComponentName, userSerial: Long) = persist(
        _tiles.value.filterNot { it.component == component && it.userSerial == userSerial },
    )

    fun resize(id: String, size: TileSize) = persist(
        _tiles.value.map { if (it.id == id) it.copy(size = size) else it },
    )

    fun move(from: Int, to: Int) {
        val list = _tiles.value.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        list.add(to, list.removeAt(from))
        persist(list)
    }

    fun isPinned(component: ComponentName?, userSerial: Long): Boolean =
        component != null &&
            _tiles.value.any { it.component == component && it.userSerial == userSerial }

    /** Drops tiles whose app is gone, so an uninstall does not leave a dead tile behind. */
    fun pruneMissing(apps: List<AppInfo>) {
        if (apps.isEmpty() || _tiles.value.isEmpty()) return
        val live = apps.mapTo(HashSet()) { it.key }
        val kept = _tiles.value.filter {
            it.kind != TileKind.APP || "${it.userSerial}|${it.component?.flattenToString()}" in live
        }
        if (kept.size != _tiles.value.size) persist(kept)
    }

    private object DefaultLayout {
        /**
         * Seeds a Start screen the way a fresh Windows 10 Mobile device looks: the clock and
         * calendar up top, then a handful of staple apps if the device has them, then whatever
         * else is installed, down to a reasonable number of tiles.
         */
        private val STAPLES = listOf(
            "com.android.dialer", "com.google.android.dialer",
            "com.android.mms", "com.google.android.apps.messaging",
            "com.android.chrome", "org.mozilla.firefox",
            "com.google.android.gm", "com.android.email",
            "com.android.contacts", "com.google.android.contacts",
            "com.google.android.apps.photos", "com.android.gallery3d",
            "com.android.camera2", "com.google.android.GoogleCamera",
            "com.android.vending",
            "com.google.android.apps.maps",
            "com.google.android.music", "com.google.android.apps.youtube.music",
            "com.android.settings",
        )

        fun build(apps: List<AppInfo>): List<Tile> {
            val tiles = ArrayList<Tile>()
            tiles += Tile(Tile.newId(), TileKind.CLOCK, TileSize.WIDE)
            tiles += Tile(Tile.newId(), TileKind.CALENDAR, TileSize.MEDIUM)
            tiles += Tile(Tile.newId(), TileKind.ALL_APPS, TileSize.MEDIUM)

            val byPackage = apps.groupBy { it.packageName }
            val used = HashSet<String>()
            for (pkg in STAPLES) {
                val app = byPackage[pkg]?.firstOrNull() ?: continue
                if (!used.add(app.key)) continue
                tiles += Tile.forApp(app, TileSize.MEDIUM)
            }
            for (app in apps) {
                if (tiles.size >= 18) break
                if (app.isWorkProfile || !used.add(app.key)) continue
                tiles += Tile.forApp(app, TileSize.SMALL)
            }
            tiles += Tile(Tile.newId(), TileKind.SETTINGS, TileSize.SMALL)
            return tiles
        }
    }
}
