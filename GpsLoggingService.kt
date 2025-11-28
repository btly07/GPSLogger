package com.example.savegpsdata

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class GpsLoggingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private var lastSavedTime: Long = 0L
    private var lastLocationTime: Long = System.currentTimeMillis()
    private lateinit var logFile: File
    private lateinit var debugLogFile: File
    private val notificationHandler = Handler(Looper.getMainLooper())
    private var lastNotificationText: String = ""

    companion object {
        var latestLocationText: String = "位置情報未取得"
        var latestSatelliteText: String = "衛星情報未取得"
        private const val CHANNEL_ID = "gps_logging_channel"
        private const val NOTIFICATION_ID = 1001
        private const val SAVE_INTERVAL_MS = 10_000L
        private const val LOCATION_GAP_THRESHOLD_MS = 10 * 60 * 1000L // 10分
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification("位置情報を記録中です")
        startForeground(NOTIFICATION_ID, notification)
        logDebug("🚀 onStartCommand 呼び出し")
        return START_STICKY
    }

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        5000L
    ).setMinUpdateIntervalMillis(3000L)
        .setMaxUpdateDelayMillis(10000L)
        .setWaitForAccurateLocation(false)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            lastLocationTime = System.currentTimeMillis()
            val location = result.lastLocation ?: return

            logDebug("📍 位置: ${location.latitude}, ${location.longitude}, 精度: ${location.accuracy}, Provider: ${location.provider}")

            val now = System.currentTimeMillis()
            if (now - lastSavedTime >= SAVE_INTERVAL_MS) {
                lastSavedTime = now
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(location.time))
                latestLocationText = """
                    時刻: $timestamp
                    緯度: ${location.latitude}
                    経度: ${location.longitude}
                    高度: ${location.altitude} m
                    精度: ${location.accuracy} m
                    速度: ${location.speed} m/s
                    方位: ${location.bearing}°
                    Provider: ${location.provider}
                """.trimIndent()
                saveLocationToFile(location)
            }
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val total = status.satelliteCount
            var used = 0
            for (i in 0 until total) {
                if (status.usedInFix(i)) used++
            }
            latestSatelliteText = "衛星数: $total（Fixに使用: $used）"
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(LocationManager::class.java)

        val timestampForFile = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "gps1_log_$timestampForFile.log"
        val debugFileName = "gps_debug_$timestampForFile.log"

        val dir = Environment.getExternalStorageDirectory().resolve("Download")
        dir.mkdirs()
        logFile = File(dir, fileName)
        debugLogFile = File(dir, debugFileName)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GpsLogger::WakeLock")
        wakeLock.acquire()

        createNotificationChannel()
        val notification = buildForegroundNotification("位置情報を記録中です")
        startForeground(NOTIFICATION_ID, notification)

        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
                locationManager.registerGnssStatusCallback(gnssCallback, null)
                logDebug("✅ 位置更新・衛星登録成功")
            } catch (e: SecurityException) {
                logDebug("❌ SecurityException: ${e.message}")
            }
        } else {
            logDebug("❌ 権限がありません。位置更新を登録できません")
        }

        logDebug("✅ WakeLock取得・サービス開始（GPSログ: ${logFile.name}, デバッグログ: ${debugLogFile.name}）")

        notificationHandler.post(object : Runnable {
            override fun run() {
                if (!isAppInForeground()) {
                    maybeUpdateNotification()
                }
                monitorLocationGap()
                notificationHandler.postDelayed(this, 60_000)
            }
        })
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        locationManager.unregisterGnssStatusCallback(gnssCallback)
        if (wakeLock.isHeld) wakeLock.release()
        notificationHandler.removeCallbacksAndMessages(null)
        logDebug("🛑 WakeLock解放・サービス停止")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent(applicationContext, GpsLoggingService::class.java)
        ContextCompat.startForegroundService(applicationContext, restartIntent)
        logDebug("🔄 サービス再起動（onTaskRemoved）")
        super.onTaskRemoved(rootIntent)
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = applicationContext.packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }

    private fun monitorLocationGap() {
        val now = System.currentTimeMillis()
        if (now - lastLocationTime > LOCATION_GAP_THRESHOLD_MS) {
            logDebug("⚠️ 位置更新が10分以上途絶 → 再登録を試行")
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
                logDebug("🔁 位置更新再登録完了")
                lastLocationTime = now
            } catch (e: Exception) {
                logDebug("❌ 位置更新再登録失敗: ${e.message}")
            }
        }
    }

    private fun maybeUpdateNotification() {
        val currentText = "位置情報を記録中です"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(currentText))
        lastNotificationText = currentText
        logDebug("🔁 通知再表示（静かに更新）")
    }

    private fun saveLocationToFile(location: Location) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(location.time))
        val line = "$timestamp,${location.latitude},${location.longitude},${location.altitude},${location.accuracy},${location.speed},${location.bearing},${location.provider},${location.isFromMockProvider}\n"
        try {
            FileOutputStream(logFile, true).use { it.write(line.toByteArray()) }
            logDebug("✅ 保存成功: ${logFile.absolutePath}")
        } catch (e: Exception) {
            logDebug("❌ 保存失敗: ${e.message}")
        }
    }

    private fun logDebug(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "$timestamp: $message"
        Log.d("GpsLoggingService", line)
        try {
            debugLogFile.appendText("$line\n")
        } catch (e: Exception) {
            Log.e("GpsLoggingService", "❌ デバッグログ保存失敗: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPS Logging",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "GPSログ記録中"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            val registeredChannel = manager.getNotificationChannel(CHANNEL_ID)
            logDebug("✅ 通知チャンネル登録完了: importance=${registeredChannel?.importance}")
        }
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Logger")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .build()
    }
}
