package com.example.savegpsdata

import android.app.Service
import android.content.Intent
import android.os.*
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class GpsLoggingService : Service() {
    private lateinit var locationHandler: LocationHandler
    private lateinit var gnssHandler: GnssHandler
    private lateinit var notificationHandler: NotificationHandler
    private lateinit var debugLogger: DebugLogger
    private lateinit var wakeLock: PowerManager.WakeLock
    private val notificationHandlerLoop = Handler(Looper.getMainLooper())

    private lateinit var batteryLogger: BatteryLogger // ✅ 追加
    private val batteryLogLoop = Handler(Looper.getMainLooper()) // ✅ 追加

    override fun onCreate() {
        debugLogger = DebugLogger()
        gnssHandler = GnssHandler(this, debugLogger)
        notificationHandler = NotificationHandler(this, debugLogger)
        locationHandler = LocationHandler(this, debugLogger)

        val timestampForFile = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = Environment.getExternalStorageDirectory().resolve("Download")
        dir.mkdirs()
        locationHandler.logFile = File(dir, "gps1_log_$timestampForFile.log")
        debugLogger.debugLogFile = File(dir, "gps_debug_$timestampForFile.log")

        batteryLogger = BatteryLogger(this) // ✅ 追加
        val batteryLogFileName = "battery_log_$timestampForFile.log" // ✅ 追加
        batteryLogger.logFile = dir.resolve(batteryLogFileName) // ✅ 追加

        batteryLogLoop.post(object : Runnable { // ✅ 追加
            override fun run() {
                batteryLogger.logBatteryLevel()
                batteryLogLoop.postDelayed(this, 600_000)
            }
        })

        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GpsLogger::WakeLock")
        wakeLock.acquire()

        notificationHandler.createNotificationChannel()
        startForeground(NotificationHandler.NOTIFICATION_ID, notificationHandler.buildForegroundNotification("位置情報を記録中です"))

        if (PermissionChecker.hasLocationPermission(this)) {
            locationHandler.startLocationUpdates()
            gnssHandler.register()
            debugLogger.logDebug("✅ WakeLock取得・サービス開始（GPSログ: ${locationHandler.logFile.name}, デバッグログ: ${debugLogger.debugLogFile.name}）")
        } else {
            debugLogger.logDebug("❌ 権限がありません。位置更新を登録できません")
        }

        notificationHandlerLoop.post(object : Runnable {
            override fun run() {
                if (!notificationHandler.isAppInForeground()) {
                    notificationHandler.maybeUpdateNotification()
                }
                if (SharedState.gpsLoggingEnabled) { // ✅ 追加
                    locationHandler.monitorLocationGap()
                }
                notificationHandlerLoop.postDelayed(this, 60_000)
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NotificationHandler.NOTIFICATION_ID, notificationHandler.buildForegroundNotification("位置情報を記録中です"))
        debugLogger.logDebug("🚀 onStartCommand 呼び出し")

        val lowPower = intent?.getBooleanExtra("low_power_mode", false) ?: false
        locationHandler.setLowPowerMode(lowPower)
        SharedState.currentLowPowerMode = lowPower
        debugLogger.logDebug("⚙️ 節電モード: ${if (lowPower) "ON" else "OFF"}")

        val gpsLoggingEnabled = intent?.getBooleanExtra("gps_logging_enabled", true) ?: true
        if (gpsLoggingEnabled) {
            locationHandler.startLocationUpdates()
        } else {
            locationHandler.stopLocationUpdates()
        }
        SharedState.gpsLoggingEnabled = gpsLoggingEnabled
        debugLogger.logDebug("🚦 GPSログ取得: ${if (gpsLoggingEnabled) "ON" else "OFF"}")

        return START_STICKY
    }

    override fun onDestroy() {
        locationHandler.stopLocationUpdates()
        gnssHandler.unregister()
        if (wakeLock.isHeld) wakeLock.release()
        notificationHandlerLoop.removeCallbacksAndMessages(null)
        batteryLogLoop.removeCallbacksAndMessages(null) // ✅ 追加
        debugLogger.logDebug("🛑 WakeLock解放・サービス停止")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent(applicationContext, GpsLoggingService::class.java).apply {
            putExtra("low_power_mode", SharedState.currentLowPowerMode)
        }
        ContextCompat.startForegroundService(applicationContext, restartIntent)
        debugLogger.logDebug("🔄 サービス再起動（onTaskRemoved）")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
