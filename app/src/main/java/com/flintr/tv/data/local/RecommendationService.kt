package com.flintr.tv.data.local

import com.flintr.tv.data.model.RatingEntry
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.WatchedItem
import com.flintr.tv.data.repository.UserRepository
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
    val watchProgress: Map<String, WatchProgress>
)

private data class CachedRatings(
    val username: String,
    val ratings: Map<String, RatingEntry>,
    val loadedAtMs: Long
)

/**
 * Combines watchlist entries, watched history and watch progress into a [GenreProfile][com.flintr.tv.domain.recommendation.GenreProfile]
 * and scores the current catalog against it, exposing the "Für dich" recommendations as a
 * [StateFlow]. Ratings have no reactive StateFlow of their own, so they are fetched read-only
 * once and reused for [RATINGS_CACHE_TTL_MS].
 */
@Singleton
class RecommendationService(
    private val watchlistRepository: WatchlistRepository,
    private val watchedHistoryService: WatchedHistoryService,
    private val watchProgressService: WatchProgressService,
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val cacheService: DataCacheService,
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
        @Named("IO") ioDispatcher: CoroutineDispatcher
    ) : this(
        watchlistRepository = watchlistRepository,
        watchedHistoryService = watchedHistoryService,
        watchProgressService = watchProgressService,
        userRepository = userRepository,
        credentialsRepository = credentialsRepository,
        cacheService = cacheService,
        ioDispatcher = ioDispatcher,
        nowMs = { System.currentTimeMillis() }
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _recommendations = MutableStateFlow(RecommendationResult(emptyList(), emptyList()))
    val recommendations: StateFlow<RecommendationResult> = _recommendations.asStateFlow()

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
                watchProgressService.watchProgress
            ) { entries, watchedHistory, watchProgress ->
                RecommendationInput(
                    entries = entries,
                    watchedHistory = watchedHistory,
                    watchProgress = watchProgress.mapValues { (_, progress) ->
                        progress.toRecommendationSignal()
                    }
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
                series = cached.series
            )

            val result = RecommendationEngine.recommend(profile, cached)
            // The engine itself is synchronous, so a cancelled run must not publish its stale result.
            coroutineContext.ensureActive()
            _recommendations.value = result
            PerfTrace.increment(trace, "recommendation_movies", result.movies.size.toLong())
            PerfTrace.increment(trace, "recommendation_series", result.series.size.toLong())
        } finally {
            PerfTrace.stop(trace)
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
