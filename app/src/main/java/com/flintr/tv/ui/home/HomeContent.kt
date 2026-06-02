package com.flintr.tv.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.CategoryType
import com.flintr.tv.data.model.Favorite
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.ui.components.CardConfig
import com.flintr.tv.ui.components.LiveStreamCard
import com.flintr.tv.ui.components.LiveStreamCardParams
import com.flintr.tv.ui.components.VODCard
import com.flintr.tv.ui.components.VODCardParams
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import kotlinx.coroutines.CoroutineDispatcher

private const val HERO_ROTATION_MS = 8_000
private val HomeHeroHeight = 680.dp
private val HomeContentBottomFocusInset = Dimensions.spacingXL

data class HomeContentParams(
    val cacheService: DataCacheService,
    val homeContentCache: HomeContentCache,
    val favoritesRepository: FavoritesRepository,
    val watchProgressService: WatchProgressService,
    val ioDispatcher: CoroutineDispatcher,
    val hiddenCategories: Map<CategoryType, Set<String>>,
    val focusRequester: FocusRequester,
    val onNavigateLeft: () -> Unit,
    val onVodClick: (VodStream) -> Unit,
    val onSeriesClick: (Series) -> Unit,
    val onLiveStreamClick: (LiveStream) -> Unit,
    val modifier: Modifier = Modifier
)

class HomeContentCache {
    private var entry: Entry? = null

    internal val latestData: HomeUiData?
        get() = entry?.data

    internal fun get(key: HomeContentCacheKey): HomeUiData? {
        return entry?.takeIf { it.key == key }?.data
    }

    internal fun put(key: HomeContentCacheKey, data: HomeUiData) {
        entry = Entry(key = key, data = data)
    }

    private data class Entry(
        val key: HomeContentCacheKey,
        val data: HomeUiData
    )
}

internal data class HomeUiData(
    val featuredItems: List<HomeFeaturedItem> = emptyList(),
    val inProgressItems: List<HomeRowItem> = emptyList(),
    val favoriteItems: List<HomeRowItem> = emptyList()
) {
    val isEmpty: Boolean
        get() = featuredItems.isEmpty() && inProgressItems.isEmpty() && favoriteItems.isEmpty()
}

internal data class HomeContentCacheKey(
    val cacheTimestamp: String?,
    val hiddenCategories: List<HomeHiddenCategorySnapshot>,
    val favorites: List<HomeFavoriteSnapshot>,
    val progress: List<HomeProgressSnapshot>
)

internal data class HomeHiddenCategorySnapshot(
    val type: CategoryType,
    val categoryIds: List<String>
)

internal data class HomeFavoriteSnapshot(
    val type: FavoriteType,
    val streamId: Int,
    val addedAt: String
)

internal data class HomeProgressSnapshot(
    val key: String,
    val position: Long,
    val duration: Long,
    val lastWatched: String?
)

@Composable
fun HomeContent(params: HomeContentParams) {
    var homeData by remember { mutableStateOf(params.homeContentCache.latestData ?: HomeUiData()) }
    var isLoading by remember { mutableStateOf(params.homeContentCache.latestData == null) }

    LaunchedEffect(params.hiddenCategories) {
        val hasVisibleCachedData = params.homeContentCache.latestData != null
        if (!hasVisibleCachedData) {
            isLoading = true
        }

        params.favoritesRepository.syncIfNeeded()
        params.watchProgressService.syncIfNeeded()
        params.watchProgressService.cleanupCompletedProgress()

        val progress = params.watchProgressService.getAllInProgress()
        val favorites = params.favoritesRepository.getAllFavorites()
        val cacheKey = createHomeContentCacheKey(
            cacheTimestamp = params.cacheService.getCacheTimestamp(),
            hiddenCategories = params.hiddenCategories,
            favorites = favorites,
            progress = progress
        )
        val cachedHomeData = params.homeContentCache.get(cacheKey)
        if (cachedHomeData != null) {
            homeData = cachedHomeData
            isLoading = false
            return@LaunchedEffect
        }

        val cachedData = params.cacheService.loadCachedDataAsync()
        val newHomeData = if (cachedData == null) {
            HomeUiData()
        } else {
            val hiddenVod = params.hiddenCategories[CategoryType.VOD].orEmpty()
            val hiddenSeries = params.hiddenCategories[CategoryType.SERIES].orEmpty()

            HomeUiData(
                featuredItems = selectFeaturedItems(
                    movies = cachedData.movies,
                    series = cachedData.series,
                    hiddenVodCategoryIds = hiddenVod,
                    hiddenSeriesCategoryIds = hiddenSeries
                ),
                inProgressItems = selectInProgressItems(
                    allProgress = progress,
                    movies = cachedData.movies,
                    series = cachedData.series
                ),
                favoriteItems = selectFavoriteItems(
                    favorites = favorites,
                    channels = cachedData.channels,
                    movies = cachedData.movies,
                    series = cachedData.series
                )
            )
        }
        params.homeContentCache.put(cacheKey, newHomeData)
        homeData = newHomeData
        isLoading = false
    }

    when {
        isLoading -> HomeLoading(modifier = params.modifier)
        homeData.isEmpty -> EmptyHomeState(params)
        else -> HomeContentLoaded(params = params, homeData = homeData)
    }
}

