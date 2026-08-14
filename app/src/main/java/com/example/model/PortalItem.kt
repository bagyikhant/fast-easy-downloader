package com.example.model

data class PortalItem(
    val id: String,
    val name: String,
    val domain: String,
    val url: String,
    val tagline: String,
    val badge: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val supportedFormats: List<String>
)

object DefaultPortals {
    val list = listOf(
        PortalItem(
            id = "ssstik",
            name = "SSSTik",
            domain = "ssstik.io",
            url = "https://ssstik.io",
            tagline = "TikTok Video & Audio Downloader without watermark",
            badge = "TikTok HD",
            primaryColorHex = 0xFF00F2FE,
            secondaryColorHex = 0xFF4FACFE,
            supportedFormats = listOf("MP4 No Watermark", "MP3 Audio", "HD Video")
        ),
        PortalItem(
            id = "fdown",
            name = "FDown",
            domain = "fdown.net",
            url = "https://fdown.net",
            tagline = "Facebook HD & SD Video Downloader",
            badge = "Facebook",
            primaryColorHex = 0xFF1877F2,
            secondaryColorHex = 0xFF0052CC,
            supportedFormats = listOf("HD Quality", "SD Normal", "Audio M4A")
        ),
        PortalItem(
            id = "savefrom",
            name = "SaveFrom",
            domain = "savefrom.net",
            url = "https://en.savefrom.net",
            tagline = "All-In-One Universal Online Media Downloader",
            badge = "Universal",
            primaryColorHex = 0xFF22C55E,
            secondaryColorHex = 0xFF16A34A,
            supportedFormats = listOf("720p / 1080p", "WebM", "MP3", "Direct Links")
        ),
        PortalItem(
            id = "y2mate",
            name = "Y2Mate",
            domain = "y2mate.com",
            url = "https://www.y2mate.com",
            tagline = "Fast Video & Audio Converter & Downloader",
            badge = "Video / MP3",
            primaryColorHex = 0xFFFF0055,
            secondaryColorHex = 0xFFFF5E3A,
            supportedFormats = listOf("1080p", "720p", "320kbps MP3", "M4A")
        )
    )
}
