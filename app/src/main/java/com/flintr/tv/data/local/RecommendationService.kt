package com.flintr.tv.data.local

import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.WatchedItem
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.domain.recommendation.RecommendationEngine
import com.flintr.tv.domain.recommendation.RecommendationResult
import com.flintr.tv.domain.recommendation.UserContentProfile
import com.flintr.tv.utils.error.reportError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "RecommendationService"
private const val RECOMPUTE_DEBOUNCE_MS = 300L

/**
 * Combines watchlist entries, watched history and watch progress into a [GenreProfile][com.flintr.tv.domain.recommendation.GenreProfile]
 * and scores the current catalog against it, exposing the "Für dich" recommendations as a
 * [StateFlow]. Ratings are re-fetched (read-only) on every recompute since they have no
 * reactive StateFlow of their own.
 */
@Singleton
class RecommendationService @Inject constructor(
    private val watchlistRepository: WatchlistRepository,
    private val watchedHistoryService: WatchedHistoryService,
    private val watchProgressService: WatchProgressService,
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val cacheService: DataCacheService,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) {
    private val serviceScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _recommendations = MutableStateFlow(RecommendationResult(emptyList(), emptyList()))
    val recommendations: StateFlow<RecommendationResult> = _recommendations.asStateFlow()

    init {
        serviceScope.launch {
            combine(
                watchlistRepository.entries,
                watchedHistoryService.watchedHistory,
                watchProgressService.watchProgress
            ) { entries, watchedHistory, watchProgress ->
                Triple(entries, watchedHistory, watchProgress)
            }
                .debounce(RECOMPUTE_DEBOUNCE_MS)
                .collect { (entries, watchedHistory, watchProgress) ->
                    recompute(entries, watchedHistory, watchProgress)
                }
        }
    }

    private suspend fun recompute(
        entries: List<WatchlistEntry>,
        watchedHistory: Map<String, WatchedItem>,
        watchProgress: Map<String, WatchProgress>
    ) {
        val cached = cacheService.loadCachedDataAsync() ?: return
        val username = credentialsRepository.getCredentials()?.username

        val ratings = username?.takeIf { it.isNotBlank() }?.let { uname ->
            userRepository.loadAllRatings(uname)
                .onFailure { reportError(TAG, "Failed to load ratings, continuing without thumbs signal", it) }
                .getOrNull()
        } ?: emptyMap()

        val profile = UserContentProfile.build(
            watchlistEntries = entries,
            watchedHistory = watchedHistory,
            watchProgress = watchProgress,
            ratings = ratings,
            movies = cached.movies,
            series = cached.series
        )

        _recommendations.value = RecommendationEngine.recommend(profile, cached)
    }
}
