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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.flintr.tv.ui.settings.*
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadCenter

/**
 * Info for a single audio track for display and selection.
 */
data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val codecs: String?,
    val channelCount: Int,
    val sampleRate: Int,
    val language: String?,
    val displayLabel: String
)

/**
 * Builds list of audio track infos from player's current tracks.
 * Returns empty list if player is null or no audio groups.
 */
@OptIn(UnstableApi::class)
fun getAudioTracksFromPlayer(player: ExoPlayer?): List<AudioTrackInfo> {
    if (player == null) return emptyList()
    val tracks = player.currentTracks
    val result = mutableListOf<AudioTrackInfo>()
    tracks.groups
        .filter { it.type == C.TRACK_TYPE_AUDIO }
        .forEachIndexed { groupIndex, group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val codecs = format.codecs
                val channels = if (format.channelCount != Format.NO_VALUE) format.channelCount else 0
                val rate = if (format.sampleRate != Format.NO_VALUE) format.sampleRate else 0
                val lang = format.language?.takeIf { s -> s.isNotEmpty() }
                val label = buildAudioTrackLabel(codecs, channels, rate, lang, result.size + 1)
                result.add(
                    AudioTrackInfo(
                        groupIndex = groupIndex,
                        trackIndex = i,
                        codecs = codecs,
                        channelCount = channels,
                        sampleRate = rate,
                        language = lang,
                        displayLabel = label
                    )
                )
            }
        }
    return result
}

private fun buildAudioTrackLabel(codecs: String?, channels: Int, sampleRate: Int, language: String?, fallbackIndex: Int): String {
    val parts = mutableListOf<String>()
    if (!codecs.isNullOrEmpty()) parts.add(codecs)
    if (channels > 0) {
        parts.add(when (channels) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "${channels}ch"
        })
    }
    if (sampleRate > 0) parts.add("${sampleRate}Hz")
    if (!language.isNullOrEmpty()) parts.add(language)
    return if (parts.isNotEmpty()) parts.joinToString(" ") else "Track $fallbackIndex"
}

/**
 * Video settings dialog with playback settings and audio track selection.
 * @param onDismiss Callback when dialog is dismissed
 * @param exoPlayer Current ExoPlayer instance for audio track list and switching; null if no player
 * @param modifier Modifier for the dialog
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSettingsDialog(
    onDismiss: () -> Unit,
    exoPlayer: ExoPlayer? = null,
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
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .focusable()
                            .onClick(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
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
                        // Audio track selection (current stream)
                        AudioTrackSelector(
                            exoPlayer = exoPlayer,
                            colors = colors
                        )
                    }
                }
            }
        }
    }
}

private fun resolveSelectedLabel(
    audioTracks: List<AudioTrackInfo>,
    selectedOverride: TrackSelectionOverride?,
    audioGroups: List<androidx.media3.common.Tracks.Group>
): String {
    if (selectedOverride == null) {
        return audioTracks.firstOrNull()?.displayLabel ?: "Auto"
    }
    val groupIdx = audioGroups.indexOfFirst { it.mediaTrackGroup == selectedOverride.mediaTrackGroup }
    val trackIdx = selectedOverride.trackIndices.minOrNull() ?: 0
    return audioTracks.find { it.groupIndex == groupIdx && it.trackIndex == trackIdx }?.displayLabel
        ?: "Track ${trackIdx + 1}"
}

private fun isTrackSelected(
    track: AudioTrackInfo,
    index: Int,
    selectedOverride: TrackSelectionOverride?,
    audioGroups: List<androidx.media3.common.Tracks.Group>
): Boolean {
    if (selectedOverride == null) return index == 0
    val groupIdx = audioGroups.indexOfFirst { it.mediaTrackGroup == selectedOverride.mediaTrackGroup }
    return track.groupIndex == groupIdx && selectedOverride.trackIndices.contains(track.trackIndex)
}

@OptIn(UnstableApi::class)
private fun selectAudioTrack(exoPlayer: ExoPlayer, track: AudioTrackInfo) {
    val tracksGroup = exoPlayer.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_AUDIO }
        .getOrNull(track.groupIndex) ?: return
    try {
        val newParams = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(tracksGroup.mediaTrackGroup, track.trackIndex))
            .build()
        exoPlayer.trackSelectionParameters = newParams
    } catch (e: Exception) {
        android.util.Log.w("AudioTrackSelector", "Failed to set audio track", e)
    }
}

/**
 * Audio track list and selection for the current stream.
 * Shows available audio tracks and allows switching during playback.
 */
@OptIn(UnstableApi::class)
@Composable
private fun AudioTrackSelector(
    exoPlayer: ExoPlayer?,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    val audioTracks = remember(exoPlayer?.currentTracks) {
        getAudioTracksFromPlayer(exoPlayer)
    }
    val audioGroups = exoPlayer?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO } ?: emptyList()
    val selectedOverride = exoPlayer?.trackSelectionParameters?.overrides?.entries
        ?.firstOrNull { (tg, _) -> audioGroups.any { it.mediaTrackGroup == tg } }
        ?.value

    if (exoPlayer == null) {
        Text(
            text = "Kein Player aktiv",
            fontSize = FontSizes.fontSizeSM,
            color = colors.textSecondary,
            modifier = Modifier.padding(vertical = Dimensions.spacingSM)
        )
        return
    }
    if (audioTracks.isEmpty()) {
        Text(
            text = "Keine Audio-Tracks verfügbar",
            fontSize = FontSizes.fontSizeSM,
            color = colors.textSecondary,
            modifier = Modifier.padding(vertical = Dimensions.spacingSM)
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (focused) colors.accentColor else colors.cardBorder,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "borderColor"
    )
    val selectedLabel = remember(audioTracks, selectedOverride, audioGroups) {
        resolveSelectedLabel(audioTracks, selectedOverride, audioGroups)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onDpadCenter { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Audio-Track") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(Dimensions.radiusMD),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = borderColor,
                unfocusedBorderColor = borderColor,
                focusedLabelColor = colors.textPrimary,
                unfocusedLabelColor = colors.textSecondary,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedTrailingIconColor = colors.accentColor,
                unfocusedTrailingIconColor = colors.iconColor
            )
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(colors.bgCard)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(Dimensions.radiusMD))
        ) {
            audioTracks.forEachIndexed { index, track ->
                val isSelected = isTrackSelected(track, index, selectedOverride, audioGroups)
                DropdownMenuItem(
                    text = { Text(track.displayLabel) },
                    onClick = {
                        selectAudioTrack(exoPlayer, track)
                        expanded = false
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = if (isSelected) colors.accentColor else colors.textPrimary
                    )
                )
            }
        }
    }
}

