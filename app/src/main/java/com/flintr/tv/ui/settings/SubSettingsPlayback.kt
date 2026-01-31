package com.flintr.tv.ui.settings

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import android.content.pm.PackageManager
import android.util.Log
import com.flintr.tv.data.local.AudioDecoderMode
import com.flintr.tv.data.local.PlaybackSettingsRepository
import com.flintr.tv.data.local.PreferredAudioCodec
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.ui.utils.onDpadDirection

@Composable
fun SettingsPlayback(
    onBack: () -> Unit,
    playbackSettingsRepository: PlaybackSettingsRepository,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Playback settings from repository
    var audioDecoderMode by remember { mutableStateOf(playbackSettingsRepository.getAudioDecoderMode()) }
    var audioPassthrough by remember { mutableStateOf(playbackSettingsRepository.isAudioPassthroughEnabled()) }
    var preferredAudioCodec by remember { mutableStateOf(playbackSettingsRepository.getPreferredAudioCodec()) }
    val isTvDevice = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    // Mock state for not-yet-persisted settings
    var videoQuality by remember { mutableStateOf(VideoQuality.Auto) }
    var hardwareDecoder by remember { mutableStateOf("Hardware") }
    var outputFormat by remember { mutableStateOf("Auto") }
    var autoPlay by remember { mutableStateOf(false) }
    var rememberPosition by remember { mutableStateOf(true) }
    var subtitlesEnabled by remember { mutableStateOf(false) }
    var subtitleLanguage by remember { mutableStateOf(SubtitleLanguage.Deutsch) }
    var audioLanguage by remember { mutableStateOf(AudioLanguage.Deutsch) }
    
    // Focus requesters
    val qualityFocusRequester = remember { FocusRequester() }
    
    // Set initial focus
    LaunchedEffect(Unit) {
        qualityFocusRequester.requestFocus()
    }
    
    // Handle Back button using BackHandler (works even when IME is visible)
    BackHandler(enabled = true) {
        Log.i("SettingsNav", "🔙 BACK: SettingsPlayback -> SettingsMainScreen (via BackHandler)")
        onBack()
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPreview)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(Dimensions.spacingXL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Responsive container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
            ) {
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
                            focusRequester = qualityFocusRequester,
                            onBack = {
                                Log.i("SettingsNav", "🔙 BACK: SettingsPlayback -> SettingsMainScreen (via D-Pad Left)")
                                onBack()
                            }
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
                
                // Audio Section
                SettingsCard(
                    title = "Audio",
                    icon = Icons.Default.VolumeUp,
                    colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                    ) {
                        // Audio decoder: Auto / Software / Hardware
                        AudioDecoderSelector(
                            selectedMode = audioDecoderMode,
                            onModeSelected = {
                                audioDecoderMode = it
                                playbackSettingsRepository.setAudioDecoderMode(it)
                            },
                            colors = colors,
                            focusRequester = remember { FocusRequester() },
                            onBack = {
                                Log.i("SettingsNav", "🔙 BACK: SettingsPlayback -> SettingsMainScreen (via D-Pad Left)")
                                onBack()
                            }
                        )
                        if (isTvDevice) {
                            SwitchSetting(
                                label = "Audio-Passthrough",
                                checked = audioPassthrough,
                                onCheckedChange = {
                                    audioPassthrough = it
                                    playbackSettingsRepository.setAudioPassthroughEnabled(it)
                                },
                                colors = colors
                            )
                        }
                        PreferredAudioCodecSelector(
                            selectedCodec = preferredAudioCodec,
                            onCodecSelected = {
                                preferredAudioCodec = it
                                playbackSettingsRepository.setPreferredAudioCodec(it)
                            },
                            colors = colors
                        )
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

@Composable
fun VideoQualitySelector(
    selectedQuality: VideoQuality,
    onQualitySelected: (VideoQuality) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    focusRequester: FocusRequester,
    onBack: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isFocused) colors.accentColor else colors.cardBorder,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "borderColor"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onDpadDirection(
                onLeft = {
                    onBack()
                    true
                }
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onDpadCenter { isExpanded = !isExpanded }
    ) {
        OutlinedTextField(
            value = selectedQuality.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Videoqualität") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
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
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            modifier = Modifier
                .background(colors.bgCard)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(Dimensions.radiusMD))
        ) {
            VideoQuality.entries.forEach { quality ->
                DropdownMenuItem(
                    text = { Text(quality.displayName) },
                    onClick = {
                        onQualitySelected(quality)
                        isExpanded = false
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = if (selectedQuality == quality) colors.accentColor else colors.textPrimary
                    )
                )
            }
        }
    }
}

@Composable
fun AudioDecoderSelector(
    selectedMode: AudioDecoderMode,
    onModeSelected: (AudioDecoderMode) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    focusRequester: FocusRequester,
    onBack: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isFocused) colors.accentColor else colors.cardBorder,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "borderColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onDpadDirection(
                onLeft = {
                    onBack()
                    true
                }
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onDpadCenter { isExpanded = !isExpanded }
    ) {
        OutlinedTextField(
            value = selectedMode.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Audio-Decoder") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
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
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            modifier = Modifier
                .background(colors.bgCard)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(Dimensions.radiusMD))
        ) {
            AudioDecoderMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) },
                    onClick = {
                        onModeSelected(mode)
                        isExpanded = false
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = if (selectedMode == mode) colors.accentColor else colors.textPrimary
                    )
                )
            }
        }
    }
}

