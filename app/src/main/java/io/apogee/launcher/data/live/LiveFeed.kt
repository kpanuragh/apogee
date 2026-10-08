package io.apogee.launcher.data.live

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The half of the live data that only a notification listener can see: what is playing, and
 * the latest line each app has posted.
 *
 * Always present so the Start screen can read it unconditionally, but only ever written by
 * `BadgeListenerService`, which exists in the `badges` flavour alone. In the standard build
 * these simply stay empty.
 */
object LiveFeed {

    private val _notificationLines = MutableStateFlow<Map<String, String>>(emptyMap())
    val notificationLines: StateFlow<Map<String, String>> = _notificationLines.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    fun updateNotificationLines(lines: Map<String, String>) {
        _notificationLines.value = lines
    }

    fun updateNowPlaying(playing: NowPlaying?) {
        _nowPlaying.value = playing
    }

    fun clear() {
        _notificationLines.value = emptyMap()
        _nowPlaying.value = null
    }
}
