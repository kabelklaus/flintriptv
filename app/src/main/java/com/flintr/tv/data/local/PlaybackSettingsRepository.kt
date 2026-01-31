package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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
    }

    fun getAudioDecoderMode(): AudioDecoderMode {
        val value = prefs.getString(KEY_AUDIO_DECODER_MODE, AudioDecoderMode.AUTO.value)!!
        return AudioDecoderMode.fromValue(value)
    }

    fun setAudioDecoderMode(mode: AudioDecoderMode) {
        prefs.edit { putString(KEY_AUDIO_DECODER_MODE, mode.value) }
    }

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
}
