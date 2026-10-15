package io.github.tuxwheel.feature.speedlimiter

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import io.github.tuxwheel.AppConfig
import io.github.tuxwheel.R
import io.github.tuxwheel.ble.BleSessionState
import io.github.tuxwheel.ble.BleSessionViewModel
import io.github.tuxwheel.ble.getWheelType
import io.github.tuxwheel.utils.Constants
import io.github.tritbool.euc.ble.protocols.CommandType
import kotlin.math.abs

/**
 * App-side speed limiter built on [CommandType.SET_SPEED_LIMIT].
 *
 * Activating it stores the user's own wheel max speed in [AppConfig.speedLimiterSavedMaxSpeed]
 * and pushes [AppConfig.speedLimiterSpeed] to the wheel; deactivating it pushes the saved
 * value back to the wheel and restores [AppConfig.wheelMaxSpeed].
 */
object SpeedLimiter {
    const val MIN_SPEED = 1
    const val MAX_SPEED = 100

    /** KingSong encodes the limit together with the alarm speeds, so they must be known first. */
    fun isAvailable(
        session: BleSessionViewModel,
        state: BleSessionState = session.sessionState.value
    ): Boolean {
        if (!state.isConnected || !session.isCommandSupported(CommandType.SET_SPEED_LIMIT)) return false
        if (state.getWheelType() != Constants.WHEEL_TYPE.KINGSONG) return true
        return reportedMaxSpeed(state) != null && state.alarm1Speed != null &&
            state.alarm2Speed != null && state.alarm3Speed != null
    }

    /** The wheel max speed currently configured by the user (before any limiter is applied). */
    fun currentWheelMaxSpeed(state: BleSessionState, config: AppConfig): Int =
        reportedMaxSpeed(state) ?: config.wheelMaxSpeed

    /** Highest limit the limiter may apply, so it never raises the wheel's own max speed. */
    fun maxLimit(state: BleSessionState, config: AppConfig): Int =
        (if (config.speedLimiterEnabled) config.speedLimiterSavedMaxSpeed
        else currentWheelMaxSpeed(state, config)).coerceIn(MIN_SPEED, MAX_SPEED)

    /** Applying a limit below the current speed mid-ride is unsafe; an invalid speed counts as moving. */
    fun isBelowCurrentSpeed(limit: Int, state: BleSessionState): Boolean =
        !(abs(state.currentSpeed) <= limit)

    /** Message explaining why [enable], [disable], [toggle] or [setLimit] returned false. */
    @StringRes
    fun refusalMessage(session: BleSessionViewModel): Int =
        if (isAvailable(session)) R.string.speed_limiter_too_fast else R.string.speed_limiter_unavailable

    @SuppressLint("MissingPermission")
    fun enable(session: BleSessionViewModel, config: AppConfig): Boolean {
        if (config.speedLimiterEnabled) return true
        if (!isAvailable(session)) return false
        val state = session.sessionState.value
        val max = currentWheelMaxSpeed(state, config)
        val limit = minOf(config.speedLimiterSpeed, max)
        if (isBelowCurrentSpeed(limit, state)) return false
        config.speedLimiterSavedMaxSpeed = max
        config.speedLimiterEnabled = true
        session.sendCommand(CommandType.SET_SPEED_LIMIT, limit)
        return true
    }

    @SuppressLint("MissingPermission")
    fun disable(session: BleSessionViewModel, config: AppConfig): Boolean {
        if (!config.speedLimiterEnabled) return true
        if (!isAvailable(session)) return false
        val saved = config.speedLimiterSavedMaxSpeed
        config.wheelMaxSpeed = saved
        config.speedLimiterEnabled = false
        session.sendCommand(CommandType.SET_SPEED_LIMIT, saved)
        return true
    }

    fun toggle(session: BleSessionViewModel, config: AppConfig): Boolean =
        if (config.speedLimiterEnabled) disable(session, config) else enable(session, config)

    /**
     * Updates the limiter speed, capped at [maxLimit], and re-applies it to the wheel when the
     * limiter is active. Returns false, keeping the previous limit, if it is below the current speed.
     */
    @SuppressLint("MissingPermission")
    fun setLimit(session: BleSessionViewModel, config: AppConfig, speed: Int): Boolean {
        val state = session.sessionState.value
        val limit = speed.coerceIn(MIN_SPEED, maxLimit(state, config))
        if (!config.speedLimiterEnabled || !isAvailable(session, state)) {
            config.speedLimiterSpeed = limit
            return true
        }
        val applied = minOf(limit, config.speedLimiterSavedMaxSpeed)
        if (isBelowCurrentSpeed(applied, state)) return false
        config.speedLimiterSpeed = limit
        session.sendCommand(CommandType.SET_SPEED_LIMIT, applied)
        return true
    }

    private fun reportedMaxSpeed(state: BleSessionState): Int? =
        if (state.getWheelType() == Constants.WHEEL_TYPE.KINGSONG)
            state.wheelMaxSpeed ?: state.speedLimit?.toInt() else null
}
