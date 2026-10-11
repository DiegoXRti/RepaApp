package com.repartidor.rutas.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    private const val TAG = "RepaAppLog"
    private const val LOG_FILE_NAME = "repa_app_debug_logs.txt"

    fun log(context: Context, message: String, exception: Throwable? = null) {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logLine = "[$timeStamp] $message ${exception?.let { "\nException: ${Log.getStackTraceString(it)}" } ?: ""}\n"
        
        // Log to Logcat
        Log.e(TAG, logLine)

        // Log to local file
        try {
            val file = File(context.getExternalFilesDir(null), LOG_FILE_NAME)
            FileOutputStream(file, true).use { 
                it.write(logLine.toByteArray()) 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    fun getLogFile(context: Context): File {
        return File(context.getExternalFilesDir(null), LOG_FILE_NAME)
    }
}