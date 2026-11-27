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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TunnixVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val serviceMutex = Mutex()

    private var tunDescriptor: ParcelFileDescriptor? = null
    private var activeServer: ServerConfig? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var transitionJob: Job? = null
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

    override fun onRevoke() {
        super.onRevoke()
        // Operating system revoked VPN permission or another VPN established
        VpnNotificationHelper.showDisconnectedNotification(
            this,
            "دسترسی VPN توسط سیستم یا برنامه دیگر لغو شد / VPN permission revoked"
        )
        stopTunnel(targetError = null)
    }

    private fun startTunnel(server: ServerConfig) {
        // Cancel any pending transition to prevent start/stop interleaving races
        transitionJob?.cancel()
        activeServer = server
        VpnTunnelManager.updateState(VpnState.Connecting)

        // 1. Start Foreground Notification immediately
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

        transitionJob = serviceScope.launch {
            serviceMutex.withLock {
                try {
                    // 2. Clean up any previous tunnel state to prevent resource leaks
                    closeTunSilently()
                    withContext(Dispatchers.IO) {
                        XrayCoreController.stop()
                    }

                    // 3. Register native core crash / shutdown listener
                    XrayCoreController.onUnexpectedShutdown = {
                        serviceScope.launch(Dispatchers.Main) {
                            if (tunDescriptor != null || VpnTunnelManager.vpnState.value is VpnState.Connected) {
                                handleError("هسته پروکسی به صورت غیرمنتظره متوقف شد / Proxy core terminated unexpectedly")
                            }
                        }
                    }

                    // 4. Establish virtual TUN interface
                    tunDescriptor = TunInterfaceBuilder.establishTun(this@TunnixVpnService, server)
                    if (tunDescriptor == null) {
                        handleError("سیستم‌عامل اجازه ایجاد اینترفیس VPN را نداد / System denied TUN establishment")
                        return@withLock
                    }
                    val tunFd = tunDescriptor!!.fd

                    // 5. Initialize Xray environment & generate JSON config on IO dispatcher
                    val configJson = withContext(Dispatchers.IO) {
                        XrayCoreController.initEnvironment(filesDir)
                        val dlConfig = CentralConfigRepository.getConfig()
                        XrayConfigGenerator.generate(server, customDownloadSettings = dlConfig)
                    }

                    // 6. Start native Xray-core loop bound directly to TUN fd
                    val startResult = XrayCoreController.start(configJson, tunFd)
                    if (startResult.isFailure) {
                        val err = startResult.exceptionOrNull()?.message ?: "خطا در استارت هسته پروکسی"
                        handleError(err)
                        return@withLock
                    }

                    // 7. Register network listener to monitor connection changes
                    registerNetworkCallback()

                    // 8. Update connected state
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
                } finally {
                    // If coroutine was cancelled before completing successfully, clean up opened resources
                    if (!isActive) {
                        withContext(NonCancellable) {
                            closeTunSilently()
                            withContext(Dispatchers.IO) {
                                XrayCoreController.stop()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stopTunnel(targetError: VpnState.Error? = null, forStartId: Int? = null) {
        transitionJob?.cancel()
        unregisterNetworkCallback()
        XrayCoreController.onUnexpectedShutdown = null

        val stopId = forStartId ?: lastStartId

        transitionJob = serviceScope.launch {
            withContext(NonCancellable) {
                serviceMutex.withLock {
                    try {
                        closeTunSilently()
                        withContext(Dispatchers.IO) {
                            XrayCoreController.stop()
                        }
                    } finally {
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

                        // Only stop service if no new startCommand has superseded this startId
                        if (stopId == lastStartId) {
                            stopSelf(stopId)
                        }
                    }
                }
            }
        }
    }

    private fun closeTunSilently() {
        try {
            tunDescriptor?.close()
        } catch (_: Exception) {}
        tunDescriptor = null
    }

    private fun handleError(message: String, cause: Throwable? = null) {
        val wasConnected = tunDescriptor != null || VpnTunnelManager.vpnState.value is VpnState.Connected
        val ipOrDomainRegex = "(?:\\d{1,3}(?:\\.\\d{1,3}){3}|(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,})(?::\\d+)?"
        val sanitized = message.replace(Regex(ipOrDomainRegex), "[server]")
        val errState = VpnState.Error(sanitized, cause)
        if (wasConnected) {
            VpnNotificationHelper.showDisconnectedNotification(this, sanitized)
        }
        stopTunnel(targetError = errState)
    }

    private fun registerNetworkCallback() {
        unregisterNetworkCallback()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                try {
                    setUnderlyingNetworks(arrayOf(network))
                } catch (_: Exception) {}
                val current = activeServer
                if (current != null && VpnTunnelManager.vpnState.value is VpnState.Reconnecting) {
                    VpnTunnelManager.updateState(VpnState.Connected(current.displayName))
                }
            }

            override fun onLost(network: Network) {
                try {
                    setUnderlyingNetworks(null)
                } catch (_: Exception) {}
                VpnTunnelManager.updateState(VpnState.Reconnecting)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    try {
                        setUnderlyingNetworks(arrayOf(network))
                    } catch (_: Exception) {}
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

    override fun onDestroy() {
        super.onDestroy()
        val wasConnected = tunDescriptor != null || VpnTunnelManager.vpnState.value is VpnState.Connected
        val isExplicitStop = VpnTunnelManager.vpnState.value is VpnState.Disconnected
        if (wasConnected && !isExplicitStop) {
            VpnNotificationHelper.showDisconnectedNotification(
                this,
                "اتصال تونل امن متوقف شد / Secure tunnel disconnected unexpectedly"
            )
        }
        transitionJob?.cancel()
        unregisterNetworkCallback()
        XrayCoreController.onUnexpectedShutdown = null
        closeTunSilently()
        try {
            XrayCoreController.stopSync()
        } catch (_: Exception) {}
        serviceScope.cancel()
    }
}
