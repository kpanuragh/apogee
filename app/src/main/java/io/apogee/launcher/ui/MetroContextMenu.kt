package io.apogee.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import io.apogee.launcher.R
import io.apogee.launcher.util.dp

/**
 * The Windows Phone hold menu: a flat, left-aligned list of lowercase verbs that slides out
 * next to whatever you held down. No icons, no rounded corners, no elevation.
 */
class MetroContextMenu(private val context: Context) {

    data class Entry(val title: String, val action: () -> Unit)

    private var popup: PopupWindow? = null

    // The root is the popup's own content view, so there are no parent layout params to
    // resolve at inflation time.
    @SuppressLint("InflateParams")
    fun show(anchor: View, entries: List<Entry>) {
        dismiss()
        if (entries.isEmpty()) return

        val root = LayoutInflater.from(context)
            .inflate(R.layout.view_context_menu, null) as LinearLayout
        val inflater = LayoutInflater.from(context)
        for (entry in entries) {
            val item = inflater.inflate(R.layout.item_context_entry, root, false) as TextView
            item.text = entry.title
            item.setOnClickListener {
                dismiss()
                entry.action()
            }
            root.addView(item)
        }

        val window = PopupWindow(
            root,
            anchor.rootView.width - context.dp(48f),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 0f
            isOutsideTouchable = true
            animationStyle = R.style.ContextMenuAnimation
        }
        popup = window

        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        window.showAtLocation(
            anchor,
            Gravity.TOP or Gravity.START,
            context.dp(24f),
            (location[1] + anchor.height / 2).coerceAtLeast(context.dp(48f)),
        )
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    val isShowing: Boolean get() = popup?.isShowing == true
}
