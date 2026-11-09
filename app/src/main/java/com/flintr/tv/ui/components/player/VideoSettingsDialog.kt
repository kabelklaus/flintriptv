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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.flintr.tv.data.local.PlaybackSettingsRepository
import com.flintr.tv.data.model.AudioLanguage
import com.flintr.tv.data.model.Subtitle
import com.flintr.tv.data.model.SubtitleLanguage
import com.flintr.tv.data.model.VideoQuality
import com.flintr.tv.data.model.VideoResizeMode
import com.flintr.tv.R
import com.flintr.tv.ui.settings.*
import com.flintr.tv.ui.settings.dropdownSelectorColors
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.utils.error.reportWarning
import java.util.Locale

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
    val displayLabel: String,
    /** False when no renderer can play this track, e.g. its MIME is on the hardware blocklist. */
    val isSupported: Boolean = true
)

/**
 * Builds list of audio track infos from player's current tracks.
 * Returns empty list if player is null or no audio groups.
 */
@OptIn(UnstableApi::class)
fun getAudioTracksFromPlayer(player: ExoPlayer?): List<AudioTrackInfo> {
    if (player == null) return emptyList()
    val result = mutableListOf<AudioTrackInfo>()
    player.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_AUDIO }
        .forEachIndexed { groupIndex, group ->
            result.addAll(collectAudioTracksFromGroup(group, groupIndex, result.size))
        }
    return result
}

/**
 * Builds the [AudioTrackInfo] entries for a single track group. [fallbackOffset] is the
 * count of tracks already collected from earlier groups, so the "Track N" fallback label
 * keeps numbering sequentially across all groups.
 */
private fun buildAudioTrackInfo(
    format: Format,
    groupIndex: Int,
    trackIndex: Int,
    fallbackIndex: Int,
    isSupported: Boolean
): AudioTrackInfo {
    val codecs = format.codecs
    val channels = if (format.channelCount != Format.NO_VALUE) format.channelCount else 0
    val rate = if (format.sampleRate != Format.NO_VALUE) format.sampleRate else 0
    val lang = format.language?.takeIf { s -> s.isNotEmpty() }
    return AudioTrackInfo(
        groupIndex = groupIndex,
        trackIndex = trackIndex,
        codecs = codecs,
        channelCount = channels,
        sampleRate = rate,
        language = lang,
        displayLabel = buildAudioTrackLabel(codecs, channels, rate, lang, fallbackIndex),
        isSupported = isSupported
    )
}

private fun collectAudioTracksFromGroup(
    group: androidx.media3.common.Tracks.Group,
    groupIndex: Int,
    fallbackOffset: Int
): List<AudioTrackInfo> {
    val tracks = mutableListOf<AudioTrackInfo>()
    for (i in 0 until group.length) {
        tracks.add(
            buildAudioTrackInfo(
                group.getTrackFormat(i), groupIndex, i, fallbackOffset + tracks.size + 1,
                isSupported = group.isTrackSupported(i, /* allowExceedsCapabilities = */ true)
            )
        )
    }
    return tracks
}

/**
 * Picks the audio track whose language matches [language]; unlike subtitle selection,
 * returns null (no override) rather than falling back to the first track, since audio
 * already plays by default and an arbitrary override would be surprising. Unsupported tracks
 * are skipped: forcing one (e.g. a blocked eac3 track) would fail again on every track change.
 */
fun pickAudioTrackByLanguage(tracks: List<AudioTrackInfo>, language: AudioLanguage): AudioTrackInfo? =
    tracks.find { it.isSupported && normalizeLanguageCode(it.language) == language.value }

private fun channelsLabel(channels: Int): String = when (channels) {
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "${channels}ch"
}

private fun buildAudioTrackLabel(codecs: String?, channels: Int, sampleRate: Int, language: String?, fallbackIndex: Int): String {
    val parts = mutableListOf<String>()
    if (!codecs.isNullOrEmpty()) parts.add(codecs)
    if (channels > 0) parts.add(channelsLabel(channels))
    if (sampleRate > 0) parts.add("${sampleRate}Hz")
    if (!language.isNullOrEmpty()) parts.add(language)
    return if (parts.isNotEmpty()) parts.joinToString(" ") else "Track $fallbackIndex"
}

