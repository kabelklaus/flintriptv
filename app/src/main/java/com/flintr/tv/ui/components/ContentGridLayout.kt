package com.flintr.tv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
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

@Composable
fun <T> ContentGridLayout(
    items: List<T>,
    isLoading: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    content: @Composable (item: T, index: Int, columnIndex: Int, onFocusChanged: ((Boolean) -> Unit)?) -> Unit
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
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = Dimensions.contentCardMaxCrossAxisExtent),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onGloballyPositioned { coordinates ->
                        with(density) {
                            gridWidth = coordinates.size.width.toDp()
                        }
                    },
                contentPadding = PaddingValues(Dimensions.contentCardGridPadding),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.contentCardCrossAxisSpacing),
                verticalArrangement = Arrangement.spacedBy(Dimensions.contentCardMainAxisSpacing)
            ) {
                items(items.size) { index ->
                    val item = items[index]
                    val columnIndex = index % columns
                    content(
                        item,
                        index,
                        columnIndex,
                        { _ ->
                            // onFocusChanged is passed through to content composable
                            // No need to track focusedIndex here as it's unused
                        }
                    )
                }
            }
        }
    }
}

