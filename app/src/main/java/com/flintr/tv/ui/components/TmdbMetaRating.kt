package com.flintr.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.flintr.tv.data.tmdb.TmdbMetaRepository
import java.util.Locale

/** Repository für die Karten-Chips; null außerhalb des Providers (Chip bleibt aus). */
val LocalTmdbMetaRepository = staticCompositionLocalOf<TmdbMetaRepository?> { null }

/**
 * Rating-Chip aus [TmdbMetaRepository.metaFlow]. Nur der tmdbId-Pfad, kein Xtream-Fallback
 * und keine Suche pro Karte. [repository.request] ist bei blank/non-numeric ein No-op.
 */
@Composable
fun rememberTmdbRating(kind: String, tmdbId: String?): String? {
    val repository = LocalTmdbMetaRepository.current ?: return null
    val id = tmdbId?.takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } } ?: return null
    val metaFlow by repository.metaFlow.collectAsState()
    LaunchedEffect(kind, id) { repository.request(kind, id) }
    val rating = metaFlow["$kind:$id"]?.rating ?: return null
    return String.format(Locale.US, "%.1f", rating)
}
