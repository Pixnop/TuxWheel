package io.github.tuxwheel.settings

import com.google.common.truth.Truth.assertThat
import io.github.tritbool.euc.ble.core.BLEConstants
import io.github.tritbool.euc.ble.models.EUCData
import io.github.tritbool.euc.ble.models.WheelLockState
import io.github.tuxwheel.ble.BleSessionState
import org.junit.Test

class WheelLockControlsTest {
    private val now = 10_000L
    private val connected = BleSessionState(
        connectionState = BLEConstants.ConnectionState.CONNECTED,
        wheelLockState = WheelLockState.LOCKED,
    )

    @Test fun `locked wheel can be unlocked without telemetry including after reconnect`() {
        assertThat(canSetWheelLocked(connected, false, now)).isTrue()
        assertThat(canSetWheelLocked(connected.copy(lastDataTimestamp = now - 5_000), false, now))
            .isTrue()
    }

    @Test fun `locking requires fresh telemetry`() {
        assertThat(canSetWheelLocked(connected, true, now)).isFalse()
        assertThat(canSetWheelLocked(connected.copy(lastDataTimestamp = now - 5_000), true, now))
            .isFalse()
        assertThat(canSetWheelLocked(connected.copy(lastDataTimestamp = now + 1), true, now))
            .isFalse()
        assertThat(canSetWheelLocked(connected.copy(lastDataTimestamp = now - 4_999), true, now))
            .isTrue()
    }

    @Test fun `disconnected wheel cannot be locked or unlocked`() {
        val disconnected = connected.copy(
            connectionState = BLEConstants.ConnectionState.DISCONNECTED,
            lastDataTimestamp = now,
        )
        assertThat(canSetWheelLocked(disconnected, true, now)).isFalse()
        assertThat(canSetWheelLocked(disconnected, false, now)).isFalse()
    }

    @Test fun `locking requires stationary wheel in either direction`() {
        listOf(-5.0, -1.0, 1.0, 5.0, Double.NaN).forEach { speed ->
            val state = connected.copy(lastDataTimestamp = now, lastData = data(speed))
            assertThat(canSetWheelLocked(state, true, now)).isFalse()
        }
        listOf(-0.5, 0.0, 0.5).forEach { speed ->
            val state = connected.copy(lastDataTimestamp = now, lastData = data(speed))
            assertThat(canSetWheelLocked(state, true, now)).isTrue()
        }
    }

    private fun data(speed: Double) = EUCData(
        speed = speed, voltage = 100.0, current = 0.0, temperature = 25.0, batteryLevel = 80,
        distance = 0.0, power = 0.0, timestamp = now, rawData = byteArrayOf(),
        manufacturer = "KingSong", model = "KS", isCharging = false, rideTime = 0,
    )
}
