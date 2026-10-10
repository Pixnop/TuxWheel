package io.github.tuxwheel.utils

import android.content.Context
import io.github.tuxwheel.AppConfig
import io.github.tuxwheel.TuxWheel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

object StringUtil: KoinComponent {
    private val appConfig: AppConfig by inject()

    @JvmStatic
    fun inArray(value: String, array: Array<String>): Boolean {
        return value in array
    }

    /**
     * Converts supplied Int value to a temperature string.
     */
    @JvmStatic
    fun Int.toTempString(): String {
        return io.github.tuxwheel.feature.dashboard.DashboardFormatting.temperature(this, appConfig.useFahrenheit)
    }

    @JvmStatic
    fun toHexString(buffer: ByteArray): String {
        return "[" + toHexStringRaw(buffer) + "]"
    }

    @JvmStatic
    fun toHexStringRaw(buffer: ByteArray): String {
        return buffer.joinToString("") { "%02x".format(it) }
    }

    @JvmStatic
    fun getRawTextResource(context: Context, resId: Int): String {
        return context.resources.openRawResource(resId)
                .bufferedReader().use { it.readText() }
    }

    @JvmStatic
    fun deleteFirstSentence(text: CharSequence): CharSequence {
        val indexOfNewLine = text.indexOfFirst { r -> r == '\n' }
        return if (indexOfNewLine != -1) {
            text.substring(indexOfNewLine + 1)
        } else {
            text
        }
    }

    @JvmStatic
    fun isCorrectMac(mac: String): Boolean {
        return Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$").matches(mac)
    }
}