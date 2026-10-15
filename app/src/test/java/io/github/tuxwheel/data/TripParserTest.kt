package io.github.tuxwheel.data

import android.content.Context
import com.google.common.collect.Range
import com.google.common.truth.Truth.*
import io.github.tuxwheel.utils.LogCsv
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.io.InputStream
import kotlin.system.measureTimeMillis

class TripParserTest {
    private lateinit var dao: TripDao

    @Before
    fun setUp() {
        dao = mockkClass(TripDao::class, relaxed = false)
        startKoin {
            modules(
                module {
                    single { dao }
                }
            )
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
        stopKoin()
    }

    @Test
    fun parseFile() {
        // Arrange.
        val context = mockkClass(Context::class, relaxed = true)
        val trip = TripDataDbEntry(fileName = "test")
        every { dao.getTripByFileName(any()) } returns trip
        justRun { dao.update(any()) }
        val inputStream: InputStream = File("src/test/resources/log_test1.csv").inputStream()

        // Act.
        measureTimeMillis {
            TripParser.parseFile(context, "test", inputStream)
        }.also {
            println("parseFile took $it ms")
        }

        // Assert.
        assertThat(trip.duration).isEqualTo(880)
        assertThat(trip.distance).isEqualTo(19_493)
        assertThat(trip.maxCurrent).isIn(Range.closed(86.8f, 86.9f))
        assertThat(trip.maxPwm).isIn(Range.closed(86.7f, 86.8f))
        assertThat(trip.maxPower).isIn(Range.closed(7825f, 7826f))
        assertThat(trip.maxSpeed).isIn(Range.closed(71f, 71.05f))
        assertThat(trip.avgSpeed).isIn(Range.closed(29.7f, 29.8f))
        assertThat(trip.consumptionTotal).isIn(Range.closed(1059f, 1060f))
        assertThat(trip.consumptionByKm).isIn(Range.closed(54.3f, 54.4f))
    }

    @Test
    fun emptyFileDoesNotInsertMetadataAndClosesStream() {
        val context = mockkClass(Context::class, relaxed = true)
        var closed = false
        val stream = object : java.io.ByteArrayInputStream(byteArrayOf()) {
            override fun close() { closed = true; super.close() }
        }
        assertThat(TripParser.parseFile(context, "empty.csv", stream).second).isNull()
        assertThat(closed).isTrue()
        verify { dao wasNot Called }
    }

    @Test
    fun cancellationClosesStreamAndDoesNotInsertMetadata() {
        val context = mockkClass(Context::class, relaxed = true)
        var closed = false
        val stream = object : java.io.ByteArrayInputStream("date,time\n".toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        var cancelled = false
        try {
            TripParser.parseFile(context, "cancelled.csv", stream) {
                throw kotlinx.coroutines.CancellationException("cancel")
            }
        } catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
        assertThat(cancelled).isTrue()
        assertThat(closed).isTrue()
        verify { dao wasNot Called }
    }

    private fun replacementCsv() = (
        "date,time,battery_level,voltage,current,power,speed,system_temp,pwm,distance,totaldistance\n" +
        "2024-01-01,10:00:00.000,80,100,10,1000,20,30,15,0,1000\n" +
        "2024-01-01,10:01:00.000,79,100,20,2000,30,31,25,1000,2000\n"
    ).byteInputStream()

    @Test
    fun replacedFileRecomputesDecreasedMaximaAndPreservesEcMetadata() {
        val context = mockkClass(Context::class, relaxed = true)
        val previous = TripDataDbEntry(fileName = "replacement.csv", maxCurrent = 999f, maxPwm = 99f, ecId = 42, ecUrl = "saved")
        every { dao.getTripByFileName(previous.fileName) } returns previous
        justRun { dao.update(any()) }
        val parsed = TripParser.parseFile(context, previous.fileName, replacementCsv()).second!!
        assertThat(parsed.maxCurrent).isEqualTo(20f)
        assertThat(parsed.maxPwm).isEqualTo(25f)
        assertThat(parsed.ecId).isEqualTo(42)
        assertThat(parsed.ecUrl).isEqualTo("saved")
        verify { dao.update(parsed) }
    }

    @Test
    fun transientCollisionParsingDoesNotReadOrWriteSharedRoomRow() {
        val context = mockkClass(Context::class, relaxed = true)
        val memory = TripDataDbEntry(fileName = "same.csv", maxCurrent = 999f, maxPwm = 99f, ecId = 42)
        val parsed = TripParser.parseFile(
            context, memory.fileName, replacementCsv(), persist = false, existingTrip = memory
        ).second!!
        assertThat(parsed.maxCurrent).isEqualTo(20f)
        assertThat(parsed.maxPwm).isEqualTo(25f)
        assertThat(parsed.ecId).isEqualTo(42)
        assertThat(memory.maxCurrent).isEqualTo(999f)
        assertThat(memory.maxPwm).isEqualTo(99f)
        verify { dao wasNot Called }
    }

    private fun writerRow(
        time: String,
        distanceKm: Double,
        totalDistanceKm: Double,
        systemTemp: Double,
        mode: String = "normal",
    ) = LogCsv.formatRow(
        dateTime = "2026-10-14,$time",
        speed = 25.5,
        voltage = 100.8,
        phaseCurrent = 12.0,
        current = 10.25,
        power = 1033.2,
        torque = 3.0,
        pwm = 42.5,
        batteryLevel = 80,
        distanceKm = distanceKm,
        totalDistanceKm = totalDistanceKm,
        systemTemp = systemTemp,
        temp2 = 40.0,
        tilt = 1.5,
        roll = -2.0,
        mode = mode,
    )

    @Test
    fun roundTripOfLoggingServiceRowKeepsDistanceAndTemperature() {
        val context = mockkClass(Context::class, relaxed = true)
        val csv = LogCsv.HEADER + "\n" +
            writerRow("10:00:00.000", 0.0, 1234.5, 35.27, mode = "=HYPERLINK(\"x\"),\"evil\"\r\nmode") + "\n" +
            writerRow("10:01:00.000", 1.5, 1236.0, 36.8) + "\n"

        val (ticks, trip) = TripParser.parseFile(
            context, "2026_10_14_10_00_00.csv", csv.byteInputStream(), persist = false
        )

        assertThat(ticks).hasSize(2)
        assertThat(ticks[0].totalDistance).isEqualTo(1_234_500)
        assertThat(ticks[0].distance).isEqualTo(0)
        assertThat(ticks[0].temperature).isEqualTo(35)
        assertThat(ticks[0].batteryLevel).isEqualTo(80)
        assertThat(ticks[0].pwm).isEqualTo(42.5)
        assertThat(ticks[1].totalDistance).isEqualTo(1_236_000)
        assertThat(ticks[1].distance).isEqualTo(1_500)
        assertThat(ticks[1].temperature).isEqualTo(37)
        assertThat(trip).isNotNull()
        assertThat(trip!!.distance).isEqualTo(1_500)
        assertThat(trip.duration).isEqualTo(1)
        verify { dao wasNot Called }
    }

    @Test
    fun writerRowKeepsColumnCountAndNeutralisesFormulaInMode() {
        val row = writerRow("10:00:00.000", 0.0, 1.0, 20.0, mode = "=1+1,\"x\"\nnext")
        val columns = row.split(",")
        assertThat(columns).hasSize(LogCsv.HEADER.split(",").size)
        val mode = columns[LogCsv.HEADER.split(",").indexOf("mode")]
        assertThat(mode).isEqualTo("'=1+1  x  next")
        assertThat(LogCsv.sanitizeField("normal")).isEqualTo("normal")
        assertThat(LogCsv.sanitizeField("-3")).isEqualTo("'-3")
        assertThat(LogCsv.sanitizeField("@cmd")).isEqualTo("'@cmd")
        assertThat(LogCsv.sanitizeField("+1")).isEqualTo("'+1")
        assertThat(LogCsv.sanitizeField("")).isEqualTo("")
    }

    @Test
    fun legacyIntegerMetresWithSameHeaderAreStillParsedAsMetres() {
        val context = mockkClass(Context::class, relaxed = true)
        val csv = LogCsv.HEADER + "\n" +
            "2024-05-24,09:35:23.015,20.00,100.36,0.00,5.00,500.00,0.00,30.00,90,0,2203646,35,0,0.00,0.00,,0\n" +
            "2024-05-24,09:36:23.015,20.00,100.36,0.00,5.00,500.00,0.00,30.00,90,1500,2205146,36,0,0.00,0.00,,0\n"

        val (ticks, trip) = TripParser.parseFile(context, "legacy.csv", csv.byteInputStream(), persist = false)

        assertThat(ticks.map { it.totalDistance }).containsExactly(2_203_646, 2_205_146).inOrder()
        assertThat(ticks[1].distance).isEqualTo(1_500)
        assertThat(ticks.map { it.temperature }).containsExactly(35, 36).inOrder()
        assertThat(trip!!.distance).isEqualTo(1_500)
    }

    @Test
    fun distanceAndTemperatureTokensAcceptBothFormats() {
        assertThat(LogCsv.parseDistanceMeters("19493")).isEqualTo(19_493)
        assertThat(LogCsv.parseDistanceMeters("19.493")).isEqualTo(19_493)
        assertThat(LogCsv.parseDistanceMeters("0.0")).isEqualTo(0)
        assertThat(LogCsv.parseDistanceMeters("")).isEqualTo(0)
        assertThat(LogCsv.parseDistanceMeters(null)).isEqualTo(0)
        assertThat(LogCsv.parseDistanceMeters("NaN")).isEqualTo(0)
        assertThat(LogCsv.parseTemperature("35")).isEqualTo(35)
        assertThat(LogCsv.parseTemperature("35.27")).isEqualTo(35)
        assertThat(LogCsv.parseTemperature("-4.6")).isEqualTo(-5)
        assertThat(LogCsv.parseTemperature("NaN")).isEqualTo(0)
        assertThat(LogCsv.parseTemperature(" ")).isEqualTo(0)
    }

    @Test
    fun missingOptionalColumnsAndShortRowsDoNotCrash() {
        val context = mockkClass(Context::class, relaxed = true)
        val csv = "date,time,speed,distance,totaldistance\n" +
            "2024-01-01,10:00:00.000,5\n" +
            "2024-01-01,10:00:01.000,6,0.1,10.0\n"

        val (ticks, trip) = TripParser.parseFile(context, "partial.csv", csv.byteInputStream(), persist = false)

        assertThat(ticks).hasSize(2)
        assertThat(ticks[0].totalDistance).isEqualTo(0)
        assertThat(ticks[1].totalDistance).isEqualTo(10_000)
        assertThat(ticks.map { it.temperature }).containsExactly(0, 0)
        assertThat(trip).isNotNull()
    }

    @Test
    fun headerWithoutDateOrTimeReturnsNoTripInsteadOfCrashing() {
        val context = mockkClass(Context::class, relaxed = true)
        val csv = "speed,distance\n5,10\n"
        val (ticks, trip) = TripParser.parseFile(context, "bad.csv", csv.byteInputStream())
        assertThat(ticks).isEmpty()
        assertThat(trip).isNull()
        assertThat(TripParser.lastError).isNotEmpty()
        verify { dao wasNot Called }
    }
}
