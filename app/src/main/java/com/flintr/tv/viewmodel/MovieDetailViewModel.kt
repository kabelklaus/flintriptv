package com.flintr.tv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.local.imdb.ImdbEnrichmentService
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.data.model.Favorite
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.mapVodInfoToDetail
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.domain.recommendation.RecommendationEngine
import com.flintr.tv.utils.error.reportWarning
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLEncoder
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
    val isFavorite: Boolean = false,
    val watchProgress: WatchProgress? = null,
    val recommendations: List<VodStream> = emptyList(),
    val error: String? = null
)

/**
 * Loads one movie's detail metadata (get_vod_info), its IMDb-only genre/rating enrichment,
 * favorite state, watch progress and a similar-movie row.
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
    private val favoritesRepository: FavoritesRepository,
    private val watchProgressService: WatchProgressService,
    private val cacheService: DataCacheService,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        const val ARG_VOD_ID = "vodId"
        private const val TAG = "MovieDetailViewModel"
        private val YEAR_PATTERN = Regex("(?:19|20)\\d{2}")
        private val TITLE_YEAR_PATTERN = Regex("\\((?:19|20)\\d{2}\\)")
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

            val credentials = credentialsRepository.getCredentials()
            if (credentials == null) {
                _uiState.value = MovieDetailUiState(
                    isLoading = false,
                    error = "Keine gespeicherten Anmeldedaten gefunden"
                )
                return@launch
            }
            if (vodId <= 0) {
                _uiState.value = MovieDetailUiState(
                    isLoading = false,
                    error = "Ungültige Film-ID"
                )
                return@launch
            }

            try {
                val cached = cacheService.loadCachedDataAsync()
                val catalogMovie = cached?.movies?.firstOrNull { it.streamId == vodId }
                if (catalogMovie != null) {
                    // Katalog ist bereits lokal vorhanden - Basisdaten (Poster/Titel) sofort
                    // zeigen, statt bis zum VodInfo-Call + IMDb-Enrichment leer zu bleiben.
                    _uiState.update { it.copy(baseStream = catalogMovie) }
                }

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
                            error = "Details konnten nicht geladen werden"
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
                            error = "Details konnten nicht geladen werden"
                        )
                    }
                    return@launch
                }

                _uiState.value = MovieDetailUiState(
                    isLoading = false,
                    baseStream = catalogMovie,
                    detail = baseDetail,
                    isFavorite = findFavorite() != null,
                    watchProgress = watchProgressService.getWatchProgress(
                        contentType = "movie",
                        streamId = vodId,
                        seriesId = null,
                        episodeId = null
                    ),
                    recommendations = emptyList()
                )

                // IMDb-Enrichment nachschieben: Rating/Genres kommen ausschliesslich von hier,
                // ein Fehler/Miss darf den bereits gezeigten Basis-State nicht zuruecknehmen.
                try {
                    imdbEnrichmentService.ensureLoaded()
                    val lookupName = resolveImdbLookupName(baseDetail.title, baseDetail.releaseDate)
                    val enrichment = lookupName?.let(imdbEnrichmentService::lookup)
                    if (enrichment != null) {
                        _uiState.update { state ->
                            state.copy(
                                detail = state.detail?.copy(
                                    genres = enrichment.genres,
                                    rating = enrichment.rating
                                )
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportWarning(TAG, "IMDb enrichment failed for vodId=$vodId", e)
                }

                // Genres are IMDb-only and therefore may be null; RecommendationEngine returns
                // an empty row in that case, so the section hides instead of falling back to
                // the (untrusted) Xtream genre string.
                val recommendations = if (cached != null) {
                    RecommendationEngine.recommendSimilar(
                        anchorGenres = _uiState.value.detail?.genres,
                        cached = cached,
                        excludedMovieIds = setOf(vodId)
                    )
                } else {
                    emptyList()
                }
                _uiState.update { it.copy(recommendations = recommendations) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        detail = null,
                        error = "Details konnten nicht geladen werden"
                    )
                }
                reportWarning(TAG, "Error loading movie detail for vodId=$vodId", e)
            }
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

    fun toggleFavorite() {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            val existing = findFavorite()
            if (existing != null) {
                if (favoritesRepository.removeFavorite(existing.id)) {
                    _uiState.update { it.copy(isFavorite = false) }
                }
            } else {
                if (favoritesRepository.addFavorite(buildFavorite(detail))) {
                    _uiState.update { it.copy(isFavorite = true) }
                }
            }
        }
    }

    private fun findFavorite(): Favorite? = favoritesRepository.getAllFavorites()
        .firstOrNull { it.type == FavoriteType.MOVIE && it.streamId == vodId }

    private fun buildFavorite(detail: DetailDisplayData): Favorite {
        val title = detail.title ?: "Film $vodId"
        val name = URLEncoder.encode(title, "UTF-8")
        val metadata = mapOf("cover" to detail.posterUrl)
        return Favorite.create(
            id = "movie:$vodId:$name",
            type = FavoriteType.MOVIE,
            streamId = vodId,
            name = title,
            metadata = metadata
        )
    }
}
