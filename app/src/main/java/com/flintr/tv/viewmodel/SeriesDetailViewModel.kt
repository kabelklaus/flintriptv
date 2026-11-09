package com.flintr.tv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.local.imdb.ImdbEnrichment
import com.flintr.tv.data.local.imdb.ImdbEnrichmentService
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.data.tmdb.TmdbCastClient
import com.flintr.tv.data.tmdb.TmdbCastMember
import com.flintr.tv.domain.recommendation.RecommendationEngine
import com.flintr.tv.utils.error.reportWarning
import com.flintr.tv.utils.parseTimestampMillisOrZero
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Eine Staffel-Kachel im Serien-Detail. [watchedCount] und [isFullyWatched] berechnen sich aus
 * den bereits vollständig gesehenen Episoden-IDs (Watched-Tracking), damit die Kachel den
 * Watched-Ribbon nur bei 100 % zeigt.
 */
data class SeasonUiModel(
    val season: Season,
    val title: String,
    val posterUrl: String?,
    val episodeCount: Int,
    val watchedCount: Int,
    val isFullyWatched: Boolean
) {
    val seasonNumber: Int get() = season.seasonNumber
}

/**
 * The concrete episode the series-detail watch button starts: resolved once via the same
 * smart-resume logic [com.flintr.tv.ui.screens.SeasonScreen] uses, never re-matched in the UI.
 */
data class WatchTargetUiModel(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episode: Episode,
    val label: String,
    val watchProgress: Float? = null
) {
    val episodeId: Int get() = episode.id
}

data class SeriesDetailUiState(
    val isLoading: Boolean = true,
    val detail: DetailDisplayData? = null,
    val series: Series? = null,
    val seasons: List<SeasonUiModel> = emptyList(),
    val isInWatchlist: Boolean = false,
    val seasonsHint: String? = null,
    val recommendations: List<Series> = emptyList(),
    val watchTarget: WatchTargetUiModel? = null,
    val watchLabel: String = "Ansehen",
    val watchProgress: Float? = null,
    val castPhotos: Map<String, String> = emptyMap(),
    val error: String? = null
)

/**
 * Loads a series' detail metadata (get_series_info), aggregates its seasons with watched counts
 * and resolves watchlist state.
 *
 * Genre/rating are IMDb-only, exactly like MovieDetail: without an enrichment match both stay
 * null and the UI hides them; the Xtream `genre`/`rating` strings are never used as a fallback.
 *
 * Navigation stays in Phase 4: the series id arrives through [ARG_SERIES_ID] and the screen
 * receives callbacks only.
 */
