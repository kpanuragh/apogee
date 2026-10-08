package io.apogee.launcher.ui.applist

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.apogee.launcher.R
import io.apogee.launcher.data.AppInfo
import io.apogee.launcher.util.AccentPalette

/** Rows in the app list: a bucket header, or an app. */
sealed interface AppListRow {
    data class Header(val letter: Char) : AppListRow
    data class App(val app: AppInfo) : AppListRow
}

/**
 * The alphabetical app list, with the square accent letter headers Windows 10 Mobile uses as
 * jump-list handles.
 */
class AppListAdapter(
    private val onClick: (AppInfo, View) -> Unit,
    private val onLongClick: (AppInfo, View) -> Unit,
    private val onHeaderClick: () -> Unit,
) : ListAdapter<AppListRow, RecyclerView.ViewHolder>(DIFF) {

    var accentColor: Int = 0
        // Every header repaints at once when the accent changes, so there is no narrower
        // event to send.
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is AppListRow.Header -> TYPE_HEADER
        is AppListRow.App -> TYPE_APP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_app_header, parent, false))
        } else {
            AppHolder(inflater.inflate(R.layout.item_app, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is AppListRow.Header -> (holder as HeaderHolder).bind(row.letter, accentColor)
            is AppListRow.App -> (holder as AppHolder).bind(row.app)
        }
    }

    private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val letter: TextView = view.findViewById(R.id.header_letter)

        init {
            view.setOnClickListener { onHeaderClick() }
        }

        fun bind(value: Char, accent: Int) {
            letter.text = value.lowercaseChar().toString()
            letter.setBackgroundColor(accent)
            // Lime, yellow and amber are too light for white text.
            letter.setTextColor(AccentPalette.contrastOn(accent))
        }
    }

    private inner class AppHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.app_icon)
        private val label: TextView = view.findViewById(R.id.app_label)
        private var bound: AppInfo? = null

        init {
            view.setOnClickListener { v -> bound?.let { onClick(it, v) } }
            view.setOnLongClickListener { v ->
                bound?.let { onLongClick(it, v) }
                true
            }
        }

        fun bind(app: AppInfo) {
            bound = app
            icon.setImageDrawable(app.icon)
            label.text = app.label
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_APP = 1

        val DIFF = object : DiffUtil.ItemCallback<AppListRow>() {
            override fun areItemsTheSame(a: AppListRow, b: AppListRow): Boolean = when {
                a is AppListRow.Header && b is AppListRow.Header -> a.letter == b.letter
                a is AppListRow.App && b is AppListRow.App -> a.app.key == b.app.key
                else -> false
            }

            override fun areContentsTheSame(a: AppListRow, b: AppListRow): Boolean = when {
                a is AppListRow.Header && b is AppListRow.Header -> a.letter == b.letter
                // The generation changes when an app update replaces its icon, so the row
                // rebinds instead of keeping the old drawable.
                a is AppListRow.App && b is AppListRow.App ->
                    a.app.label == b.app.label &&
                        a.app.iconGeneration == b.app.iconGeneration
                else -> false
            }
        }
    }
}

/** Splits a sorted app list into bucket headers plus apps. */
fun List<AppInfo>.toRows(): List<AppListRow> {
    val rows = ArrayList<AppListRow>(size + 27)
    var current: Char? = null
    for (app in this) {
        val bucket = app.bucket
        if (bucket != current) {
            rows += AppListRow.Header(bucket)
            current = bucket
        }
        rows += AppListRow.App(app)
    }
    return rows
}
