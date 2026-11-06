package com.flintr.tv.viewmodel

import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.local.imdb.ImdbEnrichment
import com.flintr.tv.data.local.imdb.ImdbEnrichmentService
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.utils.error.reportWarning
import kotlinx.coroutines.CancellationException

/**
 * [MovieDetailViewModel] and [SeriesDetailViewModel] fail into this exact message - kept as one
 * constant so the literal only needs to exist once.
 */
internal const val DETAIL_LOAD_ERROR_MESSAGE = "Details konnten nicht geladen werden"

/**
 * Runs the IMDb-only genre/rating enrichment [lookup] and feeds a hit to [onEnriched]. A miss or
 * a failure never propagates: the base state both detail ViewModels already show must not be
 * retracted just because the (slower, best-effort) enrichment missed or failed.
 */
internal suspend fun loadImdbEnrichment(
    tag: String,
    failureMessage: String,
    imdbEnrichmentService: ImdbEnrichmentService,
    lookup: () -> ImdbEnrichment?,
    onEnriched: (ImdbEnrichment) -> Unit
) {
    try {
        imdbEnrichmentService.ensureLoaded()
        lookup()?.let(onEnriched)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        reportWarning(tag, failureMessage, e)
    }
}

/** Applies an IMDb enrichment hit onto a shared [DetailDisplayData]; genres/rating are IMDb-only. */
internal fun mergeImdbEnrichment(detail: DetailDisplayData?, enrichment: ImdbEnrichment): DetailDisplayData? =
    detail?.copy(genres = enrichment.genres, rating = enrichment.rating)

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
