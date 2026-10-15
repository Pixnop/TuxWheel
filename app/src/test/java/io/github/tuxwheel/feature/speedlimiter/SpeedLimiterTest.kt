package io.github.tuxwheel.feature.speedlimiter

import com.google.common.truth.Truth.assertThat
import io.github.tritbool.euc.ble.core.BLEConstants
import io.github.tritbool.euc.ble.protocols.CommandType
import io.github.tuxwheel.AppConfig
import io.github.tuxwheel.ble.BleSessionState
import io.github.tuxwheel.ble.BleSessionViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test

class SpeedLimiterTest {
    private val states = MutableStateFlow(BleSessionState.EMPTY)
    private val session = mockk<BleSessionViewModel>(relaxed = true)
    private val config = mockk<AppConfig>(relaxed = true)
    private var enabled = false
    private var limit = 20
    private var saved = 0
    private var wheelMax = 35

    @Before
    fun setUp() {
        every { session.sessionState } returns states
        every { session.isCommandSupported(CommandType.SET_SPEED_LIMIT) } returns true
        every { config.speedLimiterEnabled } answers { enabled }
        every { config.speedLimiterEnabled = any() } answers { enabled = firstArg() }
        every { config.speedLimiterSpeed } answers { limit }
        every { config.speedLimiterSpeed = any() } answers { limit = firstArg() }
        every { config.speedLimiterSavedMaxSpeed } answers { saved }
        every { config.speedLimiterSavedMaxSpeed = any() } answers { saved = firstArg() }
        every { config.wheelMaxSpeed } answers { wheelMax }
        every { config.wheelMaxSpeed = any() } answers { wheelMax = firstArg() }
    }

    private fun connect(manufacturer: String = "Gotway", reportedMax: Int? = null) {
        states.value = BleSessionState(
            connectionState = BLEConstants.ConnectionState.CONNECTED,
            lastData = mockk(relaxed = true) {
                every { this@mockk.manufacturer } returns manufacturer
                every { this@mockk.wheelMaxSpeed } returns reportedMax
                every { this@mockk.speedLimit } returns null
                every { this@mockk.alarm1Speed } returns 20
                every { this@mockk.alarm2Speed } returns 25
                every { this@mockk.alarm3Speed } returns 30
            }
        )
    }

    @Test
    fun `enable saves user max speed and applies limit, disable restores it`() {
        connect()
        assertThat(SpeedLimiter.enable(session, config)).isTrue()
        assertThat(enabled).isTrue()
        assertThat(saved).isEqualTo(35)
        verify(exactly = 1) { session.sendCommand(CommandType.SET_SPEED_LIMIT, 20) }

        assertThat(SpeedLimiter.disable(session, config)).isTrue()
        assertThat(enabled).isFalse()
        assertThat(wheelMax).isEqualTo(35)
        verify(exactly = 1) { session.sendCommand(CommandType.SET_SPEED_LIMIT, 35) }
    }

    @Test
    fun `enabling twice keeps the originally saved max speed`() {
        connect()
        SpeedLimiter.enable(session, config)
        wheelMax = 20
        SpeedLimiter.enable(session, config)
        assertThat(saved).isEqualTo(35)
    }

    @Test
    fun `kingsong saves the wheel reported max speed`() {
        connect("Kingsong", reportedMax = 42)
        assertThat(SpeedLimiter.toggle(session, config)).isTrue()
        assertThat(saved).isEqualTo(42)
    }

    @Test
    fun `limiter cannot change state without a connected supporting wheel`() {
        assertThat(SpeedLimiter.enable(session, config)).isFalse()
        assertThat(enabled).isFalse()
        connect()
        every { session.isCommandSupported(CommandType.SET_SPEED_LIMIT) } returns false
        assertThat(SpeedLimiter.enable(session, config)).isFalse()
        verify(exactly = 0) { session.sendCommand(CommandType.SET_SPEED_LIMIT, any()) }
    }

    @Test
    fun `changing the limit re-applies it only while active`() {
        connect()
        SpeedLimiter.setLimit(session, config, 15)
        verify(exactly = 0) { session.sendCommand(CommandType.SET_SPEED_LIMIT, any()) }
        SpeedLimiter.enable(session, config)
        SpeedLimiter.setLimit(session, config, 18)
        verify(exactly = 1) { session.sendCommand(CommandType.SET_SPEED_LIMIT, 18) }
        assertThat(limit).isEqualTo(18)
    }
}
