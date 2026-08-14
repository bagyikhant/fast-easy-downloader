package com.example.model

import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadedFile(
    val id: String,
    val name: String,
    val file: File,
    val sizeBytes: Long,
    val lastModified: Long,
    val category: FileCategory,
    val extension: String,
    val mimeType: String,
    val sourceUrl: String? = null,
    val sourcePortal: String? = null
) {
    val formattedSize: String
        get() = formatBytes(sizeBytes)

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }

    val exists: Boolean
        get() = file.exists()

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val df = DecimalFormat("#,##0.#")
            return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
        }

        fun fromFile(file: File, sourceUrl: String? = null, sourcePortal: String? = null): DownloadedFile {
            val ext = file.extension
            val category = FileCategory.fromExtension(ext)
            val mimeType = when (ext.lowercase()) {
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "wav" -> "audio/wav"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }
            return DownloadedFile(
                id = file.absolutePath.hashCode().toString(),
                name = file.name,
                file = file,
                sizeBytes = if (file.exists()) file.length() else 0L,
                lastModified = if (file.exists()) file.lastModified() else System.currentTimeMillis(),
                category = category,
                extension = ext,
                mimeType = mimeType,
                sourceUrl = sourceUrl,
                sourcePortal = sourcePortal
            )
        }
    }
}