@Composable
fun PreferredAudioCodecSelector(
    selectedCodec: PreferredAudioCodec,
    onCodecSelected: (PreferredAudioCodec) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isFocused) colors.accentColor else colors.cardBorder,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "borderColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onDpadCenter { isExpanded = !isExpanded }
    ) {
        OutlinedTextField(
            value = selectedCodec.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Bevorzugter Audio-Codec") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
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
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            modifier = Modifier
                .background(colors.bgCard)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(Dimensions.radiusMD))
        ) {
            PreferredAudioCodec.entries.forEach { codec ->
                DropdownMenuItem(
                    text = { Text(codec.displayName) },
                    onClick = {
                        onCodecSelected(codec)
                        isExpanded = false
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = if (selectedCodec == codec) colors.accentColor else colors.textPrimary
                    )
                )
            }
        }
    }
}

@Composable
fun SubtitleLanguageSelector(
    selectedLanguage: SubtitleLanguage,
    onLanguageSelected: (SubtitleLanguage) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isFocused) colors.accentColor else colors.cardBorder,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "borderColor"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onDpadCenter { isExpanded = !isExpanded }
    ) {
        OutlinedTextField(
            value = selectedLanguage.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Untertitel-Sprache") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
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
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            modifier = Modifier
                .background(colors.bgCard)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(Dimensions.radiusMD))
        ) {
            SubtitleLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName) },
                    onClick = {
                        onLanguageSelected(language)
                        isExpanded = false
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = if (selectedLanguage == language) colors.accentColor else colors.textPrimary
                    )
                )
            }
        }
    }
}

@Composable
fun AudioLanguageSelector(
    selectedLanguage: AudioLanguage,
    onLanguageSelected: (AudioLanguage) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isFocused) colors.accentColor else colors.cardBorder,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "borderColor"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onDpadCenter { isExpanded = !isExpanded }
    ) {
        OutlinedTextField(
            value = selectedLanguage.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Audio-Sprache") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
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
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            modifier = Modifier
                .background(colors.bgCard)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(Dimensions.radiusMD))
        ) {
            AudioLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName) },
                    onClick = {
                        onLanguageSelected(language)
                        isExpanded = false
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = if (selectedLanguage == language) colors.accentColor else colors.textPrimary
                    )
                )
            }
        }
    }
}

@Composable
fun ReadOnlySetting(
    label: String,
    value: String,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        enabled = false,
        shape = RoundedCornerShape(Dimensions.radiusMD),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.cardBorder,
            unfocusedBorderColor = colors.cardBorder,
            focusedLabelColor = colors.textSecondary,
            unfocusedLabelColor = colors.textSecondary,
            focusedTextColor = colors.textSecondary,
            unfocusedTextColor = colors.textSecondary,
            disabledTextColor = colors.textSecondary,
            disabledBorderColor = colors.cardBorder,
            disabledLabelColor = colors.textSecondary
        )
    )
}

enum class VideoQuality(val displayName: String) {
    Auto("Auto"),
    Quality1080p("1080p"),
    Quality720p("720p"),
    Quality480p("480p"),
    Quality360p("360p")
}

enum class SubtitleLanguage(val displayName: String) {
    Deutsch("Deutsch"),
    English("English"),
    Francais("Français"),
    Espanol("Español"),
    Aus("Aus")
}

enum class AudioLanguage(val displayName: String) {
    Deutsch("Deutsch"),
    English("English"),
    Francais("Français"),
    Espanol("Español")
}

