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
import com.flintr.tv.data.local.imdb.ImdbEnrichmentService
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.mapVodInfoToDetail
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.data.tmdb.TmdbCastClient
import com.flintr.tv.data.tmdb.TmdbCastMember
import com.flintr.tv.domain.recommendation.RecommendationEngine
import com.flintr.tv.utils.error.reportWarning
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MovieDetailUiState(
    val isLoading: Boolean = true,
    val baseStream: VodStream? = null,
    val detail: DetailDisplayData? = null,
    val isInWatchlist: Boolean = false,
    val watchProgress: WatchProgress? = null,
    val recommendations: List<VodStream> = emptyList(),
    val castPhotos: Map<String, String> = emptyMap(),
    val error: String? = null
)

/**
 * Loads one movie's detail metadata (get_vod_info), its IMDb-only genre/rating enrichment,
 * watchlist state, watch progress and a similar-movie row.
 *
 * The screen does not navigate itself: the movie id arrives through the navigation route's
 * [SavedStateHandle], and the Phase-4 route owns all navigation callbacks.
 */
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
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
        const val ARG_VOD_ID = "vodId"
        private const val TAG = "MovieDetailViewModel"
        private val YEAR_PATTERN = Regex("(?:19|20)\\d{2}")
        private val TITLE_YEAR_PATTERN = Regex("\\((?:19|20)\\d{2}\\)")
        private const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w185"
        private val COMBINING_MARKS = Regex("\\p{M}")
        private val WHITESPACE = Regex("\\s+")
    }

    private val vodId: Int = savedStateHandle.get<Int>(ARG_VOD_ID)
        ?: savedStateHandle.get<String>(ARG_VOD_ID)?.toIntOrNull()
        ?: -1

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun load() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val credentials = requireCredentials() ?: return@launch
            requireValidVodId() ?: return@launch

            try {
                val cached = cacheService.loadCachedDataAsync()
                val catalogMovie = cached?.movies?.firstOrNull { it.streamId == vodId }
                // Katalog ist bereits lokal vorhanden - Basisdaten (Poster/Titel) sofort
                // zeigen, statt bis zum VodInfo-Call + IMDb-Enrichment leer zu bleiben.
                showCachedBaseStream(catalogMovie)

                val result = repository.loadVodInfo(
                    username = credentials.username,
                    password = credentials.password,
                    vodId = vodId
                )
                if (result.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = null,
                            error = DETAIL_LOAD_ERROR_MESSAGE
                        )
                    }
                    reportWarning(
                        TAG,
                        "Failed to load VOD info for vodId=$vodId",
                        result.exceptionOrNull() ?: Exception("unknown")
                    )
                    return@launch
                }
                val info = result.getOrNull()

                // Basisdaten sofort mappen, ohne auf das (teure) IMDb-Enrichment zu warten -
                // genres/rating bleiben hier bewusst null, siehe DetailDisplayData.
                val baseDetail = mapVodInfoToDetail(
                    info = info,
                    catalogTitle = catalogMovie?.name,
                    imdbLookup = { null }
                )
                if (baseDetail == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = null,
                            error = DETAIL_LOAD_ERROR_MESSAGE
                        )
                    }
                    return@launch
                }

                _uiState.value = buildLoadedState(catalogMovie, baseDetail)

                // TMDB-Cast-Fotos laufen in eigenem Job: duerfen Rating/Genres/Empfehlungen nicht
                // blockieren (ohne tmdbId/Match bleiben die Briefmarken/Initial-Kreis stehen).
                viewModelScope.launch(ioDispatcher) { loadCastPhotos(baseDetail) }

                // IMDb-Enrichment nachschieben: Rating/Genres kommen ausschliesslich von hier,
                // ein Fehler/Miss darf den bereits gezeigten Basis-State nicht zuruecknehmen.
                loadImdbEnrichment(
                    tag = TAG,
                    failureMessage = "IMDb enrichment failed for vodId=$vodId",
                    imdbEnrichmentService = imdbEnrichmentService,
                    lookup = {
                        resolveImdbLookupName(baseDetail.title, baseDetail.releaseDate)
                            ?.let(imdbEnrichmentService::lookup)
                    }
                ) { enrichment ->
                    _uiState.update { state -> state.copy(detail = mergeImdbEnrichment(state.detail, enrichment)) }
                }

                // Genres are IMDb-only and therefore may be null; RecommendationEngine returns
                // an empty row in that case, so the section hides instead of falling back to
                // the (untrusted) Xtream genre string.
                val recommendations = computeRecommendations(cached, _uiState.value.detail?.genres)
                _uiState.update { it.copy(recommendations = recommendations) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        detail = null,
                        error = DETAIL_LOAD_ERROR_MESSAGE
                    )
                }
                reportWarning(TAG, "Error loading movie detail for vodId=$vodId", e)
            }
        }
    }

    private fun requireCredentials(): Credentials? = requireCredentialsOrNull(credentialsRepository) {
        _uiState.value = MovieDetailUiState(
            isLoading = false,
            error = "Keine gespeicherten Anmeldedaten gefunden"
        )
    }

    private fun requireValidVodId(): Int? = requirePositiveIdOrNull(vodId) {
        _uiState.value = MovieDetailUiState(
            isLoading = false,
            error = "Ungültige Film-ID"
        )
    }

    private fun showCachedBaseStream(catalogMovie: VodStream?) {
        if (catalogMovie != null) {
            _uiState.update { it.copy(baseStream = catalogMovie) }
        }
    }

    private suspend fun buildLoadedState(catalogMovie: VodStream?, baseDetail: DetailDisplayData): MovieDetailUiState {
        return MovieDetailUiState(
            isLoading = false,
            baseStream = catalogMovie,
            detail = baseDetail,
            isInWatchlist = findWatchlistEntry() != null,
            watchProgress = watchProgressService.getWatchProgress(
                contentType = "movie",
                streamId = vodId,
                seriesId = null,
                episodeId = null
            ),
            recommendations = emptyList()
        )
    }

    private fun computeRecommendations(cached: CachedData?, genres: List<String>?): List<VodStream> {
        return if (cached != null) {
            RecommendationEngine.recommendSimilar(
                anchorGenres = genres,
                cached = cached,
                excludedMovieIds = setOf(vodId)
            )
        } else {
            emptyList()
        }
    }

    private fun resolveImdbLookupName(title: String?, releaseDate: String?): String? {
        val yearFromTitle = title?.let { YEAR_PATTERN.find(it)?.value?.toInt() }
        val year = releaseDate?.let { YEAR_PATTERN.find(it)?.value?.toInt() } ?: yearFromTitle
        return title?.let { rawTitle ->
            val lookupTitle = if (yearFromTitle != null) {
                rawTitle.replace(TITLE_YEAR_PATTERN, "").trim()
            } else {
                rawTitle
            }
            if (year != null) "$lookupTitle ($year)" else lookupTitle
        }
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

        val members = tmdbCastClient.movieCredits(tmdbId)
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

    fun toggleWatchlist() {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch(ioDispatcher) {
            toggleWatchlistEntry(
                watchlistRepository = watchlistRepository,
                findExisting = ::findWatchlistEntry,
                buildEntry = { buildWatchlistEntry(detail) }
            )?.let { newState -> _uiState.update { it.copy(isInWatchlist = newState) } }
        }
    }

    private fun findWatchlistEntry(): WatchlistEntry? = watchlistRepository.getAllWatchlistEntries()
        .firstOrNull { it.type == WatchlistEntryType.MOVIE && it.streamId == vodId }

    private fun buildWatchlistEntry(detail: DetailDisplayData): WatchlistEntry {
        val title = detail.title ?: "Film $vodId"
        val name = URLEncoder.encode(title, "UTF-8")
        val metadata = mapOf("cover" to detail.posterUrl)
        return WatchlistEntry.create(
            id = "movie:$vodId:$name",
            type = WatchlistEntryType.MOVIE,
            streamId = vodId,
            name = title,
            metadata = metadata
        )
    }
}
