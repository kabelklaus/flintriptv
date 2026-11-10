package com.flintr.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.flintr.tv.data.tmdb.TmdbMeta
import com.flintr.tv.data.tmdb.TmdbMetaRepository
import java.util.Locale

/** Repository für die Karten-Chips; null außerhalb des Providers (Chip bleibt aus). */
val LocalTmdbMetaRepository = staticCompositionLocalOf<TmdbMetaRepository?> { null }

/**
 * Rating-Chip: mit tmdbId der direkte [TmdbMetaRepository.request]-Pfad, ohne ID der
 * strikte Title-Search-Pfad über [TmdbMetaRepository.resolveCardMeta]. Ergebnis bleibt lokal.
 */
@Composable
fun rememberTmdbRating(
    kind: String,
    tmdbId: String?,
    year: Int? = null,
    title: String? = null
): String? {
    val repository = LocalTmdbMetaRepository.current ?: return null
    val id = tmdbId?.takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } }

    return if (id != null) {
        rememberRatingById(repository, kind, id)
    } else if (!title.isNullOrBlank()) {
        rememberRatingBySearch(repository, kind, tmdbId, title, year)
    } else {
        null
    }
}

@Composable
private fun rememberRatingById(
    repository: TmdbMetaRepository,
    kind: String,
    id: String
): String? {
    val metaFlow by repository.metaFlow.collectAsState()
    LaunchedEffect(kind, id) { repository.request(kind, id) }
    return metaFlow["$kind:$id"]?.rating?.let(::formatRating)
}

@Composable
private fun rememberRatingBySearch(
    repository: TmdbMetaRepository,
    kind: String,
    tmdbId: String?,
    title: String,
    year: Int?
): String? {
    var meta by remember(kind, title, year) { mutableStateOf<TmdbMeta?>(null) }
    LaunchedEffect(kind, title, year, tmdbId) {
        meta = repository.resolveCardMeta(kind, title, year, tmdbId)
    }
    return meta?.rating?.let(::formatRating)
}

private fun formatRating(rating: Double): String =
    String.format(Locale.US, "%.1f", rating)
