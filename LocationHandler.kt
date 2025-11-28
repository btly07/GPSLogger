package com.example.savegpsdata

import android.content.Context
import android.location.Location
import android.os.BatteryManager
import com.google.android.gms.location.*
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class LocationHandler(
    private val context: Context,
    private val debugLogger: DebugLogger
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var lastSavedTime: Long = 0L
    var lastLocationTime: Long = System.currentTimeMillis()
    lateinit var logFile: java.io.File
    private var isLowPowerMode: Boolean = false
    private var locationRequest: LocationRequest = createLocationRequest(false)
    private var sessionId: String = UUID.randomUUID().toString()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            lastLocationTime = System.currentTimeMillis()

            debugLogger.logDebug("📍 位置: ${location.latitude}, ${location.longitude}, 精度: ${location.accuracy}, Provider: ${location.provider}")

            val now = System.currentTimeMillis()
            if (now - lastSavedTime >= SharedState.SAVE_INTERVAL_MS) {
                lastSavedTime = now
                SharedState.latestLocationText = formatLocationText(location)
                saveLocationToFile(location)
            }

            if (location.accuracy >= 100.0 && now - lastSavedTime > 5 * 60 * 1000L) {
                debugLogger.logDebug("⚠️ 精度100.0mが継続 → 再登録を試行")
                restartLocationUpdates()
            }
        }
    }

    fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    fun setLowPowerMode(enabled: Boolean) {
        isLowPowerMode = enabled
        locationRequest = createLocationRequest(isLowPowerMode)
        restartLocationUpdates()
    }

    private fun restartLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            debugLogger.logDebug("🔄 位置更新設定変更（節電=$isLowPowerMode）")
        } catch (e: Exception) {
            debugLogger.logDebug("❌ 位置更新再設定失敗: ${e.message}")
        }
    }

    private fun createLocationRequest(lowPower: Boolean): LocationRequest {
        return if (lowPower) {
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L)
                .setMinUpdateIntervalMillis(15_000L)
                .setMaxUpdateDelayMillis(45_000L)
                .setWaitForAccurateLocation(true)
                .build()
        } else {
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
                .setMinUpdateIntervalMillis(3_000L)
                .setMaxUpdateDelayMillis(10_000L)
                .setWaitForAccurateLocation(false)
                .build()
        }
    }

    fun monitorLocationGap() {
        val now = System.currentTimeMillis()
        if (now - lastLocationTime > SharedState.LOCATION_GAP_THRESHOLD_MS) {
            debugLogger.logDebug("⚠️ 位置更新が10分以上途絶 → 再登録を試行")
            restartLocationUpdates()
        }
    }

    private fun formatLocationText(location: Location): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(location.time))
        val speedKmh = location.speed * 3.6
        return """
            時刻: $timestamp
            緯度: ${location.latitude}
            経度: ${location.longitude}
            高度: ${location.altitude} m
            精度: ${location.accuracy} m
            速度: ${"%.3f".format(speedKmh)} km/h
            方位: ${location.bearing}°
            Provider: ${location.provider}
        """.trimIndent()
    }

    private fun saveLocationToFile(location: Location) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(location.time))
        val speedKmh = location.speed * 3.6
        val formattedSpeed = "%.3f".format(speedKmh)
        val batteryLevel = getBatteryLevel()
        val locationAge = System.currentTimeMillis() - location.time
        val isStationary = location.speed < 0.5
        val locationSource = when {
            location.isFromMockProvider -> "Mock"
            location.accuracy < 20 && location.speed > 0.5 -> "GPS"
            location.accuracy < 50 && location.speed <= 0.5 -> "Wi-Fi"
            else -> "Cell"
        }

        val line = "$timestamp,${location.latitude},${location.longitude},${location.altitude},${location.accuracy}," +
                "$formattedSpeed,${location.bearing},${location.provider},${location.isFromMockProvider}," +
                "$batteryLevel,$locationAge,${isStationary},$sessionId,$locationSource,$isLowPowerMode\n"

        try {
            if (!logFile.exists()) {
                logFile.writeText("timestamp,latitude,longitude,altitude,accuracy,speed_kmh,bearing,provider,isMock,batteryLevel,locationAge,isStationary,sessionId,locationSource,lowPowerMode\n")
            }
            FileOutputStream(logFile, true).use { it.write(line.toByteArray()) }
            debugLogger.logDebug("✅ 保存成功: ${logFile.absolutePath}")
        } catch (e: Exception) {
            debugLogger.logDebug("❌ 保存失敗: ${e.message}")
        }
    }


    private fun getBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
