package com.flintr.tv.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.BackHandler
import com.flintr.tv.data.model.UpdateInfo
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.utils.DownloadProgress
import kotlinx.coroutines.delay

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
    val colors = appColors()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val isSmallScreen = screenWidth < 600.dp
    
    // Responsive dimensions
    val dialogWidth = if (isSmallScreen) screenWidth * 0.9f else 600.dp
    val maxDialogHeight = if (isSmallScreen) screenHeight * 0.6f else 400.dp
    
    val scrollState = rememberScrollState()
    
    // Focus requesters for buttons
    val updateButtonFocusRequester = remember { FocusRequester() }
    val laterButtonFocusRequester = remember { FocusRequester() }
    
    // Track focused button (0 = update, 1 = later)
    var focusedButtonIndex by remember { mutableStateOf(0) }
    var isDialogReady by remember { mutableStateOf(false) }
    
    // Initialize focus when dialog opens
    LaunchedEffect(Unit) {
        delay(16) // Wait for next frame
        isDialogReady = true
        updateButtonFocusRequester.requestFocus()
    }
    
    // Auto-close when installation starts (downloadProgress.isComplete == true)
    LaunchedEffect(downloadProgress?.isComplete) {
        if (downloadProgress?.isComplete == true) {
            delay(500) // Small delay to show completion
            onLater() // Close dialog
        }
    }
    
    // Handle Back button
    BackHandler(enabled = true) {
        onLater()
    }
    
    Dialog(
        onDismissRequest = onLater,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .width(dialogWidth.coerceAtMost(screenWidth * 0.95f))
                .heightIn(max = maxDialogHeight)
                .onBackButton {
                    onLater()
                },
            shape = RoundedCornerShape(Dimensions.radiusMD),
            colors = CardDefaults.cardColors(
                containerColor = colors.bgCard
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(Dimensions.spacingXL),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
            ) {
                // Title
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
                        text = "Update verfügbar",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Version info
                if (currentVersion != null && updateInfo.stableVersion != null) {
                    Text(
                        text = "Aktuelle Version: $currentVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        fontSize = FontSizes.fontSizeXL
                    )
                    Text(
                        text = "Neue Version: ${updateInfo.stableVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        fontSize = FontSizes.fontSizeXL,
                        fontWeight = FontWeight.W600
                    )
                }
                
                // Changelog
                updateInfo.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Änderungen:",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            fontSize = FontSizes.fontSizeLG,
                            fontWeight = FontWeight.W600,
                            modifier = Modifier.padding(bottom = Dimensions.spacingXS)
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = if (isSmallScreen) 150.dp else 200.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = colors.bgHover
                            ),
                            shape = RoundedCornerShape(Dimensions.radiusSM)
                        ) {
                            val changelogScrollState = rememberScrollState()
                            Text(
                                text = changelog,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textPrimary,
                                fontSize = FontSizes.fontSizeMD,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(changelogScrollState)
                                    .padding(Dimensions.spacingMD)
                            )
                        }
                    }
                }
                
                // Buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                ) {
                    // Update Now Button
                    var isUpdateButtonFocused by remember { mutableStateOf(false) }
                    
                    Button(
                        onClick = {
                            if (!isDownloading && downloadProgress?.isComplete != true) {
                                onUpdateNow()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp) // Minimum touch target size
                            .focusRequester(updateButtonFocusRequester)
                            .onFocusChanged { focusState ->
                                isUpdateButtonFocused = focusState.isFocused
                                if (focusState.isFocused) {
                                    focusedButtonIndex = 0
                                }
                            }
                            .then(
                                if (isUpdateButtonFocused) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = Color.White,
                                        shape = RoundedCornerShape(Dimensions.radiusMD)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .onDpadDirection(
                                onRight = {
                                    if (isDialogReady) {
                                        laterButtonFocusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                            )
                            .then(
                                if (isDialogReady && !isDownloading) {
                                    Modifier.focusable().onClick(
                                        onClick = { onUpdateNow() },
                                        enabled = !isDownloading && downloadProgress?.isComplete != true
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        enabled = !isDownloading && downloadProgress?.isComplete != true,
                        shape = RoundedCornerShape(Dimensions.radiusMD),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isUpdateButtonFocused) colors.buttonPrimaryHover else colors.buttonPrimary,
                            contentColor = Color.White,
                            disabledContainerColor = colors.buttonPrimary.copy(alpha = 0.6f),
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        ),
                        contentPadding = PaddingValues(
                            horizontal = Dimensions.spacingMD,
                            vertical = Dimensions.spacingSM
                        )
                    ) {
                        when {
                            downloadProgress?.isComplete == true -> {
                                Text(
                                    text = "Installation startet...",
                                    fontSize = FontSizes.fontSizeLG,
                                    fontWeight = FontWeight.W600
                                )
                            }
                            isDownloading && downloadProgress != null -> {
                                val progressPercent = (downloadProgress.progress * 100).toInt()
                                Text(
                                    text = "$progressPercent% heruntergeladen",
                                    fontSize = FontSizes.fontSizeLG,
                                    fontWeight = FontWeight.W600
                                )
                            }
                            else -> {
                                Text(
                                    text = "Herunterladen",
                                    fontSize = FontSizes.fontSizeLG,
                                    fontWeight = FontWeight.W600
                                )
                            }
                        }
                    }
                    
                    // Later Button
                    var isLaterButtonFocused by remember { mutableStateOf(false) }
                    
                    Button(
                        onClick = onLater,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp) // Minimum touch target size
                            .focusRequester(laterButtonFocusRequester)
                            .onFocusChanged { focusState ->
                                isLaterButtonFocused = focusState.isFocused
                                if (focusState.isFocused) {
                                    focusedButtonIndex = 1
                                }
                            }
                            .then(
                                if (isLaterButtonFocused) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = Color.White,
                                        shape = RoundedCornerShape(Dimensions.radiusMD)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .onDpadDirection(
                                onLeft = {
                                    if (isDialogReady) {
                                        updateButtonFocusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                            )
                            .then(
                                if (isDialogReady) {
                                    Modifier.focusable().onClick(onClick = onLater)
                                } else {
                                    Modifier
                                }
                            ),
                        shape = RoundedCornerShape(Dimensions.radiusMD),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLaterButtonFocused) colors.bgHover else colors.bgHover.copy(alpha = 0.7f),
                            contentColor = colors.textPrimary
                        ),
                        contentPadding = PaddingValues(
                            horizontal = Dimensions.spacingMD,
                            vertical = Dimensions.spacingSM
                        )
                    ) {
                        Text(
                            text = "Später",
                            fontSize = FontSizes.fontSizeLG,
                            fontWeight = FontWeight.W600
                        )
                    }
                }
                
                // Extra padding at bottom for better scrolling on small screens
                if (isSmallScreen) {
                    Spacer(modifier = Modifier.height(Dimensions.spacingMD))
                }
            }
        }
    }
}
