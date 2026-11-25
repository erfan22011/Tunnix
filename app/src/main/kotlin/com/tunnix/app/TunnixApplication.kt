package com.tunnix.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class TunnixApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vpnChannel = NotificationChannel(
                VPN_NOTIFICATION_CHANNEL_ID,
                "Tunnix VPN Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live VPN connection status and traffic stats"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(vpnChannel)
        }
    }

    companion object {
        const val VPN_NOTIFICATION_CHANNEL_ID = "tunnix_vpn_channel"
        lateinit var instance: TunnixApplication
            private set
    }
}
