package com.flintr.tv.data.local

import com.flintr.tv.data.model.RatingEntry
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.WatchedItem
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.data.tmdb.TmdbMetaRepository
import com.flintr.tv.domain.recommendation.RecommendationEngine
import com.flintr.tv.domain.recommendation.RecommendationResult
import com.flintr.tv.domain.recommendation.UserContentProfile
import com.flintr.tv.utils.error.reportError
import com.google.firebase.perf.metrics.Trace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

private const val TAG = "RecommendationService"
private const val RECOMPUTE_DEBOUNCE_MS = 300L
private const val RATINGS_CACHE_TTL_MS = 5 * 60 * 1000L
private const val PROGRESS_SIGNAL_DURATION = 100L

/**
 * The inputs that actually influence a recommendation profile. Watch progress is normalized to
 * the thresholds used by [UserContentProfile] so a 10s progress tick does not trigger a recompute.
 */
private data class RecommendationInput(
    val entries: List<WatchlistEntry>,
    val watchedHistory: Map<String, WatchedItem>,
    val watchProgress: Map<String, WatchProgress>,
    // Nur Trigger: Meta-Nachlaeufe aendern die Genre-Signale, nicht das Nutzerprofil selbst.
    val metaCount: Int
)

private data class CachedRatings(
    val username: String,
    val ratings: Map<String, RatingEntry>,
    val loadedAtMs: Long
)

/**
 * Baut aus Watchlist, Verlauf und Fortschritt ein [GenreProfile][com.flintr.tv.domain.recommendation.GenreProfile]
 * (Genre-IDs aus TMDB-Meta) und laedt danach die Top-Genres ueber TMDB-Discover. Die Engine
 * waehlt nur noch aus den bereits geladenen Listen aus; Cold Start spart alle Netz-Calls.
 * Meta-Nachlaeufe re-triggern, weil Genre-Signale aus TMDB-Meta kommen, die erst nach dem Start ankommen.
 * Ratings haben keinen reaktiven Flow und werden fuer [RATINGS_CACHE_TTL_MS] gecacht.
 */