/**
 * Info for a single subtitle (text) track for display and selection.
 */
data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val displayLabel: String
)

/**
 * Builds list of subtitle track infos from player's current tracks.
 * Returns empty list if player is null or no text groups.
 */
@OptIn(UnstableApi::class)
fun getSubtitleTracksFromPlayer(player: ExoPlayer?): List<SubtitleTrackInfo> {
    if (player == null) return emptyList()
    val result = mutableListOf<SubtitleTrackInfo>()
    player.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_TEXT }
        .forEachIndexed { groupIndex, group ->
            result.addAll(collectSubtitleTracksFromGroup(group, groupIndex, result.size))
        }
    return result
}

private fun collectSubtitleTracksFromGroup(
    group: androidx.media3.common.Tracks.Group,
    groupIndex: Int,
    fallbackOffset: Int
): List<SubtitleTrackInfo> {
    val tracks = mutableListOf<SubtitleTrackInfo>()
    for (i in 0 until group.length) {
        val lang = group.getTrackFormat(i).language?.takeIf { s -> s.isNotEmpty() }
        tracks.add(
            SubtitleTrackInfo(
                groupIndex = groupIndex,
                trackIndex = i,
                language = lang,
                displayLabel = buildSubtitleTrackLabel(lang, fallbackOffset + tracks.size + 1)
            )
        )
    }
    return tracks
}

private fun buildSubtitleTrackLabel(language: String?, fallbackIndex: Int): String {
    val normalized = normalizeLanguageCode(language)
    val known = normalized?.let { code -> SubtitleLanguage.entries.find { it.value == code } }
    return known?.displayName ?: language?.takeIf { it.isNotEmpty() } ?: "Spur $fallbackIndex"
}

/**
 * Normalizes 2- and 3-letter ISO language codes (e.g. "deu"/"ger") to the
 * 2-letter codes used by [SubtitleLanguage.value].
 */
private fun normalizeLanguageCode(code: String?): String? {
    if (code.isNullOrEmpty()) return null
    val lower = code.lowercase(Locale.ROOT)
    return LANGUAGE_CODE_ALIASES[lower] ?: lower
}

private val LANGUAGE_CODE_ALIASES = mapOf(
    "deu" to "de",
    "ger" to "de",
    "eng" to "en",
    "fre" to "fr",
    "fra" to "fr",
    "spa" to "es"
)

/**
 * Picks the subtitle track whose language matches [language]; falls back to the
 * first available track if none match, or null if there are no tracks at all.
 */
fun pickSubtitleTrackByLanguage(tracks: List<SubtitleTrackInfo>, language: SubtitleLanguage): SubtitleTrackInfo? {
    if (tracks.isEmpty()) return null
    return tracks.find { normalizeLanguageCode(it.language) == language.value } ?: tracks.first()
}

/**
 * Applies a [TrackSelectionOverride] selecting [track] on [exoPlayer]. Internal (not
 * private) so [com.flintr.tv.ui.screens.PlayerScreen] can reuse it to apply the
 * preferred-subtitle-language auto-selection once tracks are available at stream start.
 */
@OptIn(UnstableApi::class)
internal fun selectSubtitleTrack(exoPlayer: ExoPlayer, track: SubtitleTrackInfo) {
    val tracksGroup = exoPlayer.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_TEXT }
        .getOrNull(track.groupIndex) ?: return
    try {
        val newParams = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(tracksGroup.mediaTrackGroup, track.trackIndex))
            .build()
        exoPlayer.trackSelectionParameters = newParams
    } catch (e: Exception) {
        reportWarning("SubtitleTrackSelector", "Failed to set subtitle track", e)
    }
}

/**
 * Enables or disables all text (subtitle) tracks on the given parameters.
 * Pure function so the toggle logic is unit-testable without a real player.
 */
fun applySubtitlesEnabledParams(params: TrackSelectionParameters, enabled: Boolean): TrackSelectionParameters =
    params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled).build()

