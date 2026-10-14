package io.github.tuxwheel.ble

import io.github.tritbool.euc.ble.core.TrafficEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale

internal data class RawTrafficRecord(val address: String, val event: TrafficEvent) {
    fun toCsvLine(): String {
        val hex = event.bytes.joinToString(separator = "") {
            "%02X".format(Locale.US, it.toInt() and 0xFF)
        }
        return "${event.timestampMs},$hex,${event.direction.name},${event.sequence},${event.characteristicUuid}"
    }

    companion object {
        const val CSV_HEADER = "timestamp_ms,hex,type,sequence,characteristic_uuid"
    }
}

/**
 * Keeps initialization traffic until the logging service subscribes, without replaying consumed
 * events on service restarts. Overflow is best-effort; library sequence gaps remain visible in CSV.
 */
internal class RawTrafficCapture {
    private val pending = Channel<RawTrafficRecord>(
        capacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val records = pending.receiveAsFlow()
    private var active = true

    @Synchronized
    fun start() {
        active = true
    }

    @Synchronized
    fun stop() {
        active = false
        discardPending()
    }

    @Synchronized
    fun record(address: String, event: TrafficEvent, enabled: Boolean) {
        if (!enabled) {
            discardPending()
        } else if (active && address.isNotBlank()) {
            pending.trySend(RawTrafficRecord(address, event))
        }
    }

    @Synchronized
    fun discardPending() {
        while (pending.tryReceive().isSuccess) {
            // Discard pending diagnostics when recording is disabled or stopped.
        }
    }
}
