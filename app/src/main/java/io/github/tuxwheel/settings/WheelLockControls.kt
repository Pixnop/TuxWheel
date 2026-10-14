package io.github.tuxwheel.settings

import io.github.tuxwheel.ble.BleSessionState
import kotlin.math.abs

internal fun canSetWheelLocked(
    state: BleSessionState,
    locked: Boolean,
    now: Long = System.currentTimeMillis(),
): Boolean {
    if (!state.isConnected) return false
    // Locked wheels may stop telemetry; freshness is only required before locking.
    if (!locked) return true
    val timestamp = state.lastDataTimestamp ?: return false
    return now - timestamp in 0L until 5_000L && abs(state.currentSpeed) < 1
}
