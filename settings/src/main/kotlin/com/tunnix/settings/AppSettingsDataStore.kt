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

    companion object {
        private const val FAST_CACHE_PREFS = "tunnix_fast_cache"
        private const val KEY_CACHED_THEME = "fast_cached_theme_mode"
        private const val KEY_CACHED_LANG = "fast_cached_language"
        private const val KEY_CACHED_HAS_SUB = "fast_cached_has_sub"

        fun getCachedThemeMode(context: Context): ThemeMode {
            val prefs = context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_CACHED_THEME, null)
            return if (name != null) {
                try { ThemeMode.valueOf(name) } catch (_: Exception) { ThemeMode.DARK }
            } else {
                // If no preference saved yet, check system theme
                val uiMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                if (uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) ThemeMode.DARK else ThemeMode.LIGHT
            }
        }

        fun getCachedLanguage(context: Context): AppLanguage {
            val prefs = context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
            val code = prefs.getString(KEY_CACHED_LANG, AppLanguage.PERSIAN.code)
            return AppLanguage.fromCode(code ?: AppLanguage.PERSIAN.code)
        }

        fun getCachedHasSubscription(context: Context): Boolean {
            val prefs = context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_CACHED_HAS_SUB, false)
        }

        fun setCachedThemeMode(context: Context, themeMode: ThemeMode) {
            context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHED_THEME, themeMode.name)
                .apply()
        }

        fun setCachedLanguage(context: Context, language: AppLanguage) {
            context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHED_LANG, language.code)
                .apply()
        }

        fun setCachedHasSubscription(context: Context, hasSub: Boolean) {
            context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CACHED_HAS_SUB, hasSub)
                .apply()
        }
    }

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
        val rawSubUrl = prefs[Keys.SUBSCRIPTION_URL] ?: ""
        val subUrl = if (rawSubUrl.isNotBlank()) {
            try {
                LocalSecurityCipher.decrypt(rawSubUrl)
            } catch (_: Exception) {
                rawSubUrl
            }
        } else {
            ""
        }

        val serverId = prefs[Keys.SELECTED_SERVER_ID] ?: ""
        val langCode = prefs[Keys.LANGUAGE] ?: getCachedLanguage(context).code
        val themeStr = prefs[Keys.THEME_MODE] ?: getCachedThemeMode(context).name
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

        val rawServersJson = prefs[Keys.CACHED_SERVERS]
        val serversJson = if (!rawServersJson.isNullOrBlank()) {
            try {
                LocalSecurityCipher.decrypt(rawServersJson)
            } catch (_: Exception) {
                rawServersJson
            }
        } else {
            null
        }

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
        setCachedHasSubscription(context, url.isNotBlank())
        val encrypted = if (url.isNotBlank()) LocalSecurityCipher.encrypt(url) else ""
        context.dataStore.edit { it[Keys.SUBSCRIPTION_URL] = encrypted }
    }

    suspend fun saveSelectedServerId(id: String) {
        context.dataStore.edit { it[Keys.SELECTED_SERVER_ID] = id }
    }

    suspend fun saveCachedServers(servers: List<ServerConfig>) {
        val json = gson.toJson(servers)
        val encrypted = if (json.isNotBlank()) LocalSecurityCipher.encrypt(json) else ""
        context.dataStore.edit { it[Keys.CACHED_SERVERS] = encrypted }
    }

    suspend fun saveLanguage(language: AppLanguage) {
        setCachedLanguage(context, language)
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        setCachedThemeMode(context, themeMode)
        context.dataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
    }

    suspend fun saveDownloadSettings(config: DownloadSettingsConfig) {
        val json = gson.toJson(config)
        context.dataStore.edit { it[Keys.DOWNLOAD_SETTINGS] = json }
    }

    suspend fun clearSubscription() {
        setCachedHasSubscription(context, false)
        context.dataStore.edit {
            it.remove(Keys.SUBSCRIPTION_URL)
            it.remove(Keys.SELECTED_SERVER_ID)
            it.remove(Keys.CACHED_SERVERS)
        }
    }
}
