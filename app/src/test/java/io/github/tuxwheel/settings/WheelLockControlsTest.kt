package io.github.tuxwheel.settings

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.tritbool.euc.ble.EucBleClient
import io.github.tritbool.euc.ble.core.BLEConstants
import io.github.tritbool.euc.ble.models.EUCData
import io.github.tritbool.euc.ble.models.WheelLockState
import io.github.tritbool.euc.ble.models.WheelSecurityEvent
import io.github.tritbool.euc.ble.protocols.CommandSupport
import io.github.tritbool.euc.ble.protocols.CommandType
import io.github.tuxwheel.ble.BleSessionState
import io.github.tuxwheel.ble.BleSessionViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class WheelLockControlsTest {
    private lateinit var model: BleSessionViewModel
    private val client = mockk<EucBleClient>(relaxed = true)
    private val now = 100_000L

    @Before fun setup() {
        model = BleSessionViewModel(ApplicationProvider.getApplicationContext())
        model.javaClass.getDeclaredField("_eucBleClient\$delegate").apply { isAccessible = true }
            .set(model, lazyOf(client))
        every { client.getCommandSupport(any()) } returns CommandSupport.SUPPORTED
        model.now = { now }
    }

    @After fun cleanup() {
        model.viewModelScope.cancel()
    }

    @Suppress("UNCHECKED_CAST")
    private val state get() = model.javaClass.getDeclaredField("_sessionState").apply { isAccessible = true }
        .get(model) as MutableStateFlow<BleSessionState>

    private fun telemetry(speed: Double = 0.0, timestamp: Long? = now, connected: Boolean = true) {
        state.value = state.value.copy(
            connectionState = if (connected) BLEConstants.ConnectionState.CONNECTED
                else BLEConstants.ConnectionState.DISCONNECTED,
            lastDataTimestamp = timestamp,
            lastData = data(speed),
        )
    }

    @Test fun `stopped wheel with fresh telemetry can be locked`() {
        listOf(-0.9, 0.0, 0.9).forEach { speed ->
            listOf(now, now - 2_000).forEach { timestamp ->
                telemetry(speed, timestamp)
                assertThat(model.setWheelLocked(true)).isTrue()
            }
        }
        verify(exactly = 6) { client.sendCommand(CommandType.LOCK, Unit) }
        verify(exactly = 6) { client.sendCommand(CommandType.REQUEST_LOCK_STATE, Unit) }
    }

    @Test fun `moving wheel is never locked`() {
        listOf(-5.0, -1.0, 1.0, 5.0, Double.NaN).forEach { speed ->
            telemetry(speed)
            assertThat(model.setWheelLocked(true)).isFalse()
        }
        verify(exactly = 0) { client.sendCommand(CommandType.LOCK, any()) }
    }

    @Test fun `stale missing or future telemetry and a disconnected wheel refuse locking`() {
        telemetry(timestamp = now - 2_001)
        assertThat(model.setWheelLocked(true)).isFalse()
        telemetry(timestamp = null)
        assertThat(model.setWheelLocked(true)).isFalse()
        telemetry(timestamp = now + 1)
        assertThat(model.setWheelLocked(true)).isFalse()
        telemetry(connected = false)
        assertThat(model.setWheelLocked(true)).isFalse()
        verify(exactly = 0) { client.sendCommand(CommandType.LOCK, any()) }
        verify(exactly = 0) { client.sendCommand(CommandType.REQUEST_LOCK_STATE, any()) }
    }

    @Test fun `unlock is always sent whatever the speed telemetry age or lock state`() {
        val states = WheelLockState.entries.flatMap { lockState ->
            listOf(true, false).flatMap { connected ->
                listOf(null, 1L, now, Long.MAX_VALUE).flatMap { timestamp ->
                    listOf(-5.0, 0.0, 5.0, Double.NaN).map { speed ->
                        BleSessionState(
                            connectionState = if (connected) BLEConstants.ConnectionState.CONNECTED
                                else BLEConstants.ConnectionState.DISCONNECTED,
                            wheelLockState = lockState,
                            lastDataTimestamp = timestamp,
                            lastData = data(speed),
                        )
                    }
                }
            }
        }
        states.forEach {
            state.value = it
            assertThat(model.setWheelLocked(false)).isTrue()
        }
        verify(exactly = states.size) { client.sendCommand(CommandType.UNLOCK, Unit) }
    }

    @Test fun `toggle refuses to lock a moving wheel and never guesses an unknown state`() {
        telemetry(speed = 20.0)
        model.handleWheelSecurityEvent(WheelSecurityEvent.LockStatus(WheelLockState.UNLOCKED))
        assertThat(model.toggleWheelLock()).isFalse()
        verify(exactly = 0) { client.sendCommand(CommandType.LOCK, any()) }

        model.handleWheelSecurityEvent(WheelSecurityEvent.LockStatus(WheelLockState.LOCKED))
        assertThat(model.toggleWheelLock()).isTrue()
        verify(exactly = 1) { client.sendCommand(CommandType.UNLOCK, Unit) }

        model.handleWheelSecurityEvent(WheelSecurityEvent.LockStatus(WheelLockState.UNKNOWN))
        telemetry()
        assertThat(model.toggleWheelLock()).isFalse()
        verify(exactly = 0) { client.sendCommand(CommandType.LOCK, any()) }
        verify(exactly = 1) { client.sendCommand(CommandType.UNLOCK, Unit) }
    }

    @Test fun `repeated wheel reports can correct an optimistic toggle without suppressing commands`() {
        telemetry()
        model.handleWheelSecurityEvent(WheelSecurityEvent.LockStatus(WheelLockState.UNLOCKED))
        val revision = model.sessionState.value.wheelLockStateRevision
        model.setWheelLocked(true)
        model.handleWheelSecurityEvent(WheelSecurityEvent.LockStatus(WheelLockState.UNLOCKED))
        assertThat(model.sessionState.value.wheelLockState).isEqualTo(WheelLockState.UNLOCKED)
        assertThat(model.sessionState.value.wheelLockStateRevision).isEqualTo(revision + 1)
        model.setWheelLocked(true)
        verify(exactly = 2) { client.sendCommand(CommandType.LOCK, Unit) }
    }

    private fun data(speed: Double) = EUCData(
        speed = speed, voltage = 100.0, current = 0.0, temperature = 25.0, batteryLevel = 80,
        distance = 0.0, power = 0.0, timestamp = 1L, rawData = byteArrayOf(),
        manufacturer = "KingSong", model = "KS", isCharging = false, rideTime = 0,
    )
}
