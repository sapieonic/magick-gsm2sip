package com.magick.gsm2sip.gateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleService
import com.magick.gsm2sip.MainActivity
import com.magick.gsm2sip.R
import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogTag
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the gateway process alive and holds the WiFi +
 * wake locks needed to keep SIP registration and RTP flowing while the screen
 * is off. Delegates all SIP/GSM logic to [GatewayController]; this class is just
 * the Android lifecycle host + persistent notification.
 */
class GatewayService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        GatewayController.init(this)
        createChannel()
        acquireLocks()

        // Keep the notification text in sync with gateway state.
        lifecycleScope.launch {
            GatewayController.state.collect { state ->
                notificationManager().notify(NOTIF_ID, buildNotification(state.label))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                GatewayController.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundCompat()
                GatewayController.start()
            }
        }
        // Restart if the OS kills us mid-call so registration recovers.
        return START_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification(GatewayController.state.value.label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, GatewayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GSM ↔ SIP Gateway")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_gateway)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(open)
            .addAction(R.drawable.ic_stop, "Stop", stop)
            .build()
    }

    private fun acquireLocks() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gsm2sip:wake").apply {
            setReferenceCounted(false)
            acquire(WAKELOCK_TIMEOUT_MS)
        }
        val wifi = getSystemService(WifiManager::class.java)
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "gsm2sip:wifi").apply {
            setReferenceCounted(false)
            acquire()
        }
        GatewayLog.d(LogTag.SYSTEM, "wake + wifi locks acquired")
    }

    private fun releaseLocks() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        wakeLock = null
        wifiLock = null
        GatewayLog.d(LogTag.SYSTEM, "locks released")
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Gateway status", NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Ongoing GSM-to-SIP gateway status" }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "gateway_status"
        private const val NOTIF_ID = 1001
        private const val ACTION_STOP = "com.magick.gsm2sip.STOP"
        private const val WAKELOCK_TIMEOUT_MS = 12L * 60 * 60 * 1000  // 12h safety cap

        fun start(context: Context) {
            val intent = Intent(context, GatewayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, GatewayService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
