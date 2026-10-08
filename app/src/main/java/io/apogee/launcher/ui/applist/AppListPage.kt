package io.apogee.launcher.ui.applist

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.apogee.launcher.R
import io.apogee.launcher.data.AppInfo
import io.apogee.launcher.util.applySystemBarPadding
import io.apogee.launcher.util.dp

/**
 * The app list: every installed app in one alphabetical column, with accent letter headers
 * that open the jump list, plus a search field.
 */
@SuppressLint("ViewConstructor")
class AppListPage(context: Context) : FrameLayout(context) {

    var onAppClick: ((AppInfo, View) -> Unit)? = null
    var onAppLongClick: ((AppInfo, View) -> Unit)? = null

    private val recycler: RecyclerView
    private val search: EditText
    private val empty: TextView
    private val picker = LetterPickerView(context)

    private val adapter = AppListAdapter(
        onClick = { app, view -> onAppClick?.invoke(app, view) },
        onLongClick = { app, view -> onAppLongClick?.invoke(app, view) },
        onHeaderClick = { showJumpList() },
    )

    private var allApps: List<AppInfo> = emptyList()
    private var rows: List<AppListRow> = emptyList()

    init {
        LayoutInflater.from(context).inflate(R.layout.view_app_list, this, true)
        recycler = findViewById(R.id.app_list)
        search = findViewById(R.id.app_search)
        empty = findViewById(R.id.app_list_empty)

        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter
        recycler.setHasFixedSize(false)
        recycler.setPadding(0, 0, 0, context.dp(24f))
        recycler.applySystemBarPadding(top = false, bottom = true)

        findViewById<View>(R.id.app_list_content).applySystemBarPadding(
            top = true,
            bottom = false,
        )

        search.addTextChangedListener(
            object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = applyFilter(s?.toString().orEmpty())
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            },
        )

        addView(picker, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        picker.setOnPickListener(::scrollToBucket)
    }

    fun setAccent(color: Int) {
        adapter.accentColor = color
        picker.accentColor = color
    }

    /** The app list has its own backdrop, unlike Start; the jump list shares it. */
    fun setPageBackground(color: Int) {
        setBackgroundColor(color)
        picker.setBackgroundColor(color)
    }

    fun submit(apps: List<AppInfo>) {
        allApps = apps
        applyFilter(search.text?.toString().orEmpty())
    }

    private fun applyFilter(query: String) {
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.contains(trimmed, ignoreCase = true) }
        }
        rows = if (trimmed.isEmpty()) {
            filtered.toRows()
        } else {
            // Search results drop the letter headers, matching Windows 10 Mobile.
            filtered.map { AppListRow.App(it) }
        }
        adapter.submitList(rows)
        empty.isVisible = filtered.isEmpty()
    }

    private fun showJumpList() {
        hideKeyboard()
        picker.show(allApps.mapTo(HashSet()) { it.bucket })
    }

    private fun scrollToBucket(bucket: Char) {
        val index = rows.indexOfFirst { it is AppListRow.Header && it.letter == bucket }
        if (index < 0) return
        (recycler.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 0)
    }

    /** True when the page consumed the back press itself. */
    fun handleBack(): Boolean {
        if (picker.isVisible) {
            picker.hide()
            return true
        }
        if (search.text?.isNotEmpty() == true) {
            search.setText("")
            hideKeyboard()
            return true
        }
        return false
    }

    fun reset() {
        picker.hide()
        search.setText("")
        hideKeyboard()
        recycler.scrollToPosition(0)
    }

    private fun hideKeyboard() {
        search.clearFocus()
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(windowToken, 0)
    }
}
