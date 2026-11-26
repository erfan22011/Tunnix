package com.tunnix.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tunnix.core.models.AppLanguage
import com.tunnix.core.models.DownloadSettingsConfig
import com.tunnix.core.models.ServerConfig
import com.tunnix.core.models.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tunnix_preferences")

data class AppPreferences(
    val subscriptionUrl: String = "",
    val selectedServerId: String = "",
    val language: AppLanguage = AppLanguage.PERSIAN,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val downloadSettings: DownloadSettingsConfig = DownloadSettingsConfig.DEFAULT,
    val cachedServers: List<ServerConfig> = emptyList(),
    val bypassIran: Boolean = true
)

class AppSettingsDataStore(private val context: Context) {

    private val gson = Gson()

    private object Keys {
        val SUBSCRIPTION_URL = stringPreferencesKey("key_sub_url")
        val SELECTED_SERVER_ID = stringPreferencesKey("key_selected_server_id")
        val LANGUAGE = stringPreferencesKey("key_language")
        val THEME_MODE = stringPreferencesKey("key_theme_mode")
        val DOWNLOAD_SETTINGS = stringPreferencesKey("key_download_settings")
        val CACHED_SERVERS = stringPreferencesKey("key_cached_servers")
        val BYPASS_IRAN = booleanPreferencesKey("key_bypass_iran")
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val subUrl = prefs[Keys.SUBSCRIPTION_URL] ?: ""
        val serverId = prefs[Keys.SELECTED_SERVER_ID] ?: ""
        val langCode = prefs[Keys.LANGUAGE] ?: AppLanguage.PERSIAN.code
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        val dlJson = prefs[Keys.DOWNLOAD_SETTINGS]

        val dlConfig = if (!dlJson.isNullOrBlank()) {
            try {
                val parsed = gson.fromJson(dlJson, DownloadSettingsConfig::class.java)
                if (parsed != null) {
                    val validTargetPort = parsed.effectiveTargetPort
                    parsed.copy(targetPort = validTargetPort)
                } else {
                    DownloadSettingsConfig.DEFAULT
                }
            } catch (_: Exception) {
                DownloadSettingsConfig.DEFAULT
            }
        } else {
            DownloadSettingsConfig.DEFAULT
        }

        val serversJson = prefs[Keys.CACHED_SERVERS]
        val cachedServers = if (!serversJson.isNullOrBlank()) {
            try {
                val listType = object : TypeToken<List<ServerConfig>>() {}.type
                gson.fromJson<List<ServerConfig>>(serversJson, listType) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val bypassIran = prefs[Keys.BYPASS_IRAN] ?: true

        AppPreferences(
            subscriptionUrl = subUrl,
            selectedServerId = serverId,
            language = AppLanguage.fromCode(langCode),
            themeMode = try { ThemeMode.valueOf(themeStr) } catch (_: Exception) { ThemeMode.DARK },
            downloadSettings = dlConfig,
            cachedServers = cachedServers,
            bypassIran = bypassIran
        )
    }

    suspend fun saveBypassIran(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BYPASS_IRAN] = enabled }
    }

    suspend fun saveSubscriptionUrl(url: String) {
        context.dataStore.edit { it[Keys.SUBSCRIPTION_URL] = url }
    }

    suspend fun saveSelectedServerId(id: String) {
        context.dataStore.edit { it[Keys.SELECTED_SERVER_ID] = id }
    }

    suspend fun saveCachedServers(servers: List<ServerConfig>) {
        val json = gson.toJson(servers)
        context.dataStore.edit { it[Keys.CACHED_SERVERS] = json }
    }

    suspend fun saveLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
    }

    suspend fun saveDownloadSettings(config: DownloadSettingsConfig) {
        val json = gson.toJson(config)
        context.dataStore.edit { it[Keys.DOWNLOAD_SETTINGS] = json }
    }

    suspend fun clearSubscription() {
        context.dataStore.edit {
            it.remove(Keys.SUBSCRIPTION_URL)
            it.remove(Keys.SELECTED_SERVER_ID)
            it.remove(Keys.CACHED_SERVERS)
        }
    }
}
