package com.hcqdrive

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class HcqDriveApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "HcqDrive 服务",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "HcqDrive HTTP 服务运行时常驻通知"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val SERVICE_CHANNEL_ID = "hcqdrive_service"

        @Volatile
        private var appContextRef: Context? = null

        var appContext: Context
            get() = appContextRef
                ?: throw IllegalStateException("HcqDriveApp not yet created")
            internal set(value) {
                appContextRef = value
            }
    }
}
