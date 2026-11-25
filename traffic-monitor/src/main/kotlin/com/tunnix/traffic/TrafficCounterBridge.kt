package com.tunnix.traffic

import com.tunnix.xray.XrayCoreController

/**
 * Reads genuine byte counters from Xray-core stats engine.
 * Ensures zero fake or simulated numbers are displayed.
 */
object TrafficCounterBridge {

    /**
     * Retrieves cumulative total bytes transferred so far: Pair(totalUpload, totalDownload)
     */
    fun readCumulativeBytes(): Pair<Long, Long> {
        return if (XrayCoreController.isRunning()) {
            XrayCoreController.queryTrafficStats()
        } else {
            Pair(0L, 0L)
        }
    }
}
