package io.apogee.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.apogee.launcher.ApogeeApp
import io.apogee.launcher.R
import io.apogee.launcher.data.AppInfo
import io.apogee.launcher.BuildConfig
import io.apogee.launcher.data.BadgeCounts
import io.apogee.launcher.data.live.LiveFeed
import io.apogee.launcher.data.Prefs
import io.apogee.launcher.data.Tile
import io.apogee.launcher.data.TileKind
import io.apogee.launcher.data.TileSize
import io.apogee.launcher.ui.applist.AppListPage
import io.apogee.launcher.ui.settings.SettingsActivity
import io.apogee.launcher.ui.start.StartPage
import io.apogee.launcher.ui.start.LiveTileText
import io.apogee.launcher.ui.start.TileGrid
import io.apogee.launcher.ui.start.TileView
import io.apogee.launcher.util.Launch
import io.apogee.launcher.util.applySystemBarPadding
import io.apogee.launcher.util.startActivitySafely
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The home screen. Two pages — Start and the app list — side by side, exactly the way
 * Windows 10 Mobile lays them out, with the tile grid doing the heavy lifting on page one.
 */
class LauncherActivity : AppCompatActivity(), TileGrid.Host, Prefs.Listener {

    private val app by lazy { ApogeeApp.from(this) }
    private val prefs get() = app.prefs
    private val tileStore get() = app.tiles
    private val appRepository get() = app.apps
    private val liveTiles get() = app.liveTiles

    private lateinit var pager: ViewPager2
    private lateinit var startPage: StartPage
    private lateinit var appListPage: AppListPage
    private lateinit var editDone: TextView

    private val contextMenu by lazy { MetroContextMenu(this) }
    private val glyphCache = HashMap<String, Drawable>()

