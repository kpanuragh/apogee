package io.apogee.launcher.data

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.apogee.launcher.data.live.CalendarEvent
import io.apogee.launcher.data.live.LiveState
import io.apogee.launcher.data.live.NowPlaying
import io.apogee.launcher.ui.start.LiveTileText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class LiveTileTextTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun tile(kind: TileKind, pkg: String? = null) = Tile(
        id = "t",
        kind = kind,
        size = TileSize.WIDE,
        component = pkg?.let { ComponentName(it, "$it.Main") },
    )

    private fun live(
        tile: Tile,
        state: LiveState,
        appLabel: String? = null,
        notificationText: Boolean = true,
    ) = LiveTileText.forTile(context, tile, state, appLabel, notificationText)

    /** Noon today, so "today" is unambiguous whatever hour the test runs at. */
    private fun todayAtNoon(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `a clock tile with no alarm set says nothing`() {
        assertNull(live(tile(TileKind.CLOCK), LiveState()))
    }

    @Test
    fun `a clock tile announces the next alarm`() {
        val state = LiveState(nextAlarmAt = todayAtNoon())

        val result = live(tile(TileKind.CLOCK), state)!!

        assertTrue("headline was ${result.headline}", result.headline.startsWith("alarm "))
        assertEquals("today", result.detail)
    }

    @Test
    fun `an alarm tomorrow is labelled tomorrow`() {
        val state = LiveState(nextAlarmAt = todayAtNoon() + TimeUnit.DAYS.toMillis(1))

        assertEquals("tomorrow", live(tile(TileKind.CLOCK), state)!!.detail)
    }

    @Test
    fun `a calendar tile leads with the event title`() {
        val state = LiveState(
            nextEvent = CalendarEvent("Standup", todayAtNoon(), allDay = false),
        )

        val result = live(tile(TileKind.CALENDAR), state)!!

        assertEquals("Standup", result.headline)
        assertTrue("detail was ${result.detail}", result.detail!!.startsWith("today ·"))
    }

    @Test
    fun `an all day event shows the day without a time`() {
        val state = LiveState(
            nextEvent = CalendarEvent("Holiday", todayAtNoon(), allDay = true),
        )

        assertEquals("today", live(tile(TileKind.CALENDAR), state)!!.detail)
    }

    @Test
    fun `a media tile shows the track over the artist and album`() {
        val state = LiveState(
            nowPlaying = NowPlaying("Teardrop", "Massive Attack", "Mezzanine", "x", true),
        )

        val result = live(tile(TileKind.MEDIA), state)!!

        assertEquals("Teardrop", result.headline)
        assertEquals("Massive Attack · Mezzanine", result.detail)
    }

    @Test
    fun `a single track repeated as its own album is not shown twice`() {
        val state = LiveState(nowPlaying = NowPlaying("Song", "Band", "Band", "x", true))

        assertEquals("Band", live(tile(TileKind.MEDIA), state)!!.detail)
    }

    @Test
    fun `a media tile with nothing playing says nothing`() {
        assertNull(live(tile(TileKind.MEDIA), LiveState()))
    }

    @Test
    fun `an app tile shows its latest notification line`() {
        val state = LiveState(notificationLines = mapOf("com.mail" to "Ada: lunch?"))

        val result = live(tile(TileKind.APP, "com.mail"), state, appLabel = "Mail")!!

        assertEquals("Ada: lunch?", result.headline)
        assertEquals("Mail", result.detail)
    }

    @Test
    fun `notification text stays off a tile when the setting is off`() {
        val state = LiveState(notificationLines = mapOf("com.mail" to "Ada: lunch?"))

        assertNull(live(tile(TileKind.APP, "com.mail"), state, notificationText = false))
    }

    @Test
    fun `an app with no notification shows nothing, even with others pending`() {
        val state = LiveState(notificationLines = mapOf("com.other" to "hi"))

        assertNull(live(tile(TileKind.APP, "com.mail"), state))
    }

    @Test
    fun `tiles with no live source never produce a line`() {
        val state = LiveState(
            nextAlarmAt = todayAtNoon(),
            nowPlaying = NowPlaying("Song", "Band", null, "x", true),
        )

        assertNull(live(tile(TileKind.ALL_APPS), state))
        assertNull(live(tile(TileKind.SETTINGS), state))
    }
}
