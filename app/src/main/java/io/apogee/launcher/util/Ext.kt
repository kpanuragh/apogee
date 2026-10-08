package io.apogee.launcher.util

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun Context.dp(value: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
        .toInt()

fun Context.dpf(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

fun Context.sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

fun View.updatePadding(
    left: Int = paddingLeft,
    top: Int = paddingTop,
    right: Int = paddingRight,
    bottom: Int = paddingBottom,
) = setPadding(left, top, right, bottom)

/** Applies system-bar insets as padding, so content clears the status and navigation bars. */
fun View.applySystemBarPadding(
    top: Boolean = true,
    bottom: Boolean = true,
    sides: Boolean = false,
) {
    val basePaddingTop = paddingTop
    val basePaddingBottom = paddingBottom
    val basePaddingLeft = paddingLeft
    val basePaddingRight = paddingRight
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(
            basePaddingLeft + if (sides) bars.left else 0,
            basePaddingTop + if (top) bars.top else 0,
            basePaddingRight + if (sides) bars.right else 0,
            basePaddingBottom + if (bottom) bars.bottom else 0,
        )
        insets
    }
    requestApplyInsets()
}

val View.marginParams: ViewGroup.MarginLayoutParams?
    get() = layoutParams as? ViewGroup.MarginLayoutParams
