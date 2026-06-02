package com.flintr.tv.ui.sidebars

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.theme.Dimensions

/**
 * Calculates the number of columns based on grid width
 */
private fun calculateGridColumns(gridWidth: androidx.compose.ui.unit.Dp): Int {
    return if (gridWidth > 0.dp) {
        val cardWidth = Dimensions.contentCardWidth + Dimensions.contentCardCrossAxisSpacing
        (gridWidth / cardWidth).toInt().coerceAtLeast(1)
    } else {
        1
    }
}

/**
 * Gets or creates a FocusRequester for a content item
 */
private fun <T> getOrCreateFocusRequester(
    item: T,
    contentIdSelector: ((T) -> String)?,
    focusRequesterMap: MutableMap<String, FocusRequester>?
): FocusRequester? {
    return if (contentIdSelector != null && focusRequesterMap != null) {
        val contentId = contentIdSelector(item)
        focusRequesterMap.getOrPut(contentId) { FocusRequester() }
    } else {
        null
    }
}

/**
 * Creates the modifier for the LazyVerticalGrid with positioning callback
 */
private fun createGridModifier(
    focusRequester: FocusRequester,
    density: androidx.compose.ui.unit.Density,
    items: List<*>,
    onGridWidthChanged: (androidx.compose.ui.unit.Dp) -> Unit,
    onItemsRendered: (() -> Unit)?
): Modifier {
    return Modifier
        .fillMaxSize()
        .padding(bottom = Dimensions.contentGridBottomFocusInset)
        .focusRequester(focusRequester)
        .onGloballyPositioned { coordinates ->
            with(density) {
                val gridWidth = coordinates.size.width.toDp()
                onGridWidthChanged(gridWidth)
            }
            // Notify that items are rendered when grid is positioned and has items
            if (items.isNotEmpty() && coordinates.size.width > 0 && coordinates.size.height > 0) {
                onItemsRendered?.invoke()
            }
        }
}

data class ContentGridLayoutParams<T>(
    val items: List<T>,
    val isLoading: Boolean,
    val focusRequester: FocusRequester,
    val modifier: Modifier = Modifier,
    val lazyGridState: LazyGridState,
    val keySelector: ((T) -> Any)? = null,
    val contentIdSelector: ((T) -> String)? = null,
    val focusRequesterMap: MutableMap<String, FocusRequester>? = null,
    val onItemsRendered: (() -> Unit)? = null
)

@Composable
fun <T> ContentGridLayout(
    params: ContentGridLayoutParams<T>,
    content: @Composable (item: T, index: Int, columnIndex: Int, onFocusChanged: ((Boolean) -> Unit)?, cardFocusRequester: FocusRequester?) -> Unit
) {
    val density = LocalDensity.current
    var gridWidth by remember { mutableStateOf(0.dp) }
    
    val columns = calculateGridColumns(gridWidth)
    
    Box(
        modifier = params.modifier.fillMaxSize()
    ) {
        if (params.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyVerticalGrid(
                state = params.lazyGridState,
                columns = GridCells.Adaptive(minSize = Dimensions.contentCardWidth),
                modifier = createGridModifier(
                    focusRequester = params.focusRequester,
                    density = density,
                    items = params.items,
                    onGridWidthChanged = { gridWidth = it },
                    onItemsRendered = params.onItemsRendered
                ),
                contentPadding = PaddingValues(
                    top = Dimensions.contentCardGridPadding * 2,
                    bottom = Dimensions.contentCardGridPadding,
                    start = Dimensions.contentCardGridPadding,
                    end = Dimensions.contentCardGridPadding
                ),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.contentCardCrossAxisSpacing),
                verticalArrangement = Arrangement.spacedBy(
                    Dimensions.contentCardMainAxisSpacing - Dimensions.contentCardFocusReserve
                )
            ) {
                items(
                    count = params.items.size,
                    key = params.keySelector?.let { { index -> it(params.items[index]) } }
                ) { index ->
                    val item = params.items[index]
                    val columnIndex = index % columns
                    val cardFocusRequester = getOrCreateFocusRequester(
                        item = item,
                        contentIdSelector = params.contentIdSelector,
                        focusRequesterMap = params.focusRequesterMap
                    )
                    Box(
                        modifier = Modifier.height(
                            Dimensions.contentCardHeight + Dimensions.contentCardFocusReserve
                        )
                    ) {
                        content(
                            item,
                            index,
                            columnIndex,
                            { _ ->
                                // onFocusChanged is passed through to content composable
                                // No need to track focusedIndex here as it's unused
                            },
                            cardFocusRequester
                        )
                    }
                }
            }
        }
    }
}
