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
 * Lade-Zustand eines TMDB-Chips (Karten-Rating, Detail-Meta): [Loading] zeigt ein Skeleton,
 * [Value] den fertigen Chip, [Absent] nichts - ein fehlender Titel/tmdbId ist keine Ladezeit.
 */
sealed interface TmdbChipState {
    data object Loading : TmdbChipState
    data class Value(val rating: String) : TmdbChipState
    data object Absent : TmdbChipState
}

/**
 * Tri-State-Pendant zu [rememberTmdbRating]: mit tmdbId der direkte [TmdbMetaRepository.request]-
 * Pfad (reaktiv über [TmdbMetaRepository.metaFlow] - Treffer ist sofort [TmdbChipState.Value],
 * sonst [TmdbChipState.Loading]), ohne ID der strikte Title-Search-Pfad über
 * [TmdbMetaRepository.resolveCardMeta] ([TmdbChipState.Loading] während die Suche läuft, danach
 * [TmdbChipState.Value]/[TmdbChipState.Absent]). Ohne Titel und ohne tmdbId gibt es nichts zu
 * laden - direkt [TmdbChipState.Absent].
 */
@Composable
fun rememberTmdbChipState(
    kind: String,
    tmdbId: String?,
    year: Int? = null,
    title: String? = null
): TmdbChipState {
    val repository = LocalTmdbMetaRepository.current ?: return TmdbChipState.Absent
    val id = tmdbId?.takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } }

    return if (id != null) {
        rememberChipStateById(repository, kind, id)
    } else if (!title.isNullOrBlank()) {
        rememberChipStateBySearch(repository, kind, tmdbId, title, year)
    } else {
        TmdbChipState.Absent
    }
}

@Composable
private fun rememberChipStateById(
    repository: TmdbMetaRepository,
    kind: String,
    id: String
): TmdbChipState {
    val metaFlow by repository.metaFlow.collectAsState()
    LaunchedEffect(kind, id) { repository.request(kind, id) }
    val meta = metaFlow["$kind:$id"]
    return if (meta != null) TmdbChipState.Value(formatRating(meta.rating)) else TmdbChipState.Loading
}

@Composable
private fun rememberChipStateBySearch(
    repository: TmdbMetaRepository,
    kind: String,
    tmdbId: String?,
    title: String,
    year: Int?
): TmdbChipState {
    var resolved by remember(kind, title, year) {
        mutableStateOf<TmdbChipState>(TmdbChipState.Loading)
    }
    LaunchedEffect(kind, title, year, tmdbId) {
        val meta: TmdbMeta? = repository.resolveCardMeta(kind, title, year, tmdbId)
        resolved = meta?.let { TmdbChipState.Value(formatRating(it.rating)) } ?: TmdbChipState.Absent
    }
    return resolved
}

/** Alter String?-Rückgabetyp für externe Call-Sites; mappt den Tri-State auf [TmdbChipState.Value]. */
@Deprecated(
    "Zeigt kein Loading mehr an eigene Call-Sites - auf rememberTmdbChipState umstellen.",
    ReplaceWith("rememberTmdbChipState(kind, tmdbId, year, title)")
)
@Composable
fun rememberTmdbRating(
    kind: String,
    tmdbId: String?,
    year: Int? = null,
    title: String? = null
): String? = (rememberTmdbChipState(kind, tmdbId, year, title) as? TmdbChipState.Value)?.rating

private fun formatRating(rating: Double): String =
    String.format(Locale.US, "%.1f", rating)
