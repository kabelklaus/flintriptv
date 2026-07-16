package com.flintr.tv.data.model

/**
 * Gemeinsame Test-Factories für Model-Instanzen mit sinnvollen Defaults.
 */
internal fun testVodStream(
    name: String = "Testfilm",
    streamId: Int = 1,
    rating: String? = null,
    rating5Based: Double? = null,
    added: String? = null,
    categoryId: String? = null,
    containerExtension: String? = null
) = VodStream(
    num = null,
    name = name,
    streamType = null,
    streamId = streamId,
    streamIcon = null,
    rating = rating,
    rating5Based = rating5Based,
    added = added,
    categoryId = categoryId,
    categoryName = null,
    containerExtension = containerExtension,
    customSid = null,
    directSource = null,
    releaseDate = null,
    duration = null,
    plot = null,
    cast = null,
    director = null,
    genre = null,
    backdropPath = null,
    youtubeTrailer = null,
    cover = null,
    tmdbId = null
)

internal fun testSeries(
    name: String = "Testserie",
    seriesId: Int = 10,
    rating: String? = null,
    rating5Based: Double? = null,
    lastModified: String? = null,
    categoryId: String? = null,
    cover: String? = null,
    backdropPath: List<String>? = null,
    seasons: List<Season>? = null
) = Series(
    num = null,
    name = name,
    seriesId = seriesId,
    cover = cover,
    plot = null,
    cast = null,
    director = null,
    genre = null,
    releaseDate = null,
    rating = rating,
    rating5Based = rating5Based,
    lastModified = lastModified,
    categoryId = categoryId,
    categoryName = null,
    youtubeTrailer = null,
    backdropPath = backdropPath,
    seasons = seasons
)

internal fun testLiveStream(
    name: String = "Testsender",
    streamId: Int = 100,
    streamIcon: String? = null,
    added: String? = null,
    categoryId: String? = null
) = LiveStream(
    num = null,
    name = name,
    streamType = null,
    streamId = streamId,
    streamIcon = streamIcon,
    epgChannelId = null,
    added = added,
    categoryId = categoryId,
    categoryName = null,
    customSid = null,
    tvArchive = null,
    directSource = null,
    tvArchiveDuration = null
)

internal fun testEpisode(
    id: Int = 500,
    title: String = "Folge 1",
    containerExtension: String? = null,
    season: Int? = 1,
    episodeNum: Int? = null,
    info: EpisodeInfo? = null
) = Episode(
    id = id,
    episodeNum = episodeNum,
    title = title,
    containerExtension = containerExtension,
    info = info,
    subtitles = null,
    added = null,
    season = season,
    directSource = null
)

internal fun testSeason(
    seasonNumber: Int = 1,
    name: String = "Season $seasonNumber",
    episodes: List<Episode>? = null
) = Season(
    airDate = null,
    episodeCount = episodes?.size,
    id = null,
    name = name,
    overview = null,
    seasonNumber = seasonNumber,
    cover = null,
    episodes = episodes
)
