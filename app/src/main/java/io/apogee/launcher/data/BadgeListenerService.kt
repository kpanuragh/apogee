package io.apogee.launcher.data

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Counts active notifications per package so tiles can show live badges, the way Windows 10
 * Mobile shows an unread count on Mail and Messaging. Entirely opt-in: nothing is counted
 * unless the user grants notification access in settings.
 */
class BadgeListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        recount()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val counts = HashMap<String, Int>()
        for (sbn in active) {
            if (!sbn.isClearable) continue
            if (sbn.notification?.group != null && isSummary(sbn)) continue
            counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
        }
        Badges.update(counts)
    }

    private fun isSummary(sbn: StatusBarNotification): Boolean =
        sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0

    /** Per-package notification counts, empty until notification access is granted. */
    object Badges {
        private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

        fun update(next: Map<String, Int>) {
            _counts.value = next
        }

        fun countFor(packageName: String?): Int =
            packageName?.let { _counts.value[it] } ?: 0
    }
}
