package com.flintr.tv.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import com.flintr.tv.R
import com.flintr.tv.data.model.UpdateInfo
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.safeRequestFocus
import com.flintr.tv.utils.DownloadProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dialog for displaying available updates and handling download/installation
 * Optimized for TV navigation with D-Pad support
 */
@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    currentVersion: String?,
    downloadProgress: DownloadProgress?,
    isDownloading: Boolean,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit
) {
    val dimensions = rememberDialogDimensions()
    val scrollState = rememberScrollState()
    val updateButtonFocusRequester = remember { FocusRequester() }
    val laterButtonFocusRequester = remember { FocusRequester() }
    val isDialogReady = rememberDialogFocusState(updateButtonFocusRequester)
    
    HandleAutoClose(downloadProgress, onLater)
    BackHandler(enabled = true) { onLater() }
    
    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        UpdateDialogContent(
            updateState = UpdateDialogState(
                updateInfo = updateInfo,
                currentVersion = currentVersion,
                downloadProgress = downloadProgress,
                isDownloading = isDownloading
            ),
            uiState = DialogUIState(
                dimensions = dimensions,
                isDialogReady = isDialogReady,
                scrollState = scrollState
            ),
            focusRequesters = DialogFocusRequesters(
                updateButtonFocusRequester = updateButtonFocusRequester,
                laterButtonFocusRequester = laterButtonFocusRequester
            ),
            callbacks = UpdateDialogCallbacks(
                onUpdateNow = onUpdateNow,
                onLater = onLater
            )
        )
    }
}

// Helper Functions

@Composable
private fun rememberDialogDimensions(): DialogDimensions {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val isSmallScreen = screenWidth < 600.dp
    
    return remember(screenWidth, screenHeight) {
        DialogDimensions(
            width = if (isSmallScreen) screenWidth * 0.9f else 600.dp,
            maxHeight = if (isSmallScreen) screenHeight * 0.6f else 400.dp,
            changelogMaxHeight = if (isSmallScreen) 150.dp else 200.dp,
            isSmallScreen = isSmallScreen
        )
    }
}

@Immutable
private data class DialogDimensions(
    val width: Dp,
    val maxHeight: Dp,
    val changelogMaxHeight: Dp,
    val isSmallScreen: Boolean
)

@Immutable
private data class UpdateDialogState(
    val updateInfo: UpdateInfo,
    val currentVersion: String?,
    val downloadProgress: DownloadProgress?,
    val isDownloading: Boolean
)

@Immutable
private data class DialogUIState(
    val dimensions: DialogDimensions,
    val isDialogReady: Boolean,
    val scrollState: ScrollState
)

@Immutable
private data class DialogFocusRequesters(
    val updateButtonFocusRequester: FocusRequester,
    val laterButtonFocusRequester: FocusRequester
)

@Immutable
private data class UpdateDialogCallbacks(
    val onUpdateNow: () -> Unit,
    val onLater: () -> Unit
)

@Composable
private fun getUpdateButtonText(
    isDownloading: Boolean,
    downloadProgress: DownloadProgress?
): String {
    return when {
        downloadProgress?.isComplete == true -> stringResource(R.string.installation_starting)
        isDownloading && downloadProgress != null -> {
            val progressPercent = (downloadProgress.progress * 100).toInt()
            stringResource(R.string.download_percent, progressPercent)
        }
        else -> stringResource(R.string.download)
    }
}

private fun Modifier.focusBorder(isFocused: Boolean): Modifier {
    return if (isFocused) {
        this.border(
            width = 2.dp,
            color = Color.White,
            shape = RoundedCornerShape(Dimensions.radiusMD)
        )
    } else {
        this
    }
}

@Composable
private fun rememberDialogFocusState(
    updateButtonFocusRequester: FocusRequester
): Boolean {
    var isDialogReady by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(16)
        isDialogReady = true
        updateButtonFocusRequester.safeRequestFocus(targetName = "UpdateNowButton")
    }
    
    return isDialogReady
}

@Composable
private fun HandleAutoClose(
    downloadProgress: DownloadProgress?,
    onLater: () -> Unit
) {
    LaunchedEffect(downloadProgress?.isComplete) {
        if (downloadProgress?.isComplete == true) {
            delay(500)
            onLater()
        }
    }
}

// UI Components

@Composable
private fun DialogTitle() {
    val colors = appColors()
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = null,
            tint = colors.accentColor,
            modifier = Modifier.size(Dimensions.iconSizeLG)
        )
        Text(
            text = stringResource(R.string.update_available),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun VersionInfoSection(
    currentVersion: String?,
    newVersion: String?
) {
    if (currentVersion != null && newVersion != null) {
        Text(
            text = "${stringResource(R.string.current_version)}: $currentVersion",
            style = MaterialTheme.typography.bodyMedium,
            color = appColors().textSecondary,
            fontSize = FontSizes.fontSizeXL
        )
        Text(
            text = "${stringResource(R.string.new_version)}: $newVersion",
            style = MaterialTheme.typography.bodyMedium,
            color = appColors().textPrimary,
            fontSize = FontSizes.fontSizeXL,
            fontWeight = FontWeight.W600
        )
    }
}

@Composable
private fun ChangelogSection(
    changelog: String?,
    maxHeight: Dp
) {
    changelog?.takeIf { it.isNotBlank() }?.let { changelogText ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.changelog_label),
                style = MaterialTheme.typography.titleSmall,
                color = appColors().textPrimary,
                fontSize = FontSizes.fontSizeLG,
                fontWeight = FontWeight.W600,
                modifier = Modifier.padding(bottom = Dimensions.spacingXS)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                colors = CardDefaults.cardColors(
                    containerColor = appColors().bgHover
                ),
                shape = RoundedCornerShape(Dimensions.radiusSM)
            ) {
                Text(
                    text = changelogText,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textPrimary,
                    fontSize = FontSizes.fontSizeMD,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimensions.spacingMD)
                )
            }
        }
    }
}

