package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import com.flintr.tv.data.model.AudioLanguage
import com.flintr.tv.data.model.SubtitleLanguage
import com.flintr.tv.data.model.VideoQuality
import com.flintr.tv.data.model.VideoResizeMode
import com.flintr.tv.ui.components.player.FALLBACK_AUDIO_MIMES
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio decoder mode for ExoPlayer RenderersFactory.
 */
enum class AudioDecoderMode(val value: String, val displayName: String) {
    AUTO("auto", "Auto"),
    SOFTWARE("software", "Software"),
    HARDWARE("hardware", "Hardware");

    companion object {
        fun fromValue(value: String): AudioDecoderMode =
            entries.find { it.value == value } ?: AUTO
    }
}

/**
 * Preferred audio codec for track selection.
 */
enum class PreferredAudioCodec(val value: String, val displayName: String) {
    AUTO("auto", "Auto"),
    AAC("aac", "AAC"),
    STEREO_FALLBACK("stereo_fallback", "Stereo-Fallback");

    companion object {
        fun fromValue(value: String): PreferredAudioCodec =
            entries.find { it.value == value } ?: AUTO
    }
}

/**
 * Persists playback-related settings (audio decoder, passthrough, preferred codec).
 * Uses plain SharedPreferences (no sensitive data).
 */
