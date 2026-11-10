package com.flintr.tv.data.tmdb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Reaktive Schicht ueber [TmdbMetaClient] und [TmdbMetaCache].
 *
 * [metaFlow] ist die UI-Quelle und enthaelt alle nicht-negativen Metas, auch
 * abgelaufene (Stale-Fallback). [request] koalesziert Grid-Anfragen zu einer
 * gemeinsamen Fetch-Welle; [detailMeta] arbeitet welle-frei und faellt bei
 * fehlender ID auf die Titelsuche zurueck.
 */
@Singleton
class TmdbMetaRepository @Inject constructor(
    private val client: TmdbMetaClient,
    private val discoverClient: TmdbDiscoverClient,
    private val cache: TmdbMetaCache,
    private val normalizer: TitleNormalizer,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) {

    private companion object {
        const val COALESCE_MS = 100L
        const val MAX_PARALLEL = 8
        val NUMERIC_ID = Regex("\\d+")
    }

    private val _metaFlow = MutableStateFlow<Map<String, TmdbMeta>>(emptyMap())
    val metaFlow: StateFlow<Map<String, TmdbMeta>> = _metaFlow.asStateFlow()

    private val ensureMutex = Mutex()

    /** Serialisiert Cache-Recheck plus Suche, damit derselbe Search-Key nur einmal HTTP sieht. */
    private val searchMutex = Mutex()

    @Volatile
    private var loaded = false

    private val pendingMutex = Mutex()
    private val pending = mutableSetOf<String>()
    private var waveCompletion: CompletableDeferred<Unit>? = null

    /** Laedt den Cache einmalig und fuellt [metaFlow]; idempotent und memoized. */
    suspend fun ensureLoaded() {
        if (loaded) return
        ensureMutex.withLock {
            if (loaded) return
            refreshFlow()
            loaded = true
        }
    }

    /** Grid-Pfad: frischer/negativ-frischer Cache-Hit kehrt sofort zurueck, sonst koaleszierte Welle. */
    suspend fun request(kind: String, tmdbId: String) {
        if (!NUMERIC_ID.matches(tmdbId)) return
        ensureLoaded()

        val key = metaKey(kind, tmdbId)
        val now = now()
        val entry = cache.loadAll()[key]
        if (entry != null) {
            if (TmdbMetaCache.isFresh(entry, now)) return
            if (isNegativeFresh(entry, now)) return
        }
        coalescedFetch(key)
    }

    /**
     * Karten-Pfad: numerische tmdbId laeuft direkt, sonst strikter Title-Match ueber die Suche.
     * Die Suche selbst laeuft nicht durch den Leader-Drain; [searchMutex] verhindert nur
     * doppelte Suchen desselben Keys in einer Welle.
     */
    suspend fun resolveCardMeta(
        kind: String,
        title: String,
        year: Int?,
        tmdbId: String?
    ): TmdbMeta? {
        ensureLoaded()

        if (tmdbId != null && NUMERIC_ID.matches(tmdbId)) {
            val key = metaKey(kind, tmdbId)
            val now = now()
            val entry = cache.loadAll()[key]
            if (entry != null) {
                if (TmdbMetaCache.isFresh(entry, now)) return entry.meta
                if (isNegativeFresh(entry, now)) return null
            }
            request(kind, tmdbId)
            return metaFlow.value[key]
        }

        val (clean, titleYear) = normalizer.normalize(title)
        if (clean.isBlank()) return null

        // Wie im Detail-Pfad: explizites Jahr gewinnt, sonst das Jahr aus dem Titel.
        val queryYear = year ?: titleYear
        val key = searchKey(kind, clean, queryYear)
        val now = now()
        val cached = cache.loadAll()[key]
        if (cached != null) {
            if (isNegativeFresh(cached, now)) return null
            if (TmdbMetaCache.isFresh(cached, now)) {
                val resolvedId = cached.meta?.tmdbId ?: return null
                return requestResolved(kind, resolvedId)
            }
        }

        val resolvedId = searchMutex.withLock {
            // Zweiter Check unter dem Lock: derselbe Key in einer Welle sucht nur einmal.
            val current = cache.loadAll()[key]
            val lockNow = now()
            if (current != null) {
                if (isNegativeFresh(current, lockNow)) return@withLock null
                if (TmdbMetaCache.isFresh(current, lockNow)) return@withLock current.meta?.tmdbId
            }

            when (val result = client.searchCandidates(kind, clean, queryYear)) {
                is FetchResult.Data -> {
                    val match = result.value.firstOrNull { candidate ->
                        normalizer.normalize(candidate.title).first == clean &&
                            yearMatches(queryYear, candidate.year)
                    }
                    if (match == null) {
                        cache.putAll(mapOf(key to negativeSearchEntry()))
                        refreshFlow()
                        null
                    } else {
                        val id = match.tmdbId.toInt()
                        // Positiver Search-Key haelt nur die aufgeloeste tmdbId, nicht die Details.
                        cache.putAll(
                            mapOf(
                                key to TmdbMetaCacheEntry(
                                    meta = resolvedMeta(id),
                                    fetchedAtMs = now(),
                                    negative = false
                                )
                            )
                        )
                        refreshFlow()
                        id
                    }
                }
                FetchResult.Negative -> {
                    cache.putAll(mapOf(key to negativeSearchEntry()))
                    refreshFlow()
                    null
                }
                FetchResult.Transient -> null
            }
        }

        return resolvedId?.let { requestResolved(kind, it) }
    }

    /** Detail-Fetch nach aufgeloester Suche; laeuft bewusst durch Koaleszenz/Semaphore. */
    private suspend fun requestResolved(kind: String, tmdbId: Int): TmdbMeta? {
        val id = tmdbId.toString()
        request(kind, id)
        return metaFlow.value[metaKey(kind, id)]
    }

    /**
     * ±1 Jahr: TMDB fuehrt haeufig das Premieren-/Festivaljahr, der Katalog das Release-Jahr.
     * Mehr Abweichung gilt als anderer Film; fehlendes Kandidaten-Jahr matcht nie.
     */
    private fun yearMatches(queryYear: Int?, candidateYear: Int?): Boolean {
        if (queryYear == null) return true
        if (candidateYear == null) return false
        return abs(candidateYear - queryYear) <= 1
    }

    private fun resolvedMeta(tmdbId: Int): TmdbMeta =
        TmdbMeta(tmdbId = tmdbId, rating = 0.0, voteCount = 0, genres = emptyList())

    private fun negativeSearchEntry(): TmdbMetaCacheEntry =
        TmdbMetaCacheEntry(meta = null, fetchedAtMs = now(), negative = true)

    /**
     * Detail-Pfad. Mit numerischer ID wird direkt geladen, sonst ueber die Suche.
     * Transiente Fehler liefern die stale Meta zurueck, sofern vorhanden.
     */
    suspend fun detailMeta(
        kind: String,
        tmdbId: String?,
        title: String,
        year: Int?
    ): TmdbMeta? {
        ensureLoaded()

        if (tmdbId != null && NUMERIC_ID.matches(tmdbId)) {
            val key = metaKey(kind, tmdbId)
            val now = now()
            val entry = cache.loadAll()[key]
            if (entry != null) {
                if (TmdbMetaCache.isFresh(entry, now)) return entry.meta
                if (isNegativeFresh(entry, now)) return null
            }
            return fetchSingle(key, kind, tmdbId, stale = entry?.meta)
        }

        return searchDetail(kind, title, year)
    }

    private suspend fun searchDetail(kind: String, title: String, year: Int?): TmdbMeta? {
        val (clean, titleYear) = normalizer.normalize(title)
        if (clean.isBlank()) return null

        val queryYear = year ?: titleYear
        val searchKey = searchKey(kind, clean, queryYear)
        val now = now()
        val cached = cache.loadAll()[searchKey]
        if (cached != null) {
            if (TmdbMetaCache.isFresh(cached, now)) return cached.meta
            if (isNegativeFresh(cached, now)) return null
        }

        val result = if (kind == "movie") {
            client.searchMovieId(clean, queryYear)
        } else {
            client.searchTvId(clean, queryYear)
        }

        return when (result) {
            is TmdbResult.Data -> {
                val id = result.value.toString()
                val key = metaKey(kind, id)
                val stale = cache.loadAll()[key]?.meta
                val entry = fetchMetaEntry(kind, id)
                    ?: return stale
                val fetchedAt = now()
                val writes = LinkedHashMap<String, TmdbMetaCacheEntry>()
                writes[key] = entry.copy(fetchedAtMs = fetchedAt)
                // Positives Suchergebnis auch am Search-Key ablegen: kuenftige Lookups treffen ohne Suche.
                if (!entry.negative && entry.meta != null) {
                    writes[searchKey] = entry.copy(fetchedAtMs = fetchedAt)
                }
                cache.putAll(writes)
                refreshFlow()
                entry.meta
            }
            TmdbResult.Negative -> {
                cache.putAll(
                    mapOf(
                        searchKey to TmdbMetaCacheEntry(
                            meta = null,
                            fetchedAtMs = now(),
                            negative = true
                        )
                    )
                )
                refreshFlow()
                null
            }
            TmdbResult.Transient -> null
        }
    }

    /** Einzel-Fetch ohne Koaleszenz; haelt bei Transient die vorhandene Stale-Meta unveraendert. */
    private suspend fun fetchSingle(
        key: String,
        kind: String,
        tmdbId: String,
        stale: TmdbMeta?
    ): TmdbMeta? {
        val entry = fetchMetaEntry(kind, tmdbId) ?: return stale
        cache.putAll(mapOf(key to entry.copy(fetchedAtMs = now())))
        refreshFlow()
        return entry.meta
    }

    /**
     * Leader-Drain: der erste Caller wartet [COALESCE_MS], sammelt dann alle Pending-Keys
     * und fetcht sie parallel. Folgecaller warten auf die Completion derselben Welle.
     */
    private suspend fun coalescedFetch(key: String) {
        val completion: CompletableDeferred<Unit>
        val isLeader: Boolean
        pendingMutex.withLock {
            val activeWave = waveCompletion
            completion = activeWave ?: CompletableDeferred<Unit>().also { waveCompletion = it }
            isLeader = activeWave == null
            pending += key
        }

        if (!isLeader) {
            completion.await()
            return
        }

        try {
            delay(COALESCE_MS)
            while (true) {
                val keys = pendingMutex.withLock {
                    if (pending.isEmpty()) {
                        val done = waveCompletion
                        waveCompletion = null
                        done?.complete(Unit)
                        return
                    }
                    pending.toList().also { pending.clear() }
                }
                fetchWave(keys)
            }
        } catch (e: CancellationException) {
            abortWave(e)
            throw e
        } catch (e: Exception) {
            abortWave(e)
            throw e
        }
    }

    /** Bricht eine laufende Welle ab, damit wartende Caller nicht haengen bleiben. */
    private suspend fun abortWave(cause: Throwable) {
        pendingMutex.withLock {
            pending.clear()
            val done = waveCompletion
            waveCompletion = null
            done?.completeExceptionally(cause)
        }
    }

    /** Baut die Update-Map der Welle in einem Zug und refresht den Flow danach einmal. */
    private suspend fun fetchWave(keys: Collection<String>) = withContext(ioDispatcher) {
        val updates = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL)
            keys.distinct()
                .map { key ->
                    async {
                        semaphore.withPermit { fetchEntry(key) }
                    }
                }
                .awaitAll()
        }.filterNotNull().toMap()

        if (updates.isNotEmpty()) {
            cache.putAll(updates)
            refreshFlow()
        }
    }

    /** Prueft den Key erneut und liefert nur bei tatsaechlichem Fetch einen Cache-Eintrag. */
    private suspend fun fetchEntry(key: String): Pair<String, TmdbMetaCacheEntry>? {
        val now = now()
        val existing = cache.loadAll()[key]
        if (existing != null) {
            if (TmdbMetaCache.isFresh(existing, now)) return null
            if (isNegativeFresh(existing, now)) return null
        }

        val separator = key.indexOf(':')
        if (separator <= 0 || separator == key.lastIndex) return null
        val kind = key.substring(0, separator)
        val tmdbId = key.substring(separator + 1)

        return fetchMetaEntry(kind, tmdbId)?.let { key to it }
    }

    private suspend fun fetchMetaEntry(kind: String, tmdbId: String): TmdbMetaCacheEntry? {
        val result = if (kind == "movie") {
            client.movieMeta(tmdbId)
        } else {
            client.tvMeta(tmdbId)
        }
        val fetchedAt = now()
        return when (result) {
            is TmdbResult.Data -> TmdbMetaCacheEntry(meta = result.value, fetchedAtMs = fetchedAt)
            TmdbResult.Negative ->
                TmdbMetaCacheEntry(meta = null, fetchedAtMs = fetchedAt, negative = true)
            TmdbResult.Transient -> null
        }
    }

    /** Top-bewertete tmdbIds pro Genre-Set, 24h gecacht, stale-fallback bei Transient. */
    suspend fun discoverMovieIds(genreIds: List<Int>, pages: Int = 2): List<Int> =
        discoverIds("movie", genreIds, pages)

    suspend fun discoverTvIds(genreIds: List<Int>, pages: Int = 2): List<Int> =
        discoverIds("tv", genreIds, pages)

    /** Recommendations eines Anchors, 24h gecacht. */
    suspend fun recommendationsFor(kind: String, tmdbId: String): List<Int> {
        val key = "discover:rec:$kind:$tmdbId"
        val now = now()
        val entry = cache.loadAll()[key]
        if (entry != null) {
            if (entry.discoverIds != null && TmdbMetaCache.isFresh(entry, now)) {
                return entry.discoverIds
            }
            if (isNegativeFresh(entry, now)) return emptyList()
        }

        val result = if (kind == "movie") {
            discoverClient.movieRecommendations(tmdbId)
        } else {
            discoverClient.tvRecommendations(tmdbId)
        }
        return when (result) {
            is TmdbResult.Data -> {
                cache.putAll(mapOf(key to discoverEntry(result.value)))
                result.value
            }
            TmdbResult.Negative -> {
                cache.putAll(mapOf(key to negativeDiscoverEntry()))
                emptyList()
            }
            TmdbResult.Transient -> entry?.discoverIds ?: emptyList()
        }
    }

    /** Fetcht Pages sequenziell; Negative/leer bricht ab, Transient liefert stale IDs. */
    private suspend fun discoverIds(kind: String, genreIds: List<Int>, pages: Int): List<Int> {
        // Sortiert-distinct, damit ("28","18") und ("18","28") denselben Key treffen.
        val genreKey = genreIds.distinct().sorted().joinToString("|")
        val ids = mutableListOf<Int>()

        for (page in 1..pages) {
            val key = "discover:$kind:$genreKey:$page"
            val now = now()
            val entry = cache.loadAll()[key]
            if (entry != null) {
                if (entry.discoverIds != null && TmdbMetaCache.isFresh(entry, now)) {
                    ids += entry.discoverIds
                    continue
                }
                if (isNegativeFresh(entry, now)) break
            }

            val result = if (kind == "movie") {
                discoverClient.discoverMovies(genreIds, page)
            } else {
                discoverClient.discoverTv(genreIds, page)
            }
            when (result) {
                is TmdbResult.Data -> {
                    cache.putAll(mapOf(key to discoverEntry(result.value)))
                    ids += result.value
                }
                TmdbResult.Negative -> {
                    cache.putAll(mapOf(key to negativeDiscoverEntry()))
                    break
                }
                TmdbResult.Transient -> {
                    entry?.discoverIds?.let { ids += it }
                    return ids
                }
            }
        }
        return ids
    }

    private fun discoverEntry(ids: List<Int>): TmdbMetaCacheEntry =
        TmdbMetaCacheEntry(discoverIds = ids, fetchedAtMs = now(), meta = null, negative = false)

    private fun negativeDiscoverEntry(): TmdbMetaCacheEntry =
        TmdbMetaCacheEntry(discoverIds = null, fetchedAtMs = now(), meta = null, negative = true)

    private suspend fun refreshFlow() {
        _metaFlow.value = cache.loadAll()
            .mapNotNull { (key, entry) ->
                val meta = entry.meta
                if (!entry.negative && meta != null) key to meta else null
            }
            .toMap()
    }

    private fun metaKey(kind: String, tmdbId: String): String = "$kind:$tmdbId"

    private fun searchKey(kind: String, clean: String, year: Int?): String =
        "search:$kind:${normalizer.makeKey(clean, year)}"

    private fun isNegativeFresh(entry: TmdbMetaCacheEntry, nowMs: Long): Boolean =
        entry.negative && nowMs - entry.fetchedAtMs < TmdbMetaCache.TTL_MS

    private fun now(): Long = System.currentTimeMillis()
}
