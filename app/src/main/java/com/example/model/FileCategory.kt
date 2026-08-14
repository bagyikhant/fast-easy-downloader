package com.example.model

enum class FileCategory(val label: String) {
    ALL("All"),
    VIDEO("Videos"),
    AUDIO("Audio"),
    IMAGE("Images"),
    DOCUMENT("Documents"),
    OTHER("Other");

    companion object {
        fun fromExtension(ext: String): FileCategory {
            val cleanExt = ext.lowercase().trim().removePrefix(".")
            return when (cleanExt) {
                "mp4", "mkv", "webm", "avi", "mov", "flv", "3gp", "ts", "m4v" -> VIDEO
                "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma" -> AUDIO
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic" -> IMAGE
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip", "rar", "7z", "apk" -> DOCUMENT
                else -> OTHER
            }
        }

        fun fromMimeType(mimeType: String?): FileCategory {
            if (mimeType == null) return OTHER
            val lower = mimeType.lowercase()
            return when {
                lower.startsWith("video/") -> VIDEO
                lower.startsWith("audio/") -> AUDIO
                lower.startsWith("image/") -> IMAGE
                lower.contains("pdf") || lower.contains("document") || lower.contains("sheet") || lower.contains("presentation") || lower.contains("zip") || lower.contains("text/") -> DOCUMENT
                else -> OTHER
            }
        }
    }
}
