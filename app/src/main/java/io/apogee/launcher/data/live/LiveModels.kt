package io.apogee.launcher.data.live

/** One tile's live content: a headline, optionally qualified by a smaller detail line. */
data class TileLive(val headline: String, val detail: String? = null)

/** The next event on the user's calendar. */
data class CalendarEvent(
    val title: String,
    val beginsAt: Long,
    val allDay: Boolean,
)

/** What a media app is currently playing. */
data class NowPlaying(
    val track: String,
    val artist: String?,
    val album: String?,
    val packageName: String?,
    val isPlaying: Boolean,
)

/**
 * Everything the Start screen knows about the world right now.
 *
 * Each field has a different cost: the alarm is free, the calendar needs a runtime
 * permission, and the media and notification fields need a notification listener, so they
 * only ever fill in on the `badges` build.
 */
data class LiveState(
    val nextAlarmAt: Long? = null,
    val nextEvent: CalendarEvent? = null,
    val nowPlaying: NowPlaying? = null,
    /** Package name to the latest notification's one-line summary. */
    val notificationLines: Map<String, String> = emptyMap(),
)
