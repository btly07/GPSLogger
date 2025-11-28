package com.example.savegpsdata

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val locationViewModel: LocationViewModel by viewModels()
    private val uiHandler = Handler(Looper.getMainLooper())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "📋 権限結果: $permissions")

        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (locationGranted) {
            val intent = Intent(this, GpsLoggingService::class.java).apply {
                putExtra("low_power_mode", SharedState.currentLowPowerMode)
                putExtra("gps_logging_enabled", SharedState.gpsLoggingEnabled)
            }
            Log.d("MainActivity", "🚀 サービス起動要求")
            ContextCompat.startForegroundService(this, intent)
        } else {
            Log.e("MainActivity", "❌ 位置情報権限がないためサービス起動不可")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registerNotificationChannel()

        val permissionList = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionList += Manifest.permission.POST_NOTIFICATIONS
        }

        permissionLauncher.launch(permissionList.toTypedArray())

        uiHandler.post(object : Runnable {
            override fun run() {
                locationViewModel.updateLocationText(SharedState.latestLocationText)
                locationViewModel.updateSatelliteText(SharedState.latestSatelliteText)
                uiHandler.postDelayed(this, 5000)
            }
        })

        setContent {
            MaterialTheme(colors = darkColors()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.systemBars.asPaddingValues())
                ) {
                    val context = LocalContext.current
                    LocationScreen(
                        viewModel = locationViewModel,
                        onLowPowerToggle = { lowPower ->
                            SharedState.currentLowPowerMode = lowPower
                            val intent = Intent(context, GpsLoggingService::class.java).apply {
                                putExtra("low_power_mode", lowPower)
                                putExtra("gps_logging_enabled", SharedState.gpsLoggingEnabled)
                            }
                            ContextCompat.startForegroundService(context, intent)
                            Log.d("MainActivity", "🔁 節電モード変更: $lowPower")
                        },
                        onGpsLoggingToggle = { enabled ->
                            SharedState.gpsLoggingEnabled = enabled
                            locationViewModel.toggleGpsLogging(enabled) // ✅ 最小限の追加
                            val intent = Intent(context, GpsLoggingService::class.java).apply {
                                putExtra("gps_logging_enabled", enabled)
                                putExtra("low_power_mode", SharedState.currentLowPowerMode)
                            }
                            ContextCompat.startForegroundService(context, intent)
                            Log.d("MainActivity", "🔁 GPSログ取得状態変更: $enabled")
                        }
                    )
                }
            }
        }
    }

    private fun registerNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "gps_logging_channel",
                "GPS Logging",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "GPSログ記録中"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            val registered = manager.getNotificationChannel("gps_logging_channel")
            Log.d("MainActivity", "✅ 通知チャンネル登録: importance=${registered?.importance}")
        }
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        locationViewModel.updateLocationText(SharedState.latestLocationText)
        locationViewModel.updateSatelliteText(SharedState.latestSatelliteText)
        locationViewModel.toggleLowPowerMode(SharedState.currentLowPowerMode)
        locationViewModel.toggleGpsLogging(SharedState.gpsLoggingEnabled) // ✅ 最小限の追加

        val intent = Intent(this, GpsLoggingService::class.java).apply {
            putExtra("low_power_mode", SharedState.currentLowPowerMode)
            putExtra("gps_logging_enabled", SharedState.gpsLoggingEnabled)
        }
        ContextCompat.startForegroundService(this, intent)
        Log.d("MainActivity", "🔁 onResumeでサービス再起動")
    }
}
