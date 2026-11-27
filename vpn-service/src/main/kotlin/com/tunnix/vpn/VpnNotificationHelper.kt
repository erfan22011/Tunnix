package com.tunnix.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tunnix.core.models.ServerConfig
import com.tunnix.core.models.VpnState

object VpnNotificationHelper {

    const val CHANNEL_ID = "tunnix_vpn_channel"
    const val NOTIFICATION_ID = 2026

    fun createNotification(
        context: Context,
        server: ServerConfig?,
        vpnState: VpnState
    ): Notification {
        ensureNotificationChannel(context)

        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setPackage(context.packageName)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(context, TunnixVpnService::class.java).apply {
            action = VpnTunnelManager.ACTION_STOP
        }
        val disconnectPendingIntent = PendingIntent.getService(
            context,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (vpnState) {
            is VpnState.Connected -> "Tunnix · Connected / متصل شد"
            is VpnState.Connecting -> "Tunnix · Connecting... / در حال اتصال"
            is VpnState.Reconnecting -> "Tunnix · Reconnecting... / در حال اتصال مجدد"
            is VpnState.Error -> "Tunnix · Connection Error / خطای اتصال"
            is VpnState.Disconnected -> "Tunnix · Disconnected / قطع شده"
        }

        val text = server?.displayName ?: "Freedom, One Tap Away"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(vpnState is VpnState.Connected || vpnState is VpnState.Connecting)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (vpnState is VpnState.Connected || vpnState is VpnState.Connecting) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect / قطع اتصال",
                disconnectPendingIntent
            )
        }

        return builder.build()
    }

    fun showDisconnectedNotification(context: Context, reason: String? = null) {
        try {
            ensureNotificationChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Tunnix · Disconnected / اتصال VPN قطع شد")
                .setContentText(reason ?: "اتصال تونل امن متوقف شد / Secure tunnel disconnected")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            nm.notify(NOTIFICATION_ID + 1, builder.build())
        } catch (_: SecurityException) {
            // Safe guard against missing POST_NOTIFICATIONS permission on Android 13+
        } catch (_: Exception) {}
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Tunnix VPN Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Active VPN connection and tunnel state"
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}
