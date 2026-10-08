package io.apogee.launcher

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import io.apogee.launcher.data.AppRepository
import io.apogee.launcher.data.Prefs
import io.apogee.launcher.data.TileStore
import io.apogee.launcher.data.live.LiveTileRepository
import io.apogee.launcher.util.IconLoader

/** Holds the launcher's single app list, tile layout and settings for the whole process. */
class ApogeeApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var apps: AppRepository
        private set
    lateinit var tiles: TileStore
        private set
    lateinit var icons: IconLoader
        private set
    lateinit var liveTiles: LiveTileRepository
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        AppCompatDelegate.setDefaultNightMode(prefs.nightMode)
        apps = AppRepository(this, prefs)
        tiles = TileStore(prefs)
        icons = IconLoader(this)
        liveTiles = LiveTileRepository(this, prefs)
        apps.start()
    }

    companion object {
        fun from(context: android.content.Context): ApogeeApp =
            context.applicationContext as ApogeeApp
    }
}
