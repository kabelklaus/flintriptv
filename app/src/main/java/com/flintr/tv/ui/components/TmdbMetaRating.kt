package com.flintr.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.flintr.tv.data.tmdb.TmdbCardState
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
 * Tri-State-Pendant zu [rememberTmdbRating]: ein Treffer in [TmdbMetaRepository.metaFlow]
 * ("$kind:$id") ist sofort [TmdbChipState.Value]. Sonst fragt [LaunchedEffect]
 * [TmdbMetaRepository.cardMetaState]: [TmdbCardState.Meta] wird [TmdbChipState.Value],
 * [TmdbCardState.Negative] wird [TmdbChipState.Absent], [TmdbCardState.Transient] bleibt
 * [TmdbChipState.Loading]. Negative schaltet den Chip ab (Board-Required), Transient pulsiert
 * weiter. Ohne Titel und ohne tmdbId gibt es nichts zu laden - direkt [TmdbChipState.Absent].
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
    if (id == null && title.isNullOrBlank()) return TmdbChipState.Absent

    val metaFlow by repository.metaFlow.collectAsState()
    val cachedMeta = id?.let { metaFlow["$kind:$it"] }
    var resolved by remember(kind, tmdbId, title, year) {
        mutableStateOf<TmdbChipState>(
            cachedMeta?.let { TmdbChipState.Value(formatRating(it.rating)) }
                ?: TmdbChipState.Loading
        )
    }
    LaunchedEffect(kind, tmdbId, title, year) {
        resolved = when (val state = repository.cardMetaState(kind, tmdbId, title, year)) {
            is TmdbCardState.Meta -> TmdbChipState.Value(formatRating(state.meta.rating))
            TmdbCardState.Negative -> TmdbChipState.Absent
            TmdbCardState.Transient -> TmdbChipState.Loading
        }
    }
    return cachedMeta?.let { TmdbChipState.Value(formatRating(it.rating)) } ?: resolved
}

/** Alter String?-Rückgabetyp für externe Call-Sites; mappt den Tri-State auf [TmdbChipState.Value]. */
@Deprecated(
    "Zeigt kein Loading mehr an eigene Call-Sites - auf rememberTmdbChipState umstellen.",
    ReplaceWith("rememberTmdbChipState(kind, tmdbId, year, title)")
)
@Composable
fun rememberTmdbRating( // NOSONAR (S1133) - bewusster Deprecation-Pfad, Entfernung geplant
    kind: String,
    tmdbId: String?,
    year: Int? = null,
    title: String? = null
): String? = (rememberTmdbChipState(kind, tmdbId, year, title) as? TmdbChipState.Value)?.rating

private fun formatRating(rating: Double): String =
    String.format(Locale.US, "%.1f", rating)
