package io.apogee.launcher.data

import android.app.Notification
import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.apogee.launcher.data.live.LiveFeed
import io.apogee.launcher.data.live.NowPlaying

/**
 * The one component that can see what the rest of the system is doing: unread counts, the
 * latest line each app posted, and what is playing.
 *
 * This lives in the `badges` flavour only. Declaring a notification listener makes Google
 * Play Protect block the APK as a sideload in some regions, so the standard build ships
 * without it and [LiveFeed] and [BadgeCounts] simply stay empty there.
 *
 * Entirely opt-in even here: nothing is read until the user grants notification access, and
 * the counts and the text are separate switches in settings.
 */
class BadgeListenerService : NotificationListenerService() {

    private val sessionManager: MediaSessionManager? by lazy {
        runCatching {
            getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        }.getOrNull()
    }

    private val self: ComponentName by lazy {
        ComponentName(this, BadgeListenerService::class.java)
    }

    private val sessionsChanged =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            publishNowPlaying(controllers)
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        recount()
        runCatching {
            sessionManager?.addOnActiveSessionsChangedListener(sessionsChanged, self)
            publishNowPlaying(sessionManager?.getActiveSessions(self))
        }
    }

    override fun onListenerDisconnected() {
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        LiveFeed.clear()
        BadgeCounts.update(emptyMap())
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val counts = HashMap<String, Int>()
        val lines = HashMap<String, String>()
        val newest = HashMap<String, Long>()

        for (sbn in active) {
            if (!sbn.isClearable) continue
            if (sbn.notification?.group != null && isSummary(sbn)) continue
            counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1

            // Keep only the most recent line per app; that is what a tile has room for.
            if (sbn.postTime >= (newest[sbn.packageName] ?: Long.MIN_VALUE)) {
                summarise(sbn)?.let {
                    newest[sbn.packageName] = sbn.postTime
                    lines[sbn.packageName] = it
                }
            }
        }
        BadgeCounts.update(counts)
        LiveFeed.updateNotificationLines(lines)
    }

    /** "Sender: message" where both exist, otherwise whichever one does. */
    private fun summarise(sbn: StatusBarNotification): String? {
        val extras = sbn.notification?.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = (
            extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            )?.toString()?.trim()?.replace('\n', ' ')
        return when {
            !title.isNullOrEmpty() && !text.isNullOrEmpty() -> "$title: $text"
            !title.isNullOrEmpty() -> title
            !text.isNullOrEmpty() -> text
            else -> null
        }
    }

    /** The playing session wins; otherwise the most recently active one, paused. */
    private fun publishNowPlaying(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            LiveFeed.updateNowPlaying(null)
            return
        }
        val playing = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        val controller = playing ?: controllers.first()
        val metadata = controller.metadata
        if (metadata == null) {
            LiveFeed.updateNowPlaying(null)
            return
        }
        val track = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
            ?.trim()
            .orEmpty()
        if (track.isEmpty()) {
            LiveFeed.updateNowPlaying(null)
            return
        }
        LiveFeed.updateNowPlaying(
            NowPlaying(
                track = track,
                artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                album = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                packageName = controller.packageName,
                isPlaying = playing != null,
            ),
        )
    }

    private fun isSummary(sbn: StatusBarNotification): Boolean =
        sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
}
