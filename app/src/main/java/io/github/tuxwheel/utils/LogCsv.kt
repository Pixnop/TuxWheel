package io.github.tuxwheel.utils

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Trip CSV format shared by [io.github.tuxwheel.LoggingService] and the trip parser.
 *
 * WheelLog wrote the same header with distances in integer metres; TuxWheel writes kilometres
 * with decimals, so a value containing '.' is read as kilometres.
 */
object LogCsv {
    const val HEADER =
        "date,time,speed,voltage,phase_current,current,power,torque,pwm,battery_level," +
            "distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"

    const val RAW_SUFFIX = ".raw.csv"
    const val BMS_SUFFIX = ".bms.csv"

    private const val FORMULA_PREFIXES = "=+-@\t\r"

    fun formatRow(
        dateTime: String,
        speed: Double,
        voltage: Double,
        phaseCurrent: Double,
        current: Double,
        power: Double,
        torque: Double,
        pwm: Double,
        batteryLevel: Int,
        distanceKm: Double,
        totalDistanceKm: Double,
        systemTemp: Double,
        temp2: Double,
        tilt: Double,
        roll: Double,
        mode: String,
        alert: Int = 0,
    ): String = String.format(
        Locale.US,
        "%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%.3f,%.3f,%.2f,%.2f,%.2f,%.2f,%s,%d",
        dateTime, speed, voltage, phaseCurrent, current, power, torque, pwm, batteryLevel,
        distanceKm, totalDistanceKm, systemTemp, temp2, tilt, roll, sanitizeField(mode), alert,
    )

    /** Keeps a device-supplied value on one cell and stops spreadsheets from running it. */
    fun sanitizeField(value: String): String {
        val cleaned = value.replace(Regex("[,\"\r\n]"), " ")
        return if (cleaned.isNotEmpty() && cleaned[0] in FORMULA_PREFIXES) "'$cleaned" else cleaned
    }

    fun parseDistanceMeters(token: String?): Int {
        val value = token?.trim().orEmpty()
        if ('.' !in value) return value.toIntOrNull() ?: 0
        val km = value.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return 0
        return (km * 1000).roundToInt()
    }

    fun parseTemperature(token: String?): Int =
        token?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }?.roundToInt() ?: 0

    fun isTripLogFileName(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return lower.endsWith(".csv") && !lower.endsWith(RAW_SUFFIX) &&
            !lower.endsWith(BMS_SUFFIX) && !name.startsWith("RAW")
    }
}
