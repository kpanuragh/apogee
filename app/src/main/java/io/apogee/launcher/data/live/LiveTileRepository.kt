package io.apogee.launcher.data.live

import android.Manifest
import android.app.AlarmManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import io.apogee.launcher.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Gathers what the live tiles show.
 *
 * Two of the four sources cost nothing to read: the next alarm comes from [AlarmManager]
 * without any permission, and the clock is just the clock. The calendar needs READ_CALENDAR,
 * which the user grants explicitly and which this treats as absent until they do. The media
 * and notification fields arrive from [LiveFeed], written by the notification listener in
 * the `badges` build.
 */
class LiveTileRepository(
    private val context: Context,
    private val prefs: Prefs,
) {

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Re-reads the alarm and the calendar. Cheap enough to call whenever Start resumes. */
    fun refresh() {
        scope.launch {
            val alarm = nextAlarm()
            val event = nextEvent()
            _state.value = _state.value.copy(nextAlarmAt = alarm, nextEvent = event)
        }
    }

    /** Pushes listener-sourced content in; called from collectors in the activity. */
    fun onFeedChanged(nowPlaying: NowPlaying?, notificationLines: Map<String, String>) {
        _state.value = _state.value.copy(
            nowPlaying = nowPlaying,
            notificationLines = notificationLines,
        )
    }

    /** The system's next alarm, whichever app set it. Needs no permission. */
    private fun nextAlarm(): Long? =
        runCatching { alarmManager.nextAlarmClock?.triggerTime }.getOrNull()

    fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The next event starting within a week. Queried through `Instances`, which expands
     * recurring events into real occurrences — querying `Events` would show the series'
     * original date forever.
     */
    private suspend fun nextEvent(): CalendarEvent? = withContext(Dispatchers.IO) {
        if (!prefs.calendarOnTiles || !hasCalendarPermission()) return@withContext null

        val now = System.currentTimeMillis()
        val until = now + TimeUnit.DAYS.toMillis(7)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .let { ContentUris.appendId(it, now); ContentUris.appendId(it, until); it.build() }

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
        )

        runCatching {
            context.contentResolver.query(
                uri,
                projection,
                "${CalendarContract.Instances.ALL_DAY} = 0 OR " +
                    "${CalendarContract.Instances.ALL_DAY} = 1",
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val begin = cursor.getLong(1)
                    val allDay = cursor.getInt(2) == 1
                    // Instances can come back already under way; the tile is about what is
                    // next, so skip anything that has already started (all-day aside).
                    if (!allDay && begin < now) continue
                    val title = cursor.getString(0)?.trim().orEmpty()
                    return@use CalendarEvent(
                        title = title.ifEmpty { "(busy)" },
                        beginsAt = begin,
                        allDay = allDay,
                    )
                }
                null
            }
        }.getOrNull()
    }
}
