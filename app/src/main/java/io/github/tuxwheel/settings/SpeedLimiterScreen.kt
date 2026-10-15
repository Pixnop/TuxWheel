package io.github.tuxwheel.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.tuxwheel.AppConfig
import io.github.tuxwheel.R
import io.github.tuxwheel.ble.BleSessionViewModel
import io.github.tuxwheel.feature.speedlimiter.SpeedLimiter
import io.github.tuxwheel.utils.ThemeIconEnum
import org.koin.compose.koinInject

@Composable
fun speedLimiterScreen(
    appConfig: AppConfig = koinInject(),
    viewModel: BleSessionViewModel = koinInject(),
) {
    val context = LocalContext.current
    val state by viewModel.sessionState.collectAsState()
    val available = SpeedLimiter.isAvailable(viewModel, state)
    val enabled = remember { mutableStateOf(appConfig.speedLimiterEnabled) }
    var savedMaxSpeed by remember { mutableIntStateOf(appConfig.speedLimiterSavedMaxSpeed) }
    var sliderRevision by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        switchPref(
            name = stringResource(R.string.speed_limiter_enabled_title),
            desc = stringResource(R.string.speed_limiter_enabled_description),
            themeIcon = ThemeIconEnum.SettingsSpeedLimiter,
            defaultState = enabled,
            enabled = available,
        ) { checked ->
            val applied = if (checked) SpeedLimiter.enable(viewModel, appConfig)
                else SpeedLimiter.disable(viewModel, appConfig)
            if (!applied) {
                Toast.makeText(context, SpeedLimiter.refusalMessage(viewModel), Toast.LENGTH_SHORT).show()
            }
            enabled.value = appConfig.speedLimiterEnabled
            savedMaxSpeed = appConfig.speedLimiterSavedMaxSpeed
        }
        // Recreated after each change so the slider shows the stored, possibly clamped, limit.
        key(sliderRevision) {
            val maxLimit = SpeedLimiter.maxLimit(state, appConfig)
            sliderPref(
                name = stringResource(R.string.speed_limiter_speed_title),
                desc = stringResource(R.string.speed_limiter_speed_description),
                position = appConfig.speedLimiterSpeed.coerceAtMost(maxLimit).toFloat(),
                min = SpeedLimiter.MIN_SPEED.toFloat(),
                max = maxLimit.toFloat(),
                unit = R.string.kmh,
            ) {
                if (!SpeedLimiter.setLimit(viewModel, appConfig, it.toInt())) {
                    Toast.makeText(context, SpeedLimiter.refusalMessage(viewModel), Toast.LENGTH_SHORT).show()
                }
                sliderRevision++
            }
        }
        if (enabled.value) {
            baseSettings(
                name = stringResource(R.string.speed_limiter_saved_title),
                desc = stringResource(R.string.speed_limiter_saved_description),
                rightContent = { Text("$savedMaxSpeed ${stringResource(R.string.kmh)}") },
            )
        }
        if (!available) {
            baseSettings(
                name = "",
                desc = stringResource(R.string.speed_limiter_not_connected),
                showDiv = false,
            )
        }
    }
}
