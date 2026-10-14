package io.github.tuxwheel.utils

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class FileUtilBufferingTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun writer(file: File, buffered: Boolean): FileUtil {
        val context = ApplicationProvider.getApplicationContext<Application>()
        return FileUtil(context, buffered).apply {
            javaClass.getDeclaredField("file").apply { isAccessible = true }.set(this, file)
            prepareStream()
        }
    }

    @Test fun `buffered diagnostics are flushed on close`() {
        val file = temporaryFolder.newFile()
        val writer = writer(file, buffered = true)
        writer.writeLine("timestamp_ms,hex,type,sequence,characteristic_uuid")
        assertThat(file.length()).isEqualTo(0L)
        writer.close()
        assertThat(file.readText()).isEqualTo("timestamp_ms,hex,type,sequence,characteristic_uuid\r\n")
    }

    @Test fun `ordinary log writes still flush immediately`() {
        val file = temporaryFolder.newFile()
        val writer = writer(file, buffered = false)
        try {
            writer.writeLine("ride data")
            assertThat(file.readText()).isEqualTo("ride data\r\n")
        } finally {
            writer.close()
        }
    }
}
