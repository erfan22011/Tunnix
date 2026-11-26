package com.tunnix.xray

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Controller managing the runtime lifecycle of Xray-core.
 * Integrates directly with native Libv2ray / CoreController engine.
 * Thread-safe with Mutex-guarded lifecycle state transitions.
 */
object XrayCoreController {

    private val isCoreRunning = AtomicBoolean(false)
    private val lifecycleMutex = Mutex()
    private var activeConfigJson: String? = null
    private var coreController: libv2ray.CoreController? = null

    /**
     * Test hook to allow JVM unit tests to mock execution without failing on missing Android .so libraries.
     * In production (default = false), missing native binaries produce a real startup failure.
     */
    @Volatile
    var bypassNativeCoreForTesting: Boolean = false

    // Real-time counter caches for traffic stats
    private var lastUplinkBytes: Long = 0L
    private var lastDownlinkBytes: Long = 0L

    /**
     * Listener invoked when the native proxy core terminates unexpectedly (crash or Go shutdown).
     */
    @Volatile
    var onUnexpectedShutdown: (() -> Unit)? = null

    private val callbackHandler = object : libv2ray.CoreCallbackHandler {
        override fun onEmitStatus(p0: Long, p1: String?): Long = 0L
        override fun startup(): Long = 0L
        override fun shutdown(): Long {
            if (isCoreRunning.compareAndSet(true, false)) {
                activeConfigJson = null
                onUnexpectedShutdown?.invoke()
            }
            return 0L
        }
    }

    /**
     * Initializes environment variables and native paths for Xray-core.
     */
    fun initEnvironment(filesDir: File) {
        val assetDir = File(filesDir, "xray_assets")
        if (!assetDir.exists()) {
            assetDir.mkdirs()
        }
        System.setProperty("xray.location.asset", assetDir.absolutePath)
        System.setProperty("v2ray.location.asset", assetDir.absolutePath)
        try {
            libv2ray.Libv2ray.initCoreEnv(assetDir.absolutePath, "")
        } catch (_: Throwable) {
            // Handled when running in host unit test JVM without Android native .so
        }
    }

    private var lastStartupError: String? = null

    /**
     * Validates and starts the Xray core with the provided JSON configuration and TUN file descriptor.
     * Guarded with [lifecycleMutex] to prevent start/stop race conditions.
     */
    suspend fun start(configJson: String, tunFd: Int = -1): Result<Boolean> = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            val validation = XrayConfigValidator.validate(configJson)
            if (!validation.isValid) {
                return@withLock Result.failure(
                    IllegalArgumentException("اعتبارسنجی کانفیگ Xray ناموفق بود: ${validation.issues.joinToString("; ")}")
                )
            }

            try {
                stopInternal() // Ensure any prior instance is cleanly terminated

                val success = startNativeCore(configJson, tunFd)
                if (success) {
                    isCoreRunning.set(true)
                    activeConfigJson = configJson
                    Result.success(true)
                } else {
                    val detail = lastStartupError ?: "خطای ناشناخته در هسته بومی"
                    Result.failure(IllegalStateException("خطا در اجرای هسته Xray: $detail"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Stops the running Xray core instance.
     * Guarded with [lifecycleMutex] to prevent start/stop race conditions.
     */
    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            stopInternal()
        }
    }

    private fun stopInternal(): Boolean {
        if (!isCoreRunning.get() && coreController == null) return true
        return try {
            stopNativeCore()
            isCoreRunning.set(false)
            activeConfigJson = null
            lastUplinkBytes = 0L
            lastDownlinkBytes = 0L
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Synchronous stop for use in Service onDestroy or urgent lifecycle teardown.
     */
    fun stopSync(): Boolean {
        return stopInternal()
    }

    fun isRunning(): Boolean = isCoreRunning.get()

    fun getActiveConfig(): String? = activeConfigJson

    /**
     * Queries stats from Xray core (e.g. "proxy,uplink,1024;proxy,downlink,2048;")
     * Assigns cumulative metrics directly without compounding accumulation.
     */
    fun queryTrafficStats(): Pair<Long, Long> {
        if (!isCoreRunning.get()) return Pair(0L, 0L)
        try {
            val statsStr = coreController?.queryAllOutboundTrafficStats()
            if (!statsStr.isNullOrEmpty()) {
                var totalUplink = 0L
                var totalDownlink = 0L
                statsStr.split(";").forEach { item ->
                    val parts = item.split(",")
                    if (parts.size >= 3) {
                        val dir = parts[1].trim().lowercase()
                        val value = parts[2].trim().toLongOrNull() ?: 0L
                        if (dir == "uplink") totalUplink += value
                        else if (dir == "downlink") totalDownlink += value
                    }
                }
                // Assign directly rather than += compounding (Xray returns cumulative outbound stats)
                if (totalUplink > 0 || totalDownlink > 0) {
                    lastUplinkBytes = totalUplink
                    lastDownlinkBytes = totalDownlink
                }
            }
        } catch (_: Throwable) {}

        return Pair(lastUplinkBytes, lastDownlinkBytes)
    }

    fun updateStatsCache(uplink: Long, downlink: Long) {
        lastUplinkBytes = uplink
        lastDownlinkBytes = downlink
    }

    private fun startNativeCore(configJson: String, tunFd: Int): Boolean {
        lastStartupError = null
        return try {
            val controller = libv2ray.CoreController(callbackHandler)
            controller.startLoop(configJson, tunFd)
            coreController = controller
            true
        } catch (e: UnsatisfiedLinkError) {
            if (bypassNativeCoreForTesting) {
                true
            } else {
                lastStartupError = "کتابخانه نیتیو هسته لود نشد (UnsatisfiedLinkError): ${e.message}"
                false
            }
        } catch (e: NoClassDefFoundError) {
            if (bypassNativeCoreForTesting) {
                true
            } else {
                lastStartupError = "کلاس نیتیو هسته یافت نشد (NoClassDefFoundError): ${e.message}"
                false
            }
        } catch (e: Exception) {
            lastStartupError = e.message ?: e.toString()
            false
        }
    }

    private fun stopNativeCore(): Boolean {
        return try {
            coreController?.stopLoop()
            coreController = null
            true
        } catch (e: UnsatisfiedLinkError) {
            true
        } catch (e: NoClassDefFoundError) {
            true
        } catch (_: Exception) {
            false
        }
    }
}
