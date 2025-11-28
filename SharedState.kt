package com.example.savegpsdata

object SharedState {
    var latestLocationText: String = "位置情報未取得"
    var latestSatelliteText: String = "衛星情報未取得"
    var gpsLoggingEnabled: Boolean = true // ✅ 初期状態はON
    var currentLowPowerMode: Boolean = true

    const val SAVE_INTERVAL_MS = 10_000L
    const val LOCATION_GAP_THRESHOLD_MS = 10 * 60 * 1000L // 10分

}