/**
 * Internal (not private) so [com.flintr.tv.ui.screens.PlayerScreen] can reuse it to apply
 * the persisted subtitles-enabled setting once a new stream is prepared.
 */
@OptIn(UnstableApi::class)
internal fun applySubtitlesEnabled(exoPlayer: ExoPlayer, enabled: Boolean) {
    exoPlayer.trackSelectionParameters = applySubtitlesEnabledParams(exoPlayer.trackSelectionParameters, enabled)
}

/**
 * Maps a [VideoResizeMode] to the [AspectRatioFrameLayout] resize-mode constant plus an
 * optional forced aspect ratio (null = use the video's natural ratio).
 */
@OptIn(UnstableApi::class)
fun mapResizeMode(mode: VideoResizeMode): Pair<Int, Float?> = when (mode) {
    VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT to null
    VideoResizeMode.ASPECT_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIT to (16f / 9f)
    VideoResizeMode.ASPECT_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT to (4f / 3f)
    VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL to null
    VideoResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM to null
}

/**
 * Applies [mode] to [playerView] immediately (works live during playback). Internal so
 * [com.flintr.tv.ui.screens.PlayerScreen] can reuse it to apply the persisted resize mode
 * once the PlayerView is attached.
 */
@OptIn(UnstableApi::class)
internal fun applyResizeMode(playerView: PlayerView, mode: VideoResizeMode) {
    val (resizeMode, forcedAspectRatio) = mapResizeMode(mode)
    playerView.resizeMode = resizeMode
    playerView.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
        ?.setAspectRatio(forcedAspectRatio ?: 0f)
}

/**
 * Maps a [VideoQuality] to the (width, height) upper bound for track selection.
 * Auto returns null (no limit).
 */
fun mapVideoQualityToMaxSize(quality: VideoQuality): Pair<Int, Int>? = when (quality) {
    VideoQuality.Auto -> null
    VideoQuality.Quality1080p -> 1920 to 1080
    VideoQuality.Quality720p -> 1280 to 720
    VideoQuality.Quality480p -> 854 to 480
    VideoQuality.Quality360p -> 640 to 360
}

/**
 * Applies [quality] as an upper bound on video track selection. Only constrains the
 * selection when the current stream actually offers more than one video track - a
 * single-track stream that happens to exceed the bound would otherwise go black, since
 * there is nothing left for the selector to fall back to. Internal so
 * [com.flintr.tv.ui.screens.PlayerScreen] can reuse it once a new stream's tracks are known.
 */
@OptIn(UnstableApi::class)
internal fun applyMaxVideoSize(exoPlayer: ExoPlayer, quality: VideoQuality) {
    if (quality == VideoQuality.Auto) {
        exoPlayer.trackSelectionParameters =
            exoPlayer.trackSelectionParameters.buildUpon().clearVideoSizeConstraints().build()
        return
    }
    val maxSize = mapVideoQualityToMaxSize(quality) ?: return
    val videoTrackCount = exoPlayer.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .sumOf { it.length }
    if (videoTrackCount <= 1) return
    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
        .buildUpon()
        .setMaxVideoSize(maxSize.first, maxSize.second)
        .build()
}

/**
 * Builds a [MediaItem.SubtitleConfiguration] per sidecar [Subtitle] with a URL.
 * Mime type is inferred from the file extension; entries without a usable URL are skipped.
 */
