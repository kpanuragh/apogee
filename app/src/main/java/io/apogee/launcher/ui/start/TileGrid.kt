package io.apogee.launcher.ui.start

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ScrollView
import io.apogee.launcher.R
import io.apogee.launcher.data.AppInfo
import io.apogee.launcher.data.Tile
import io.apogee.launcher.data.TilePacker
import io.apogee.launcher.data.TileSize
import io.apogee.launcher.util.dp
import kotlin.math.hypot

/**
 * The Start screen grid.
 *
 * Tiles are packed first-fit, row by row, into a fixed number of unit columns — six by
 * default, eight with "show more tiles" — which is how Windows 10 Mobile keeps a mixed bag of
 * small, medium, wide and large tiles flush with no gaps. The grid owns the whole gesture
 * story too: press tilt (delegated to [TileView]), long-press to enter edit mode, and
 * drag-to-reorder with a live reflow of everything else.
 */
class TileGrid(context: Context) : ViewGroup(context) {

    /** What the grid needs from its host to paint and act on a tile. */
    data class Content(
        val app: AppInfo?,
        val glyph: Drawable?,
        val color: Int,
        val monochrome: Boolean,
        val badge: Int,
    )

    interface Host {
        fun resolve(tile: Tile): Content
        fun onTileClick(tile: Tile, view: TileView)
        fun onUnpin(tile: Tile)
        fun onResize(tile: Tile, next: TileSize)
        fun onReorder(from: Int, to: Int)
        fun onEditModeChanged(editing: Boolean)

        /** A long press while already editing: offer the sizes outright. */
        fun onTileMenu(tile: Tile, view: TileView)
        fun onEmptySpaceClick()
        val transparentTiles: Boolean
        val tiltEnabled: Boolean
        val columns: Int
    }

    var host: Host? = null

    private var tiles: List<Tile> = emptyList()
    private val views = LinkedHashMap<String, TileView>()
    private val placement = HashMap<String, IntArray>() // tile id -> [row, col]
    private val previousBounds = HashMap<String, IntArray>() // tile id -> [left, top]
    private val rects = HashMap<String, IntArray>() // tile id -> [left, top, right, bottom]

    private val gap = context.resources.getDimensionPixelSize(R.dimen.tile_gap)

    /** Width of one grid unit plus one gap, in pixels. */
    private var step = 0f
    private var gridColumns = 6
    private var rows = 0