internal fun createHomeContentCacheKey(
    cacheTimestamp: String?,
    hiddenCategories: Map<CategoryType, Set<String>>,
    favorites: List<Favorite>,
    progress: Map<String, WatchProgress>
): HomeContentCacheKey {
    return HomeContentCacheKey(
        cacheTimestamp = cacheTimestamp,
        hiddenCategories = hiddenCategories.map { (type, categoryIds) ->
            HomeHiddenCategorySnapshot(
                type = type,
                categoryIds = categoryIds.sorted()
            )
        }.sortedBy { it.type.name },
        favorites = favorites.map { favorite ->
            HomeFavoriteSnapshot(
                type = favorite.type,
                streamId = favorite.streamId,
                addedAt = favorite.addedAt
            )
        }.sortedWith(
            compareBy<HomeFavoriteSnapshot> { it.type.name }
                .thenBy { it.streamId }
                .thenBy { it.addedAt }
        ),
        progress = progress.map { (key, item) ->
            HomeProgressSnapshot(
                key = key,
                position = item.position,
                duration = item.duration,
                lastWatched = item.lastWatched
            )
        }.sortedBy { it.key }
    )
}

@Composable
private fun HomeLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 2.dp,
            color = appColors().accentColor
        )
    }
}

@Composable
private fun EmptyHomeState(params: HomeContentParams) {
    Box(
        modifier = params.modifier
            .fillMaxSize()
            .focusRequester(params.focusRequester)
            .focusable()
            .onDpadDirection(onLeft = {
                params.onNavigateLeft()
                true
            }),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Keine Inhalte fuer die Startseite gefunden",
            color = appColors().textSecondary,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun HomeContentLoaded(
    params: HomeContentParams,
    homeData: HomeUiData
) {
    LazyColumn(
        modifier = params.modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = HomeContentBottomFocusInset),
        contentPadding = PaddingValues(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLG)
    ) {
        item {
            HomeHero(
                items = homeData.featuredItems,
                focusRequester = params.focusRequester,
                onNavigateLeft = params.onNavigateLeft,
                onItemClick = { item ->
                    when (item) {
                        is HomeFeaturedItem.Movie -> params.onVodClick(item.vod)
                        is HomeFeaturedItem.SeriesItem -> params.onSeriesClick(item.series)
                    }
                }
            )
        }

        if (homeData.inProgressItems.isNotEmpty()) {
            item {
                HomeRow(
                    title = "Zuletzt geschaut",
                    items = homeData.inProgressItems,
                    params = params
                )
            }
        }

        if (homeData.favoriteItems.isNotEmpty()) {
            item {
                HomeRow(
                    title = "Meine Favoriten",
                    items = homeData.favoriteItems,
                    params = params
                )
            }
        }
    }
}

@Composable
private fun HomeHero(
    items: List<HomeFeaturedItem>,
    focusRequester: FocusRequester,
    onNavigateLeft: () -> Unit,
    onItemClick: (HomeFeaturedItem) -> Unit
) {
    if (items.isEmpty()) {
        Spacer(modifier = Modifier.height(24.dp))
        return
    }

    var selectedIndex by remember(items) { mutableIntStateOf(0) }
    var isFocused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val selectedItem = items[selectedIndex.coerceIn(items.indices)]
    val colors = appColors()

    LaunchedEffect(items, selectedIndex) {
        progress.snapTo(0f)
        if (items.size > 1) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = HERO_ROTATION_MS, easing = LinearEasing)
            )
            selectedIndex = (selectedIndex + 1) % items.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HomeHeroHeight)
            .padding(horizontal = Dimensions.spacingMD, vertical = Dimensions.spacingSM)
            .clip(RoundedCornerShape(Dimensions.radiusMD))
            .border(
                width = 2.dp,
                color = if (isFocused) colors.accentColor else Color.Transparent,
                shape = RoundedCornerShape(Dimensions.radiusMD)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onDpadDirection(
                onLeft = {
                    onNavigateLeft()
                    true
                },
                onRight = {
                    selectedIndex = (selectedIndex + 1) % items.size
                    true
                }
            )
            .onClick(onClick = { onItemClick(selectedItem) })
    ) {
        val imageUrl = selectedItem.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            Image(
                painter = rememberAsyncImagePainter(model = imageUrl),
                contentDescription = selectedItem.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bgCard)
            )
        }

        HeroGradientOverlay()
        HeroMetadata(item = selectedItem)
        HeroIndicators(
            items = items,
            selectedIndex = selectedIndex,
            progress = progress.value,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun HeroGradientOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        SharedColors.overlayBlack.copy(alpha = 0.88f),
                        SharedColors.overlayBlack.copy(alpha = 0.48f),
                        SharedColors.overlayBlack.copy(alpha = 0.05f)
                    )
                )
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SharedColors.overlayBlack.copy(alpha = 0.16f),
                        SharedColors.overlayBlack.copy(alpha = 0.72f)
                    )
                )
            )
    )
}

