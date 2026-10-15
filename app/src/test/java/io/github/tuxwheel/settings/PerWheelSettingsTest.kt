package io.github.tuxwheel.settings

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.tuxwheel.AppConfig
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PerWheelSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before fun setup() {
        preferences.edit().clear().putInt("versionSettings", 1).commit()
    }

    @Test fun `lock and limiter state are kept per wheel`() {
        val config = AppConfig(context)
        config.lastMac = "AA:AA:AA:AA:AA:AA"
        config.wheelLocked = true
        config.speedLimiterEnabled = true
        config.speedLimiterSavedMaxSpeed = 42

        config.lastMac = "BB:BB:BB:BB:BB:BB"
        assertThat(config.wheelLocked).isFalse()
        assertThat(config.speedLimiterEnabled).isFalse()
        assertThat(config.speedLimiterSavedMaxSpeed).isEqualTo(25)

        config.lastMac = "AA:AA:AA:AA:AA:AA"
        assertThat(config.wheelLocked).isTrue()
        assertThat(config.speedLimiterEnabled).isTrue()
        assertThat(config.speedLimiterSavedMaxSpeed).isEqualTo(42)
    }

    @Test fun `global values from before move to the last wheel only`() {
        preferences.edit()
            .putBoolean("speed_limiter_enabled", true)
            .putInt("speed_limiter_saved_max_speed", 40)
            .putString("last_mac", "AA:AA:AA:AA:AA:AA")
            .commit()
        val config = AppConfig(context)
        assertThat(config.speedLimiterEnabled).isTrue()
        assertThat(config.speedLimiterSavedMaxSpeed).isEqualTo(40)
        assertThat(preferences.contains("speed_limiter_enabled")).isFalse()

        config.lastMac = "BB:BB:BB:BB:BB:BB"
        assertThat(config.speedLimiterEnabled).isFalse()
        assertThat(config.speedLimiterSavedMaxSpeed).isEqualTo(25)
    }
}
