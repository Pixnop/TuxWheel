package io.github.tuxwheel.utils

import android.content.Context
import io.github.tuxwheel.AppConfig
import io.github.tuxwheel.ble.BleSessionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Checks alarms on every telemetry frame, whether or not the UI is visible. */
class AlarmMonitor(
    private val context: Context,
    private val viewModel: BleSessionViewModel,
    private val appConfig: AppConfig,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = viewModel.sessionState
            .filter { it.isConnected && it.lastDataTimestamp != null }
            .distinctUntilChangedBy { it.lastDataTimestamp }
            .onEach {
                if (appConfig.alarmsEnabled) {
                    Alarms.checkAlarm(viewModel.calculatedPwm / 100, context)
                }
            }
            .launchIn(scope)
    }
}
