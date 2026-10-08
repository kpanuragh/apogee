package io.apogee.launcher.ui.start

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import io.apogee.launcher.R
import io.apogee.launcher.util.applySystemBarPadding
import io.apogee.launcher.util.dp

/**
 * The Start screen: a vertically scrolling tile grid over the wallpaper, with the arrow that
 * takes you to the app list sitting at the top right, as on Windows 10 Mobile.
 */
@SuppressLint("ViewConstructor")
class StartPage(context: Context) : FrameLayout(context) {

    val grid = TileGrid(context)

    private val scroll = ScrollView(context).apply {
        isFillViewport = true
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        isVerticalScrollBarEnabled = false
        clipChildren = false
        addView(
            grid,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
    }

    private val allAppsArrow = ImageView(context).apply {
        setImageResource(R.drawable.ic_all_apps)
        contentDescription = context.getString(R.string.all_apps)
        val padding = context.dp(10f)
        setPadding(padding, padding, padding, padding)
        isClickable = true
        isFocusable = true
    }

    var onAllAppsClick: (() -> Unit)? = null

    init {
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(
            allAppsArrow,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
                .apply {
                    topMargin = context.dp(20f)
                    marginEnd = context.dp(8f)
                },
        )
        allAppsArrow.setOnClickListener { onAllAppsClick?.invoke() }
        allAppsArrow.applySystemBarPadding(top = true, bottom = false)
        // The grid's own padding is the base; insets are added on top so the first row of
        // tiles clears the status bar and the last clears the navigation bar.
        grid.applySystemBarPadding(top = true, bottom = true)
    }

    /** Hides the arrow while tiles are being rearranged, as Windows 10 Mobile does. */
    fun setEditChromeVisible(editing: Boolean) {
        allAppsArrow.animate()
            .alpha(if (editing) 0f else 1f)
            .setDuration(140L)
            .withEndAction {
                allAppsArrow.visibility = if (editing) View.INVISIBLE else View.VISIBLE
            }
            .start()
        if (!editing) allAppsArrow.visibility = View.VISIBLE
    }

    fun scrollToTop() = scroll.smoothScrollTo(0, 0)

    val isScrolledToTop: Boolean get() = scroll.scrollY == 0
}
