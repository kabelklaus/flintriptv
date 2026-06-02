package com.flintr.tv.ui.home

import com.flintr.tv.data.model.Favorite
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.CategoryType
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HomeContentModelsTest {

    @Test
    fun `featured items contain at most five movies and five series`() {
        val movies = (1..8).map { movie(id = it, added = (100 + it).toString()) }
        val series = (1..8).map { series(id = it, lastModified = (200 + it).toString()) }

        val result = selectFeaturedItems(
            movies = movies,
            series = series,
            hiddenVodCategoryIds = emptySet(),
            hiddenSeriesCategoryIds = emptySet()
        )

        assertEquals(10, result.size)
        assertEquals(5, result.filterIsInstance<HomeFeaturedItem.Movie>().size)
        assertEquals(5, result.filterIsInstance<HomeFeaturedItem.SeriesItem>().size)
    }

    @Test
    fun `featured item sorting prefers newest timestamp and rating on ties`() {
        val movies = listOf(
            movie(id = 1, added = "100", rating = "6.0"),
            movie(id = 2, added = "200", rating = "5.0"),
            movie(id = 3, added = "200", rating = "9.0")
        )

        val result = selectFeaturedItems(
            movies = movies,
            series = emptyList(),
            hiddenVodCategoryIds = emptySet(),
            hiddenSeriesCategoryIds = emptySet()
        ).filterIsInstance<HomeFeaturedItem.Movie>()

        assertEquals(listOf(3, 2, 1), result.map { it.vod.streamId })
    }

    @Test
    fun `featured items exclude hidden categories`() {
        val result = selectFeaturedItems(
            movies = listOf(
                movie(id = 1, categoryId = "visible"),
                movie(id = 2, categoryId = "hidden")
            ),
            series = listOf(
                series(id = 1, categoryId = "hidden-series"),
                series(id = 2, categoryId = "visible-series")
            ),
            hiddenVodCategoryIds = setOf("hidden"),
            hiddenSeriesCategoryIds = setOf("hidden-series")
        )

        assertEquals(listOf("Movie 1", "Series 2"), result.map { it.title })
    }

    @Test
    fun `featured image uses backdrop before cover and stream icon`() {
        val result = selectFeaturedItems(
            movies = listOf(
                movie(
                    id = 1,
                    backdropPath = listOf("https://image/backdrop.jpg"),
                    cover = "https://image/cover.jpg",
                    streamIcon = "https://image/icon.jpg"
                )
            ),
            series = emptyList(),
            hiddenVodCategoryIds = emptySet(),
            hiddenSeriesCategoryIds = emptySet()
        ).single()

        assertEquals("https://image/backdrop.jpg", result.imageUrl)
    }

    @Test
    fun `featured movie image falls back to cover before stream icon`() {
        val result = selectFeaturedItems(
            movies = listOf(
                movie(
                    id = 1,
                    backdropPath = null,
                    cover = "https://image/cover.jpg",
                    streamIcon = "https://image/icon.jpg"
                )
            ),
            series = emptyList(),
            hiddenVodCategoryIds = emptySet(),
            hiddenSeriesCategoryIds = emptySet()
        ).single()

        assertEquals("https://image/cover.jpg", result.imageUrl)
    }

    @Test
    fun `featured movie image falls back to stream icon when cover is missing`() {
        val result = selectFeaturedItems(
            movies = listOf(
                movie(
                    id = 1,
                    backdropPath = null,
                    cover = null,
                    streamIcon = "https://image/icon.jpg"
                )
            ),
            series = emptyList(),
            hiddenVodCategoryIds = emptySet(),
            hiddenSeriesCategoryIds = emptySet()
        ).single()

        assertEquals("https://image/icon.jpg", result.imageUrl)
    }

    @Test
    fun `featured series image falls back to cover`() {
        val result = selectFeaturedItems(
            movies = emptyList(),
            series = listOf(
                series(
                    id = 1,
                    backdropPath = null,
                    cover = "https://image/series-cover.jpg"
                )
            ),
            hiddenVodCategoryIds = emptySet(),
            hiddenSeriesCategoryIds = emptySet()
        ).single()

        assertEquals("https://image/series-cover.jpg", result.imageUrl)
    }

    @Test
    fun `featured image ignores null and blank backdrop entries`() {
        assertEquals("https://image/backdrop.jpg", listOf(null, "", "https://image/backdrop.jpg").firstUsable())

        val result = selectFeaturedItems(
            movies = listOf(
                movie(
                    id = 1,
                    backdropPath = listOf(null, "", "https://image/backdrop.jpg"),
                    cover = "https://image/cover.jpg"
                )
            ),
            series = emptyList(),
            hiddenVodCategoryIds = emptySet(),
            hiddenSeriesCategoryIds = emptySet()
        ).single()

        assertEquals("https://image/backdrop.jpg", result.imageUrl)
    }

    @Test
    fun `in progress items include only movie and series progress sorted by last watched`() {
        val progress = mapOf(
            "movie:10" to watchProgress("2026-01-01T10:00:00Z"),
            "series:20:5" to watchProgress("2026-01-02T10:00:00Z"),
            "live:30" to watchProgress("2026-01-03T10:00:00Z")
        )

        val result = selectInProgressItems(
            allProgress = progress,
            movies = listOf(movie(id = 10)),
            series = listOf(series(id = 20))
        )

        assertEquals(listOf(HomeItemType.SERIES, HomeItemType.MOVIE), result.map { it.type })
    }

    @Test
    fun `favorite items include live movie and series sorted by added date`() {
        val favorites = listOf(
            favorite(type = FavoriteType.MOVIE, streamId = 10, addedAt = "2026-01-01T10:00:00Z"),
            favorite(type = FavoriteType.LIVE, streamId = 30, addedAt = "2026-01-03T10:00:00Z"),
            favorite(type = FavoriteType.SERIES, streamId = 20, addedAt = "2026-01-02T10:00:00Z")
        )

        val result = selectFavoriteItems(
            favorites = favorites,
            channels = listOf(channel(id = 30)),
            movies = listOf(movie(id = 10)),
            series = listOf(series(id = 20))
        )

        assertEquals(listOf(HomeItemType.LIVE, HomeItemType.SERIES, HomeItemType.MOVIE), result.map { it.type })
    }

    @Test
    fun `series progress keeps newest episode per series`() {
        val result = selectInProgressItems(
            allProgress = mapOf(
                "series:20:1" to watchProgress("2026-01-01T10:00:00Z"),
                "series:20:2" to watchProgress("2026-01-03T10:00:00Z")
            ),
            movies = emptyList(),
            series = listOf(series(id = 20))
        )

        assertEquals(1, result.size)
        assertTrue(result.single() is HomeRowItem.SeriesItem)
        assertEquals(Instant.parse("2026-01-03T10:00:00Z").toEpochMilli(), result.single().sortTimestamp)
    }

    @Test
    fun `home content cache key is stable for reordered favorites and progress`() {
        val favorites = listOf(
            favorite(type = FavoriteType.MOVIE, streamId = 10, addedAt = "2026-01-01T10:00:00Z"),
            favorite(type = FavoriteType.SERIES, streamId = 20, addedAt = "2026-01-02T10:00:00Z")
        )
        val progress = mapOf(
            "movie:10" to watchProgress("2026-01-01T10:00:00Z"),
            "series:20:1" to watchProgress("2026-01-02T10:00:00Z")
        )

        val firstKey = createHomeContentCacheKey(
            cacheTimestamp = "2026-01-01T00:00:00.000+0000",
            hiddenCategories = mapOf(CategoryType.VOD to setOf("hidden-2", "hidden-1")),
            favorites = favorites,
            progress = progress
        )
        val secondKey = createHomeContentCacheKey(
            cacheTimestamp = "2026-01-01T00:00:00.000+0000",
            hiddenCategories = mapOf(CategoryType.VOD to setOf("hidden-1", "hidden-2")),
            favorites = favorites.reversed(),
            progress = progress.toList().reversed().toMap()
        )

        assertEquals(firstKey, secondKey)
    }

    @Test
    fun `home content cache key changes when hidden categories change`() {
        val baseKey = createHomeContentCacheKey(
            cacheTimestamp = "2026-01-01T00:00:00.000+0000",
            hiddenCategories = mapOf(CategoryType.VOD to setOf("hidden-1")),
            favorites = emptyList(),
            progress = emptyMap()
        )
        val changedKey = createHomeContentCacheKey(
            cacheTimestamp = "2026-01-01T00:00:00.000+0000",
            hiddenCategories = mapOf(CategoryType.VOD to setOf("hidden-2")),
            favorites = emptyList(),
            progress = emptyMap()
        )

        assertTrue(baseKey != changedKey)
    }

    private fun movie(
        id: Int,
        added: String = "100",
        rating: String? = "7.0",
        categoryId: String? = "cat",
        backdropPath: List<String?>? = null,
        cover: String? = null,
        streamIcon: String? = null
    ) = VodStream(
        num = null,
        name = "Movie $id",
        streamType = "movie",
        streamId = id,
        streamIcon = streamIcon,
        rating = rating,
        rating5Based = null,
        added = added,
        categoryId = categoryId,
        categoryName = null,
        containerExtension = "mkv",
        customSid = null,
        directSource = null,
        releaseDate = null,
        duration = null,
        plot = "Movie plot $id",
        cast = null,
        director = null,
        genre = null,
        backdropPath = backdropPath?.filterNotNull(),
        youtubeTrailer = null,
        cover = cover,
        tmdbId = null
    )

    private fun series(
        id: Int,
        lastModified: String = "100",
        rating: String? = "7.0",
        categoryId: String? = "cat",
        backdropPath: List<String?>? = null,
        cover: String? = null
    ) = Series(
        num = null,
        name = "Series $id",
        seriesId = id,
        cover = cover,
        plot = "Series plot $id",
        cast = null,
        director = null,
        genre = null,
        releaseDate = null,
        rating = rating,
        rating5Based = null,
        lastModified = lastModified,
        categoryId = categoryId,
        categoryName = null,
        youtubeTrailer = null,
        backdropPath = backdropPath?.filterNotNull(),
        seasons = null
    )

    private fun channel(id: Int) = LiveStream(
        num = null,
        name = "Channel $id",
        streamType = "live",
        streamId = id,
        streamIcon = null,
        epgChannelId = null,
        added = null,
        categoryId = null,
        categoryName = null,
        customSid = null,
        tvArchive = null,
        directSource = null,
        tvArchiveDuration = null
    )

    private fun favorite(type: FavoriteType, streamId: Int, addedAt: String) = Favorite(
        id = "$type:$streamId",
        type = type,
        streamId = streamId,
        name = "$type $streamId",
        metadata = null,
        addedAt = addedAt
    )

    private fun watchProgress(lastWatched: String) = WatchProgress(
        position = 60_000L,
        duration = 600_000L,
        lastWatched = lastWatched,
        title = "Progress"
    )
}
