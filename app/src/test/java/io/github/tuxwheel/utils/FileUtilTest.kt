package io.github.tuxwheel.utils

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.io.ByteArrayInputStream

class FileUtilTest {
    @Test fun `reading a log closes its input stream and preserves all bytes`() {
        val bytes = ByteArray(20000) { it.toByte() }
        var closed = false
        val input = object : ByteArrayInputStream(bytes) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver
        every { resolver.openInputStream(uri) } returns input
        val file = FileUtil(context)
        FileUtil::class.java.getDeclaredField("uri").apply {
            isAccessible = true
            set(file, uri)
        }

        assertThat(file.readBytes()).isEqualTo(bytes)
        assertThat(closed).isTrue()
    }

    @Test fun `reading an unprepared log returns null`() {
        assertThat(FileUtil(mockk()).readBytes()).isNull()
    }

    @Test fun `diagnostic raw and bms files are not trip logs`() {
        assertThat(LogCsv.isTripLogFileName("2026_10_14_10_00_00.csv")).isTrue()
        assertThat(LogCsv.isTripLogFileName("2026_10_14_10_00_00.CSV")).isTrue()
        assertThat(LogCsv.isTripLogFileName("2026_10_14_10_00_00.raw.csv")).isFalse()
        assertThat(LogCsv.isTripLogFileName("2026_10_14_10_00_00.bms.csv")).isFalse()
        assertThat(LogCsv.isTripLogFileName("RAW_2026_10_14_10_00_00.csv")).isFalse()
        assertThat(LogCsv.isTripLogFileName("2026_10_14_10_00_00.txt")).isFalse()
    }
}
