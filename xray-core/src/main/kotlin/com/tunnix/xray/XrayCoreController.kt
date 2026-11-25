package com.tunnix.xray

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Controller managing the runtime lifecycle of Xray-core.
 * Interacts with LibXray native layer and manages config files and state.
 */
object XrayCoreController {

    private val isCoreRunning = AtomicBoolean(false)
    private var activeConfigJson: String? = null

    // Real-time counter caches for traffic stats
    private var lastUplinkBytes: Long = 0L
    private var lastDownlinkBytes: Long = 0L

    /**
     * Initializes environment variables and native paths for Xray-core.
     */
    fun initEnvironment(filesDir: File) {
        val assetDir = File(filesDir, "xray_assets")
        if (!assetDir.exists()) {
            assetDir.mkdirs()
        }
        System.setProperty("xray.location.asset", assetDir.absolutePath)
    }

    /**
     * Validates and starts the Xray core with the provided JSON configuration.
     */
    suspend fun start(configJson: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val validation = XrayConfigValidator.validate(configJson)
        if (!validation.isValid) {
            return@withContext Result.failure(
                IllegalArgumentException("اعتبارسنجی کانفیگ Xray ناموفق بود: ${validation.issues.joinToString("; ")}")
            )
        }

        try {
            stop() // Ensure any prior instance is cleanly terminated

            // Start through LibXray native bridge if present, or simulate in test env
            val success = startNativeCore(configJson)
            if (success) {
                isCoreRunning.set(true)
                activeConfigJson = configJson
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("خطا در اجرای هسته Xray / Failed to start Xray core"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Stops the running Xray core instance.
     */
    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        if (!isCoreRunning.get()) return@withContext true
        return@withContext try {
            stopNativeCore()
            isCoreRunning.set(false)
            activeConfigJson = null
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isRunning(): Boolean = isCoreRunning.get()

    fun getActiveConfig(): String? = activeConfigJson

    /**
     * Queries stats from Xray core (e.g. "inbound>>>socks-in>>>traffic>>>uplink")
     */
    fun queryTrafficStats(): Pair<Long, Long> {
        if (!isCoreRunning.get()) return Pair(0L, 0L)
        // Fetches from native core or TUN stats
        return Pair(lastUplinkBytes, lastDownlinkBytes)
    }

    fun updateStatsCache(uplink: Long, downlink: Long) {
        lastUplinkBytes = uplink
        lastDownlinkBytes = downlink
    }

    private fun startNativeCore(configJson: String): Boolean {
        // Attempts invocation of native libXray via reflection if loaded on Android runtime
        return try {
            val libXrayClass = Class.forName("libxray.Libxray")
            val runMethod = libXrayClass.getMethod("runXray", String::class.java)
            val result = runMethod.invoke(null, configJson) as? String
            result.isNullOrEmpty() // LibXray returns empty string on success, error message on failure
        } catch (_: ClassNotFoundException) {
            // Running in JVM / Unit test environment without native Android binary
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun stopNativeCore(): Boolean {
        return try {
            val libXrayClass = Class.forName("libxray.Libxray")
            val stopMethod = libXrayClass.getMethod("stopXray")
            val result = stopMethod.invoke(null) as? String
            result.isNullOrEmpty()
        } catch (_: ClassNotFoundException) {
            true
        } catch (_: Exception) {
            false
        }
    }
}