@Composable
private fun RowScope.UpdateButton(
    isDownloading: Boolean,
    downloadProgress: DownloadProgress?,
    isDialogReady: Boolean,
    focusRequester: FocusRequester,
    laterButtonFocusRequester: FocusRequester,
    onUpdateNow: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Button(
        onClick = {
            if (!isDownloading && downloadProgress?.isComplete != true) {
                onUpdateNow()
            }
        },
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 44.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusBorder(isFocused)
            .onDpadDirection(
                onRight = {
                    if (isDialogReady) {
                        coroutineScope.launch {
                            laterButtonFocusRequester.safeRequestFocus(targetName = "LaterButton")
                        }
                        true
                    } else {
                        false
                    }
                }
            ),
        enabled = !isDownloading && downloadProgress?.isComplete != true,
        shape = RoundedCornerShape(Dimensions.radiusMD),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) appColors().buttonPrimaryHover else appColors().buttonPrimary,
            contentColor = Color.White,
            disabledContainerColor = appColors().buttonPrimary.copy(alpha = 0.6f),
            disabledContentColor = Color.White.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(
            horizontal = Dimensions.spacingMD,
            vertical = Dimensions.spacingSM
        )
    ) {
        Text(
            text = getUpdateButtonText(isDownloading, downloadProgress),
            fontSize = FontSizes.fontSizeLG,
            fontWeight = FontWeight.W600
        )
    }
}

@Composable
private fun RowScope.LaterButton(
    isDialogReady: Boolean,
    focusRequester: FocusRequester,
    updateButtonFocusRequester: FocusRequester,
    onLater: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val colors = appColors()
    val coroutineScope = rememberCoroutineScope()

    Button(
        onClick = onLater,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 44.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusBorder(isFocused)
            .onDpadDirection(
                onLeft = {
                    if (isDialogReady) {
                        coroutineScope.launch {
                            updateButtonFocusRequester.safeRequestFocus(targetName = "UpdateNowButton")
                        }
                        true
                    } else {
                        false
                    }
                }
            ),
        shape = RoundedCornerShape(Dimensions.radiusMD),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) colors.bgHover else colors.bgHover.copy(alpha = 0.7f),
            contentColor = colors.textPrimary
        ),
        contentPadding = PaddingValues(
            horizontal = Dimensions.spacingMD,
            vertical = Dimensions.spacingSM
        )
    ) {
        Text(
            text = stringResource(R.string.later),
            fontSize = FontSizes.fontSizeLG,
            fontWeight = FontWeight.W600
        )
    }
}

@Composable
private fun ButtonsRow(
    isDownloading: Boolean,
    downloadProgress: DownloadProgress?,
    isDialogReady: Boolean,
    updateButtonFocusRequester: FocusRequester,
    laterButtonFocusRequester: FocusRequester,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
    ) {
        UpdateButton(
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            isDialogReady = isDialogReady,
            focusRequester = updateButtonFocusRequester,
            laterButtonFocusRequester = laterButtonFocusRequester,
            onUpdateNow = onUpdateNow
        )
        LaterButton(
            isDialogReady = isDialogReady,
            focusRequester = laterButtonFocusRequester,
            updateButtonFocusRequester = updateButtonFocusRequester,
            onLater = onLater
        )
    }
}

@Composable
private fun UpdateDialogContent(
    updateState: UpdateDialogState,
    uiState: DialogUIState,
    focusRequesters: DialogFocusRequesters,
    callbacks: UpdateDialogCallbacks
) {
    val colors = appColors()
    
    Card(
        modifier = Modifier
            .width(uiState.dimensions.width)
            .heightIn(max = uiState.dimensions.maxHeight)
            .onBackButton { callbacks.onLater() },
        shape = RoundedCornerShape(Dimensions.radiusMD),
        colors = CardDefaults.cardColors(containerColor = colors.bgCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(uiState.scrollState)
                .padding(Dimensions.spacingXL),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            DialogTitle()
            VersionInfoSection(updateState.currentVersion, updateState.updateInfo.stableVersion)
            ChangelogSection(updateState.updateInfo.changelog, uiState.dimensions.changelogMaxHeight)
            ButtonsRow(
                isDownloading = updateState.isDownloading,
                downloadProgress = updateState.downloadProgress,
                isDialogReady = uiState.isDialogReady,
                updateButtonFocusRequester = focusRequesters.updateButtonFocusRequester,
                laterButtonFocusRequester = focusRequesters.laterButtonFocusRequester,
                onUpdateNow = callbacks.onUpdateNow,
                onLater = callbacks.onLater
            )
            if (uiState.dimensions.isSmallScreen) {
                Spacer(modifier = Modifier.height(Dimensions.spacingMD))
            }
        }
    }
}
