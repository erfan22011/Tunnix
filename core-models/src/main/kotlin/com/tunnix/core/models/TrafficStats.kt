package com.tunnix.core.models

import java.io.Serializable

data class TrafficStats(
    val uploadSpeedBytesPerSec: Long = 0L,
    val downloadSpeedBytesPerSec: Long = 0L,
    val totalUploadBytes: Long = 0L,
    val totalDownloadBytes: Long = 0L,
    val durationSeconds: Long = 0L
) : Serializable {

    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MiB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format(java.util.Locale.US, "%.2f KiB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }

    fun formatTotal(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f GiB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MiB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(java.util.Locale.US, "%.2f KiB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    val formattedDownloadSpeed: String get() = formatSpeed(downloadSpeedBytesPerSec)
    val formattedUploadSpeed: String get() = formatSpeed(uploadSpeedBytesPerSec)
    val formattedTotalDownload: String get() = formatTotal(totalDownloadBytes)
    val formattedTotalUpload: String get() = formatTotal(totalUploadBytes)
}