    var editMode = false
        private set

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())

    private var draggingView: TileView? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var candidateView: TileView? = null
    private var candidateDownX = 0f
    private var candidateDownY = 0f

    init {
        clipChildren = false
        clipToPadding = false
        isChildrenDrawingOrderEnabled = true
        setPadding(
            context.resources.getDimensionPixelSize(R.dimen.start_side_padding),
            context.resources.getDimensionPixelSize(R.dimen.start_top_padding),
            context.resources.getDimensionPixelSize(R.dimen.start_side_padding),
            context.resources.getDimensionPixelSize(R.dimen.start_bottom_padding),
        )
    }

    // ---------------------------------------------------------------- binding

    fun setTiles(next: List<Tile>) {
        tiles = next
        val keep = next.mapTo(HashSet()) { it.id }
        views.keys.filterNot { it in keep }.forEach { id ->
            views.remove(id)?.let { removeView(it) }
            previousBounds.remove(id)
            rects.remove(id)
        }
        for (tile in next) {
            val view = views.getOrPut(tile.id) {
                previousBounds[tile.id] = intArrayOf(NONE, NONE)
                rects[tile.id] = IntArray(4)
                TileView(context).also { tv ->
                    tv.setOnClickListener(::onTileViewClick)
                    tv.setOnLongClickListener(::onTileViewLongClick)
                    addView(tv)
                }
            }
            bindOne(tile, view)
            view.editMode = editMode
        }
        // Keep child order in sync with tile order so drawing order matches the layout.
        for ((index, tile) in next.withIndex()) {
            val view = views[tile.id] ?: continue
            if (indexOfChild(view) != index) {
                removeView(view)
                addView(view, index)
            }
        }
        requestLayout()
    }

    /** Re-reads colours, glyphs and badges without disturbing the layout. */
    fun rebind() {
        tiles.forEach { tile -> views[tile.id]?.let { bindOne(tile, it) } }
    }

    private fun bindOne(tile: Tile, view: TileView) {
        val host = host ?: return
        val content = host.resolve(tile)
        view.monochromeGlyph = content.monochrome
        view.bind(
            tile = tile,
            app = content.app,
            glyph = content.glyph,
            tileColor = content.color,
            transparent = host.transparentTiles,
            tiltEnabled = host.tiltEnabled,
            badgeCount = content.badge,
        )
    }

    fun setEditMode(editing: Boolean) {
        if (editMode == editing) return
        editMode = editing
        views.values.forEach {
            it.editMode = editing
            if (!editing) it.resetTilt()
        }
        host?.onEditModeChanged(editing)
    }

    // ---------------------------------------------------------------- packing

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val columns = (host?.columns ?: 6).coerceAtLeast(1)
        gridColumns = columns
        val usable = (width - paddingLeft - paddingRight).coerceAtLeast(columns)

        // One unit plus one gap. Gaps sit *between* tiles, so a row of tiles starts flush
        // with the left padding and ends flush with the right: edge = round(index * step),
        // and the last edge lands exactly on paddingLeft + usable. Working in floats and
        // rounding each edge also spreads the leftover pixels of a grid that does not
        // divide evenly, instead of dumping them all on the final column.
        step = (usable + gap).toFloat() / columns
        rows = pack(columns)

        for (tile in tiles) {
            val view = views[tile.id] ?: continue
            val rect = rectFor(tile) ?: continue
            view.measure(
                MeasureSpec.makeMeasureSpec(rect[2] - rect[0], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(rect[3] - rect[1], MeasureSpec.EXACTLY),
            )
        }

        val height = paddingTop + contentHeight() + paddingBottom
        setMeasuredDimension(width, maxOf(height, MeasureSpec.getSize(heightMeasureSpec)))
    }

    /** The pixel offset of grid line [index], measured from the content edge. */
    private fun edge(index: Int): Int = Math.round(index * step)

    private fun contentHeight(): Int = if (rows == 0) 0 else edge(rows) - gap

    /**
     * Resolves a tile's pixel bounds from its grid slot, into the array allocated when the
     * tile was bound so that measure and layout allocate nothing.
     */
    private fun rectFor(tile: Tile): IntArray? {
        val slot = placement[tile.id] ?: return null
        val rect = rects[tile.id] ?: return null
        val row = slot[0]
        val col = slot[1]
        rect[0] = paddingLeft + edge(col)
        rect[1] = paddingTop + edge(row)
        rect[2] = paddingLeft + edge(col + tile.size.cols(gridColumns)) - gap
        rect[3] = paddingTop + edge(row + tile.size.rows) - gap
        return rect
    }

    /** Delegates to [TilePacker]; the grid only needs the resulting slots. */
    private fun pack(columns: Int): Int {
        val result = TilePacker.pack(tiles, columns)
        placement.clear()
        result.slots.forEach { placement[it.id] = intArrayOf(it.row, it.col) }
        return result.rows
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (tile in tiles) {
            val view = views[tile.id] ?: continue
            val rect = rectFor(tile) ?: continue
            val left = rect[0]
            val top = rect[1]
            val right = rect[2]
            val bottom = rect[3]

            // Allocated when the tile was bound, so a reflow allocates nothing.
            val previous = previousBounds[tile.id] ?: continue
            val previousLeft = previous[0]
            val previousTop = previous[1]
            previous[0] = left
            previous[1] = top
            view.layout(left, top, right, bottom)

            if (view === draggingView) {
                // Reordering moves the dragged tile's slot underneath it. Shift its
                // translation by the same amount so it stays pinned to the finger instead of
                // jumping to the new slot (which would immediately swap it back).
                if (previousLeft != NONE) {
                    val shiftX = (previousLeft - left).toFloat()
                    val shiftY = (previousTop - top).toFloat()
                    dragOffsetX += shiftX
                    dragOffsetY += shiftY
                    view.translationX += shiftX
                    view.translationY += shiftY
                }
                continue
            }
            if (previousLeft != NONE && (previousLeft != left || previousTop != top)) {
                // Slide the tile from where it used to be, so a reflow reads as movement.
                view.translationX = (previousLeft - left).toFloat()
                view.translationY = (previousTop - top).toFloat()
                view.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(REFLOW_DURATION)
                    .setInterpolator(DecelerateInterpolator(1.6f))
                    .start()
            }
        }
    }

    override fun getChildDrawingOrder(childCount: Int, i: Int): Int {
        // The dragged tile draws last so it floats above its neighbours.
        val dragged = draggingView ?: return i
        val draggedIndex = indexOfChild(dragged)
        if (draggedIndex < 0) return i
        return when {
            i == childCount - 1 -> draggedIndex
            i >= draggedIndex -> i + 1
            else -> i
        }
    }

    // ---------------------------------------------------------------- clicks

    private fun onTileViewClick(view: View) {
        val tv = view as? TileView ?: return
        val tile = tv.tile
        if (editMode) {
            when {
                tv.hitsUnpin(tv.downX, tv.downY) -> host?.onUnpin(tile)
                tv.hitsResize(tv.downX, tv.downY) -> host?.onResize(tile, tile.size.next())
                else -> setEditMode(false)
            }
            return
        }
        host?.onTileClick(tile, tv)
    }

    private fun onTileViewLongClick(view: View): Boolean {
        val tv = view as? TileView ?: return false
        tv.resetTilt()
        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        if (editMode) {
            // Already editing, so the chevron is on screen and the user wants more than the
            // one-step cycle it offers: show every size at once.
            host?.onTileMenu(tv.tile, tv)
        } else {
            setEditMode(true)
        }
        return true
    }

    // ---------------------------------------------------------------- dragging

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!editMode) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                candidateDownX = ev.x
                candidateDownY = ev.y
                candidateView = tileUnder(ev.x, ev.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val candidate = candidateView ?: return false
                if (candidate.hitsUnpin(
                        candidateDownX - candidate.left,
                        candidateDownY - candidate.top,
                    ) ||
                    candidate.hitsResize(
                        candidateDownX - candidate.left,
                        candidateDownY - candidate.top,
                    )
                ) {
                    return false
                }
                if (hypot(ev.x - candidateDownX, ev.y - candidateDownY) > touchSlop) {
                    beginDrag(candidate, ev)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> candidateView = null
        }
        return false
    }

    // Taps are delivered by each TileView's own click listener; the grid's touch handling
    // exists only for dragging and for dismissing edit mode, so there is no click to
    // forward from here.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (draggingView == null) {
            if (event.actionMasked == MotionEvent.ACTION_UP &&
                tileUnder(event.x, event.y) == null
            ) {
                if (editMode) setEditMode(false) else host?.onEmptySpaceClick()
                return true
            }
            return editMode
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> moveDrag(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag()
        }
        return true
    }

    private fun beginDrag(view: TileView, ev: MotionEvent) {
        draggingView = view
        dragStartX = ev.x
        dragStartY = ev.y
        dragOffsetX = view.translationX
        dragOffsetY = view.translationY
        view.animate().cancel()
        view.elevation = context.dp(8f).toFloat()
        view.animate().scaleX(1.06f).scaleY(1.06f).alpha(0.85f).setDuration(120L).start()
        parent?.requestDisallowInterceptTouchEvent(true)
        invalidate()
    }

    private fun moveDrag(event: MotionEvent) {
        val view = draggingView ?: return
        view.translationX = dragOffsetX + (event.x - dragStartX)
        view.translationY = dragOffsetY + (event.y - dragStartY)
        autoScroll(event)

        val centerX = view.left + view.translationX + view.width / 2f
        val centerY = view.top + view.translationY + view.height / 2f
        val target = tileUnder(centerX, centerY, ignore = view)
        if (target != null) {
            val toIndex = tiles.indexOfFirst { it.id == target.tile.id }
            val fromIndex = tiles.indexOfFirst { it.id == view.tile.id }
            if (toIndex >= 0 && fromIndex >= 0 && toIndex != fromIndex) {
                host?.onReorder(fromIndex, toIndex)
            }
        }
    }

    /** Keeps dragging usable on a Start screen taller than the window. */
    private fun autoScroll(event: MotionEvent) {
        val scroller = parent as? ScrollView ?: return
        val edge = context.dp(72f)
        val localY = event.y - scroller.scrollY
        val step = context.dp(12f)
        when {
            localY < edge && scroller.scrollY > 0 -> scroller.scrollBy(0, -step)
            localY > scroller.height - edge -> scroller.scrollBy(0, step)
        }
    }

    private fun endDrag() {
        val view = draggingView ?: return
        draggingView = null
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .setDuration(160L)
            .withEndAction { view.elevation = 0f }
            .start()
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    private fun tileUnder(x: Float, y: Float, ignore: View? = null): TileView? {
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i) as? TileView ?: continue
            if (child === ignore) continue
            if (x >= child.left && x <= child.right && y >= child.top && y <= child.bottom) {
                return child
            }
        }
        return null
    }

    // ---------------------------------------------------------------- animation

    /** The cascade Windows 10 Mobile plays when Start comes forward. */
    fun playEntryAnimation() {
        var index = 0
        for (tile in tiles) {
            val view = views[tile.id] ?: continue
            view.animate().cancel()
            view.alpha = 0f
            view.translationX = context.dp(36f).toFloat()
            view.animate()
                .alpha(1f)
                .translationX(0f)
                .setStartDelay(index * ENTRY_STAGGER)
                .setDuration(240L)
                .setInterpolator(DecelerateInterpolator(1.8f))
                .start()
            index++
        }
    }

    /**
     * Flips one live tile at a time, round-robin, while the Start screen is visible. A no-op
     * when the user has turned live tile animation off.
     */
    fun startLiveTicker(enabled: Boolean) {
        stopLiveTicker()
        if (enabled) handler.postDelayed(liveTick, LIVE_INTERVAL)
    }

    fun stopLiveTicker() = handler.removeCallbacks(liveTick)

    private var liveIndex = 0

    private val liveTick = object : Runnable {
        override fun run() {
            val flippable = tiles.mapNotNull { views[it.id] }.filter { it.hasBackFace() }
            if (flippable.isNotEmpty()) {
                liveIndex = (liveIndex + 1) % flippable.size
                flippable[liveIndex].flip()
            }
            handler.postDelayed(this, LIVE_INTERVAL)
        }
    }

    /** Redraws the clock and calendar faces; called on every minute tick. */
    fun refreshLiveFaces() {
        views.values.forEach { if (it.hasBackFace()) it.invalidate() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLiveTicker()
    }

    private companion object {
        const val REFLOW_DURATION = 180L
        const val ENTRY_STAGGER = 22L
        const val LIVE_INTERVAL = 6_000L

        /** Sentinel for "this tile has not been laid out yet", so it does not animate in. */
        const val NONE = Int.MIN_VALUE
    }
}
