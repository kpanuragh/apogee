package io.apogee.launcher.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-package notification counts behind the live tile badges.
 *
 * Always present so the Start screen can read it unconditionally, but only ever written by
 * [BadgeListenerService], which exists in the `badges` flavour alone. In the standard
 * flavour this simply stays empty.
 */
object BadgeCounts {

    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    fun update(next: Map<String, Int>) {
        _counts.value = next
    }

    fun countFor(packageName: String?): Int =
        packageName?.let { _counts.value[it] } ?: 0
}
