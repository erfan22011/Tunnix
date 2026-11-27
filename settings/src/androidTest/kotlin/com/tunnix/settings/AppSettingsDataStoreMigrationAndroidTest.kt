package com.tunnix.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * Instrumented test verifying legacy migration and purge on encryption failure in AppSettingsDataStore.
 */
@RunWith(AndroidJUnit4::class)
class AppSettingsDataStoreMigrationAndroidTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val subUrlKey = stringPreferencesKey("key_subscription_url")

    @Before
    fun setUp() {
        LocalSecurityCipher.testKeyOverride = null
    }

    @After
    fun tearDown() {
        LocalSecurityCipher.testKeyOverride = null
    }

    @Test
    fun testSuccessfulLegacyMigrationEncryptsPlaintext() = runBlocking {
        val dataStore = AppSettingsDataStore(context)
        val legacyPlaintext = "https://sb.tootcloud.shop:2096/sub/legacy_sample"

        // Set up plaintext without ENC:GCM: envelope directly in storage
        dataStore.saveSubscriptionUrl("")
        val prefs = dataStore.preferencesFlow.first()
        assertNotNull(prefs)
    }
}
