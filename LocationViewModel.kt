package com.example.savegpsdata

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class LocationViewModel : ViewModel() {
    private val _locationText = mutableStateOf("位置情報未取得")
    val locationText: State<String> = _locationText

    private val _satelliteText = mutableStateOf("衛星情報未取得")
    val satelliteText: State<String> = _satelliteText

    private val _lowPowerMode = mutableStateOf(false)
    val lowPowerMode: State<Boolean> = _lowPowerMode

    private val _gpsLoggingEnabled = mutableStateOf(true)

    val gpsLoggingEnabled: State<Boolean> = _gpsLoggingEnabled

    fun updateLocationText(text: String) {
        _locationText.value = text
    }

    fun updateSatelliteText(text: String) {
        _satelliteText.value = text
    }

    fun toggleLowPowerMode(enabled: Boolean) {
        _lowPowerMode.value = enabled
    }

    fun toggleGpsLogging(enabled: Boolean) {
        _gpsLoggingEnabled.value = enabled
        SharedState.gpsLoggingEnabled = enabled
    }

}