fun buildSubtitleConfigurations(subtitles: List<Subtitle>): List<MediaItem.SubtitleConfiguration> =
    subtitles.mapNotNull { subtitle ->
        val url = subtitle.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val path = runCatching { url.toUri().path }.getOrNull() ?: url
        val mimeType = when {
            path.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
            path.endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        MediaItem.SubtitleConfiguration.Builder(url.toUri())
            .setMimeType(mimeType)
            .setLanguage(subtitle.lang)
            .build()
    }

/**
 * Video settings dialog with playback settings and audio track selection.
 * @param onDismiss Callback when dialog is dismissed
 * @param modifier Modifier for the dialog
 * @param exoPlayer Current ExoPlayer instance for audio track list and switching; null if no player
 * @param playerView Current PlayerView for live resize-mode changes; null disables that control's effect
 * @param playbackSettingsRepository Repository for persisting settings; null disables persistence
 * @param onAudioTrackOverridden Called whenever the user explicitly changes the audio track/language,
 *   so the caller can stop re-applying the preferred-language auto-selection for this stream
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSettingsDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    exoPlayer: ExoPlayer? = null,
    playerView: PlayerView? = null,
    playbackSettingsRepository: PlaybackSettingsRepository? = null,
    onAudioTrackOverridden: () -> Unit = {}
) {
    val colors = appColors()
    val state = remember { VideoSettingsState(playbackSettingsRepository) }
    val actions = VideoSettingsActions(
        state = state,
        exoPlayer = exoPlayer,
        playerView = playerView,
        repository = playbackSettingsRepository,
        onAudioTrackOverridden = onAudioTrackOverridden
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.75f)
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

                QualitySettingsSection(actions = actions, colors = colors)

                Spacer(modifier = Modifier.height(Dimensions.spacingMD))

                SubtitleSettingsSection(actions = actions, colors = colors)

                Spacer(modifier = Modifier.height(Dimensions.spacingMD))

                AudioSettingsSection(actions = actions, colors = colors)
            }
        }
    }
}

/**
 * The dialog's setting values, seeded once from [repository] (defaults when null).
 */
@Stable
private class VideoSettingsState(repository: PlaybackSettingsRepository?) {
    var videoQuality by mutableStateOf(repository?.getVideoQuality() ?: VideoQuality.Auto)
    var resizeMode by mutableStateOf(repository?.getVideoResizeMode() ?: VideoResizeMode.FIT)
    var autoPlay by mutableStateOf(repository?.isAutoPlayNextEnabled() ?: false)
    var rememberPosition by mutableStateOf(repository?.isRememberPositionEnabled() ?: true)
    var subtitlesEnabled by mutableStateOf(repository?.getSubtitlesEnabled() ?: false)
    var subtitleLanguage by mutableStateOf(repository?.getPreferredSubtitleLanguage() ?: SubtitleLanguage.Deutsch)
    var audioLanguage by mutableStateOf(repository?.getPreferredAudioLanguage() ?: AudioLanguage.Deutsch)
}

/**
 * Setting-change handlers of [VideoSettingsDialog]: each updates [state], persists via
 * [repository] and applies the change live to [exoPlayer]/[playerView]. Rebuilt on every
 * composition so the handlers always target the current player instances.
 */
private class VideoSettingsActions(
    val state: VideoSettingsState,
    val exoPlayer: ExoPlayer?,
    private val playerView: PlayerView?,
    private val repository: PlaybackSettingsRepository?,
    val onAudioTrackOverridden: () -> Unit
) {
    fun selectQuality(quality: VideoQuality) {
        state.videoQuality = quality
        repository?.setVideoQuality(quality)
        exoPlayer?.let { applyMaxVideoSize(it, quality) }
    }

    fun selectResizeMode(mode: VideoResizeMode) {
        state.resizeMode = mode
        repository?.setVideoResizeMode(mode)
        playerView?.let { applyResizeMode(it, mode) }
    }

    fun setAutoPlay(enabled: Boolean) {
        state.autoPlay = enabled
        repository?.setAutoPlayNextEnabled(enabled)
    }

    fun setRememberPosition(enabled: Boolean) {
        state.rememberPosition = enabled
        repository?.setRememberPositionEnabled(enabled)
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        state.subtitlesEnabled = enabled
        repository?.setSubtitlesEnabled(enabled)
        exoPlayer?.let { applySubtitlesEnabled(it, enabled) }
    }

    fun selectSubtitleLanguage(language: SubtitleLanguage) {
        state.subtitleLanguage = language
        repository?.setPreferredSubtitleLanguage(language)
        if (state.subtitlesEnabled) {
            val tracks = getSubtitleTracksFromPlayer(exoPlayer)
            pickSubtitleTrackByLanguage(tracks, language)?.let { track ->
                exoPlayer?.let { selectSubtitleTrack(it, track) }
            }
        }
    }

    fun selectAudioLanguage(language: AudioLanguage) {
        state.audioLanguage = language
        repository?.setPreferredAudioLanguage(language)
        val tracks = getAudioTracksFromPlayer(exoPlayer)
        pickAudioTrackByLanguage(tracks, language)?.let { track ->
            exoPlayer?.let { selectAudioTrack(it, track) }
            onAudioTrackOverridden()
        }
    }
}

/**
 * Quality section card: max video resolution, aspect ratio, auto-play and remember-position.
 */
@Composable
private fun QualitySettingsSection(
    actions: VideoSettingsActions,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    val state = actions.state
    SettingsCard(
        title = "Qualität",
        icon = Icons.Default.HighQuality,
        colors = colors
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            // Max video resolution (upper bound) - persists and, when the current
            // stream has more than one video track, immediately constrains it
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXXS)
            ) {
                MaxVideoResolutionSelector(
                    selectedQuality = state.videoQuality,
                    onQualitySelected = actions::selectQuality,
                    colors = colors
                )
                Text(
                    text = "Wirkt nur bei Streams mit mehreren Qualitätsstufen",
                    fontSize = FontSizes.fontSizeXS,
                    color = colors.textSecondary
                )
            }

            // Aspect ratio / resize mode - persists and applies live to the PlayerView
            ResizeModeSelector(
                selectedMode = state.resizeMode,
                onModeSelected = actions::selectResizeMode,
                colors = colors
            )

            // Auto Play Switch - persists; actual next-item advancing is gated
            // on this setting in PlayerScreen/PlayerViewModel
            SwitchSetting(
                label = "Automatisch abspielen",
                checked = state.autoPlay,
                onCheckedChange = actions::setAutoPlay,
                colors = colors
            )

            // Remember Position Switch - persists; gates watch-progress saving
            // in PlayerScreen
            SwitchSetting(
                label = "Position merken",
                checked = state.rememberPosition,
                onCheckedChange = actions::setRememberPosition,
                colors = colors
            )
        }
    }
}

