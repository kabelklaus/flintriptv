package com.flintr.tv.ui.sidebars

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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

@Composable
fun <T> ContentGridLayout(
    items: List<T>,
    isLoading: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    lazyGridState: LazyGridState = rememberLazyGridState(),
    keySelector: ((T) -> Any)? = null,
    contentIdSelector: ((T) -> String)? = null,
    focusRequesterMap: MutableMap<String, FocusRequester>? = null,
    onItemsRendered: (() -> Unit)? = null,
    content: @Composable (item: T, index: Int, columnIndex: Int, onFocusChanged: ((Boolean) -> Unit)?, cardFocusRequester: FocusRequester?) -> Unit
) {
    val density = LocalDensity.current
    var gridWidth by remember { mutableStateOf(0.dp) }
    
    // Calculate columns based on grid width
    val columns = if (gridWidth > 0.dp) {
        val cardWidth = Dimensions.contentCardMaxCrossAxisExtent + Dimensions.contentCardCrossAxisSpacing
        (gridWidth / cardWidth).toInt().coerceAtLeast(1)
    } else {
        1
    }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Adaptive(minSize = Dimensions.contentCardMaxCrossAxisExtent),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .onGloballyPositioned { coordinates ->
                        with(density) {
                            gridWidth = coordinates.size.width.toDp()
                        }
                        // Notify that items are rendered when grid is positioned and has items
                        if (items.isNotEmpty() && coordinates.size.width > 0 && coordinates.size.height > 0) {
                            onItemsRendered?.invoke()
                        }
                    },
                contentPadding = PaddingValues(
                    top = Dimensions.contentCardGridPadding * 2,
                    bottom = Dimensions.contentCardGridPadding,
                    start = Dimensions.contentCardGridPadding,
                    end = Dimensions.contentCardGridPadding
                ),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.contentCardCrossAxisSpacing),
                verticalArrangement = Arrangement.spacedBy(Dimensions.contentCardMainAxisSpacing)
            ) {
                items(
                    count = items.size,
                    key = if (keySelector != null) {
                        { index -> keySelector(items[index]) }
                    } else {
                        null
                    }
                ) { index ->
                    val item = items[index]
                    val columnIndex = index % columns
                    // Get or create FocusRequester for this content item
                    val cardFocusRequester = if (contentIdSelector != null && focusRequesterMap != null) {
                        val contentId = contentIdSelector(item)
                        focusRequesterMap.getOrPut(contentId) { FocusRequester() }
                    } else {
                        null
                    }
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

