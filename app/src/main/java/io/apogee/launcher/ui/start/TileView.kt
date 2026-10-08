package io.apogee.launcher.ui.start

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import io.apogee.launcher.R
import io.apogee.launcher.data.AppInfo
import io.apogee.launcher.data.Tile
import io.apogee.launcher.data.TileKind
import io.apogee.launcher.data.TileSize
import io.apogee.launcher.util.AccentPalette
import io.apogee.launcher.util.dpf
import io.apogee.launcher.util.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * A single live tile: a flat rectangle of colour with a glyph, a label and, for the live
 * kinds, a second face it flips to.
 *
 * Everything is drawn rather than composed from child views — a tile is a handful of shapes
 * and three runs of text, and drawing it keeps the grid cheap to reflow while dragging.
 */
@SuppressLint("ViewConstructor")
class TileView(context: Context) : View(context) {

    /** Which face is showing; live tiles flip between the two. */
    private var flipProgress = 0f
    private var showingBack = false
    private var flipAnimator: ValueAnimator? = null

    var tile: Tile = Tile(Tile.newId(), TileKind.APP, TileSize.MEDIUM)
        private set

    var app: AppInfo? = null
        private set

    var editMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var tiltEnabled: Boolean = true
    var transparent: Boolean = false
    var badgeCount: Int = 0
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var glyph: Drawable? = null
    private var tileColor: Int = AccentPalette.DEFAULT
    private var foreground: Int = Color.WHITE

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chromePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textSize = context.sp(13f)
    }
    private val bigPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val smallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val rect = RectF()
    private val glyphBounds = Rect()
    // The system 12/24-hour setting, not a hardcoded pattern.
    private val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
    private val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("d MMMM", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())

    init {
        isClickable = true
        isLongClickable = true
        isFocusable = true
    }

    fun bind(
        tile: Tile,
        app: AppInfo?,
        glyph: Drawable?,
        tileColor: Int,
        transparent: Boolean,
        tiltEnabled: Boolean,
        badgeCount: Int,
    ) {
        this.tile = tile
        this.app = app
        this.glyph = glyph
        this.tileColor = tileColor
        this.transparent = transparent
        this.tiltEnabled = tiltEnabled
        this.badgeCount = badgeCount
        foreground = if (transparent) Color.WHITE else AccentPalette.contrastOn(tileColor)
        contentDescription = displayLabel()
        cancelFlip()
        invalidate()
    }

    /** Tiles with something live to say can flip; plain app tiles stay put. */
    fun hasBackFace(): Boolean = when (tile.kind) {
        TileKind.CLOCK -> true
        TileKind.CALENDAR -> true
        TileKind.APP -> badgeCount > 0 && tile.size.showsLabel
        else -> false
    }

    fun flip() {
        if (!hasBackFace() || width == 0) return
        flipAnimator?.cancel()
        val target = !showingBack
        flipAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FLIP_DURATION
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                flipProgress = it.animatedFraction
                if (flipProgress >= 0.5f && showingBack != target) showingBack = target
                invalidate()
            }
            addListener(
                onEnd = {
                    showingBack = target
                    flipProgress = 0f
                    invalidate()
                },
            )
            start()
        }
    }

    private fun cancelFlip() {
        flipAnimator?.cancel()
        flipAnimator = null
        flipProgress = 0f
        showingBack = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelFlip()
    }

    // ---------------------------------------------------------------- press tilt

    private var tiltAnimator: ValueAnimator? = null

    /** Where the current gesture went down, used to hit-test the edit affordances. */
    var downX = 0f
        private set
    var downY = 0f
        private set

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.x
            downY = event.y
        }
        if (tiltEnabled && !editMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> applyTilt(event.x, event.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> releaseTilt()
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * The Windows Phone press effect: the tile pivots about its centre so the corner under
     * your finger sinks into the screen.
     */
    private fun applyTilt(x: Float, y: Float) {
        tiltAnimator?.cancel()
        val halfW = width / 2f
        val halfH = height / 2f
        if (halfW <= 0f || halfH <= 0f) return
        cameraDistance = width.coerceAtLeast(height) * 12f
        val dx = ((x - halfW) / halfW).coerceIn(-1f, 1f)
        val dy = ((y - halfH) / halfH).coerceIn(-1f, 1f)
        rotationY = dx * MAX_TILT
        rotationX = -dy * MAX_TILT
        val depth = 1f - PRESS_DEPTH * maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
        scaleX = depth
        scaleY = depth
    }

    private fun releaseTilt() {
        tiltAnimator?.cancel()
        val fromX = rotationX
        val fromY = rotationY
        val fromScale = scaleX
        tiltAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 170L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                rotationX = fromX * f
                rotationY = fromY * f
                val s = 1f + (fromScale - 1f) * f
                scaleX = s
                scaleY = s
            }
            start()
        }
    }

    fun resetTilt() {
        tiltAnimator?.cancel()
        rotationX = 0f
        rotationY = 0f
        scaleX = 1f
        scaleY = 1f
    }

    // ---------------------------------------------------------------- edit affordances

    private fun chromeRadius() = context.dpf(14f)

    private fun unpinCenter(): Pair<Float, Float> =
        (width - chromeRadius() - context.dpf(2f)) to (chromeRadius() + context.dpf(2f))

    private fun resizeCenter(): Pair<Float, Float> =
        (width - chromeRadius() - context.dpf(2f)) to (height - chromeRadius() - context.dpf(2f))

    fun hitsUnpin(x: Float, y: Float): Boolean = editMode && within(unpinCenter(), x, y)

    fun hitsResize(x: Float, y: Float): Boolean = editMode && within(resizeCenter(), x, y)

    private fun within(center: Pair<Float, Float>, x: Float, y: Float): Boolean {
        val slop = chromeRadius() * 1.5f
        return kotlin.math.hypot(x - center.first, y - center.second) <= slop
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())

        fillPaint.color = tileColor
        fillPaint.alpha = if (transparent) TRANSPARENT_ALPHA else 255
        canvas.drawRect(rect, fillPaint)

        // Mid-flip the face is squashed vertically, which reads as a fold without needing
        // a hardware layer for a real 3D rotation.
        val squash = if (flipProgress == 0f) {
            1f
        } else {
            kotlin.math.abs(1f - 2f * flipProgress).coerceAtLeast(0.02f)
        }
        val saved = canvas.save()
        if (squash != 1f) canvas.scale(1f, squash, width / 2f, height / 2f)

        if (showingBack) drawBackFace(canvas) else drawFrontFace(canvas)

        canvas.restoreToCount(saved)

        if (editMode) drawEditChrome(canvas)
    }

    private fun drawFrontFace(canvas: Canvas) {
        when (tile.kind) {
            TileKind.CLOCK -> drawClock(canvas)
            TileKind.CALENDAR -> drawCalendar(canvas)
            TileKind.ALL_APPS -> drawGlyphTile(canvas, context.getString(R.string.all_apps))
            TileKind.SETTINGS -> drawGlyphTile(canvas, displayLabel())
            TileKind.APP -> drawGlyphTile(canvas, displayLabel())
        }
    }

    private fun drawBackFace(canvas: Canvas) {
        when (tile.kind) {
            TileKind.CLOCK -> drawClockBack(canvas)
            TileKind.CALENDAR -> drawCalendarBack(canvas)
            else -> drawBadgeFace(canvas)
        }
    }

    private fun drawGlyphTile(canvas: Canvas, label: String?) {
        val icon = glyph
        // Scale off the shorter side: a full-width banner is six units across but only two
        // tall, so sizing from the width would give it an absurd glyph.
        val shortSide = minOf(width, height).toFloat()
        val padding = shortSide * when {
            tile.size == TileSize.SMALL -> 0.24f
            tile.size == TileSize.MEDIUM -> 0.26f
            tile.size.isBanner -> 0.22f
            else -> 0.30f
        }
        if (icon != null) {
            val available = minOf(
                width - padding * 2f,
                height - padding * 2f - if (tile.size.showsLabel) context.dpf(14f) else 0f,
            ).coerceAtLeast(context.dpf(16f))
            val cx = width / 2f
            val cy = if (tile.size.showsLabel) {
                (height - context.dpf(16f)) / 2f
            } else {
                height / 2f
            }
            val half = available / 2f
            glyphBounds.set(
                (cx - half).toInt(),
                (cy - half).toInt(),
                (cx + half).toInt(),
                (cy + half).toInt(),
            )
            icon.bounds = glyphBounds
            if (shouldTintGlyph()) icon.setTint(foreground) else icon.setTintList(null)
            icon.draw(canvas)
        }

        if (tile.size.showsLabel && !label.isNullOrEmpty()) drawLabel(canvas, label)
        if (badgeCount > 0) drawBadge(canvas)
    }

    private fun shouldTintGlyph(): Boolean =
        tile.kind != TileKind.APP || monochromeGlyph

    /** Set by the grid: true when the glyph is a mask that needs tinting to the foreground. */
    var monochromeGlyph: Boolean = true

    private fun drawLabel(canvas: Canvas, label: String) {
        labelPaint.color = foreground
        labelPaint.textSize = context.sp(if (tile.size == TileSize.LARGE) 15f else 13f)
        val margin = context.dpf(9f)
        val maxWidth = width - margin * 2f
        val text = TextUtils.ellipsize(label, labelPaint, maxWidth, TextUtils.TruncateAt.END)
        canvas.drawText(
            text.toString(),
            margin,
            height - margin - labelPaint.descent(),
            labelPaint,
        )
    }

    private fun drawBadge(canvas: Canvas) {
        smallPaint.color = foreground
        smallPaint.textSize = context.sp(if (tile.size == TileSize.SMALL) 13f else 20f)
        smallPaint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val text = if (badgeCount > 99) "99+" else badgeCount.toString()
        val margin = context.dpf(9f)
        canvas.drawText(
            text,
            width - margin - smallPaint.measureText(text),
            margin - smallPaint.ascent(),
            smallPaint,
        )
    }

    private fun drawBadgeFace(canvas: Canvas) {
        bigPaint.color = foreground
        bigPaint.textSize = height * 0.42f
        val text = if (badgeCount > 99) "99+" else badgeCount.toString()
        val margin = context.dpf(9f)
        canvas.drawText(
            text,
            margin,
            height / 2f - (bigPaint.descent() + bigPaint.ascent()) / 2f - context.dpf(6f),
            bigPaint,
        )
        drawLabel(canvas, displayLabel().orEmpty())
    }

    private fun drawClock(canvas: Canvas) {
        val now = Date()
        bigPaint.color = foreground
        bigPaint.textSize = minOf(height * 0.46f, context.sp(56f))
        val time = timeFormat.format(now)
        val margin = context.dpf(10f)
        val baseline = height / 2f - (bigPaint.descent() + bigPaint.ascent()) / 2f -
            context.dpf(if (tile.size == TileSize.SMALL) 0f else 9f)
        canvas.drawText(time, margin, baseline, bigPaint)
        if (tile.size.showsLabel) {
            smallPaint.color = foreground
            smallPaint.alpha = 210
            smallPaint.textSize = context.sp(13f)
            canvas.drawText(
                dayFormat.format(now).lowercase(Locale.getDefault()),
                margin,
                height - margin - smallPaint.descent(),
                smallPaint,
            )
            smallPaint.alpha = 255
        }
    }

    private fun drawClockBack(canvas: Canvas) {
        val now = Date()
        bigPaint.color = foreground
        bigPaint.textSize = minOf(height * 0.3f, context.sp(30f))
        val margin = context.dpf(10f)
        canvas.drawText(
            dateFormat.format(now).lowercase(Locale.getDefault()),
            margin,
            height / 2f - (bigPaint.descent() + bigPaint.ascent()) / 2f,
            bigPaint,
        )
    }

    private fun drawCalendar(canvas: Canvas) {
        val calendar = Calendar.getInstance()
        smallPaint.color = foreground
        smallPaint.textSize = context.sp(13f)
        smallPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val margin = context.dpf(10f)
        canvas.drawText(
            dayFormat.format(calendar.time).lowercase(Locale.getDefault()),
            margin,
            margin - smallPaint.ascent(),
            smallPaint,
        )
        bigPaint.color = foreground
        bigPaint.textSize = minOf(height * 0.52f, context.sp(64f))
        val day = calendar.get(Calendar.DAY_OF_MONTH).toString()
        canvas.drawText(
            day,
            margin,
            height - margin - bigPaint.descent(),
            bigPaint,
        )
    }

    private fun drawCalendarBack(canvas: Canvas) {
        val calendar = Calendar.getInstance()
        bigPaint.color = foreground
        bigPaint.textSize = minOf(height * 0.26f, context.sp(26f))
        val margin = context.dpf(10f)
        canvas.drawText(
            monthFormat.format(calendar.time).lowercase(Locale.getDefault()),
            margin,
            height / 2f - (bigPaint.descent() + bigPaint.ascent()) / 2f,
            bigPaint,
        )
    }

    private fun drawEditChrome(canvas: Canvas) {
        // Dim the tile so the two affordances read clearly against it.
        chromePaint.color = Color.BLACK
        chromePaint.alpha = 60
        canvas.drawRect(rect, chromePaint)
        chromePaint.alpha = 255

        val radius = chromeRadius()
        val (ux, uy) = unpinCenter()
        chromePaint.color = Color.WHITE
        canvas.drawCircle(ux, uy, radius, chromePaint)
        chromePaint.color = Color.BLACK
        chromePaint.strokeWidth = context.dpf(2f)
        val arm = radius * 0.42f
        canvas.drawLine(ux - arm, uy - arm, ux + arm, uy + arm, chromePaint)
        canvas.drawLine(ux + arm, uy - arm, ux - arm, uy + arm, chromePaint)

        val (rx, ry) = resizeCenter()
        chromePaint.color = Color.WHITE
        canvas.drawCircle(rx, ry, radius, chromePaint)
        chromePaint.color = Color.BLACK
        // A double chevron, as on the Windows 10 Mobile resize affordance.
        val step = radius * 0.34f
        canvas.drawLine(rx - step, ry - step * 1.4f, rx + step, ry, chromePaint)
        canvas.drawLine(rx + step, ry, rx - step, ry + step * 1.4f, chromePaint)
    }

    private fun displayLabel(): String? = when (tile.kind) {
        TileKind.APP -> app?.label ?: tile.label
        TileKind.CLOCK -> context.getString(R.string.clock_tile)
        TileKind.CALENDAR -> context.getString(R.string.calendar_tile)
        TileKind.ALL_APPS -> context.getString(R.string.all_apps)
        TileKind.SETTINGS -> context.getString(R.string.apogee_settings_tile)
    }

    private companion object {
        const val MAX_TILT = 9f
        const val PRESS_DEPTH = 0.035f
        const val TRANSPARENT_ALPHA = 92
        const val FLIP_DURATION = 520L
    }
}

private fun ValueAnimator.addListener(onEnd: () -> Unit) {
    addListener(
        object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = onEnd()
        },
    )
}
