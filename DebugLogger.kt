package com.example.savegpsdata

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DebugLogger {
    lateinit var debugLogFile: File

    fun logDebug(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "$timestamp: $message"
        Log.d("GpsLoggingService", line)
        try {
            debugLogFile.appendText("$line\n")
        } catch (e: Exception) {
            Log.e("GpsLoggingService", "❌ デバッグログ保存失敗: ${e.message}")
        }
    }
}