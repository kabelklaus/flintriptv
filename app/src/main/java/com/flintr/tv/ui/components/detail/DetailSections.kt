package com.flintr.tv.ui.components.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.theme.CardTitleStyle
import com.flintr.tv.ui.theme.DarkColors
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.theme.tvFocusRing
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import java.text.Normalizer

/**
 * Shared cast row (Movie/Series detail) - identical layout, only the root testTag differs;
 * member testTags derive from it as `$testTag-member-$index`.
 */
@Composable
fun DetailCastSection(cast: List<String>, testTag: String, photos: Map<String, String> = emptyMap()) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .padding(top = Dimensions.spacingLG, start = Dimensions.spacingXL, end = Dimensions.spacingXL)
            .testTag(testTag)
    ) {
        Text(
            text = "Besetzung",
            color = DarkColors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Dimensions.spacingSM)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSM),
            contentPadding = PaddingValues(horizontal = Dimensions.spacingXS)
        ) {
            itemsIndexed(items = cast) { index, name ->
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .testTag("$testTag-member-$index"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(Dimensions.radiusRound))
                            .background(colors.bgCard),
                        contentAlignment = Alignment.Center
                    ) {
                        // Briefmarke liegt als Unterlay immer da - ein Ladefehler blendet nur
                        // das Foto aus, ohne dass die Initialen kurz aufblitzen oder fehlen.
                        Text(
                            text = name.trim().take(1).uppercase(),
                            color = colors.accentBright,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val photoUrl = photos[name]
                        if (photoUrl != null) {
                            var photoFailed by remember(photoUrl) { mutableStateOf(false) }
                            if (!photoFailed) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    onError = { photoFailed = true },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    Text(
                        text = name,
                        color = DarkColors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimensions.spacingXS, start = 2.dp, end = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Shared "Das konnte dir gefallen" row (Movie/Series detail) - identical layout; callers supply
 * the per-item poster/title/testTag/click since movie and series recommendations are different
 * model types.
 */
@Composable
fun <T> DetailRecommendationsSection(
    testTag: String,
    recommendations: List<T>,
    listState: LazyListState,
    focusRequesters: List<FocusRequester>,
    onUp: () -> Unit,
    key: (T) -> Any,
    posterUrl: (T) -> String?,
    title: (T) -> String,
    cardTestTag: (T) -> String,
    onItemClick: (T) -> Unit
) {
    // The backend may return the same film under several language/quality variants
    // (e.g. "Apollo 13", "Apollo 13 DE", "Apollo 13 4K"). Collapse those on the UI
    // side until the recommendation service can own the catalog-level dedup.
    val visibleRecommendations = remember(recommendations) {
        deduplicateRecommendations(recommendations, title)
    }
    Column(
        modifier = Modifier
            .padding(top = Dimensions.spacingLG, start = Dimensions.spacingXL, end = Dimensions.spacingXL)
            .testTag(testTag)
    ) {
        Text(
            text = "Das könnte dir gefallen",
            color = DarkColors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Dimensions.spacingSM)
        )
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSM),
            // Wie DetailCastSection: ohne contentPadding klebt die fokussierte Randkarte am
            // eigenen Clip-Rand der LazyRow und ihr Fokus-Ring/-Scale wird dort beschnitten.
            // Vertikal: Raum fuer den Scale-UEberhang des Fokus-Rings (1.04).
            contentPadding = PaddingValues(
                start = Dimensions.spacingXS,
                end = Dimensions.spacingXS,
                top = Dimensions.spacingSM,
                bottom = Dimensions.spacingSM
            )
        ) {
            itemsIndexed(items = visibleRecommendations, key = { _, item -> key(item) }) { index, item ->
                DetailRecommendationCard(
                    posterUrl = posterUrl(item),
                    title = title(item),
                    testTag = cardTestTag(item),
                    focusRequester = focusRequesters.getOrNull(index),
                    onUp = onUp,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

/** Quality/language markers that Xtream appends to otherwise identical title variants. */
private val RecommendationTitleNoiseSuffixes = setOf(
    "4k", "uhd", "hdr", "fhd", "sd", "uv",
    "de", "en", "ger", "eng", "german", "english"
)

private val RecommendationYearPattern = Regex("""(19|20)\d{2}""")

internal fun normalizeRecommendationTitle(raw: String): String {
    // Cap before normalizing: recommendation titles are external data and only the first
    // 256 characters can influence the dedup key.
    val withoutDiacritics = Normalizer.normalize(raw.take(256), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    val tokens = withoutDiacritics
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .toMutableList()
    // Only trailing markers are dropped so titles that legitimately contain a marker
    // word in the middle keep their meaning.
    while (tokens.isNotEmpty() && tokens.last() in RecommendationTitleNoiseSuffixes) {
        tokens.removeAt(tokens.lastIndex)
    }
    return tokens.joinToString(" ")
}

private fun recommendationYear(item: Any?, title: String): Int? {
    val releaseDate = when (item) {
        is VodStream -> item.releaseDate
        is Series -> item.releaseDate
        else -> null
    }
    return RecommendationYearPattern.find(releaseDate.orEmpty())?.value?.toIntOrNull()
        ?: RecommendationYearPattern.find(title)?.value?.toIntOrNull()
}

/**
 * Keeps the first occurrence per normalized title + year. Series/movie are the only
 * recommendation models in this app, so the year lookup stays local to this UI helper.
 */
internal fun <T> deduplicateRecommendations(
    items: List<T>,
    title: (T) -> String
): List<T> {
    val seen = mutableSetOf<String>()
    return items.filter { item ->
        val itemTitle = title(item)
        val normalized = normalizeRecommendationTitle(itemTitle)
        // NFD + Mn-stripping and the ASCII token filter only cover Latin scripts. For
        // Cyrillic/Greek/Arabic/CJK (and titles that consist only of noise suffixes) the
        // normalized title is blank; falling back to the raw title keeps distinct films
        // apart instead of collapsing them all onto "#<year>".
        val titleKey = normalized.ifBlank { itemTitle.take(256).lowercase().trim() }
        val dedupKey = "$titleKey#${recommendationYear(item, itemTitle) ?: ""}"
        seen.add(dedupKey)
    }
}

/**
 * Unterer Puffer zwischen fokussierbaren Layout-Bounds und Inhalt: Der Ring wird NICHT um den
 * Puffer gezeichnet (Padding sitzt in der Modifier-Kette NACH tvFocusRing), aber BringIntoView
 * scrollt ihn mit rein — Fokus-Scale/-Ring bleiben am Viewport-Rand vollständig sichtbar.
 *
 * `scale()` ist ein graphicsLayer-Transform und vergrößert die Layout-Bounds nicht; das
 * BringIntoView-Rect von `focusable()` basiert auf den LAYOUT-Bounds und kennt Scale/Ring nicht —
 * deshalb dieser Layout-Puffer.
 */
internal val FocusBottomBuffer = Dimensions.spacingMD

fun Modifier.focusBottomBuffer(): Modifier = padding(bottom = FocusBottomBuffer)

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DetailRecommendationCard(
    posterUrl: String?,
    title: String,
    testTag: String,
    focusRequester: FocusRequester?,
    onUp: () -> Unit,
    onClick: () -> Unit
) {
    val colors = appColors()
    var isFocused by remember { mutableStateOf(false) }
    val internalFocusRequester = remember { FocusRequester() }
    val actualFocusRequester = focusRequester ?: internalFocusRequester
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    Column(
        modifier = Modifier
            .width(140.dp)
            .onSizeChanged { cardSize = it }
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(actualFocusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                // Fokus-Ring skaliert 1.04 und ragt unterhalb der Layout-Bounds —
                // bringIntoView mit erweitertem Rect, damit der untere Rand nicht
                // am Screen-Unterrand abgeschnitten wird.
                if (it.isFocused && cardSize != IntSize.Zero) {
                    val extra = with(density) { Dimensions.spacingLG.toPx() }
                    scope.launch {
                        bringIntoViewRequester.bringIntoView(
                            Rect(0f, 0f, cardSize.width.toFloat(), cardSize.height + extra)
                        )
                    }
                }
            }
            .focusable()
            .onDpadDirection(onUp = { onUp(); true })
            .onClick(onClick = onClick)
            .tvFocusRing(
                isFocused = isFocused,
                onArtwork = true,
                shape = RoundedCornerShape(Dimensions.radiusMD)
            )
            .focusBottomBuffer()
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(Dimensions.radiusMD))
                .background(colors.bgCard)
        ) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = title,
                    color = DarkColors.textPrimary,
                    fontSize = CardTitleStyle.fontSize,
                    fontWeight = CardTitleStyle.fontWeight,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(Dimensions.spacingSM)
                )
            }
        }
        Text(
            text = title,
            color = DarkColors.textPrimary,
            fontSize = CardTitleStyle.fontSize,
            fontWeight = CardTitleStyle.fontWeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Dimensions.spacingXS)
        )
    }
}
