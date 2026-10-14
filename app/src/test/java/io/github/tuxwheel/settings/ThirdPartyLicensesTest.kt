package io.github.tuxwheel.settings

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.tuxwheel.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ThirdPartyLicensesTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `dependency inventory and existing license notices are bundled for offline reading`() {
        val inventory = readAsset("third_party_dependencies.txt")
        assertThat(inventory).contains("Direct app dependencies")
        assertThat(inventory).contains("not a complete transitive dependency inventory")
        assertThat(inventory).contains("io.github.tritbool:euc-ble-library:0.1.0")
        assertThat(inventory).contains("com.github.PhilJay:MPAndroidChart:v3.1.0")
        assertThat(inventory).contains("io.insert-koin:koin-android")
        assertThat(inventory).contains("debug only")

        assertThat(readAsset("NOTICE.txt")).contains("upstream metadata discrepancy")
        assertThat(readAsset("GPL-3.0.txt")).contains("GNU GENERAL PUBLIC LICENSE")
        assertThat(readAsset("SourceSansPro-OFL.txt")).contains("SIL OPEN FONT LICENSE")
    }

    @Test fun `license viewer title is available in English and French`() {
        mapOf(
            Locale.ENGLISH to "Third-party software and licenses",
            Locale.FRENCH to "Logiciels tiers et licences",
        ).forEach { (locale, expected) ->
            val configuration = Configuration(application.resources.configuration)
            configuration.setLocale(locale)
            val context = application.createConfigurationContext(configuration)
            assertThat(context.getString(R.string.third_party_licenses_title)).isEqualTo(expected)
        }
    }

    private fun readAsset(name: String): String =
        application.assets.open("licenses/$name").bufferedReader().use { it.readText() }
}
