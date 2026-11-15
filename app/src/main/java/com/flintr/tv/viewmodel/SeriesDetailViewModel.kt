package com.flintr.tv.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.data.tmdb.TmdbCastClient
import com.flintr.tv.data.tmdb.TmdbCastMember
import com.flintr.tv.data.tmdb.TmdbMetaRepository
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
    /** True zwischen Basis-Anzeige und dem TMDB-Meta-Nachlauf - steuert das Rating/Genre-Skeleton. */
    val isMetaLoading: Boolean = false,
    val error: String? = null
)

/**
 * Loads a series' detail metadata (get_series_info), aggregates its seasons with watched counts
 * and resolves watchlist state.
 *
 * Genre/rating come from TMDB, exactly like MovieDetail: without a meta match both stay
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
    private val tmdbMetaRepository: TmdbMetaRepository,
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

                // Staffel-Poster VOR dem ersten State-Bau holen: Provider liefert info.seasons leer
                // (verifiziert) - nachtraegliche Updates an bereits gerenderte Kacheln sind fehler-
                // anfaellig (v6-Beweis: Merge-Log da, Kacheln leer). ~100ms TMDB-Latenz ist ok;
                // Rating/Genres/Empfehlungen bleiben weiter nachgelagert und blocken nicht.
                val seasonPosters = loadSeasonPosters(loadedSeries)
                _uiState.value = buildLoadedState(loadedSeries, seasonPosters)

                // TMDB-Cast-Fotos laufen in eigenem Job: duerfen Rating/Genres/Empfehlungen nicht
                // blockieren (ohne tmdbId/Match bleiben die Briefmarken/Initial-Kreis stehen).
                viewModelScope.launch { loadCastPhotos(_uiState.value.detail) }

                // TMDB-Meta nachschieben: Rating/Genres kommen ausschliesslich von hier,
                // ein Fehler/Miss darf den bereits gezeigten Basis-State nicht zuruecknehmen.
                _uiState.update { it.copy(isMetaLoading = true) }
                loadTmdbMeta(
                    tag = TAG,
                    failureMessage = "TMDB meta failed for seriesId=$seriesId",
                    lookup = {
                        val detail = _uiState.value.detail
                        tmdbMetaRepository.detailMeta(
                            kind = "tv",
                            tmdbId = detail?.tmdbId?.takeIf { it.isNotBlank() },
                            title = detail?.title ?: "",
                            year = resolveYear(detail?.title, detail?.releaseDate)
                        )
                    },
                    onMeta = { meta ->
                        _uiState.update { state -> state.copy(detail = mergeTmdbMeta(state.detail, meta)) }
                    },
                    onDone = { _uiState.update { it.copy(isMetaLoading = false) } }
                )

                // Similar-Row kommt aus TMDB-Recommendations des Anchor-tmdbId; ohne Anchor
                // bleibt die Reihe leer, statt auf Xtream-Genres zurueckzufallen.
                val cached = cacheService.loadCachedDataAsync()
                val recommendations = computeRecommendations(cached)
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

    private suspend fun buildLoadedState(
        loadedSeries: Series,
        seasonPosters: Map<Int, String>
    ): SeriesDetailUiState {
        val watchedEpisodeIds = watchProgressService.getWatchedEpisodeIds(seriesId)
        val seasons = buildSeasons(loadedSeries, watchedEpisodeIds, seasonPosters)
        // Basisdaten sofort mappen, ohne auf die (teure) TMDB-Meta-Abfrage zu warten -
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

    private suspend fun computeRecommendations(cached: CachedData?): List<Series> {
        if (cached == null) return emptyList()
        val anchorId = _uiState.value.detail?.tmdbId
            ?.takeIf { it.toIntOrNull() != null }
            ?: return emptyList()
        val recIds = tmdbMetaRepository.recommendationsFor("tv", anchorId)
        return RecommendationEngine.selectSimilarSeries(
            anchorTmdbIds = recIds,
            cached = cached,
            excludedSeriesIds = setOf(seriesId)
        )
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
        watchedEpisodeIds: Set<Int>,
        seasonPosters: Map<Int, String>
    ): List<SeasonUiModel> {
        return series.seasons
            .orEmpty()
            .sortedBy { it.seasonNumber }
            .map { season ->
                val episodes = season.episodes.orEmpty()
                val watchedCount = episodes.count { it.id in watchedEpisodeIds }
                val resolvedUrl = seasonPosters[season.seasonNumber]?.takeIf { it.isNotBlank() }
                        ?: season.cover?.takeIf { it.isNotBlank() }
                        ?: series.cover?.takeIf { it.isNotBlank() }
                        ?: episodes.firstNotNullOfOrNull { episode ->
                            episode.info?.movieImage?.takeIf { it.isNotBlank() }
                        }
                        ?: series.backdropPath?.firstOrNull { it.isNotBlank() }
                    SeasonUiModel(
                        season = season,
                        title = season.name.takeIf { it.isNotBlank() } ?: "Staffel ${season.seasonNumber}",
                        posterUrl = resolvedUrl,
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
     * TMDB (see [loadTmdbMeta]) and start out null here - they are filled in by a later state
     * update once the (slower) TMDB lookup completes.
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

    /** Jahres-Auflösung fuer die TMDB-Suche: bevorzugt `releaseDate`, sonst der Titel. */
    private fun resolveYear(title: String?, releaseDate: String?): Int? =
        releaseDate?.let { YEAR_PATTERN.find(it)?.value?.toInt() }
            ?: title?.let { YEAR_PATTERN.find(it)?.value?.toInt() }

    /**
     * Loads the cast for [detail]'s tmdbId from TMDB's aggregate_credits (full series cast,
     * already capped to 25 by [TmdbCastClient]).
     *
     * Liefert TMDB mindestens so viele Mitglieder wie Xtream (`detail.cast`) UND taucht darunter
     * mindestens ein Anubis-Name wieder auf, wird TMDB zur Anzeige-Quelle: Xtream-Serien wie
     * Supernatural liefern oft nur die aktuelle Staffel (3 Namen), TMDB die gesamte Besetzung.
     * Namen und Fotos kommen dann 1:1 aus derselben Liste, kein Name-Matching noetig. Der
     * Namens-Treffer ist ein Plausibilitaets-Check gegen eine falsche tmdbId: ohne ihn wuerde eine
     * zufaellig groessere, aber komplett fremde Besetzung die korrekten Anubis-Namen ersetzen.
     * Fehlt der Treffer oder liefert TMDB weniger Mitglieder, bleiben die Xtream-Namen und Fotos
     * werden wie bisher per Namens-Matching zugeordnet.
     */
    private suspend fun loadCastPhotos(detail: DetailDisplayData?) {
        val tmdbId = detail?.tmdbId?.takeIf { it.isNotBlank() } ?: return
        val castNames = detail.cast
        if (castNames.isEmpty()) return

        val members = tmdbCastClient.tvCredits(tmdbId)
        val nonBlankMembers = members.filter { it.name.isNotBlank() }
        val hasPlausibleMatch = nonBlankMembers.any { member ->
            castNames.any { name -> normalizeCastName(name) == normalizeCastName(member.name) }
        }
        if (nonBlankMembers.size >= castNames.size && hasPlausibleMatch) {
            val photos = dedupeByNormalizedName(nonBlankMembers).values.mapNotNull { member ->
                member.validProfilePath()?.let { path -> member.name to "$TMDB_IMAGE_BASE_URL$path" }
            }.toMap()
            _uiState.update { state ->
                state.copy(
                    detail = state.detail?.copy(cast = nonBlankMembers.map { it.name }),
                    castPhotos = photos
                )
            }
        } else {
            val photos = matchCastPhotos(castNames, members)
            _uiState.update { it.copy(castPhotos = photos) }
        }
    }

    /**
     * Provider liefert info.seasons leer (verifiziert) - Poster aus TMDB. Xtream gewinnt (schon in
     * [buildSeasons] als [SeasonUiModel.posterUrl] aufgeloest), TMDB fuellt nur die Luecken ueber
     * die Staffelnummer. Wird vor dem ersten State-Bau awaited, damit die Kacheln nie ohne Bild
     * gerendert werden.
     */
    private suspend fun loadSeasonPosters(series: Series): Map<Int, String> {
        val tmdbId = series.tmdbId?.takeIf { it.isNotBlank() } ?: return emptyMap()
        val tmdbPosters = try {
            tmdbCastClient.seasonPosters(tmdbId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "seasonPosters failed for tmdbId=$tmdbId", e)
            return emptyMap()
        }
        Log.d(TAG, "seasonPosters merged: ${tmdbPosters.size}")
        return tmdbPosters
    }

    private fun matchCastPhotos(castNames: List<String>, members: List<TmdbCastMember>): Map<String, String> {
        if (members.isEmpty()) return emptyMap()
        val membersByNormalizedName = dedupeByNormalizedName(members)
        return castNames.mapNotNull { name ->
            val profilePath = membersByNormalizedName[normalizeCastName(name)]?.validProfilePath()
                ?: return@mapNotNull null
            name to "$TMDB_IMAGE_BASE_URL$profilePath"
        }.toMap()
    }

    /**
     * Bei normalisierten Namens-Duplikaten gewinnt deterministisch der Eintrag mit Foto, statt
     * von der (zufaelligen) API-Reihenfolge abzuhaengen.
     */
    private fun dedupeByNormalizedName(members: List<TmdbCastMember>): Map<String, TmdbCastMember> {
        val result = mutableMapOf<String, TmdbCastMember>()
        for (member in members) {
            val key = normalizeCastName(member.name)
            val existing = result[key]
            if (existing == null || (existing.validProfilePath() == null && member.validProfilePath() != null)) {
                result[key] = member
            }
        }
        return result
    }

    private fun TmdbCastMember.validProfilePath(): String? = profilePath?.takeIf { it.startsWith("/") }

    private fun normalizeCastName(name: String): String {
        val withoutDiacritics = Normalizer.normalize(name, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        return WHITESPACE.replace(withoutDiacritics.lowercase(Locale.ROOT).trim(), " ")
    }
}
