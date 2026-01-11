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
                SettingsCard(
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
                        SwitchSetting(
                            label = "Automatisch abspielen",
                            checked = autoPlay,
                            onCheckedChange = { autoPlay = it },
                            colors = colors
                        )
                        
                        // Remember Position Switch
                        SwitchSetting(
                            label = "Position merken",
                            checked = rememberPosition,
                            onCheckedChange = { rememberPosition = it },
                            colors = colors
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(Dimensions.spacingMD))
                
                // Subtitles Section
                SettingsCard(
                    title = "Untertitel",
                    icon = Icons.Default.ClosedCaption,
                    colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                    ) {
                        // Subtitles Enabled Switch
                        SwitchSetting(
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
                SettingsCard(
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