/**
 * Subtitles section card: enabled switch, preferred language and current-stream track selection.
 */
@Composable
private fun SubtitleSettingsSection(
    actions: VideoSettingsActions,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    val subtitlesEnabled = actions.state.subtitlesEnabled
    SettingsCard(
        title = "Untertitel",
        icon = Icons.Default.ClosedCaption,
        colors = colors
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            // Subtitles enabled switch - persists and immediately enables/disables
            // text tracks on the current player
            SwitchSetting(
                label = "Untertitel aktivieren",
                checked = subtitlesEnabled,
                onCheckedChange = actions::setSubtitlesEnabled,
                colors = colors
            )

            // Preferred subtitle language - persists and, while subtitles are
            // enabled, immediately selects the matching track on the current stream
            Box(
                modifier = Modifier
                    .alpha(if (subtitlesEnabled) 1f else 0.5f)
                    .let { base ->
                        if (subtitlesEnabled) base else base.focusProperties { canFocus = false }
                    }
            ) {
                SubtitleLanguageSelector(
                    selectedLanguage = actions.state.subtitleLanguage,
                    onLanguageSelected = actions::selectSubtitleLanguage,
                    colors = colors
                )
            }

            // Subtitle track selection (current stream)
            SubtitleTrackSelector(
                exoPlayer = actions.exoPlayer,
                colors = colors
            )
        }
    }
}

/**
 * Audio section card: preferred language and current-stream track selection.
 */
@Composable
private fun AudioSettingsSection(
    actions: VideoSettingsActions,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    SettingsCard(
        title = "Audio",
        icon = Icons.Default.VolumeUp,
        colors = colors
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            // Preferred audio language - persists and immediately selects the
            // matching track on the current stream, if one exists
            AudioLanguageSelector(
                selectedLanguage = actions.state.audioLanguage,
                onLanguageSelected = actions::selectAudioLanguage,
                colors = colors
            )
            // Audio track selection (current stream)
            AudioTrackSelector(
                exoPlayer = actions.exoPlayer,
                colors = colors,
                onTrackSelected = actions.onAudioTrackOverridden
            )
        }
    }
}

