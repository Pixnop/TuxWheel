package io.github.tuxwheel.feature.speedlimiter

import com.google.common.truth.Truth.assertThat
import io.github.tritbool.euc.ble.core.BLEConstants
import io.github.tritbool.euc.ble.protocols.CommandType
import io.github.tuxwheel.AppConfig
import io.github.tuxwheel.R
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

    private fun connect(manufacturer: String = "Gotway", reportedMax: Int? = null, speed: Double = 0.0) {
        states.value = BleSessionState(
            connectionState = BLEConstants.ConnectionState.CONNECTED,
            lastData = mockk(relaxed = true) {
                every { this@mockk.speed } returns speed
                every { this@mockk.manufacturer } returns manufacturer
                every { this@mockk.wheelMaxSpeed } returns reportedMax
                every { this@mockk.speedLimit } returns null
                every { this@mockk.alarm1Speed } returns 20
                every { this@mockk.alarm2Speed } returns 25
                every { this@mockk.alarm3Speed } returns 30
            }
        )
    }

    private fun disconnect() {
        states.value = BleSessionState.EMPTY
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

    @Test
    fun `limiter is not enabled below the current speed in either direction`() {
        listOf(20.5, -25.0, Double.NaN).forEach { speed ->
            connect(speed = speed)
            assertThat(SpeedLimiter.enable(session, config)).isFalse()
            assertThat(SpeedLimiter.refusalMessage(session)).isEqualTo(R.string.speed_limiter_too_fast)
        }
        assertThat(enabled).isFalse()
        assertThat(saved).isEqualTo(0)
        verify(exactly = 0) { session.sendCommand(CommandType.SET_SPEED_LIMIT, any()) }

        connect(speed = -20.0)
        assertThat(SpeedLimiter.enable(session, config)).isTrue()
        verify(exactly = 1) { session.sendCommand(CommandType.SET_SPEED_LIMIT, 20) }
    }

    @Test
    fun `active limit is not lowered below the current speed`() {
        connect()
        SpeedLimiter.enable(session, config)
        connect(speed = 25.0)
        assertThat(SpeedLimiter.setLimit(session, config, 18)).isFalse()
        assertThat(limit).isEqualTo(20)
        verify(exactly = 0) { session.sendCommand(CommandType.SET_SPEED_LIMIT, 18) }

        assertThat(SpeedLimiter.setLimit(session, config, 30)).isTrue()
        verify(exactly = 1) { session.sendCommand(CommandType.SET_SPEED_LIMIT, 30) }
    }

    @Test
    fun `limit is stored while inactive whatever the speed`() {
        connect(speed = 30.0)
        assertThat(SpeedLimiter.setLimit(session, config, 15)).isTrue()
        assertThat(limit).isEqualTo(15)
        verify(exactly = 0) { session.sendCommand(CommandType.SET_SPEED_LIMIT, any()) }
    }

    @Test
    fun `disabling restores the saved max speed while riding`() {
        connect()
        SpeedLimiter.enable(session, config)
        connect(speed = 50.0)
        assertThat(SpeedLimiter.disable(session, config)).isTrue()
        assertThat(enabled).isFalse()
        verify(exactly = 1) { session.sendCommand(CommandType.SET_SPEED_LIMIT, 35) }
    }

    @Test
    fun `limiter never sends more than the wheel max speed`() {
        connect()
        limit = 50
        assertThat(SpeedLimiter.maxLimit(states.value, config)).isEqualTo(35)
        assertThat(SpeedLimiter.enable(session, config)).isTrue()
        verify(exactly = 1) { session.sendCommand(CommandType.SET_SPEED_LIMIT, 35) }
        verify(exactly = 0) { session.sendCommand(CommandType.SET_SPEED_LIMIT, 50) }

        assertThat(SpeedLimiter.setLimit(session, config, 80)).isTrue()
        assertThat(limit).isEqualTo(35)
        verify(exactly = 2) { session.sendCommand(CommandType.SET_SPEED_LIMIT, 35) }
    }

    @Test
    fun `inactive limit is capped at the wheel max speed`() {
        connect("Kingsong", reportedMax = 30)
        SpeedLimiter.setLimit(session, config, 45)
        assertThat(limit).isEqualTo(30)
        wheelMax = 0
        disconnect()
        SpeedLimiter.setLimit(session, config, 45)
        assertThat(limit).isEqualTo(SpeedLimiter.MIN_SPEED)
    }
}
