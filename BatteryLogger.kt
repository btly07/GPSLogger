package com.example.savegpsdata

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class BatteryLogger(private val context: Context) {
    lateinit var logFile: File
    private var headerWritten = false

    fun logBatteryLevel() {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val gpsState = if (SharedState.gpsLoggingEnabled) "ON" else "OFF"
        val modeState = if (SharedState.currentLowPowerMode) "LOW_POWER" else "NORMAL"

        if (!headerWritten) {
            logFile.appendText("timestamp,battery_level,gps_logging,low_power_mode\n")
            headerWritten = true
        }

        logFile.appendText("$timestamp,$level,$gpsState,$modeState\n")
    }
}
