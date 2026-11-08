package com.flintr.tv.data.model

import androidx.media3.common.MimeTypes

/** Audio MIME types eligible for the hardware→software decoder fallback. */
internal val FALLBACK_AUDIO_MIMES = setOf(
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_AC3,
    MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_TRUEHD
)
