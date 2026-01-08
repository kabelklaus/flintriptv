package com.flintr.tv.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import android.util.Log
import kotlinx.coroutines.delay
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.ui.components.ChannelCard
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.viewmodel.MainViewModel

@Composable
fun LiveTVContent(
    viewModel: MainViewModel,
    selectedCategoryId: String?,
    focusRequester: FocusRequester,
    onNavigateLeft: () -> Unit,
    onChannelClick: (LiveStream) -> Unit,
    modifier: Modifier = Modifier
) {
    val streams by viewModel.liveStreams.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val density = LocalDensity.current
    var gridWidth by remember { mutableStateOf(0.dp) }
    var focusedIndex by remember { mutableStateOf(-1) }
    // Use the external focusRequester for the first item to handle external focus requests
    val firstItemFocusRequester = focusRequester
    
    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != null) {
            viewModel.loadLiveStreams(selectedCategoryId)
        }
    }
    
    // Don't automatically request focus when streams are loaded
    // Focus will be set manually when navigating from CategorySidebar
    // This keeps the focus on the Navbar when the app starts
    
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            if (streams.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Keine Streams gefunden",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = Dimensions.contentCardMaxCrossAxisExtent),
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coordinates ->
                            with(density) {
                                gridWidth = coordinates.size.width.toDp()
                            }
                        },
                    contentPadding = PaddingValues(Dimensions.contentCardGridPadding),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.contentCardCrossAxisSpacing),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.contentCardMainAxisSpacing)
                ) {
                    items(streams.size) { index ->
                        val stream = streams[index]
                        val columnIndex = index % columns
                        ChannelCard(
                            stream = stream,
                            onClick = { onChannelClick(stream) },
                            focusRequester = if (index == 0 && streams.isNotEmpty()) firstItemFocusRequester else null,
                            onFocusChanged = { isFocused ->
                                if (isFocused) {
                                    focusedIndex = index
                                }
                            },
                            onNavigateLeft = if (columnIndex == 0) {
                                { onNavigateLeft() }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveTVContentTab(
    viewModel: MainViewModel,
    selectedCategoryId: String?,
    onChannelClick: (LiveStream) -> Unit,
    modifier: Modifier = Modifier
) {
    val streams by viewModel.liveStreams.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != null) {
            viewModel.loadLiveStreams(selectedCategoryId)
        }
    }
    
    // Request focus when streams are loaded
    LaunchedEffect(streams.isNotEmpty(), uiState.isLoading) {
        if (!uiState.isLoading && streams.isNotEmpty()) {
            Log.i("TVFocus", "[LiveTVScreen] Requesting focus, ${streams.size} streams loaded")
            focusRequester.requestFocus()
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            if (streams.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Keine Streams gefunden",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(streams.chunked(4)) { rowStreams ->
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(rowStreams) { stream ->
                                ChannelCard(
                                    stream = stream,
                                    onClick = { onChannelClick(stream) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


