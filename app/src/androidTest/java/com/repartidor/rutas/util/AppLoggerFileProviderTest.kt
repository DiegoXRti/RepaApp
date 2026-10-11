package com.repartidor.rutas.util

import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLoggerFileProviderTest {

    @Test
    fun logFileCanBeSharedThroughFileProvider() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        AppLogger.log(context, "file provider test")
        val logFile = AppLogger.getLogFile(context)

        assertTrue(logFile.exists())
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )

        assertEquals("content", uri.scheme)
    }
}
