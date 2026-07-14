package com.webautomation

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class WebAutomationApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "AutomationServiceChannel"
        const val NOTIFICATION_CHANNEL_NAME = "Background Automation"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for background web automation"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