/**
 * Shared visuals for the dropdown selectors below ([MaxVideoResolutionSelector],
 * [TrackDropdownField]), pulled out of their DropdownMenu/trailingIcon lambdas so those
 * stay flat (no branching nested inside the forEach/DropdownMenuItem structure).
 */
private fun dropdownBorderColor(focused: Boolean, colors: com.flintr.tv.ui.theme.AppColors): Color =
    if (focused) colors.accentColor else colors.cardBorder

private fun dropdownExpandIcon(expanded: Boolean) =
    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore

private fun dropdownItemTextColor(selected: Boolean, colors: com.flintr.tv.ui.theme.AppColors): Color =
    if (selected) colors.accentColor else colors.textPrimary

/**
 * Max video resolution (upper bound) dropdown for the current stream.
 */
@Composable
private fun MaxVideoResolutionSelector(
    selectedQuality: VideoQuality,
    onQualitySelected: (VideoQuality) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val fieldLabel = "Max. Videoauflösung"
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = dropdownBorderColor(focused, colors),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "borderColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onClick(onClick = { expanded = !expanded })
            .onDpadCenter { expanded = !expanded }
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
                    imageVector = dropdownExpandIcon(expanded),
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(Dimensions.radiusMD),
            colors = dropdownSelectorColors(colors, borderColor, focused)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(colors.bgCard)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(Dimensions.radiusMD))
        ) {
            VideoQuality.entries.forEach { quality ->
                DropdownMenuItem(
                    text = { Text(quality.displayName) },
                    onClick = {
                        onQualitySelected(quality)
                        expanded = false
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = dropdownItemTextColor(selectedQuality == quality, colors)
                    )
                )
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

/**
 * Internal (not private) so [com.flintr.tv.ui.screens.PlayerScreen] can reuse it to apply
 * the preferred-audio-language auto-selection once tracks are available at stream start.
 */
@OptIn(UnstableApi::class)
internal fun selectAudioTrack(exoPlayer: ExoPlayer, track: AudioTrackInfo) {
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
        reportWarning("AudioTrackSelector", "Failed to set audio track", e)
    }
}

/**
 * Dropdown field shared by [AudioTrackSelector] and [SubtitleTrackSelector]. Its Modifier/semantics
 * chain mirrors the touch/d-pad regression-tested ResizeModeSelector (SubSettingsPlaybackTouchTest)
 * and must stay in that shape.
 */
@Composable
private fun <T> TrackDropdownField(
    fieldLabel: String,
    selectedLabel: String,
    tracks: List<T>,
    labelOf: (T) -> String,
    isSelected: (track: T, index: Int) -> Boolean,
    onTrackSelected: (T) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    isEnabled: (track: T, index: Int) -> Boolean = { _, _ -> true }
) {
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = dropdownBorderColor(focused, colors),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "borderColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onClick(onClick = { expanded = !expanded })
            .semantics {
                contentDescription = "$fieldLabel: $selectedLabel"
            }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            enabled = false,
            readOnly = true,
            label = { Text(fieldLabel) },
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    this[SemanticsProperties.Text] = listOf(AnnotatedString(fieldLabel), AnnotatedString(selectedLabel))
                    this[SemanticsProperties.EditableText] = AnnotatedString(selectedLabel)
                },
            trailingIcon = {
                Icon(
                    imageVector = dropdownExpandIcon(expanded),
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(Dimensions.radiusMD),
            colors = dropdownSelectorColors(colors, borderColor, focused)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(colors.bgCard)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(Dimensions.radiusMD))
        ) {
            tracks.forEachIndexed { index, track ->
                val trackEnabled = isEnabled(track, index)
                DropdownMenuItem(
                    text = { Text(labelOf(track)) },
                    onClick = {
                        onTrackSelected(track)
                        expanded = false
                    },
                    enabled = trackEnabled,
                    colors = MenuDefaults.itemColors(
                        textColor = dropdownItemTextColor(isSelected(track, index), colors)
                    )
                )
            }
        }
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
    colors: com.flintr.tv.ui.theme.AppColors,
    onTrackSelected: () -> Unit = {}
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

    val selectedLabel = remember(audioTracks, selectedOverride, audioGroups) {
        resolveSelectedLabel(audioTracks, selectedOverride, audioGroups)
    }

    val notSupportedLabel = stringResource(R.string.audio_track_not_supported)
    TrackDropdownField(
        fieldLabel = "Audio-Track",
        selectedLabel = selectedLabel,
        tracks = audioTracks,
        labelOf = { if (it.isSupported) it.displayLabel else notSupportedLabel.format(it.displayLabel) },
        isSelected = { track, index -> isTrackSelected(track, index, selectedOverride, audioGroups) },
        onTrackSelected = { track ->
            selectAudioTrack(exoPlayer, track)
            onTrackSelected()
        },
        colors = colors,
        isEnabled = { track, _ -> track.isSupported }
    )
}