@Composable
private fun HeroMetadata(item: HomeFeaturedItem) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(560.dp)
            .padding(start = 40.dp, top = 28.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 31.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(Dimensions.spacingSM))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSM)
        ) {
            item.rating?.let { rating ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = SharedColors.contentCardRatingIconColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = rating,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = when (item) {
                    is HomeFeaturedItem.Movie -> "Film"
                    is HomeFeaturedItem.SeriesItem -> "Serie"
                },
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        val description = item.description
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Dimensions.spacingMD))
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(Dimensions.spacingLG))

        Row(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(Dimensions.radiusSM))
                .padding(horizontal = Dimensions.spacingMD, vertical = Dimensions.spacingXS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Abspielen",
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HeroIndicators(
    items: List<HomeFeaturedItem>,
    selectedIndex: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(bottom = Dimensions.spacingMD),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXS)
    ) {
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .width(112.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(Dimensions.radiusRound)),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.25f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(if (index == selectedIndex) 8.dp else 6.dp)
                        .background(
                            color = if (index == selectedIndex) Color.White else Color.White.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(Dimensions.radiusRound)
                        )
                )
            }
        }
    }
}

@Composable
private fun HomeRow(
    title: String,
    items: List<HomeRowItem>,
    params: HomeContentParams
) {
    Column(
        modifier = Modifier.padding(bottom = Dimensions.spacingSM),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSM)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = Dimensions.spacingMD),
            color = appColors().textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        LazyRow(
            modifier = Modifier.height(Dimensions.contentCardHeight + 18.dp),
            contentPadding = PaddingValues(
                start = Dimensions.spacingMD,
                top = 4.dp,
                end = Dimensions.spacingMD,
                bottom = 14.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.contentCardCrossAxisSpacing)
        ) {
            itemsIndexed(items) { index, item ->
                HomeRowCard(
                    item = item,
                    params = params,
                    onNavigateLeft = if (index == 0) params.onNavigateLeft else null
                )
            }
        }
    }
}

@Composable
private fun HomeRowCard(
    item: HomeRowItem,
    params: HomeContentParams,
    onNavigateLeft: (() -> Unit)?
) {
    when (item) {
        is HomeRowItem.Live -> {
            LiveStreamCard(
                params = LiveStreamCardParams(
                    stream = item.stream,
                    onClick = { params.onLiveStreamClick(item.stream) },
                    favoritesRepository = params.favoritesRepository,
                    config = CardConfig(onNavigateLeft = onNavigateLeft)
                )
            )
        }
        is HomeRowItem.Movie -> {
            VODCard(
                params = VODCardParams(
                    vod = item.vod,
                    onClick = { params.onVodClick(item.vod) },
                    favoritesRepository = params.favoritesRepository,
                    watchProgressService = params.watchProgressService,
                    onNavigateLeft = onNavigateLeft
                )
            )
        }
        is HomeRowItem.SeriesItem -> {
            VODCard(
                params = VODCardParams(
                    series = item.series,
                    onClick = { params.onSeriesClick(item.series) },
                    favoritesRepository = params.favoritesRepository,
                    onNavigateLeft = onNavigateLeft
                )
            )
        }
    }
}
