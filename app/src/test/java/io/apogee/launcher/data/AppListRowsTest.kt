package io.apogee.launcher.data

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import io.apogee.launcher.ui.applist.AppListRow
import io.apogee.launcher.ui.applist.toRows
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppListRowsTest {

    private fun app(label: String) = AppInfo(
        component = ComponentName("com.example", "com.example.$label"),
        userSerial = 0L,
        label = label,
        icon = ColorDrawable(0),
        iconColor = 0,
        iconGeneration = 0L,
        firstInstallTime = 0L,
        isWorkProfile = false,
    )

    @Test
    fun `apps are grouped under one header per initial`() {
        val rows = listOf(app("Alarm"), app("Alpha"), app("Books")).toRows()

        assertEquals(
            listOf("header:A", "app:Alarm", "app:Alpha", "header:B", "app:Books"),
            rows.map { describe(it) },
        )
    }

    @Test
    fun `labels that do not start with a letter land in the hash bucket`() {
        assertEquals('#', app("1Password").bucket)
        assertEquals('#', app("+Message").bucket)
        assertEquals('A', app("  Acrobat").bucket)
        assertEquals('A', app("acrobat").bucket)
    }

    @Test
    fun `an empty app list produces no rows`() {
        assertEquals(emptyList<AppListRow>(), emptyList<AppInfo>().toRows())
    }

    private fun describe(row: AppListRow): String = when (row) {
        is AppListRow.Header -> "header:${row.letter}"
        is AppListRow.App -> "app:${row.app.label}"
    }
}
