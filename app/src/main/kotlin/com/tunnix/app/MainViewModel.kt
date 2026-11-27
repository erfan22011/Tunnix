package com.tunnix.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tunnix.core.models.AppLanguage
import com.tunnix.core.models.DownloadSettingsConfig
import com.tunnix.core.models.ServerConfig
import com.tunnix.core.models.ThemeMode
import com.tunnix.core.models.TrafficStats
import com.tunnix.core.models.VpnState
import com.tunnix.modifier.CentralConfigRepository
import com.tunnix.modifier.XhttpConfigModifier
import com.tunnix.parser.SubscriptionFetcher
import com.tunnix.settings.AppSettingsDataStore
import com.tunnix.traffic.TrafficMonitorRepository
import com.tunnix.vpn.VpnTunnelManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val language: AppLanguage = AppLanguage.PERSIAN,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val vpnState: VpnState = VpnState.Disconnected,
    val ipv6Protected: Boolean? = null,
    val trafficStats: TrafficStats = TrafficStats(),
    val servers: List<ServerConfig> = emptyList(),
    val selectedServer: ServerConfig? = null,
    val downloadSettings: DownloadSettingsConfig = DownloadSettingsConfig.DEFAULT,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasSubscription: Boolean = false,
    val isInitialized: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = AppSettingsDataStore(application)
    private val fetcher = SubscriptionFetcher()
    private var rawServers: List<ServerConfig> = emptyList()
    private var currentSubscriptionUrl: String = ""
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNetworkRefreshTime = 0L
    private var fetchJob: Job? = null
    private var hasDoneInitialRefresh: Boolean = false

    companion object {
        private const val NETWORK_REFRESH_DEBOUNCE_MS = 60_000L
    }

    private val cachedTheme = AppSettingsDataStore.getCachedThemeMode(application)
    private val cachedLang = AppSettingsDataStore.getCachedLanguage(application)
    private val cachedHasSub = AppSettingsDataStore.getCachedHasSubscription(application)
    private val _uiState = MutableStateFlow(
        UiState(
            themeMode = cachedTheme,
            language = cachedLang,
            hasSubscription = cachedHasSub,
            isInitialized = true
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeDataStore()
        observeVpnState()
        observeTrafficStats()
        observeNetworkConnectivity()
    }

    private fun isOnline(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun observeNetworkConnectivity() {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Skip network refresh if VPN is already connecting or connected
                val vState = _uiState.value.vpnState
                if (vState is VpnState.Connected || vState is VpnState.Connecting) return

                val now = System.currentTimeMillis()
                if (now - lastNetworkRefreshTime < NETWORK_REFRESH_DEBOUNCE_MS) return
                lastNetworkRefreshTime = now

                val subUrl = currentSubscriptionUrl
                if (subUrl.isNotBlank()) {
                    refreshSubscription(subUrl, _uiState.value.selectedServer?.id, isSilent = _uiState.value.servers.isNotEmpty())
                }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(networkCallback!!)
            } else {
                val req = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(req, networkCallback!!)
            }
        } catch (_: Exception) {}
    }

    private fun observeDataStore() {
        viewModelScope.launch {
            dataStore.preferencesFlow.collect { prefs ->
                currentSubscriptionUrl = prefs.subscriptionUrl
                CentralConfigRepository.updateConfig(prefs.downloadSettings)

                // 1. Immediately hydrate memory with cached servers if rawServers is empty
                if (rawServers.isEmpty() && prefs.cachedServers.isNotEmpty()) {
                    rawServers = prefs.cachedServers
                }

                val baseList = (if (rawServers.isNotEmpty()) rawServers else prefs.cachedServers).distinctBy { it.id }
                val updatedServers = if (baseList.isNotEmpty()) {
                    XhttpConfigModifier.modifyAll(baseList, prefs.downloadSettings).distinctBy { it.id }
                } else {
                    _uiState.value.servers
                }

                // If user has already made a selection in memory, respect it!
                val currentSelectedId = _uiState.value.selectedServer?.id ?: prefs.selectedServerId
                val updatedSelected = updatedServers.firstOrNull { it.id == currentSelectedId }
                    ?: updatedServers.firstOrNull()

                _uiState.value = _uiState.value.copy(
                    language = prefs.language,
                    themeMode = prefs.themeMode,
                    downloadSettings = prefs.downloadSettings,
                    servers = updatedServers,
                    selectedServer = updatedSelected,
                    hasSubscription = prefs.subscriptionUrl.isNotBlank(),
                    isInitialized = true
                )

                // 2. Fetch or refresh from network ONLY ONCE on launch (prevents infinite loop & selection overriding)
                if (prefs.subscriptionUrl.isNotBlank() && !hasDoneInitialRefresh) {
                    hasDoneInitialRefresh = true
                    if (isOnline()) {
                        refreshSubscription(prefs.subscriptionUrl, prefs.selectedServerId, isSilent = updatedServers.isNotEmpty())
                    }
                }
            }
        }
    }

    private fun observeVpnState() {
        viewModelScope.launch {
            VpnTunnelManager.vpnState.collect { state ->
                _uiState.value = _uiState.value.copy(vpnState = state)
                when (state) {
                    is VpnState.Connected -> {
                        TrafficMonitorRepository.startMonitoring(viewModelScope, state.connectedAtMillis)
                    }
                    is VpnState.Disconnected, is VpnState.Error -> {
                        TrafficMonitorRepository.stopMonitoring()
                    }
                    else -> {}
                }
            }
        }
        viewModelScope.launch {
            VpnTunnelManager.isIpv6Supported.collect { supported ->
                _uiState.value = _uiState.value.copy(ipv6Protected = supported)
            }
        }
    }

    private fun observeTrafficStats() {
        viewModelScope.launch {
            TrafficMonitorRepository.trafficStats.collect { stats ->
                _uiState.value = _uiState.value.copy(trafficStats = stats)
            }
        }
    }

    fun loadSubscription(urlOrToken: String, preferredServerId: String? = null) {
        refreshSubscription(urlOrToken, preferredServerId, isSilent = false)
    }

    private fun refreshSubscription(urlOrToken: String, preferredServerId: String? = null, isSilent: Boolean = false) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val hasExistingServers = _uiState.value.servers.isNotEmpty()
            // Only show full-screen loading spinner if there are no existing servers to show and not silent
            if (!hasExistingServers && !isSilent) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }

            val result = fetcher.fetch(urlOrToken)
            if (result.isSuccess) {
                val fetchedServers = result.getOrThrow().distinctBy { it.id }
                if (fetchedServers.isNotEmpty()) {
                    rawServers = fetchedServers
                    // Persist valid server list into local cache securely
                    var storageError: String? = null
                    try {
                        dataStore.saveCachedServers(fetchedServers)
                    } catch (_: SecurityException) {
                        storageError = "اطلاعات امن ذخیره نشد؛ بعد از بستن برنامه باید دوباره لینک را وارد کنید."
                    }

                    val currentConfig = _uiState.value.downloadSettings
                    val modifiedServers = XhttpConfigModifier.modifyAll(fetchedServers, currentConfig).distinctBy { it.id }

                    // PRESERVE CURRENT USER SELECTION:
                    // If the user selected a server, keep that selection active!
                    val currentlySelectedId = _uiState.value.selectedServer?.id ?: preferredServerId
                    val selected = modifiedServers.firstOrNull { it.id == currentlySelectedId }
                        ?: modifiedServers.firstOrNull()

                    try {
                        dataStore.saveSubscriptionUrl(urlOrToken)
                    } catch (_: SecurityException) {
                        storageError = "اطلاعات امن ذخیره نشد؛ بعد از بستن برنامه باید دوباره لینک را وارد کنید."
                    }

                    _uiState.value = _uiState.value.copy(
                        servers = modifiedServers,
                        selectedServer = selected,
                        isLoading = false,
                        errorMessage = storageError,
                        hasSubscription = true
                    )
                    selected?.let { dataStore.saveSelectedServerId(it.id) }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = if (!hasExistingServers && !isSilent) "سابسکریپشن خالی است یا سروری یافت نشد" else null
                    )
                }
            } else {
                val rawMsg = result.exceptionOrNull()?.message
                val errorMsg = sanitizeErrorMessage(rawMsg)
                // RESILIENT: Never wipe out existing servers on network failure!
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (!hasExistingServers && !isSilent) errorMsg else null
                )
            }
        }
    }

    private fun sanitizeErrorMessage(raw: String?): String {
        if (raw.isNullOrBlank()) return "خطا در برقراری ارتباط با سرور. لطفاً مجدداً تلاش نمایید."
        var msg = raw
        // Strip full URLs (http:// or https://...)
        msg = msg.replace(Regex("https?://[^\\s)\\]]+"), "")
        // Strip IP addresses with or without port
        msg = msg.replace(Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(?::\\d+)?\\b"), "")
        // Strip trailing or isolated null, redundant colons, brackets
        msg = msg.replace(": null", "").replace("null", "").replace("()", "")
        msg = msg.trim().trim(':', '-', ' ')
        if (msg.isBlank()) {
            return "خطا در برقراری ارتباط با سرور سابسکریپشن. لطفاً اتصال اینترنت خود را بررسی نمایید."
        }
        return msg
    }

    fun selectServer(server: ServerConfig) {
        _uiState.value = _uiState.value.copy(selectedServer = server)
        viewModelScope.launch {
            dataStore.saveSelectedServerId(server.id)
            if (_uiState.value.vpnState is VpnState.Connected) {
                val currentConfig = _uiState.value.downloadSettings
                val preparedServer = if (XhttpConfigModifier.isEligible(server, currentConfig)) {
                    XhttpConfigModifier.modify(server, currentConfig)
                } else {
                    server
                }
                VpnTunnelManager.startVpn(getApplication(), preparedServer)
            }
        }
    }

    /**
     * Checks if VPN permission is needed. Returns Intent if needed, or null if already granted.
     */
    fun checkVpnPermission(context: Context): Intent? {
        return VpnService.prepare(context)
    }

    fun toggleConnection(context: Context) {
        val currentState = _uiState.value.vpnState
        val server = _uiState.value.selectedServer

        when (currentState) {
            is VpnState.Disconnected, is VpnState.Error -> {
                if (server != null) {
                    val currentConfig = _uiState.value.downloadSettings
                    CentralConfigRepository.updateConfig(currentConfig)
                    val preparedServer = if (XhttpConfigModifier.isEligible(server, currentConfig)) {
                        XhttpConfigModifier.modify(server, currentConfig)
                    } else {
                        server
                    }
                    VpnTunnelManager.startVpn(context, preparedServer)
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = "هیچ سروری انتخاب نشده است")
                }
            }
            is VpnState.Connected, is VpnState.Connecting, is VpnState.Reconnecting -> {
                VpnTunnelManager.stopVpn(context)
            }
        }
    }

    fun changeLanguage(language: AppLanguage) {
        viewModelScope.launch {
            dataStore.saveLanguage(language)
        }
    }

    fun changeTheme(themeMode: ThemeMode) {
        viewModelScope.launch {
            dataStore.saveThemeMode(themeMode)
        }
    }

    fun saveDownloadSettings(config: DownloadSettingsConfig) {
        viewModelScope.launch {
            dataStore.saveDownloadSettings(config)
            CentralConfigRepository.updateConfig(config)
            // Re-apply modification to servers list starting from raw servers if available
            val baseServers = if (rawServers.isNotEmpty()) rawServers else _uiState.value.servers
            val updatedServers = XhttpConfigModifier.modifyAll(baseServers, config)
            val updatedSelected = updatedServers.firstOrNull { it.id == _uiState.value.selectedServer?.id }
                ?: updatedServers.firstOrNull()

            _uiState.value = _uiState.value.copy(
                servers = updatedServers,
                selectedServer = updatedSelected,
                downloadSettings = config
            )
        }
    }

    fun clearSubscription() {
        fetchJob?.cancel()
        hasDoneInitialRefresh = false
        viewModelScope.launch {
            dataStore.clearSubscription()
            VpnTunnelManager.stopVpn(getApplication())
            rawServers = emptyList()
            _uiState.value = _uiState.value.copy(
                servers = emptyList(),
                selectedServer = null,
                hasSubscription = false,
                errorMessage = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            networkCallback?.let { cm?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        networkCallback = null
    }
}
