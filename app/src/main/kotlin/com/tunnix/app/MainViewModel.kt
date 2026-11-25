package com.tunnix.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val language: AppLanguage = AppLanguage.PERSIAN,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val vpnState: VpnState = VpnState.Disconnected,
    val trafficStats: TrafficStats = TrafficStats(),
    val servers: List<ServerConfig> = emptyList(),
    val selectedServer: ServerConfig? = null,
    val downloadSettings: DownloadSettingsConfig = DownloadSettingsConfig.DEFAULT,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasSubscription: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = AppSettingsDataStore(application)
    private val fetcher = SubscriptionFetcher()
    private var rawServers: List<ServerConfig> = emptyList()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeDataStore()
        observeVpnState()
        observeTrafficStats()
    }

    private fun observeDataStore() {
        viewModelScope.launch {
            dataStore.preferencesFlow.collect { prefs ->
                CentralConfigRepository.updateConfig(prefs.downloadSettings)

                val baseList = if (rawServers.isNotEmpty()) rawServers else _uiState.value.servers
                val updatedServers = if (baseList.isNotEmpty()) {
                    XhttpConfigModifier.modifyAll(baseList, prefs.downloadSettings)
                } else {
                    _uiState.value.servers
                }

                val currentSelectedId = _uiState.value.selectedServer?.id ?: prefs.selectedServerId
                val updatedSelected = updatedServers.firstOrNull { it.id == currentSelectedId }
                    ?: updatedServers.firstOrNull()

                _uiState.value = _uiState.value.copy(
                    language = prefs.language,
                    themeMode = prefs.themeMode,
                    downloadSettings = prefs.downloadSettings,
                    servers = updatedServers,
                    selectedServer = updatedSelected,
                    hasSubscription = prefs.subscriptionUrl.isNotBlank()
                )

                // If subscription URL exists and servers empty, fetch them
                if (prefs.subscriptionUrl.isNotBlank() && _uiState.value.servers.isEmpty()) {
                    loadSubscription(prefs.subscriptionUrl, prefs.selectedServerId)
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
    }

    private fun observeTrafficStats() {
        viewModelScope.launch {
            TrafficMonitorRepository.trafficStats.collect { stats ->
                _uiState.value = _uiState.value.copy(trafficStats = stats)
            }
        }
    }

    fun loadSubscription(urlOrToken: String, preferredServerId: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = fetcher.fetch(urlOrToken)
            if (result.isSuccess) {
                val fetchedServers = result.getOrThrow()
                rawServers = fetchedServers
                // Apply intelligent XHTTP modification with current downloadSettings
                val currentConfig = _uiState.value.downloadSettings
                val modifiedServers = XhttpConfigModifier.modifyAll(fetchedServers, currentConfig)

                val selected = if (!preferredServerId.isNullOrBlank()) {
                    modifiedServers.firstOrNull { it.id == preferredServerId } ?: modifiedServers.firstOrNull()
                } else {
                    modifiedServers.firstOrNull()
                }

                _uiState.value = _uiState.value.copy(
                    servers = modifiedServers,
                    selectedServer = selected,
                    isLoading = false,
                    hasSubscription = true
                )
                dataStore.saveSubscriptionUrl(urlOrToken)
                selected?.let { dataStore.saveSelectedServerId(it.id) }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "خطا در دریافت سرورها"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = errorMsg
                )
            }
        }
    }

    fun selectServer(server: ServerConfig) {
        _uiState.value = _uiState.value.copy(selectedServer = server)
        viewModelScope.launch {
            dataStore.saveSelectedServerId(server.id)
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
        viewModelScope.launch {
            dataStore.clearSubscription()
            VpnTunnelManager.stopVpn(getApplication())
            _uiState.value = _uiState.value.copy(
                servers = emptyList(),
                selectedServer = null,
                hasSubscription = false
            )
        }
    }
}
