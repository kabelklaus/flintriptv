package com.flintr.tv.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flintr.tv.ui.settings.*
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.ui.utils.onDpadDirection

/**
 * Video settings dialog with playback settings
 * @param onDismiss Callback when dialog is dismissed
 * @param modifier Modifier for the dialog
 */
@Composable
fun VideoSettingsDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    
    // Mock state - should be connected to actual settings repository
    var videoQuality by remember { mutableStateOf(VideoQuality.Auto) }
    var hardwareDecoder by remember { mutableStateOf("Hardware") }
    var outputFormat by remember { mutableStateOf("Auto") }
    var autoPlay by remember { mutableStateOf(false) }
    var rememberPosition by remember { mutableStateOf(true) }
    var subtitlesEnabled by remember { mutableStateOf(false) }
    var subtitleLanguage by remember { mutableStateOf(SubtitleLanguage.Deutsch) }
    var audioLanguage by remember { mutableStateOf(AudioLanguage.Deutsch) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(Dimensions.radiusMD),
            colors = CardDefaults.cardColors(
                containerColor = colors.bgCard
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimensions.spacingXL)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Video-Einstellungen",
                        fontSize = FontSizes.fontSizeXL,
                        fontWeight = FontWeight.W600,
                        color = colors.textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = colors.iconColor
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(Dimensions.spacingLG))
                
                // Quality Section
                VideoSettingsCard(
                    title = "Qualität",
                    icon = Icons.Default.HighQuality,
                    colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                    ) {
                        // Video Quality Selection
                        VideoQualitySelector(
                            selectedQuality = videoQuality,
                            onQualitySelected = { videoQuality = it },
                            colors = colors,
                            focusRequester = remember { androidx.compose.ui.focus.FocusRequester() },
                            onBack = {}
                        )
                        
                        // Hardware Decoder (read-only)
                        ReadOnlySetting(
                            label = "Hardware-Decoder",
                            value = hardwareDecoder,
                            colors = colors
                        )
                        
                        // Output Format (read-only)
                        ReadOnlySetting(
                            label = "Output-Format",
                            value = outputFormat,
                            colors = colors
                        )
                        
                        // Auto Play Switch
                        VideoSwitchSetting(
                            label = "Automatisch abspielen",
                            checked = autoPlay,
                            onCheckedChange = { autoPlay = it },
                            colors = colors
                        )
                        
                        // Remember Position Switch
                        VideoSwitchSetting(
                            label = "Position merken",
                            checked = rememberPosition,
                            onCheckedChange = { rememberPosition = it },
                            colors = colors
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(Dimensions.spacingMD))
                
                // Subtitles Section
                VideoSettingsCard(
                    title = "Untertitel",
                    icon = Icons.Default.ClosedCaption,
                    colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                    ) {
                        // Subtitles Enabled Switch
                        VideoSwitchSetting(
                            label = "Untertitel aktivieren",
                            checked = subtitlesEnabled,
                            onCheckedChange = { subtitlesEnabled = it },
                            colors = colors
                        )
                        
                        // Subtitle Language Selection
                        if (subtitlesEnabled) {
                            SubtitleLanguageSelector(
                                selectedLanguage = subtitleLanguage,
                                onLanguageSelected = { subtitleLanguage = it },
                                colors = colors
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(Dimensions.spacingMD))
                
                // Audio Section
                VideoSettingsCard(
                    title = "Audio",
                    icon = Icons.Default.VolumeUp,
                    colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                    ) {
                        AudioLanguageSelector(
                            selectedLanguage = audioLanguage,
                            onLanguageSelected = { audioLanguage = it },
                            colors = colors
                        )
                    }
                }
            }
        }
    }
}

/**
 * Local SettingsCard component to avoid conflicts with other SettingsCard definitions
 */
@Composable
private fun VideoSettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: com.flintr.tv.ui.theme.AppColors,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius),
        colors = CardDefaults.cardColors(
            containerColor = colors.bgCard
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = colors.cardBorder,
                    shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius)
                )
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.listItemHoverPadding),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingLG),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(28.dp),
                    tint = colors.iconColor
                )
                Text(
                    text = title,
                    fontSize = FontSizes.fontSizeXL,
                    fontWeight = FontWeight.W600,
                    color = colors.textPrimary
                )
            }
            
            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.spacingMD)
                    .padding(top = 0.dp),
                content = content
            )
        }
    }
}

/**
 * Local SwitchSetting component to avoid conflicts with other SwitchSetting definitions
 */
@Composable
private fun VideoSwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onDpadCenter { onCheckedChange(!checked) }
            .padding(vertical = Dimensions.spacingXS),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = FontSizes.fontSizeXL,
            color = if (isFocused) colors.accentColor else colors.textPrimary,
            fontWeight = if (isFocused) FontWeight.W600 else FontWeight.Normal
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accentColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colors.cardBorder
            )
        )
    }
}
