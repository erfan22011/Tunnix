package com.tunnix.traffic

import com.tunnix.core.models.TrafficStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object TrafficMonitorRepository {

    private val _trafficStats = MutableStateFlow(TrafficStats())
    val trafficStats: StateFlow<TrafficStats> = _trafficStats.asStateFlow()

    private var monitorJob: Job? = null
    private var connectionStartTime: Long = 0L

    private var prevUploadBytes: Long = 0L
    private var prevDownloadBytes: Long = 0L
    private var prevTimestamp: Long = 0L

    /**
     * Starts active traffic polling every 1 second.
     */
    fun startMonitoring(
        scope: CoroutineScope,
        connectedAtMillis: Long = System.currentTimeMillis()
    ) {
        stopMonitoring()
        connectionStartTime = connectedAtMillis

        val (initialUpload, initialDownload) = TrafficCounterBridge.readCumulativeBytes()
        prevUploadBytes = initialUpload
        prevDownloadBytes = initialDownload
        prevTimestamp = System.currentTimeMillis()

        _trafficStats.value = TrafficStats(
            uploadSpeedBytesPerSec = 0L,
            downloadSpeedBytesPerSec = 0L,
            totalUploadBytes = initialUpload,
            totalDownloadBytes = initialDownload,
            durationSeconds = 0L
        )

        monitorJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                updateStats()
            }
        }
    }

    /**
     * Stops active polling and resets instant speeds.
     */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null

        val current = _trafficStats.value
        _trafficStats.value = current.copy(
            uploadSpeedBytesPerSec = 0L,
            downloadSpeedBytesPerSec = 0L
        )
    }

    /**
     * Performs a single calculation step. Exposed for testing.
     */
    fun calculateStatsStep(
        currentUploadBytes: Long,
        currentDownloadBytes: Long,
        currentTimestamp: Long
    ): TrafficStats {
        val timeDeltaMs = (currentTimestamp - prevTimestamp).coerceAtLeast(1L)
        val timeDeltaSec = timeDeltaMs / 1000.0

        val deltaUpload = (currentUploadBytes - prevUploadBytes).coerceAtLeast(0L)
        val deltaDownload = (currentDownloadBytes - prevDownloadBytes).coerceAtLeast(0L)

        val uploadSpeed = (deltaUpload / timeDeltaSec).toLong()
        val downloadSpeed = (deltaDownload / timeDeltaSec).toLong()

        prevUploadBytes = currentUploadBytes
        prevDownloadBytes = currentDownloadBytes
        prevTimestamp = currentTimestamp

        val durationSec = ((currentTimestamp - connectionStartTime) / 1000L).coerceAtLeast(0L)

        return TrafficStats(
            uploadSpeedBytesPerSec = uploadSpeed,
            downloadSpeedBytesPerSec = downloadSpeed,
            totalUploadBytes = currentUploadBytes,
            totalDownloadBytes = currentDownloadBytes,
            durationSeconds = durationSec
        )
    }

    private fun updateStats() {
        val (currentUpload, currentDownload) = TrafficCounterBridge.readCumulativeBytes()
        val now = System.currentTimeMillis()
        val newStats = calculateStatsStep(currentUpload, currentDownload, now)
        _trafficStats.value = newStats
    }

    /**
     * Resets all stats to zero.
     */
    fun reset() {
        stopMonitoring()
        prevUploadBytes = 0L
        prevDownloadBytes = 0L
        prevTimestamp = 0L
        connectionStartTime = 0L
        _trafficStats.value = TrafficStats()
    }
}
