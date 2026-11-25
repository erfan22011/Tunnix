package com.tunnix.traffic

import org.junit.Assert.*
import org.junit.Test

class TrafficMonitorRepositoryTest {

    @Test
    fun `test speed calculation step with realistic data`() {
        val startTime = 1000000L
        TrafficMonitorRepository.reset()

        // Initial state at t0 = 1000000L
        TrafficMonitorRepository.calculateStatsStep(
            currentUploadBytes = 1000L,
            currentDownloadBytes = 5000L,
            currentTimestamp = startTime
        )

        // After 1 second (1000ms), 26.13 KiB uploaded and 282.70 KiB downloaded
        val t1 = startTime + 1000L
        val stats1 = TrafficMonitorRepository.calculateStatsStep(
            currentUploadBytes = 1000L + 26757L,
            currentDownloadBytes = 5000L + 289484L,
            currentTimestamp = t1
        )

        assertEquals("26.13 KiB/s", stats1.formattedUploadSpeed)
        assertEquals("282.70 KiB/s", stats1.formattedDownloadSpeed)
        assertEquals(27757L, stats1.totalUploadBytes)
        assertEquals(294484L, stats1.totalDownloadBytes)
    }

    @Test
    fun `test connection duration increases accurately`() {
        TrafficMonitorRepository.reset()
        val startTime = 5000000L

        // t0
        TrafficMonitorRepository.calculateStatsStep(0L, 0L, startTime)

        // After 65 seconds
        val t65 = startTime + 65000L
        val stats = TrafficMonitorRepository.calculateStatsStep(100L, 200L, t65)

        assertEquals(65L, stats.durationSeconds)
    }
}
