package com.flintr.tv.viewmodel

import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.tmdb.TmdbMeta
import com.flintr.tv.data.tmdb.TmdbMetaRepository
import com.flintr.tv.utils.error.reportWarning
import kotlinx.coroutines.CancellationException

/**
 * [MovieDetailViewModel] and [SeriesDetailViewModel] fail into this exact message - kept as one
 * constant so the literal only needs to exist once.
 */
internal const val DETAIL_LOAD_ERROR_MESSAGE = "Details konnten nicht geladen werden"

/**
 * Fuehrt den TMDB-Meta-[lookup] aus und reicht einen Treffer an [onMeta] weiter. Ein Miss oder
 * Fehler wird nie propagiert: der bereits gezeigte Basis-State der Detail-ViewModels darf nicht
 * zurueckgenommen werden, nur weil die (langsamere, best-effort) Meta-Abfrage leer blieb.
 */
internal suspend fun loadTmdbMeta(
    tag: String,
    failureMessage: String,
    tmdbMetaRepository: TmdbMetaRepository,
    lookup: suspend () -> TmdbMeta?,
    onMeta: (TmdbMeta) -> Unit
) {
    try {
        // detailMeta() laedt den Cache selbst; ein separates ensureLoaded() entfaellt.
        lookup()?.let(onMeta)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        reportWarning(tag, failureMessage, e)
    }
}

/** Wendet einen TMDB-Treffer auf die gemeinsamen [DetailDisplayData] an; Genres/Rating kommen von TMDB. */
internal fun mergeTmdbMeta(detail: DetailDisplayData?, meta: TmdbMeta): DetailDisplayData? =
    detail?.copy(genres = meta.genres.map { it.name }, rating = meta.rating)

/**
 * Returns [credentialsRepository]'s stored credentials, invoking [onMissing] to set the
 * caller's "no credentials" error state when there are none.
 */
internal fun requireCredentialsOrNull(
    credentialsRepository: CredentialsRepository,
    onMissing: () -> Unit
): Credentials? {
    val credentials = credentialsRepository.getCredentials()
    if (credentials == null) onMissing()
    return credentials
}

/** Returns [id] if positive, invoking [onInvalid] to set the caller's "invalid id" error state otherwise. */
internal fun requirePositiveIdOrNull(id: Int, onInvalid: () -> Unit): Int? {
    if (id <= 0) {
        onInvalid()
        return null
    }
    return id
}

/**
 * Toggles [findExisting]'s presence on the watchlist: removes it if present, otherwise adds
 * [buildEntry]'s result. Returns the resulting isInWatchlist value, or null if the repository
 * call failed (caller should leave its UI state unchanged in that case).
 */
internal suspend fun toggleWatchlistEntry(
    watchlistRepository: WatchlistRepository,
    findExisting: () -> WatchlistEntry?,
    buildEntry: () -> WatchlistEntry
): Boolean? {
    val existing = findExisting()
    return if (existing != null) {
        false.takeIf { watchlistRepository.removeFromWatchlist(existing.id) }
    } else {
        true.takeIf { watchlistRepository.addToWatchlist(buildEntry()) }
    }
}
