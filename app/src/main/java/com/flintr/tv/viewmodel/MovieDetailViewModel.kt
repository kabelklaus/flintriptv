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
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.mapVodInfoToDetail
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.data.tmdb.TmdbCastClient
import com.flintr.tv.data.tmdb.TmdbCastMember
import com.flintr.tv.data.tmdb.TmdbMetaRepository
import com.flintr.tv.data.tmdb.TmdbRecItem
import com.flintr.tv.data.tmdb.TmdbResult
import com.flintr.tv.data.tmdb.TitleNormalizer
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
import kotlin.math.abs

data class MovieDetailUiState(
    val isLoading: Boolean = true,
    val baseStream: VodStream? = null,
    val detail: DetailDisplayData? = null,
    val isInWatchlist: Boolean = false,
    val watchProgress: WatchProgress? = null,
    val recommendations: List<VodStream> = emptyList(),
    val castPhotos: Map<String, String> = emptyMap(),
    /** True zwischen Basis-Anzeige und dem TMDB-Meta-Nachlauf - steuert das Rating/Genre-Skeleton. */
    val isMetaLoading: Boolean = false,
    val error: String? = null
)

/**
 * Loads one movie's detail metadata (get_vod_info), its TMDB genre/rating meta,
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
    private val tmdbMetaRepository: TmdbMetaRepository,
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
        private const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w185"
        private val COMBINING_MARKS = Regex("\\p{M}")
        private val WHITESPACE = Regex("\\s+")
        /**
         * Mindestgroesse fuer den TMDB-Cast-Fallback. Bewusst niedrig: die tmdbId kommt direkt aus
         * der Provider-VodInfo, es gibt also kein Such-Falsch-Treffer-Risiko; ein 2er-TMDB-Cast ist
         * besser als gar keiner (Emulator-Beweis: Film 277386 hat nur 2 TMDB-Cast-Members und zeigte
         * ohne Fallback GAR keinen Cast).
         */
        private const val MIN_CAST_FALLBACK_SIZE = 2
    }

    private val vodId: Int = savedStateHandle.get<Int>(ARG_VOD_ID)
        ?: savedStateHandle.get<String>(ARG_VOD_ID)?.toIntOrNull()
        ?: -1

    /**
     * Memoized Katalog-Titel (Quell-Movies plus normalisierte Titel/Jahre). Die Referenz auf
     * `cached.movies` ist pro Katalog-Load stabil, daher genuegt Identitaetsvergleich.
     * Zugriff nur aus [viewModelScope] auf dem Main-Thread, kein Lock noetig.
     */
    private var catalogTitleCache: Pair<List<VodStream>, List<CatalogTitle>>? = null

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
                // zeigen, statt bis zum VodInfo-Call + TMDB-Meta leer zu bleiben.
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

                // Basisdaten sofort mappen, ohne auf die (teure) TMDB-Meta-Abfrage zu warten -
                // genres/rating bleiben hier bewusst null, siehe DetailDisplayData.
                val baseDetail = mapVodInfoToDetail(
                    info = info,
                    catalogTitle = catalogMovie?.name
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

                // TMDB-Meta nachschieben: Rating/Genres kommen ausschliesslich von hier,
                // ein Fehler/Miss darf den bereits gezeigten Basis-State nicht zuruecknehmen.
                _uiState.update { it.copy(isMetaLoading = true) }
                loadTmdbMeta(
                    tag = TAG,
                    failureMessage = "TMDB meta failed for vodId=$vodId",
                    tmdbMetaRepository = tmdbMetaRepository,
                    lookup = {
                        val detail = _uiState.value.detail
                        tmdbMetaRepository.detailMeta(
                            kind = "movie",
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
                val recommendations = computeRecommendations(cached)
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

    /**
     * Movie-Recs: TMDB-Rec-Titel -> Katalog-Match (Provider hat keine Katalog-tmdbIds, 0/57291).
     *
     * F7 bleibt als erster, netzfreier Index: bereits gecachte Card-Resolves werden ueber ihre
     * tmdbId gematcht. Danach liefert [TmdbMetaRepository.recommendationTitlesFor] die
     * Rec-Titel aus demselben TMDB-Call, der vorher nur IDs lieferte; pro Titel wird gegen
     * normalisierten Katalogtitel plus Jahr (+/-1, beidseitig null-tolerant) gematcht.
     * Dedupe nach streamId, das Anchor-Item ist ausgeschlossen.
     */
    private suspend fun computeRecommendations(cached: CachedData?): List<VodStream> {
        if (cached == null) return emptyList()
        val anchorId = _uiState.value.detail?.tmdbId
            ?.takeIf { it.toIntOrNull() != null }
            ?: return emptyList()
        val resolved = tmdbMetaRepository.cachedResolvedIds(
            kind = "movie",
            items = cached.movies,
            idSelector = { it.streamId },
            titleSelector = { it.name },
            yearSelector = { resolveYear(it.name, it.releaseDate) }
        )
        val index = cached.movies.mapNotNull { movie ->
            val tmdbId = movie.tmdbId?.toIntOrNull() ?: resolved[movie.streamId] ?: return@mapNotNull null
            tmdbId to movie
        }.toMap()

        val recItems = when (val result = tmdbMetaRepository.recommendationTitlesFor("movie", anchorId)) {
            is TmdbResult.Data -> result.value
            TmdbResult.Negative, TmdbResult.Transient -> emptyList()
        }
        val selected = LinkedHashMap<Int, VodStream>()

        // F7 zuerst: die Rec-IDs treffen den netzfreien Card-Resolve-Index, bevor Titel matchen.
        RecommendationEngine.selectSimilarMovies(
            anchorTmdbIds = recItems.map { it.id },
            index = index,
            excludedMovieIds = setOf(vodId)
        ).forEach { selected[it.streamId] = it }

        val cachedTitles = catalogTitleCache
        val catalogTitles = if (cachedTitles != null && cachedTitles.first === cached.movies) {
            cachedTitles.second
        } else {
            cached.movies.map { movie ->
                val (cleanTitle, _) = TitleNormalizer.normalize(movie.name)
                CatalogTitle(
                    cleanTitle = cleanTitle,
                    year = resolveYear(movie.name, movie.releaseDate),
                    movie = movie
                )
            }.also { catalogTitleCache = cached.movies to it }
        }

        for (item in recItems) {
            if (selected.size >= RecommendationEngine.DEFAULT_LIMIT_PER_TYPE) break
            val match = findCatalogMatch(item, catalogTitles, selected) ?: continue
            selected[match.streamId] = match
        }

        return selected.values.take(RecommendationEngine.DEFAULT_LIMIT_PER_TYPE)
    }

    private fun findCatalogMatch(
        item: TmdbRecItem,
        catalogTitles: List<CatalogTitle>,
        selected: Map<Int, VodStream>
    ): VodStream? {
        val recTitle = item.title?.takeIf { it.isNotBlank() } ?: return null
        val (cleanRecTitle, _) = TitleNormalizer.normalize(recTitle)
        if (cleanRecTitle.isBlank()) return null
        val recYear = resolveYear(item.title, item.releaseDate)
        return catalogTitles.firstOrNull { candidate ->
            candidate.movie.streamId != vodId &&
                !selected.containsKey(candidate.movie.streamId) &&
                candidate.cleanTitle == cleanRecTitle &&
                yearCompatible(recYear, candidate.year)
        }?.movie
    }

    /** Jahr +/-1; fehlt eine Seite, entscheidet alleine der normalisierte Titel. */
    private fun yearCompatible(recYear: Int?, catalogYear: Int?): Boolean {
        if (recYear == null || catalogYear == null) return true
        return abs(recYear - catalogYear) <= 1
    }

    private data class CatalogTitle(
        val cleanTitle: String,
        val year: Int?,
        val movie: VodStream
    )

    /** Jahres-Auflösung fuer die TMDB-Suche: bevorzugt `releaseDate`, sonst der Titel. */
    private fun resolveYear(title: String?, releaseDate: String?): Int? =
        releaseDate?.let { YEAR_PATTERN.find(it)?.value?.toInt() }
            ?: title?.let { YEAR_PATTERN.find(it)?.value?.toInt() }

    /**
     * Loads cast photos from TMDB for [detail]'s tmdbId and matches them onto [detail]'s cast
     * names. Unmatched names (no tmdbId, no TMDB match, or no profile photo) simply get no map
     * entry - the cast section falls back to its initial-letter stamp for those.
     *
     * Leerer Xtream-Cast wird aus TMDB-Credits gefuellt (Provider liefert nur teils Cast):
     * Stichprobe zeigt, dass gut ein Drittel der Filme ganz ohne Xtream-Cast ankommt, waehrend
     * [loadTmdbMeta]/tmdbId trotzdem gesetzt sind. Liefert TMDB dafuer mindestens
     * [MIN_CAST_FALLBACK_SIZE] Mitglieder, werden Namen und Fotos 1:1 aus TMDB uebernommen - ohne
     * das Series-Plausibilitaets-Gate (es gibt ja keine Xtream-Namen zum Abgleichen), nur die
     * Mindestgroesse schuetzt vor einem winzigen/falschen TMDB-Treffer. Mit vorhandenem
     * Xtream-Cast bleibt das Namens-Matching unten unveraendert.
     */
    private suspend fun loadCastPhotos(detail: DetailDisplayData?) {
        val tmdbId = detail?.tmdbId?.takeIf { it.isNotBlank() } ?: return
        val castNames = detail.cast
        val members = tmdbCastClient.movieCredits(tmdbId)

        if (castNames.isEmpty()) {
            val nonBlankMembers = members.filter { it.name.isNotBlank() }.take(20)
            if (nonBlankMembers.size < MIN_CAST_FALLBACK_SIZE) return
            val photos = dedupeByNormalizedName(nonBlankMembers).values.mapNotNull { member ->
                member.validProfilePath()?.let { path -> member.name to "$TMDB_IMAGE_BASE_URL$path" }
            }.toMap()
            _uiState.update { state ->
                state.copy(
                    detail = state.detail?.copy(cast = nonBlankMembers.map { it.name }),
                    castPhotos = photos
                )
            }
            return
        }

        val photos = matchCastPhotos(castNames, members)
        _uiState.update { it.copy(castPhotos = photos) }
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
