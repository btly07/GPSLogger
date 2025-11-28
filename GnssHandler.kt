package com.example.savegpsdata

import android.location.GnssStatus
import android.location.LocationManager

class GnssHandler(
    private val context: android.content.Context,
    private val debugLogger: DebugLogger
) {
    private val locationManager = context.getSystemService(LocationManager::class.java)

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val total = status.satelliteCount
            var used = 0
            for (i in 0 until total) {
                if (status.usedInFix(i)) used++
            }
            SharedState.latestSatelliteText = "衛星数: $total（Fixに使用: $used）"
        }
    }

    fun register() {
        locationManager.registerGnssStatusCallback(gnssCallback, null)
    }

    fun unregister() {
        locationManager.unregisterGnssStatusCallback(gnssCallback)
    }
}