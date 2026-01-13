package com.flintr.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.viewmodel.MainViewModel

@Composable
fun EPGContent(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val streams by viewModel.liveStreams.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        viewModel.loadLiveStreams()
    }
    
    // Request focus when streams are loaded
    LaunchedEffect(streams.isNotEmpty(), uiState.isLoading) {
        if (!uiState.isLoading && streams.isNotEmpty()) {
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
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(streams) { stream ->
                    EPGChannelItem(
                        stream = stream
                    )
                }
            }
        }
    }
}

@Composable
fun EPGChannelItem(
    stream: LiveStream
) {
    // Note: EPG data would need to be loaded per channel
    // This is a simplified version
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = stream.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "EPG Daten werden geladen...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