@Singleton
class PlaybackSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "playback_settings"
        private const val KEY_AUDIO_DECODER_MODE = "audio_decoder_mode"
        private const val KEY_AUDIO_PASSTHROUGH = "audio_passthrough"
        private const val KEY_PREFERRED_AUDIO_CODEC = "preferred_audio_codec"
        private const val KEY_SUBTITLES_ENABLED = "subtitles_enabled"
        private const val KEY_PREFERRED_SUBTITLE_LANGUAGE = "preferred_subtitle_language"
        private const val KEY_PREFERRED_AUDIO_LANGUAGE = "preferred_audio_language"
        private const val KEY_RESIZE_MODE = "resize_mode"
        private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        private const val KEY_REMEMBER_POSITION = "remember_position"
        private const val KEY_VIDEO_QUALITY = "video_quality"
        private const val KEY_HW_BLOCKED_AUDIO_MIMES = "hw_blocked_audio_mimes"
        private const val KEY_HW_BLOCKLIST_FINGERPRINT = "hw_blocklist_fingerprint"
    }

    fun getAudioDecoderMode(): AudioDecoderMode {
        val value = prefs.getString(KEY_AUDIO_DECODER_MODE, AudioDecoderMode.AUTO.value)!!
        return AudioDecoderMode.fromValue(value)
    }

    fun setAudioDecoderMode(mode: AudioDecoderMode) {
        prefs.edit { putString(KEY_AUDIO_DECODER_MODE, mode.value) }
        // Explicitly choosing hardware is the user's way to give blocked HW decoders another try.
        if (mode == AudioDecoderMode.HARDWARE) clearHardwareBlockedAudioMimes()
    }

    /**
     * Audio MIME types whose hardware decoder failed to initialise on this device. Empty
     * when the list was recorded under a different [Build.FINGERPRINT], since a firmware
     * update may have fixed the decoder.
     */
    fun getHardwareBlockedAudioMimes(): Set<String> {
        if (prefs.getString(KEY_HW_BLOCKLIST_FINGERPRINT, null) != Build.FINGERPRINT) return emptySet()
        // Defense-in-depth: a tampered prefs file (root/restore) could otherwise inject a
        // non-audio MIME (e.g. video/avc) and block it via blockingMediaCodecSelector.
        return prefs.getStringSet(KEY_HW_BLOCKED_AUDIO_MIMES, null)
            ?.filterTo(mutableSetOf()) { it in FALLBACK_AUDIO_MIMES }
            ?: emptySet()
    }

    fun addHardwareBlockedAudioMime(mime: String) {
        val updated = getHardwareBlockedAudioMimes() + mime
        prefs.edit {
            putStringSet(KEY_HW_BLOCKED_AUDIO_MIMES, updated)
            putString(KEY_HW_BLOCKLIST_FINGERPRINT, Build.FINGERPRINT)
        }
    }

    fun clearHardwareBlockedAudioMimes() {
        prefs.edit {
            remove(KEY_HW_BLOCKED_AUDIO_MIMES)
            remove(KEY_HW_BLOCKLIST_FINGERPRINT)
        }
    }

    /**
     * Default off: Dolby/DTS audio is decoded and downmixed to stereo, which is what TV
     * speakers need and what most hardware decoders on cheap TV boxes can't reliably do for
     * eac3/ac3 (see [AudioDecoderMode] fallback). Users with an AVR/soundbar that accepts the
     * bitstream directly can opt in via [setAudioPassthroughEnabled]; on some boxes that has
     * been observed to cause audio errors when resuming from pause.
     */
    fun isAudioPassthroughEnabled(): Boolean =
        prefs.getBoolean(KEY_AUDIO_PASSTHROUGH, false)

    fun setAudioPassthroughEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUDIO_PASSTHROUGH, enabled) }
    }

    fun getPreferredAudioCodec(): PreferredAudioCodec {
        val value = prefs.getString(KEY_PREFERRED_AUDIO_CODEC, PreferredAudioCodec.AUTO.value)!!
        return PreferredAudioCodec.fromValue(value)
    }

    fun setPreferredAudioCodec(codec: PreferredAudioCodec) {
        prefs.edit { putString(KEY_PREFERRED_AUDIO_CODEC, codec.value) }
    }

    fun getSubtitlesEnabled(): Boolean =
        prefs.getBoolean(KEY_SUBTITLES_ENABLED, false)

    fun setSubtitlesEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SUBTITLES_ENABLED, enabled) }
    }

    fun getPreferredSubtitleLanguage(): SubtitleLanguage {
        val value = prefs.getString(KEY_PREFERRED_SUBTITLE_LANGUAGE, SubtitleLanguage.Deutsch.value)!!
        return SubtitleLanguage.fromValue(value)
    }

    fun setPreferredSubtitleLanguage(language: SubtitleLanguage) {
        prefs.edit { putString(KEY_PREFERRED_SUBTITLE_LANGUAGE, language.value) }
    }

    fun getPreferredAudioLanguage(): AudioLanguage {
        val value = prefs.getString(KEY_PREFERRED_AUDIO_LANGUAGE, AudioLanguage.Deutsch.value)!!
        return AudioLanguage.fromValue(value)
    }

    fun setPreferredAudioLanguage(language: AudioLanguage) {
        prefs.edit { putString(KEY_PREFERRED_AUDIO_LANGUAGE, language.value) }
    }

    fun getVideoResizeMode(): VideoResizeMode {
        val value = prefs.getString(KEY_RESIZE_MODE, VideoResizeMode.FIT.value)!!
        return VideoResizeMode.fromValue(value)
    }

    fun setVideoResizeMode(mode: VideoResizeMode) {
        prefs.edit { putString(KEY_RESIZE_MODE, mode.value) }
    }

    fun isAutoPlayNextEnabled(): Boolean =
        prefs.getBoolean(KEY_AUTO_PLAY_NEXT, false)

    fun setAutoPlayNextEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_PLAY_NEXT, enabled) }
    }

    fun isRememberPositionEnabled(): Boolean =
        prefs.getBoolean(KEY_REMEMBER_POSITION, true)

    fun setRememberPositionEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_REMEMBER_POSITION, enabled) }
    }

    fun getVideoQuality(): VideoQuality {
        val value = prefs.getString(KEY_VIDEO_QUALITY, VideoQuality.Auto.value)!!
        return VideoQuality.fromValue(value)
    }

    fun setVideoQuality(quality: VideoQuality) {
        prefs.edit { putString(KEY_VIDEO_QUALITY, quality.value) }
    }
}