@Singleton
class RecommendationService(
    private val watchlistRepository: WatchlistRepository,
    private val watchedHistoryService: WatchedHistoryService,
    private val watchProgressService: WatchProgressService,
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val cacheService: DataCacheService,
    private val tmdbMetaRepository: TmdbMetaRepository,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    /** DI constructor: Dagger cannot fill Kotlin defaults, so bind the production clock here. */
    @Inject
    constructor(
        watchlistRepository: WatchlistRepository,
        watchedHistoryService: WatchedHistoryService,
        watchProgressService: WatchProgressService,
        userRepository: UserRepository,
        credentialsRepository: CredentialsRepository,
        cacheService: DataCacheService,
        tmdbMetaRepository: TmdbMetaRepository,
        @Named("IO") ioDispatcher: CoroutineDispatcher
    ) : this(
        watchlistRepository = watchlistRepository,
        watchedHistoryService = watchedHistoryService,
        watchProgressService = watchProgressService,
        userRepository = userRepository,
        credentialsRepository = credentialsRepository,
        cacheService = cacheService,
        tmdbMetaRepository = tmdbMetaRepository,
        ioDispatcher = ioDispatcher,
        nowMs = { System.currentTimeMillis() }
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _recommendations = MutableStateFlow(RecommendationResult(emptyList(), emptyList()))
    val recommendations: StateFlow<RecommendationResult> = _recommendations.asStateFlow()

    /** True waehrend eines Recompute-Laufs (jeder Input-Aenderung, auch Meta-Nachlaeufen); false sofort nach Publish. */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Read-through cache for ratings. Fresh ratings reach recommendations only after up to
     * [RATINGS_CACHE_TTL_MS] (5 min). Accepted trade-off: recompute cost > freshness.
     */
    @Volatile
    private var ratingsCache: CachedRatings? = null

    init {
        serviceScope.launch {
            combine(
                watchlistRepository.entries,
                watchedHistoryService.watchedHistory,
                watchProgressService.watchProgress,
                tmdbMetaRepository.metaFlow.map { it.size }
            ) { entries, watchedHistory, watchProgress, metaCount ->
                RecommendationInput(
                    entries = entries,
                    watchedHistory = watchedHistory,
                    watchProgress = watchProgress.mapValues { (_, progress) ->
                        progress.toRecommendationSignal()
                    },
                    metaCount = metaCount
                )
            }
                .distinctUntilChanged()
                .debounce(RECOMPUTE_DEBOUNCE_MS)
                .collectLatest { input ->
                    recompute(input)
                }
        }
    }

    private suspend fun recompute(input: RecommendationInput) {
        val trace = PerfTrace.start("flintr_recommendation_recompute")
        _isLoading.value = true
        try {
            coroutineContext.ensureActive()
            val cached = cacheService.loadCachedDataAsync() ?: return

            coroutineContext.ensureActive()
            val ratings = loadRatings(trace)

            coroutineContext.ensureActive()
            val profile = UserContentProfile.build(
                watchlistEntries = input.entries,
                watchedHistory = input.watchedHistory,
                watchProgress = input.watchProgress,
                ratings = ratings,
                movies = cached.movies,
                series = cached.series,
                // Key = tmdbId des Items (UserContentProfile), series->tv-Uebersetzung bleibt.
                metaLookup = { kind, id ->
                    val key = if (kind == "series") "tv:$id" else "movie:$id"
                    tmdbMetaRepository.metaFlow.value[key]
                }
            )

            // Cold Start vor jedem Netz-Call: ohne genug Kernsignale kein Discover.
            if (profile.signalCount < RecommendationEngine.COLD_START_MIN_SIGNALS) {
                _recommendations.value = RecommendationResult(emptyList(), emptyList())
                return
            }

            val anchorGenreIds = profile.weights.entries
                .filter { it.value > 0.0 }
                .sortedWith(compareByDescending<Map.Entry<Int, Double>> { it.value }.thenBy { it.key })
                .take(RecommendationEngine.TOP_GENRE_COUNT)
                .map { it.key }

            val discoveredMoviesByGenre = linkedMapOf<Int, List<Int>>()
            val discoveredSeriesByGenre = linkedMapOf<Int, List<Int>>()
            anchorGenreIds.forEach { genreId ->
                coroutineContext.ensureActive()
                discoveredMoviesByGenre[genreId] =
                    tmdbMetaRepository.discoverMovieIds(listOf(genreId))
                coroutineContext.ensureActive()
                discoveredSeriesByGenre[genreId] =
                    tmdbMetaRepository.discoverTvIds(listOf(genreId))
            }

            val result = RecommendationEngine.recommend(
                profile = profile,
                cached = cached,
                discoveredMoviesByGenre = discoveredMoviesByGenre,
                discoveredSeriesByGenre = discoveredSeriesByGenre
            )
            // The engine itself is synchronous, so a cancelled run must not publish its stale result.
            coroutineContext.ensureActive()
            _recommendations.value = result
            PerfTrace.increment(trace, "recommendation_movies", result.movies.size.toLong())
            PerfTrace.increment(trace, "recommendation_series", result.series.size.toLong())
        } finally {
            PerfTrace.stop(trace)
            _isLoading.value = false
        }
    }

    private suspend fun loadRatings(trace: Trace?): Map<String, RatingEntry> {
        val username = credentialsRepository.getCredentials()
            ?.username
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()

        val now = nowMs()
        ratingsCache
            ?.takeIf { it.username == username && now - it.loadedAtMs < RATINGS_CACHE_TTL_MS }
            ?.let {
                PerfTrace.increment(trace, "ratings_from_cache")
                return it.ratings
            }

        val ratings = userRepository.loadAllRatings(username)
            .onFailure { reportError(TAG, "Failed to load ratings, continuing without thumbs signal", it) }
            .getOrNull()
            ?: return emptyMap()

        PerfTrace.increment(trace, "ratings_from_network")
        ratingsCache = CachedRatings(username = username, ratings = ratings, loadedAtMs = now)
        return ratings
    }

    /**
     * Collapses a progress value onto the only distinctions [UserContentProfile] evaluates:
     * below 20% (abandoned), 20-90% (started), at/above 90% (completed), plus the watch day
     * used for age decay. Keeping the real position would make every 10s tick distinct.
     */
    internal fun WatchProgress.toRecommendationSignal(): WatchProgress {
        val ratio = if (duration <= 0L) {
            0.0
        } else {
            (position.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
        }
        val bucketPosition = when {
            ratio >= 0.9 -> 100L
            ratio >= 0.2 -> 50L
            ratio > 0.0 -> 10L
            else -> 0L
        }
        val watchedDay = lastWatched.substringBefore('T')
        val normalizedTimestamp = if (watchedDay.length == 10) {
            "${watchedDay}T00:00:00Z"
        } else {
            lastWatched
        }
        return copy(
            position = bucketPosition,
            duration = PROGRESS_SIGNAL_DURATION,
            lastWatched = normalizedTimestamp
        )
    }
}
