package com.hcqdrive.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hcqdrive.HcqDriveApp
import com.hcqdrive.MainActivity
import com.hcqdrive.app.R
import com.hcqdrive.auth.AuthService
import com.hcqdrive.server.HttpServer
import com.hcqdrive.service.mdns.MdnsService
import com.hcqdrive.ui.QrActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HcqDriveService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        startHttpServer()
        startNotificationRefresh()
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startNotificationRefresh() {
        serviceScope.launch {
            while (isActive) {
                delay(15_000L)
                runCatching {
                    val notification = buildNotification()
                    getSystemService(NotificationManager::class.java)
                        ?.notify(NOTIFICATION_ID, notification)
                }.onFailure { Log.w("HcqDrive", "Notification refresh failed: ${it.message}") }
            }
        }
    }

    private fun buildNotification(): Notification {
        val ipAddress = getWifiIpAddress()
        val serviceAddress = "http://$ipAddress:${DEFAULT_PORT}"
        val localAddress = "http://${MDNS_HOSTNAME}.local:${DEFAULT_PORT}"
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val qrIntent = Intent(this, QrActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val qrPi = PendingIntent.getActivity(
            this, 1, qrIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val code = AuthService.currentCodeAndExpiry()?.first ?: "——"
        val bigText = buildString {
            append("配对码 ")
            append(code)
            append("\n")
            append(localAddress)
            if (ipAddress != "0.0.0.0") {
                append("\n")
                append(serviceAddress)
            }
        }
        val text = "配对码 $code · $localAddress"
        val connections = AuthService.pairedCount()
        val contentText = if (connections > 0) {
            "已连接 $connections 台 · $localAddress"
        } else {
            localAddress
        }
        val builder = NotificationCompat.Builder(this, HcqDriveApp.SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSubText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText).setBigContentTitle(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPi)
            .addAction(0, "查看二维码", qrPi)
        return builder.build()
    }

    private fun startHttpServer() {
        serviceScope.launch {
            HttpServer.start(port = DEFAULT_PORT, host = "0.0.0.0")
            MdnsService.register(this@HcqDriveService, DEFAULT_PORT, MDNS_SERVICE_NAME)
        }
    }

    private fun getWifiIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
        if (ipInt == 0) return "0.0.0.0"
        return "%d.%d.%d.%d".format(
            ipInt and 0xFF,
            (ipInt shr 8) and 0xFF,
            (ipInt shr 16) and 0xFF,
            (ipInt shr 24) and 0xFF,
        )
    }

    override fun onDestroy() {
        MdnsService.unregister()
        HttpServer.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val DEFAULT_PORT = 8080
        // Lowercased `.local` hostname advertised by NsdManager. Browsers that resolve
        // mDNS hit `http://hcqdrive.local:8080`. Kept in sync with `MDNS_SERVICE_NAME`.
        const val MDNS_HOSTNAME = "hcqdrive"
        // Display name registered as the mDNS service. Bonjour conventional mixed-case
        // form of the same identity; appears in service browsers like `dns-sd -B`.
        const val MDNS_SERVICE_NAME = "HcqDrive"
        private const val NOTIFICATION_ID = 1001
    }
}
