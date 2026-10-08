package io.apogee.launcher.ui.applist

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import io.apogee.launcher.util.AccentPalette
import io.apogee.launcher.util.dp

/**
 * The Windows 10 Mobile jump list: a grid of accent squares, one per bucket, that replaces
 * the app list when you tap a letter header. Buckets with no apps are dimmed and inert.
 */
class LetterPickerView(context: Context) : FrameLayout(context) {

    private val buckets = ("#" + ('A'..'Z').joinToString("")).toCharArray()
    private val cells = LinkedHashMap<Char, TextView>()
    private var columns = 4

    var accentColor: Int = AccentPalette.DEFAULT
        set(value) {
            field = value
            applyAvailability()
        }

    private var available: Set<Char> = emptySet()
    private var onPick: ((Char) -> Unit)? = null

    init {
        isVisible = false
        // The backdrop colour is supplied by the host so it follows the theme.
        setOnClickListener { hide() }
        for (bucket in buckets) {
            val cell = TextView(context).apply {
                text = bucket.lowercaseChar().toString()
                gravity = Gravity.CENTER
                textSize = 24f
                setOnClickListener {
                    if (bucket in available) {
                        onPick?.invoke(bucket)
                        hide()
                    }
                }
            }
            cells[bucket] = cell
            addView(cell)
        }
    }

    fun setOnPickListener(listener: (Char) -> Unit) {
        onPick = listener
    }

    fun show(available: Set<Char>) {
        this.available = available
        applyAvailability()
        isVisible = true
        alpha = 0f
        scaleX = 0.92f
        scaleY = 0.92f
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160L).start()
    }

    fun hide() {
        if (!isVisible) return
        animate().alpha(0f).setDuration(120L).withEndAction { isVisible = false }.start()
    }

    private fun applyAvailability() {
        for ((bucket, cell) in cells) {
            val enabled = bucket in available
            cell.setBackgroundColor(
                if (enabled) accentColor else AccentPalette.shade(accentColor, 0.35f),
            )
            cell.alpha = if (enabled) 1f else 0.45f
            cell.isClickable = enabled
            cell.setTextColor(AccentPalette.contrastOn(accentColor))
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val gap = context.dp(4f)
        val padding = context.dp(16f)
        // Four columns on a phone, six when there is width to spare.
        columns = if (width > context.dp(520f)) 6 else 4
        val cellSize = ((width - padding * 2) - gap * (columns - 1)) / columns
        val spec = MeasureSpec.makeMeasureSpec(cellSize, MeasureSpec.EXACTLY)
        cells.values.forEach { it.measure(spec, spec) }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val gap = context.dp(4f)
        val padding = context.dp(16f)
        val cellSize = ((width - padding * 2) - gap * (columns - 1)) / columns
        val rows = (cells.size + columns - 1) / columns
        val gridHeight = rows * cellSize + (rows - 1) * gap
        val startTop = ((height - gridHeight) / 2).coerceAtLeast(paddingTop + padding)
        var index = 0
        for (cell in cells.values) {
            val row = index / columns
            val column = index % columns
            val left = padding + column * (cellSize + gap)
            val top = startTop + row * (cellSize + gap)
            cell.layout(left, top, left + cellSize, top + cellSize)
            index++
        }
    }

    /** Swallow taps that land between cells so the backdrop closes the picker. */
    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean = false

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (visibility != View.VISIBLE) alpha = 1f
    }
}