@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: XtreamRepository,
    private val credentialsRepository: CredentialsRepository,
    private val imdbEnrichmentService: ImdbEnrichmentService,
    private val watchlistRepository: WatchlistRepository,
    private val watchProgressService: WatchProgressService,
    private val cacheService: DataCacheService,
    private val tmdbCastClient: TmdbCastClient,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        const val ARG_SERIES_ID = "seriesId"
        private const val TAG = "SeriesDetailViewModel"
        private const val NO_SEASONS_HINT =
            "Für diese Serie sind derzeit keine Staffeln und Episoden verfügbar."
        private val YEAR_PATTERN = Regex("(?:19|20)\\d{2}")
        private val TITLE_YEAR_PATTERN = Regex("\\((?:19|20)\\d{2}\\)")
        private const val CAST_SEPARATOR = ","
        private const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w185"
        private val COMBINING_MARKS = Regex("\\p{M}")
        private val WHITESPACE = Regex("\\s+")

        /**
         * In-process handoff from a series-grid click to the detail destination: the grid handler
         * has no [SeriesDetailViewModel] instance yet (the route creates it), so it stages the
         * clicked [Series] here and the next ViewModel instance consumes it as its loading base.
         */
        private val pendingBaseSeed = AtomicReference<Series?>(null)

        fun seedNextDetail(seed: Series) {
            pendingBaseSeed.set(seed)
        }
    }

    private val seriesId: Int = savedStateHandle.get<Int>(ARG_SERIES_ID)
        ?: savedStateHandle.get<String>(ARG_SERIES_ID)?.toIntOrNull()
        ?: -1

    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    init {
        pendingBaseSeed.getAndSet(null)?.let(::seedBaseSeries)
        load()
    }

    /**
     * Uses [seed] only as the loading placeholder (poster/title/backdrop). The real state from
     * [load] replaces it wholesale, so this can never race the network result.
     */
    fun seedBaseSeries(seed: Series?) {
        if (seed == null) return
        _uiState.update { state ->
            if (state.detail != null) state else state.copy(series = seed)
        }
    }

    fun retry() = load()

    fun load() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val credentials = requireCredentials() ?: return@launch
            requireValidSeriesId() ?: return@launch

            try {
                val result = repository.getSeriesInfo(
                    username = credentials.username,
                    password = credentials.password,
                    seriesId = seriesId
                )
                if (result.isFailure) {
                    _uiState.value = SeriesDetailUiState(
                        isLoading = false,
                        error = DETAIL_LOAD_ERROR_MESSAGE
                    )
                    reportWarning(
                        TAG,
                        "Failed to load series info for seriesId=$seriesId",
                        result.exceptionOrNull() ?: Exception("unknown")
                    )
                    return@launch
                }

                val loadedSeries = result.getOrNull()
                if (loadedSeries == null) {
                    _uiState.value = SeriesDetailUiState(
                        isLoading = false,
                        error = DETAIL_LOAD_ERROR_MESSAGE
                    )
                    return@launch
                }

                _uiState.value = buildLoadedState(loadedSeries)

                // TMDB-Cast-Fotos laufen in eigenem Job: duerfen Rating/Genres/Empfehlungen nicht
                // blockieren (ohne tmdbId/Match bleiben die Briefmarken/Initial-Kreis stehen).
                viewModelScope.launch(ioDispatcher) { loadCastPhotos(_uiState.value.detail) }

                // IMDb-Enrichment nachschieben: Rating/Genres kommen ausschliesslich von hier,
                // ein Fehler/Miss darf den bereits gezeigten Basis-State nicht zuruecknehmen.
                loadImdbEnrichment(
                    tag = TAG,
                    failureMessage = "IMDb enrichment failed for seriesId=$seriesId",
                    imdbEnrichmentService = imdbEnrichmentService,
                    lookup = { lookupEnrichment(loadedSeries) }
                ) { enrichment ->
                    _uiState.update { state -> state.copy(detail = mergeImdbEnrichment(state.detail, enrichment)) }
                }

                // Genres are IMDb-only and therefore may be null; RecommendationEngine returns
                // an empty row in that case, so the section hides instead of falling back to
                // the (untrusted) Xtream genre string.
                val cached = cacheService.loadCachedDataAsync()
                val recommendations = computeRecommendations(cached, _uiState.value.detail?.genres)
                _uiState.update { it.copy(recommendations = recommendations) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = SeriesDetailUiState(
                    isLoading = false,
                    error = DETAIL_LOAD_ERROR_MESSAGE
                )
                reportWarning(TAG, "Error loading series detail for seriesId=$seriesId", e)
            }
        }
    }

    private fun requireCredentials(): Credentials? = requireCredentialsOrNull(credentialsRepository) {
        _uiState.value = SeriesDetailUiState(
            isLoading = false,
            error = "Keine gespeicherten Anmeldedaten gefunden"
        )
    }

    private fun requireValidSeriesId(): Int? = requirePositiveIdOrNull(seriesId) {
        _uiState.value = SeriesDetailUiState(
            isLoading = false,
            error = "Ungültige Serien-ID"
        )
    }

    private suspend fun buildLoadedState(loadedSeries: Series): SeriesDetailUiState {
        val watchedEpisodeIds = watchProgressService.getWatchedEpisodeIds(seriesId)
        val seasons = buildSeasons(loadedSeries, watchedEpisodeIds)
        // Basisdaten sofort mappen, ohne auf das (teure) IMDb-Enrichment zu warten -
        // genres/rating bleiben hier bewusst null, siehe DetailDisplayData.
        val baseDetail = buildDetail(loadedSeries)
        val watchTarget = resolveWatchTarget(loadedSeries, watchedEpisodeIds)

        return SeriesDetailUiState(
            isLoading = false,
            detail = baseDetail,
            series = loadedSeries,
            seasons = seasons,
            isInWatchlist = findWatchlistEntry() != null,
            seasonsHint = if (seasons.isEmpty()) NO_SEASONS_HINT else null,
            recommendations = emptyList(),
            watchTarget = watchTarget,
            watchLabel = watchTarget?.label ?: "Ansehen",
            watchProgress = watchTarget?.watchProgress
        )
    }

    private fun computeRecommendations(cached: CachedData?, genres: List<String>?): List<Series> {
        return if (cached != null) {
            RecommendationEngine.recommendSimilarSeries(
                anchorGenres = genres,
                cached = cached,
                excludedSeriesIds = setOf(seriesId)
            )
        } else {
            emptyList()
        }
    }

    fun toggleWatchlist() {
        val series = _uiState.value.series ?: return
        viewModelScope.launch(ioDispatcher) {
            toggleWatchlistEntry(
                watchlistRepository = watchlistRepository,
                findExisting = ::findWatchlistEntry,
                buildEntry = { series.toWatchlistEntry() }
            )?.let { newState -> _uiState.update { it.copy(isInWatchlist = newState) } }
        }
    }

    private fun findWatchlistEntry(): WatchlistEntry? = watchlistRepository.getAllWatchlistEntries()
        .firstOrNull { it.type == WatchlistEntryType.SERIES && it.streamId == seriesId }

    private fun buildSeasons(
        series: Series,
        watchedEpisodeIds: Set<Int>
    ): List<SeasonUiModel> {
        return series.seasons
            .orEmpty()
            .sortedBy { it.seasonNumber }
            .map { season ->
                val episodes = season.episodes.orEmpty()
                val watchedCount = episodes.count { it.id in watchedEpisodeIds }
                SeasonUiModel(
                    season = season,
                    title = season.name.takeIf { it.isNotBlank() } ?: "Staffel ${season.seasonNumber}",
                    posterUrl = season.cover?.takeIf { it.isNotBlank() }
                        ?: series.cover?.takeIf { it.isNotBlank() }
                        ?: episodes.firstNotNullOfOrNull { episode ->
                            episode.info?.movieImage?.takeIf { it.isNotBlank() }
                        }
                        ?: series.backdropPath?.firstOrNull { it.isNotBlank() },
                    episodeCount = episodes.size,
                    watchedCount = watchedCount,
                    isFullyWatched = episodes.isNotEmpty() && watchedCount >= episodes.size
                )
            }
    }

    /**
     * Resolves the watch button's target episode from the same watched-history source as the
     * season ribbons ([WatchProgressService.getWatchedEpisodeIds]). A failed timestamp parse in
     * [WatchProgressService.getLastInteraction] must never make the button contradict the
     * ribbons.
     *
     * - No data at all: first episode, label "ansehen".
     * - In-progress episode: exactly that episode, label "fortsetzen", with progress bar.
     * - Only completed episodes: first unwatched episode in season/episode order.
     * - Everything watched: last episode.
     */
    private suspend fun resolveWatchTarget(
        series: Series,
        watchedEpisodeIds: Set<Int>
    ): WatchTargetUiModel? {
        val sortedSeasons = series.seasons.orEmpty().sortedBy { it.seasonNumber }
        val episodesWithSeason = sortedSeasons.flatMap { season ->
            season.episodes.orEmpty().map { episode -> season to episode }
        }
        if (episodesWithSeason.isEmpty()) return null

        // Find the most recently touched in-progress episode. Entries already present in the
        // watched history are treated as completed, because the ribbons say so.
        var inProgress: Pair<Season, Episode>? = null
        var inProgressPercent: Float? = null
        var latestInProgressTimestamp = Long.MIN_VALUE
        for ((season, episode) in episodesWithSeason) {
            if (episode.id in watchedEpisodeIds) continue
            val progress = watchProgressService.getWatchProgress(
                contentType = "series",
                streamId = null,
                seriesId = seriesId,
                episodeId = episode.id
            ) ?: continue
            if (progress.position <= 0L || progress.duration <= 0L) continue
            val timestamp = parseTimestampMillisOrZero(progress.lastWatched) { error ->
                reportWarning(TAG, "Error parsing lastWatched timestamp: ${progress.lastWatched}", error)
            }
            if (inProgress == null || timestamp > latestInProgressTimestamp) {
                latestInProgressTimestamp = timestamp
                inProgress = season to episode
                inProgressPercent = (progress.position.toDouble() / progress.duration * 100.0)
                    .toFloat()
                    .takeIf { it > 0f && it < 100f }
            }
        }

        val hasWatchedInSeries = episodesWithSeason.any { (_, episode) ->
            episode.id in watchedEpisodeIds
        }
        val chosen = inProgress
            ?: episodesWithSeason.firstOrNull { (_, episode) -> episode.id !in watchedEpisodeIds }
            ?: episodesWithSeason.last()
        val action = if (inProgress != null || hasWatchedInSeries) {
            "fortsetzen"
        } else {
            "ansehen"
        }

        val (season, episode) = chosen
        // API episode_num is 1-based; only fall back to the position (also 1-based) when missing.
        val episodeNumber = episode.episodeNum?.takeIf { it > 0 }
            ?: (season.episodes.orEmpty().indexOfFirst { it.id == episode.id } + 1)

        return WatchTargetUiModel(
            seasonNumber = season.seasonNumber,
            episodeNumber = episodeNumber,
            episode = episode,
            label = String.format(
                Locale.US,
                "S%d E%d %s",
                season.seasonNumber,
                episodeNumber,
                action
            ),
            watchProgress = if (inProgress != null) inProgressPercent else null
        )
    }

    /**
     * Builds the shared detail display model from SeriesInfo. Genre/rating come exclusively from
     * the IMDb enrichment (see [lookupEnrichment]) and start out null here - they are filled in by
     * a later state update once the (slower) IMDb lookup completes.
     */
    private fun buildDetail(series: Series): DetailDisplayData {
        val title = series.name.takeIf { it.isNotBlank() }
        return DetailDisplayData(
            title = title,
            plot = series.plot,
            cast = series.cast
                ?.split(CAST_SEPARATOR)
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList(),
            director = series.director,
            runtime = null,
            releaseDate = series.releaseDate,
            youtubeTrailer = series.youtubeTrailer,
            backdropPath = series.backdropPath,
            posterUrl = series.cover?.takeIf { it.isNotBlank() },
            tmdbId = series.tmdbId,
            genres = null,
            rating = null
        )
    }

    /**
     * Looks up the IMDb-only genre/rating enrichment; the lookup key mirrors
     * mapVodInfoToDetail ("title (year)").
     */
    private fun lookupEnrichment(series: Series): ImdbEnrichment? {
        val title = series.name.takeIf { it.isNotBlank() } ?: return null
        val yearFromTitle = YEAR_PATTERN.find(title)?.value?.toInt()
        val year = series.releaseDate?.let { YEAR_PATTERN.find(it)?.value?.toInt() } ?: yearFromTitle
        val lookupTitle = if (yearFromTitle != null) {
            title.replace(TITLE_YEAR_PATTERN, "").trim()
        } else {
            title
        }
        val lookupName = if (year != null) "$lookupTitle ($year)" else lookupTitle
        return imdbEnrichmentService.lookup(lookupName)
    }

    /**
     * Loads cast photos from TMDB for [detail]'s tmdbId and matches them onto [detail]'s cast
     * names. Unmatched names (no tmdbId, no TMDB match, or no profile photo) simply get no map
     * entry - the cast section falls back to its initial-letter stamp for those.
     */
    private suspend fun loadCastPhotos(detail: DetailDisplayData?) {
        val tmdbId = detail?.tmdbId?.takeIf { it.isNotBlank() } ?: return
        val castNames = detail.cast
        if (castNames.isEmpty()) return

        val members = tmdbCastClient.tvCredits(tmdbId)
        val photos = matchCastPhotos(castNames, members)
        _uiState.update { it.copy(castPhotos = photos) }
    }

    private fun matchCastPhotos(castNames: List<String>, members: List<TmdbCastMember>): Map<String, String> {
        if (members.isEmpty()) return emptyMap()
        // Bei normalisierten Namens-Duplikaten gewinnt deterministisch der Eintrag mit Foto,
        // statt von der (zufaelligen) API-Reihenfolge abzuhaengen.
        val membersByNormalizedName = mutableMapOf<String, TmdbCastMember>()
        for (member in members) {
            val key = normalizeCastName(member.name)
            val existing = membersByNormalizedName[key]
            if (existing == null || (existing.validProfilePath() == null && member.validProfilePath() != null)) {
                membersByNormalizedName[key] = member
            }
        }
        return castNames.mapNotNull { name ->
            val profilePath = membersByNormalizedName[normalizeCastName(name)]?.validProfilePath()
                ?: return@mapNotNull null
            name to "$TMDB_IMAGE_BASE_URL$profilePath"
        }.toMap()
    }

    private fun TmdbCastMember.validProfilePath(): String? = profilePath?.takeIf { it.startsWith("/") }

    private fun normalizeCastName(name: String): String {
        val withoutDiacritics = Normalizer.normalize(name, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        return WHITESPACE.replace(withoutDiacritics.lowercase(Locale.ROOT).trim(), " ")
    }
}
