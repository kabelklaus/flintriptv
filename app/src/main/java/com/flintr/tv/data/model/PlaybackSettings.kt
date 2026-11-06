package com.flintr.tv.data.model

enum class VideoQuality(val value: String, val displayName: String) {
    Auto("auto", "Auto"),
    Quality1080p("1080p", "1080p"),
    Quality720p("720p", "720p"),
    Quality480p("480p", "480p"),
    Quality360p("360p", "360p");

    companion object {
        fun fromValue(value: String): VideoQuality =
            entries.find { it.value == value } ?: Auto
    }
}

enum class SubtitleLanguage(val value: String, val displayName: String) {
    Deutsch("de", "Deutsch"),
    English("en", "English"),
    Francais("fr", "Français"),
    Espanol("es", "Español");

    companion object {
        fun fromValue(value: String): SubtitleLanguage =
            entries.find { it.value == value } ?: Deutsch
    }
}

enum class AudioLanguage(val value: String, val displayName: String) {
    Deutsch("de", "Deutsch"),
    English("en", "English"),
    Francais("fr", "Français"),
    Espanol("es", "Español");

    companion object {
        fun fromValue(value: String): AudioLanguage =
            entries.find { it.value == value } ?: Deutsch
    }
}

enum class VideoResizeMode(val value: String, val displayName: String) {
    FIT("fit", "Fit / Original"),
    ASPECT_16_9("16_9", "16:9"),
    ASPECT_4_3("4_3", "4:3"),
    FILL("fill", "Vollbild (gestreckt)"),
    ZOOM("zoom", "Zoom (beschnitten)");

    companion object {
        fun fromValue(value: String): VideoResizeMode =
            entries.find { it.value == value } ?: FIT
    }
}