private fun resolveSelectedSubtitleLabel(
    subtitleTracks: List<SubtitleTrackInfo>,
    selectedOverride: TrackSelectionOverride?,
    subtitleGroups: List<androidx.media3.common.Tracks.Group>
): String {
    if (selectedOverride == null) {
        return subtitleTracks.firstOrNull()?.displayLabel ?: "Keine Auswahl"
    }
    val groupIdx = subtitleGroups.indexOfFirst { it.mediaTrackGroup == selectedOverride.mediaTrackGroup }
    val trackIdx = selectedOverride.trackIndices.minOrNull() ?: 0
    return subtitleTracks.find { it.groupIndex == groupIdx && it.trackIndex == trackIdx }?.displayLabel
        ?: "Spur ${trackIdx + 1}"
}

private fun isSubtitleTrackSelected(
    track: SubtitleTrackInfo,
    index: Int,
    selectedOverride: TrackSelectionOverride?,
    subtitleGroups: List<androidx.media3.common.Tracks.Group>
): Boolean {
    if (selectedOverride == null) return index == 0
    val groupIdx = subtitleGroups.indexOfFirst { it.mediaTrackGroup == selectedOverride.mediaTrackGroup }
    return track.groupIndex == groupIdx && selectedOverride.trackIndices.contains(track.trackIndex)
}

/**
 * Subtitle track list and selection for the current stream.
 * Shows available subtitle tracks and allows switching during playback.
 */
@OptIn(UnstableApi::class)
@Composable
private fun SubtitleTrackSelector(
    exoPlayer: ExoPlayer?,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    val subtitleTracks = remember(exoPlayer?.currentTracks) {
        getSubtitleTracksFromPlayer(exoPlayer)
    }
    val subtitleGroups = exoPlayer?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT } ?: emptyList()
    val selectedOverride = exoPlayer?.trackSelectionParameters?.overrides?.entries
        ?.firstOrNull { (tg, _) -> subtitleGroups.any { it.mediaTrackGroup == tg } }
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
    if (subtitleTracks.isEmpty()) {
        Text(
            text = "Keine Untertitel verfügbar",
            fontSize = FontSizes.fontSizeSM,
            color = colors.textSecondary,
            modifier = Modifier.padding(vertical = Dimensions.spacingSM)
        )
        return
    }

    val selectedLabel = remember(subtitleTracks, selectedOverride, subtitleGroups) {
        resolveSelectedSubtitleLabel(subtitleTracks, selectedOverride, subtitleGroups)
    }

    TrackDropdownField(
        fieldLabel = "Untertitel-Track",
        selectedLabel = selectedLabel,
        tracks = subtitleTracks,
        labelOf = { it.displayLabel },
        isSelected = { track, index -> isSubtitleTrackSelected(track, index, selectedOverride, subtitleGroups) },
        onTrackSelected = { track -> selectSubtitleTrack(exoPlayer, track) },
        colors = colors
    )
}

