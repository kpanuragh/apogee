package io.apogee.launcher.ui.start

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import io.apogee.launcher.R
import io.apogee.launcher.data.Tile
import io.apogee.launcher.data.TileKind
import io.apogee.launcher.data.live.LiveState
import io.apogee.launcher.data.live.TileLive
import java.util.Calendar
import java.util.Date

/** Turns the raw [LiveState] into the line each tile kind actually shows. */
object LiveTileText {

    fun forTile(
        context: Context,
        tile: Tile,
        state: LiveState,
        appLabel: String?,
        notificationTextEnabled: Boolean,
    ): TileLive? = when (tile.kind) {
        TileKind.CLOCK -> alarm(context, state)
        TileKind.CALENDAR -> event(context, state)
        TileKind.MEDIA -> nowPlaying(state)
        TileKind.APP -> appLine(tile, state, notificationTextEnabled, appLabel)
        else -> null
    }

    private fun alarm(context: Context, state: LiveState): TileLive? {
        val at = state.nextAlarmAt ?: return null
        return TileLive(
            headline = context.getString(R.string.live_alarm, time(context, at)),
            detail = relativeDay(context, at),
        )
    }

    private fun event(context: Context, state: LiveState): TileLive? {
        val event = state.nextEvent ?: return null
        val detail = if (event.allDay) {
            relativeDay(context, event.beginsAt)
        } else {
            "${relativeDay(context, event.beginsAt)} · ${time(context, event.beginsAt)}"
        }
        return TileLive(headline = event.title, detail = detail)
    }

    private fun nowPlaying(state: LiveState): TileLive? {
        val playing = state.nowPlaying ?: return null
        // Artist and album together where both are known, since that is what a now-playing
        // tile is for; either alone otherwise.
        val detail = listOfNotNull(playing.artist, playing.album)
            .distinct()
            .joinToString(" · ")
            .takeIf { it.isNotEmpty() }
        return TileLive(headline = playing.track, detail = detail)
    }

    private fun appLine(
        tile: Tile,
        state: LiveState,
        enabled: Boolean,
        appLabel: String?,
    ): TileLive? {
        if (!enabled) return null
        val line = state.notificationLines[tile.component?.packageName] ?: return null
        return TileLive(headline = line, detail = appLabel)
    }

    /** "today", "tomorrow", or the weekday — the resolution a tile has room for. */
    private fun relativeDay(context: Context, at: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = at }
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val dayDelta = then.get(Calendar.DAY_OF_YEAR) - now.get(Calendar.DAY_OF_YEAR)
        return when {
            sameYear && dayDelta == 0 -> context.getString(R.string.live_today)
            sameYear && dayDelta == 1 -> context.getString(R.string.live_tomorrow)
            else -> DateUtils.formatDateTime(
                context,
                at,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL,
            )
        }
    }

    private fun time(context: Context, at: Long): String =
        DateFormat.getTimeFormat(context).format(Date(at))
}
