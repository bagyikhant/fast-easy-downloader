package com.example.model

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED
}

data class DownloadTask(
    val id: String,
    val url: String,
    val fileName: String,
    val category: FileCategory = FileCategory.OTHER,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val downloadSpeed: Long = 0L, // Bytes per second
    val startTime: Long = System.currentTimeMillis(),
    val completedTime: Long? = null,
    val localFilePath: String = "",
    val errorMessage: String? = null,
    val sourcePortal: String? = null,
    val mimeType: String? = null
) {
    val progress: Float
        get() = when {
            status == DownloadStatus.COMPLETED -> 1f
            totalBytes > 0L -> (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }

    val progressPercentInt: Int
        get() = (progress * 100).toInt()

    val formattedDownloaded: String
        get() = formatBytes(downloadedBytes)

    val formattedTotal: String
        get() = if (totalBytes > 0L) formatBytes(totalBytes) else "Unknown"

    val formattedSpeed: String
        get() = when {
            status == DownloadStatus.DOWNLOADING && downloadSpeed > 0 -> "${formatBytes(downloadSpeed)}/s"
            status == DownloadStatus.DOWNLOADING -> "Connecting..."
            status == DownloadStatus.PAUSED -> "Paused"
            status == DownloadStatus.COMPLETED -> "Completed"
            status == DownloadStatus.FAILED -> "Failed"
            status == DownloadStatus.QUEUED -> "Queued"
            status == DownloadStatus.CANCELED -> "Canceled"
            else -> ""
        }

    val formattedEta: String
        get() = when {
            status != DownloadStatus.DOWNLOADING || downloadSpeed <= 0 || totalBytes <= 0 -> ""
            else -> {
                val remainingBytes = totalBytes - downloadedBytes
                if (remainingBytes <= 0) "Finishing..."
                else {
                    val remainingSeconds = remainingBytes / downloadSpeed
                    when {
                        remainingSeconds < 60 -> "${remainingSeconds}s left"
                        remainingSeconds < 3600 -> "${remainingSeconds / 60}m ${remainingSeconds % 60}s left"
                        else -> "${remainingSeconds / 3600}h ${(remainingSeconds % 3600) / 60}m left"
                    }
                }
            }
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
            return sdf.format(Date(completedTime ?: startTime))
        }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val df = DecimalFormat("#,##0.#")
            return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
        }
    }
}
