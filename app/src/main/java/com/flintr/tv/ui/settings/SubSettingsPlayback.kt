package com.flintr.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import android.content.pm.PackageManager
import android.util.Log
import com.flintr.tv.R
import com.flintr.tv.data.local.AudioDecoderMode
import com.flintr.tv.data.local.PlaybackSettingsRepository
import com.flintr.tv.data.local.PreferredAudioCodec
import com.flintr.tv.data.model.AudioLanguage
import com.flintr.tv.data.model.SubtitleLanguage
import com.flintr.tv.data.model.VideoQuality
import com.flintr.tv.data.model.VideoResizeMode
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.safeRequestFocus

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

    var videoQuality by remember { mutableStateOf(playbackSettingsRepository.getVideoQuality()) }
    var resizeMode by remember { mutableStateOf(playbackSettingsRepository.getVideoResizeMode()) }
    var autoPlay by remember { mutableStateOf(playbackSettingsRepository.isAutoPlayNextEnabled()) }
    var rememberPosition by remember { mutableStateOf(playbackSettingsRepository.isRememberPositionEnabled()) }
    var subtitlesEnabled by remember { mutableStateOf(playbackSettingsRepository.getSubtitlesEnabled()) }
    var subtitleLanguage by remember { mutableStateOf(playbackSettingsRepository.getPreferredSubtitleLanguage()) }
    var audioLanguage by remember { mutableStateOf(playbackSettingsRepository.getPreferredAudioLanguage()) }
    
    // Focus requesters
    val qualityFocusRequester = remember { FocusRequester() }
    
    // Set initial focus
    LaunchedEffect(Unit) {
        qualityFocusRequester.safeRequestFocus(targetName = "VideoQualitySelector")
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
                        // Max video resolution (upper bound, no real downscaling guarantee)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXXS)
                        ) {
                            VideoQualitySelector(
                                selectedQuality = videoQuality,
                                onQualitySelected = {
                                    videoQuality = it
                                    playbackSettingsRepository.setVideoQuality(it)
                                },
                                colors = colors,
                                focusRequester = qualityFocusRequester,
                                onBack = {
                                    Log.i("SettingsNav", "🔙 BACK: SettingsPlayback -> SettingsMainScreen (via D-Pad Left)")
                                    onBack()
                                }
                            )
                            Text(
                                text = stringResource(R.string.max_resolution_warning),
                                fontSize = FontSizes.fontSizeXS,
                                color = colors.textSecondary
                            )
                        }

                        // Aspect ratio / resize mode
                        ResizeModeSelector(
                            selectedMode = resizeMode,
                            onModeSelected = {
                                resizeMode = it
                                playbackSettingsRepository.setVideoResizeMode(it)
                            },
                            colors = colors
                        )

                        // Auto Play Switch
                        SwitchSetting(
                            label = "Automatisch abspielen",
                            checked = autoPlay,
                            onCheckedChange = {
                                autoPlay = it
                                playbackSettingsRepository.setAutoPlayNextEnabled(it)
                            },
                            colors = colors
                        )

                        // Remember Position Switch
                        SwitchSetting(
                            label = "Position merken",
                            checked = rememberPosition,
                            onCheckedChange = {
                                rememberPosition = it
                                playbackSettingsRepository.setRememberPositionEnabled(it)
                            },
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
                            onCheckedChange = {
                                subtitlesEnabled = it
                                playbackSettingsRepository.setSubtitlesEnabled(it)
                            },
                            colors = colors
                        )

                        // Subtitle Language Selection
                        if (subtitlesEnabled) {
                            SubtitleLanguageSelector(
                                selectedLanguage = subtitleLanguage,
                                onLanguageSelected = {
                                    subtitleLanguage = it
                                    playbackSettingsRepository.setPreferredSubtitleLanguage(it)
                                },
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
                        Text(
                            text = stringResource(R.string.audio_decoder_hint),
                            fontSize = FontSizes.fontSizeXS,
                            color = colors.textSecondary
                        )
                        if (isTvDevice) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXXS)
                            ) {
                                SwitchSetting(
                                    label = "Audio-Passthrough",
                                    checked = audioPassthrough,
                                    onCheckedChange = {
                                        audioPassthrough = it
                                        playbackSettingsRepository.setAudioPassthroughEnabled(it)
                                    },
                                    colors = colors
                                )
                                Text(
                                    text = stringResource(R.string.audio_passthrough_warning),
                                    fontSize = FontSizes.fontSizeXS,
                                    color = colors.textSecondary
                                )
                            }
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
                            onLanguageSelected = {
                                audioLanguage = it
                                playbackSettingsRepository.setPreferredAudioLanguage(it)
                            },
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
    val fieldLabel = "Max. Videoauflösung"

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
            .onClick(onClick = { isExpanded = !isExpanded })
            .semantics {
                contentDescription = "$fieldLabel: ${selectedQuality.displayName}"
            }
    ) {
        OutlinedTextField(
            value = selectedQuality.displayName,
            onValueChange = {},
            enabled = false,
            readOnly = true,
            label = { Text(fieldLabel) },
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    this[SemanticsProperties.Text] = listOf(AnnotatedString(fieldLabel), AnnotatedString(selectedQuality.displayName))
                    this[SemanticsProperties.EditableText] = AnnotatedString(selectedQuality.displayName)
                },
            trailingIcon = {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(Dimensions.radiusMD),
            colors = dropdownSelectorColors(colors, borderColor, isFocused)
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
fun ResizeModeSelector(
    selectedMode: VideoResizeMode,
    onModeSelected: (VideoResizeMode) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val fieldLabel = "Seitenverhältnis"

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
            .onClick(onClick = { isExpanded = !isExpanded })
            .semantics {
                contentDescription = "$fieldLabel: ${selectedMode.displayName}"
            }
    ) {
        OutlinedTextField(
            value = selectedMode.displayName,
            onValueChange = {},
            enabled = false,
            readOnly = true,
            label = { Text(fieldLabel) },
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    this[SemanticsProperties.Text] = listOf(AnnotatedString(fieldLabel), AnnotatedString(selectedMode.displayName))
                    this[SemanticsProperties.EditableText] = AnnotatedString(selectedMode.displayName)
                },
            trailingIcon = {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(Dimensions.radiusMD),
            colors = dropdownSelectorColors(colors, borderColor, isFocused)
        )

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            modifier = Modifier
                .background(colors.bgCard)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(Dimensions.radiusMD))
        ) {
            VideoResizeMode.entries.forEach { mode ->
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
fun AudioDecoderSelector(
    selectedMode: AudioDecoderMode,
    onModeSelected: (AudioDecoderMode) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    focusRequester: FocusRequester,
    onBack: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val fieldLabel = "Audio-Decoder"

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
            .onClick(onClick = { isExpanded = !isExpanded })
            .semantics {
                contentDescription = "$fieldLabel: ${selectedMode.displayName}"
            }
    ) {
        OutlinedTextField(
            value = selectedMode.displayName,
            onValueChange = {},
            enabled = false,
            readOnly = true,
            label = { Text(fieldLabel) },
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    this[SemanticsProperties.Text] = listOf(AnnotatedString(fieldLabel), AnnotatedString(selectedMode.displayName))
                    this[SemanticsProperties.EditableText] = AnnotatedString(selectedMode.displayName)
                },
            trailingIcon = {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(Dimensions.radiusMD),
            colors = dropdownSelectorColors(colors, borderColor, isFocused)
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
    val fieldLabel = "Bevorzugter Audio-Codec"

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
            .onClick(onClick = { isExpanded = !isExpanded })
            .semantics {
                contentDescription = "$fieldLabel: ${selectedCodec.displayName}"
            }
    ) {
        OutlinedTextField(
            value = selectedCodec.displayName,
            onValueChange = {},
            enabled = false,
            readOnly = true,
            label = { Text(fieldLabel) },
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    this[SemanticsProperties.Text] = listOf(AnnotatedString(fieldLabel), AnnotatedString(selectedCodec.displayName))
                    this[SemanticsProperties.EditableText] = AnnotatedString(selectedCodec.displayName)
                },
            trailingIcon = {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(Dimensions.radiusMD),
            colors = dropdownSelectorColors(colors, borderColor, isFocused)
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
    val fieldLabel = "Untertitel-Sprache"

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
            .onClick(onClick = { isExpanded = !isExpanded })
            .semantics {
                contentDescription = "$fieldLabel: ${selectedLanguage.displayName}"
            }
    ) {
        OutlinedTextField(
            value = selectedLanguage.displayName,
            onValueChange = {},
            enabled = false,
            readOnly = true,
            label = { Text(fieldLabel) },
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    this[SemanticsProperties.Text] = listOf(AnnotatedString(fieldLabel), AnnotatedString(selectedLanguage.displayName))
                    this[SemanticsProperties.EditableText] = AnnotatedString(selectedLanguage.displayName)
                },
            trailingIcon = {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(Dimensions.radiusMD),
            colors = dropdownSelectorColors(colors, borderColor, isFocused)
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
    val fieldLabel = "Audio-Sprache"

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
            .onClick(onClick = { isExpanded = !isExpanded })
            .semantics {
                contentDescription = "$fieldLabel: ${selectedLanguage.displayName}"
            }
    ) {
        OutlinedTextField(
            value = selectedLanguage.displayName,
            onValueChange = {},
            enabled = false,
            readOnly = true,
            label = { Text(fieldLabel) },
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    this[SemanticsProperties.Text] = listOf(AnnotatedString(fieldLabel), AnnotatedString(selectedLanguage.displayName))
                    this[SemanticsProperties.EditableText] = AnnotatedString(selectedLanguage.displayName)
                },
            trailingIcon = {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(Dimensions.radiusMD),
            colors = dropdownSelectorColors(colors, borderColor, isFocused)
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


