package io.github.tuxwheel.ble

import android.app.Application
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.tritbool.euc.ble.EucBleClient
import io.github.tritbool.euc.ble.models.AuthenticationState
import io.github.tritbool.euc.ble.models.WheelSecurityEvent
import io.github.tritbool.euc.ble.models.WheelSecurityState
import io.github.tritbool.euc.ble.models.WheelLockState
import io.github.tritbool.euc.ble.protocols.CommandSupport
import io.github.tritbool.euc.ble.protocols.CommandType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class WheelPasswordTest {
    private lateinit var model: BleSessionViewModel
    private val client = mockk<EucBleClient>(relaxed = true)

    @Before fun setup() {
        model = BleSessionViewModel(ApplicationProvider.getApplicationContext())
        model.javaClass.getDeclaredField("_eucBleClient\$delegate").apply { isAccessible = true }
            .set(model, lazyOf(client))
        every { client.getCommandSupport(CommandType.AUTHENTICATE) } returns CommandSupport.SUPPORTED
    }

    @After fun cleanup() {
        model.viewModelScope.cancel()
    }

    @Test fun `prompt once per protocol session including reconnect`() {
        model.updateWheelSecurity(null)
        assertThat(model.sessionState.value.passwordPromptRequired).isFalse()
        model.updateWheelSecurity(WheelSecurityState())
        assertThat(model.sessionState.value.passwordPromptRequired).isTrue()
        model.submitWheelPassword("")
        model.updateWheelSecurity(WheelSecurityState(AuthenticationState.REQUIRED))
        assertThat(model.sessionState.value.passwordPromptRequired).isFalse()
        verify(exactly = 0) { client.authenticate(any()) }
        model.updateWheelSecurity(null)
        model.updateWheelSecurity(WheelSecurityState())
        assertThat(model.sessionState.value.passwordPromptRequired).isTrue()
    }

    @Test fun `wheel lock state follows wheel notifications and resets when unavailable`() {
        model.updateWheelSecurity(WheelSecurityState(lock = WheelLockState.LOCKED))
        assertThat(model.sessionState.value.wheelLockState).isEqualTo(WheelLockState.LOCKED)

        model.updateWheelSecurity(WheelSecurityState(lock = WheelLockState.UNLOCKED))
        assertThat(model.sessionState.value.wheelLockState).isEqualTo(WheelLockState.UNLOCKED)

        model.updateWheelSecurity(null)
        assertThat(model.sessionState.value.wheelLockState).isEqualTo(WheelLockState.UNKNOWN)
    }

    @Test fun `lock and unlock request wheel-confirmed lock state afterward`() {
        every { client.getCommandSupport(CommandType.LOCK) } returns CommandSupport.SUPPORTED
        every { client.getCommandSupport(CommandType.UNLOCK) } returns CommandSupport.SUPPORTED
        every { client.getCommandSupport(CommandType.REQUEST_LOCK_STATE) } returns CommandSupport.SUPPORTED

        model.setWheelLocked(true)
        model.setWheelLocked(false)

        verify(exactly = 1) { client.sendCommand(CommandType.LOCK, Unit) }
        verify(exactly = 1) { client.sendCommand(CommandType.UNLOCK, Unit) }
        verify(exactly = 2) { client.sendCommand(CommandType.REQUEST_LOCK_STATE, Unit) }
    }

    @Test fun `validate ASCII digits and preserve leading zeros without sending unlock`() {
        model.updateWheelSecurity(WheelSecurityState())
        listOf("12", "12345", "abcd", "１２３４").forEach { model.submitWheelPassword(it) }
        verify(exactly = 0) { client.authenticate(any()) }
        assertThat(model.sessionState.value.passwordPromptRequired).isTrue()
        model.submitWheelPassword("0012")
        verify(exactly = 1) { client.authenticate("0012") }
        verify(exactly = 0) { client.unlock() }
        assertThat(model.sessionState.value.passwordPromptRequired).isFalse()
    }

    @Test fun `repeated required reply offers retry and confirmed authentication cancels timeout`() {
        model.updateWheelSecurity(WheelSecurityState(AuthenticationState.REQUIRED))
        model.submitWheelPassword("0012")
        model.handleWheelSecurityEvent(WheelSecurityEvent.AuthenticationStatus(AuthenticationState.REQUIRED))
        assertThat(model.sessionState.value.passwordPromptError).isTrue()
        model.submitWheelPassword("0012")
        model.updateWheelSecurity(WheelSecurityState(AuthenticationState.AUTHENTICATED))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        assertThat(model.sessionState.value.passwordPromptRequired).isFalse()
    }

    @Test fun `timeout offers retry but protocol reset cancels pending request`() {
        model.updateWheelSecurity(WheelSecurityState())
        model.submitWheelPassword("0012")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        assertThat(model.sessionState.value.passwordPromptRequired).isTrue()
        assertThat(model.sessionState.value.passwordPromptError).isTrue()
        model.submitWheelPassword("0012")
        model.updateWheelSecurity(null)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        assertThat(model.sessionState.value.passwordPromptRequired).isFalse()
    }
}
