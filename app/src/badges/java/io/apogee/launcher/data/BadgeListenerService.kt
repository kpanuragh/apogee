package io.apogee.launcher.data

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Counts active notifications per package so tiles can show live badges, the way Windows 10
 * Mobile shows an unread count on Mail and Messaging.
 *
 * This lives in the `badges` flavour only. Declaring a notification listener makes Google
 * Play Protect block the APK as a sideload in some regions, so the standard build ships
 * without it and the counts in [BadgeCounts] simply stay empty.
 *
 * Entirely opt-in even here: nothing is counted until the user grants notification access.
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
        BadgeCounts.update(counts)
    }

    private fun isSummary(sbn: StatusBarNotification): Boolean =
        sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0
}
