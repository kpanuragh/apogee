package io.apogee.launcher.ui.start

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import android.widget.ScrollView
import io.apogee.launcher.util.applySystemBarPadding

/**
 * The Start screen: a vertically scrolling tile grid over the wallpaper.
 *
 * Reaching the app list is a swipe to the right, or the pinned "all apps" tile. There is no
 * floating arrow: it sat on top of whatever tile occupied the top-right corner.
 */
@SuppressLint("ViewConstructor")
class StartPage(context: Context) : FrameLayout(context) {

    val grid = TileGrid(context)

    private val scroll = ScrollView(context).apply {
        isFillViewport = true
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        isVerticalScrollBarEnabled = false
        clipChildren = false
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    init {
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // The grid's own padding is the base; insets are added on top so the first row of
        // tiles clears the status bar and the last clears the navigation bar.
        grid.applySystemBarPadding(top = true, bottom = true)
    }

    fun scrollToTop() = scroll.smoothScrollTo(0, 0)

    val isScrolledToTop: Boolean get() = scroll.scrollY == 0
}
