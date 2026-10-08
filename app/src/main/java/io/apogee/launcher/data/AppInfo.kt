package io.apogee.launcher.data

import android.content.ComponentName
import android.graphics.drawable.Drawable

/** A single launchable activity, as the launcher knows it. */
data class AppInfo(
    val component: ComponentName,
    val userSerial: Long,
    val label: String,
    val icon: Drawable,
    /** Dominant colour pulled from the icon, used when tiles are coloured per app. */
    val iconColor: Int,
    /** Icon cache generation; changes when an app update replaces the icon. */
    val iconGeneration: Long,
    val firstInstallTime: Long,
    val isWorkProfile: Boolean,
) {
    val key: String get() = "$userSerial|${component.flattenToString()}"

    val packageName: String get() = component.packageName

    /**
     * Bucket this app sorts into in the app list: an uppercase initial, or '#' for anything
     * that does not start with a letter — the same split Windows 10 Mobile uses.
     */
    val bucket: Char
        get() {
            val c = label.firstOrNull { !it.isWhitespace() } ?: '#'
            return if (c.isLetter()) c.uppercaseChar() else '#'
        }
}
