package com.tunnix.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.ServerConfig
import com.tunnix.core.models.VpnState
import com.tunnix.modifier.CentralConfigRepository
import com.tunnix.xray.XrayConfigGenerator
import com.tunnix.xray.XrayCoreController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TunnixVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var tunDescriptor: ParcelFileDescriptor? = null
    private var activeServer: ServerConfig? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var tunnelJob: Job? = null
    private var lastStartId: Int = 0

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            VpnTunnelManager.ACTION_START -> {
                @Suppress("DEPRECATION")
                val server = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(VpnTunnelManager.EXTRA_SERVER, ServerConfig::class.java)
                } else {
                    intent.getSerializableExtra(VpnTunnelManager.EXTRA_SERVER) as? ServerConfig
                }

                if (server != null) {
                    startTunnel(server)
                } else {
                    // Prevent ForegroundServiceDidNotStartInTimeException on Android 8+
                    val fallbackConfig = ServerConfig(
                        id = "error_fallback",
                        name = "Error",
                        address = "0.0.0.0",
                        port = 0,
                        protocol = ProtocolType.VLESS
                    )
                    val notification = VpnNotificationHelper.createNotification(
                        this,
                        fallbackConfig,
                        VpnState.Error("اطلاعات سرور نامعتبر است / Invalid server config")
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            VpnNotificationHelper.NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        )
                    } else {
                        startForeground(VpnNotificationHelper.NOTIFICATION_ID, notification)
                    }

                    val errState = VpnState.Error("اطلاعات سرور نامعتبر است / Invalid server config")
                    stopTunnel(targetError = errState, forStartId = startId)
                }
            }
            VpnTunnelManager.ACTION_STOP -> {
                stopTunnel()
            }
        }

        return START_NOT_STICKY
    }

    private fun startTunnel(server: ServerConfig) {
        // Cancel any pending start coroutine to prevent concurrent startup races
        tunnelJob?.cancel()
        activeServer = server
        VpnTunnelManager.updateState(VpnState.Connecting)

        // 1. Start Foreground Notification (Android 14+ specialUse requirement)
        val notification = VpnNotificationHelper.createNotification(this, server, VpnState.Connecting)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                VpnNotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(VpnNotificationHelper.NOTIFICATION_ID, notification)
        }

        tunnelJob = serviceScope.launch {
            try {
                // 2. Establish virtual TUN interface first to obtain descriptor
                tunDescriptor = TunInterfaceBuilder.establishTun(this@TunnixVpnService, server)
                if (tunDescriptor == null) {
                    handleError("سیستم‌عامل اجازه ایجاد اینترفیس VPN را نداد / System denied TUN establishment")
                    return@launch
                }
                val tunFd = tunDescriptor!!.fd

                // 3. Initialize Xray environment, assets & generate JSON config on IO dispatcher
                val configJson = withContext(Dispatchers.IO) {
                    copyAssetsIfNeeded()
                    XrayCoreController.initEnvironment(filesDir)
                    val dlConfig = CentralConfigRepository.getConfig()
                    XrayConfigGenerator.generate(server, customDownloadSettings = dlConfig)
                }

                // 4. Start native Xray-core loop bound directly to TUN fd
                val startResult = XrayCoreController.start(configJson, tunFd)
                if (startResult.isFailure) {
                    val err = startResult.exceptionOrNull()?.message ?: "خطا در استارت هسته پروکسی"
                    handleError(err)
                    return@launch
                }

                // 5. Register network listener to monitor connection changes
                registerNetworkCallback()

                // 6. Update connected state
                VpnTunnelManager.updateState(VpnState.Connected(server.displayName))
                val connectedNotification = VpnNotificationHelper.createNotification(
                    this@TunnixVpnService,
                    server,
                    VpnState.Connected(server.displayName)
                )
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.notify(VpnNotificationHelper.NOTIFICATION_ID, connectedNotification)

            } catch (e: Exception) {
                handleError(e.message ?: "خطای پیش‌بینی نشده در برقراری تونل / Unexpected tunnel failure", e)
            }
        }
    }

    private fun stopTunnel(targetError: VpnState.Error? = null, forStartId: Int? = null) {
        tunnelJob?.cancel()
        unregisterNetworkCallback()

        try {
            tunDescriptor?.close()
            tunDescriptor = null
        } catch (_: Exception) {}

        val stopId = forStartId ?: lastStartId

        serviceScope.launch {
            withContext(Dispatchers.IO) {
                XrayCoreController.stop()
            }

            // DO NOT overwrite error state with Disconnected!
            if (targetError != null) {
                VpnTunnelManager.updateState(targetError)
            } else {
                VpnTunnelManager.updateState(VpnState.Disconnected)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }

            // Only stop if no new startCommand has superseded this startId
            if (stopId == lastStartId) {
                stopSelf(stopId)
            }
        }
    }

    private fun handleError(message: String, cause: Throwable? = null) {
        val ipOrDomainRegex = "(?:\\d{1,3}(?:\\.\\d{1,3}){3}|(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,})(?::\\d+)?"
        val sanitized = message.replace(Regex(ipOrDomainRegex), "[server]")
        val errState = VpnState.Error(sanitized, cause)
        stopTunnel(targetError = errState)
    }

    private fun registerNetworkCallback() {
        unregisterNetworkCallback()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                VpnTunnelManager.updateState(VpnState.Reconnecting)
            }

            override fun onAvailable(network: Network) {
                val current = activeServer
                if (current != null && VpnTunnelManager.vpnState.value is VpnState.Reconnecting) {
                    VpnTunnelManager.updateState(VpnState.Connected(current.displayName))
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Tracks only the active default network: seamlessly handles Wi-Fi <-> Cellular switches
                connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
            } else {
                val builder = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                connectivityManager?.registerNetworkCallback(builder.build(), networkCallback!!)
            }
        } catch (_: Exception) {}
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        networkCallback = null
    }

    private suspend fun copyAssetsIfNeeded() = withContext(Dispatchers.IO) {
        val assetDir = java.io.File(filesDir, "xray_assets")
        if (!assetDir.exists()) assetDir.mkdirs()
        listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat").forEach { filename ->
            val dest = java.io.File(assetDir, filename)
            if (!dest.exists() || dest.length() == 0L) {
                try {
                    assets.open(filename).use { input ->
                        dest.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tunnelJob?.cancel()
        unregisterNetworkCallback()
        try {
            tunDescriptor?.close()
        } catch (_: Exception) {}
        serviceScope.cancel()
    }
}