    /** Keeps the clock and calendar tiles honest without polling. */
    private val timeTicker = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            liveTiles.refresh()
            startPage.grid.refreshLiveFaces()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_launcher)

        pager = findViewById(R.id.pager)
        editDone = findViewById(R.id.edit_done)

        startPage = StartPage(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            grid.host = this@LauncherActivity
        }
        appListPage = AppListPage(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            onAppClick = { info, view -> launchApp(info, view) }
            onAppLongClick = { info, view -> showAppMenu(info, view) }
        }

        pager.adapter = PagerAdapter(startPage, appListPage)
        pager.offscreenPageLimit = 1
        pager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    if (position == PAGE_START) {
                        appListPage.reset()
                        startPage.grid.startLiveTicker(prefs.liveTiles)
                    } else {
                        startPage.grid.setEditMode(false)
                        startPage.grid.stopLiveTicker()
                    }
                    applyStatusBarAppearance(position)
                }
            },
        )

        editDone.setOnClickListener { startPage.grid.setEditMode(false) }
        editDone.applySystemBarPadding(top = false, bottom = true)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = onBack()
            },
        )

        prefs.addListener(this)
        applyTheme()
        observeData()
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    appRepository.apps.collect { all ->
                        tileStore.seed(all)
                        tileStore.pruneMissing(all)
                        glyphCache.clear()
                        appListPage.submit(appRepository.visible(all))
                        startPage.grid.rebind()
                    }
                }
                launch {
                    tileStore.tiles.collect { startPage.grid.setTiles(it) }
                }
                launch {
                    // A badge change only repaints; the layout is unaffected.
                    BadgeCounts.counts.collect { startPage.grid.rebind() }
                }
                launch {
                    combine(
                        LiveFeed.nowPlaying,
                        LiveFeed.notificationLines,
                    ) { playing, lines -> playing to lines }
                        .collect { (playing, lines) ->
                            liveTiles.onFeedChanged(playing, lines)
                        }
                }
                launch {
                    liveTiles.state.collect { startPage.grid.rebind() }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            timeTicker,
            IntentFilter(Intent.ACTION_TIME_TICK).apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        super.onStop()
        runCatching { unregisterReceiver(timeTicker) }
    }

    override fun onResume() {
        super.onResume()
        appRepository.reload()
        liveTiles.refresh()
        startPage.grid.rebind()
        if (pager.currentItem == PAGE_START) startPage.grid.startLiveTicker(prefs.liveTiles)
    }

    override fun onPause() {
        super.onPause()
        contextMenu.dismiss()
        startPage.grid.stopLiveTicker()
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.removeListener(this)
    }

    /** Pressing Home while already home returns to the top of Start, as on Windows Phone. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        contextMenu.dismiss()
        startPage.grid.setEditMode(false)
        if (pager.currentItem != PAGE_START) {
            pager.setCurrentItem(PAGE_START, true)
        } else {
            startPage.scrollToTop()
            startPage.grid.playEntryAnimation()
        }
    }

    private fun onBack() {
        when {
            contextMenu.isShowing -> contextMenu.dismiss()
            startPage.grid.editMode -> startPage.grid.setEditMode(false)
            pager.currentItem == PAGE_APPS -> {
                if (!appListPage.handleBack()) pager.setCurrentItem(PAGE_START, true)
            }
            !startPage.isScrolledToTop -> startPage.scrollToTop()
            // Home is the bottom of the back stack; there is nowhere further back to go.
            else -> Unit
        }
    }

    private fun showAppList() {
        pager.setCurrentItem(PAGE_APPS, true)
    }

    // ---------------------------------------------------------------- theming

    private fun applyTheme() {
        appListPage.setAccent(prefs.accent)
        applyStatusBarAppearance(pager.currentItem)
        startPage.grid.rebind()
    }

    /**
     * Start floats over the wallpaper so its bar icons stay light; the app list has its own
     * solid backdrop and follows the theme.
     */
    private fun applyStatusBarAppearance(page: Int) {
        val lightBackground = page == PAGE_APPS && !isNightMode()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBackground
            isAppearanceLightNavigationBars = lightBackground
        }
        appListPage.setPageBackground(getColor(R.color.page_bg))
    }

    private fun isNightMode(): Boolean =
        resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    override fun onPrefsChanged(key: String) {
        when (key) {
            // AppCompat recreates every started activity itself once the mode is applied.
            Prefs.KEY_NIGHT_MODE -> AppCompatDelegate.setDefaultNightMode(prefs.nightMode)
            Prefs.KEY_LAYOUT -> Unit // TileStore already pushed the new list.
            Prefs.KEY_HIDDEN -> appListPage.submit(appRepository.visible())
            Prefs.KEY_LIVE_TILES -> startPage.grid.startLiveTicker(prefs.liveTiles)
            else -> if (key in Prefs.VISUAL_KEYS) applyTheme()
        }
    }

    // ---------------------------------------------------------------- TileGrid.Host

    override val transparentTiles: Boolean get() = prefs.transparentTiles
    override val tiltEnabled: Boolean get() = prefs.tiltOnPress
    override val columns: Int get() = prefs.columns

    override fun resolve(tile: Tile): TileGrid.Content {
        val info = appRepository.find(tile.component, tile.userSerial)
        val monochrome = prefs.monochromeIcons
        val glyph = when (tile.kind) {
            TileKind.APP -> info?.let { glyphFor(it, monochrome) }
            TileKind.MEDIA -> AppCompatResources.getDrawable(this, R.drawable.ic_media)
            TileKind.ALL_APPS -> AppCompatResources.getDrawable(this, R.drawable.ic_all_apps)
            TileKind.SETTINGS -> AppCompatResources.getDrawable(this, R.drawable.ic_settings_gear)
            else -> null
        }
        val color = tile.color
            ?: if (prefs.colorFromIcon && info != null) info.iconColor else prefs.accent
        val badge = if (
            BuildConfig.BADGES_AVAILABLE && prefs.badgesEnabled && tile.kind == TileKind.APP
        ) {
            BadgeCounts.countFor(tile.component?.packageName)
        } else {
            0
        }
        val live = LiveTileText.forTile(
            context = this,
            tile = tile,
            state = liveTiles.state.value,
            appLabel = info?.label,
            notificationTextEnabled = BuildConfig.BADGES_AVAILABLE && prefs.notificationText,
        )
        return TileGrid.Content(
            app = info,
            glyph = glyph,
            color = color,
            monochrome = monochrome,
            badge = badge,
            live = live,
        )
    }

    private fun glyphFor(info: AppInfo, monochrome: Boolean): Drawable {
        val key = "${info.key}|$monochrome"
        return glyphCache.getOrPut(key) { app.icons.tileGlyph(info.icon, monochrome) }
    }

    override fun onTileClick(tile: Tile, view: TileView) {
        when (tile.kind) {
            TileKind.APP -> {
                val info = appRepository.find(tile.component, tile.userSerial)
                if (info == null) {
                    Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
                } else {
                    launchApp(info, view)
                }
            }
            TileKind.ALL_APPS -> showAppList()
            TileKind.SETTINGS -> startActivitySafely(
                Intent(this, SettingsActivity::class.java),
            )
            TileKind.CLOCK -> startActivitySafely(
                Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS),
            )
            TileKind.CALENDAR -> startActivitySafely(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
            )
            TileKind.MEDIA -> openNowPlaying()
        }
    }

    /** Straight into the app that is playing, falling back to the music category. */
    private fun openNowPlaying() {
        val playing = liveTiles.state.value.nowPlaying?.packageName
        val intent = playing?.let { packageManager.getLaunchIntentForPackage(it) }
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        startActivitySafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onUnpin(tile: Tile) {
        tileStore.unpin(tile.id)
    }

    override fun onResize(tile: Tile, next: TileSize) {
        tileStore.resize(tile.id, next)
    }

    override fun onReorder(from: Int, to: Int) {
        tileStore.move(from, to)
    }

    override fun onEditModeChanged(editing: Boolean) {
        editDone.isVisible = editing
        // Edit mode owns the horizontal gesture, or dragging a tile would flip the page.
        pager.isUserInputEnabled = !editing
    }

    override fun onEmptySpaceClick() = Unit

    /** The full set of footprints, so resizing is not only the chevron's one-step cycle. */
    override fun onTileMenu(tile: Tile, view: TileView) {
        val entries = TileSize.entries.map { size ->
            val label = getString(sizeLabel(size))
            MetroContextMenu.Entry(
                if (size == tile.size) getString(R.string.size_current, label) else label,
            ) {
                if (size != tile.size) tileStore.resize(tile.id, size)
            }
        } + MetroContextMenu.Entry(getString(R.string.unpin_from_start)) {
            tileStore.unpin(tile.id)
        }
        contextMenu.show(view, entries)
    }

    private fun sizeLabel(size: TileSize): Int = when (size) {
        TileSize.SMALL -> R.string.size_small
        TileSize.MEDIUM -> R.string.size_medium
        TileSize.WIDE -> R.string.size_wide
        TileSize.LARGE -> R.string.size_large
        TileSize.FULL -> R.string.size_full
        TileSize.FULL_TALL -> R.string.size_full_tall
    }

    // ---------------------------------------------------------------- app actions

    private fun launchApp(info: AppInfo, source: View?) {
        Launch.app(
            context = this,
            app = info,
            user = appRepository.userFor(info.userSerial),
            source = source,
            launcherApps = appRepository.launcherApps(),
        )
    }

    private fun showAppMenu(info: AppInfo, anchor: View) {
        val pinned = tileStore.isPinned(info.component, info.userSerial)
        val entries = mutableListOf<MetroContextMenu.Entry>()
        entries += if (pinned) {
            MetroContextMenu.Entry(getString(R.string.unpin_from_start)) {
                tileStore.unpinApp(info.component, info.userSerial)
                Toast.makeText(this, R.string.unpinned_toast, Toast.LENGTH_SHORT).show()
            }
        } else {
            MetroContextMenu.Entry(getString(R.string.pin_to_start)) {
                tileStore.pin(info)
                Toast.makeText(this, R.string.pinned_toast, Toast.LENGTH_SHORT).show()
            }
        }
        entries += MetroContextMenu.Entry(getString(R.string.hide_app)) {
            prefs.setHidden(info.key, true)
        }
        entries += MetroContextMenu.Entry(getString(R.string.app_info)) {
            Launch.appInfo(this, info)
        }
        if (isUninstallable(info)) {
            entries += MetroContextMenu.Entry(getString(R.string.uninstall)) {
                Launch.uninstall(this, info)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            // The app's own shortcuts, capped so the menu stays a menu.
            for (shortcut in appRepository.shortcutsFor(info).take(MAX_SHORTCUTS)) {
                val title = (shortcut.shortLabel ?: shortcut.longLabel)?.toString() ?: continue
                entries += MetroContextMenu.Entry(title) {
                    Launch.shortcut(
                        this,
                        shortcut,
                        anchor,
                        appRepository.launcherApps(),
                    )
                }
            }
        }
        contextMenu.show(anchor, entries)
    }

    private fun isUninstallable(info: AppInfo): Boolean {
        val flags = runCatching {
            packageManager.getApplicationInfo(info.packageName, 0).flags
        }.getOrNull() ?: return false
        return flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0
    }

    /**
     * Two fixed pages. Each position is its own view type and neither holder is recyclable,
     * so RecyclerView creates each page exactly once and never binds one page's view into
     * the other position — which would both lose state and leave a view with two parents.
     */
    private class PagerAdapter(
        private val start: View,
        private val apps: View,
    ) : RecyclerView.Adapter<PagerAdapter.PageHolder>() {

        class PageHolder(view: View) : RecyclerView.ViewHolder(view) {
            init {
                setIsRecyclable(false)
            }
        }

        override fun getItemCount() = 2

        override fun getItemViewType(position: Int) = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val page = if (viewType == PAGE_START) start else apps
            (page.parent as? ViewGroup)?.removeView(page)
            return PageHolder(page)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) = Unit
    }

    private companion object {
        const val PAGE_START = 0
        const val PAGE_APPS = 1
        const val MAX_SHORTCUTS = 4
    }
}
