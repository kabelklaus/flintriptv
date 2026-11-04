package com.flintr.tv.viewmodel

import com.flintr.tv.data.local.imdb.ImdbEnrichment
import com.flintr.tv.data.local.imdb.ImdbEnrichmentService
import com.flintr.tv.data.model.DetailDisplayData
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
