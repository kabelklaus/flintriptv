package com.flintr.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors

private const val SKELETON_PULSE_DURATION_MS = 900
private const val SKELETON_ALPHA_MIN = 0.3f
private const val SKELETON_ALPHA_MAX = 0.7f

/**
 * One shared pulse value for every skeleton on screen. A card grid can render dozens of
 * [SkeletonBar]s at once; each running its own `rememberInfiniteTransition` would mean dozens of
 * independent animation timers on a TV box. [SkeletonPulseProvider] starts the single shared
 * clock once near the composition root (see MainActivity). Call sites without a provider (e.g.
 * component previews/tests) fall back to a static mid-alpha - still visible, just not animated.
 *
 * Der Wert wird bewusst als Lambda bereitgestellt und erst in der Draw-Phase gelesen
 * ([graphicsLayer]), damit der Puls keine Recomposition pro Frame ausloest.
 */
private val LocalSkeletonPulseAlpha = compositionLocalOf<() -> Float> {
    { (SKELETON_ALPHA_MIN + SKELETON_ALPHA_MAX) / 2f }
}

@Composable
fun SkeletonPulseProvider(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = SKELETON_ALPHA_MIN,
        targetValue = SKELETON_ALPHA_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SKELETON_PULSE_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonPulseAlpha"
    )
    CompositionLocalProvider(LocalSkeletonPulseAlpha provides ({ alpha })) {
        content()
    }
}

private const val SKELETON_CONTRAST_FACTOR = 0.22f

/**
 * Dezenter Puls-Platzhalter fuer TMDB-Felder, die noch nachladen; [width] = null fuellt die Breite.
 *
 * Bewusst Kontrastfarbe (bgCard auf bgCard waere unsichtbar): die Pille faerbt sich aus
 * [AppColors.textPrimary] statt aus `bgCard`, skaliert mit [SKELETON_CONTRAST_FACTOR] auf eine
 * effektive Deckung von max. ~0.15 - im Dark-Theme hell genug, um auf der dunklen Karte sichtbar
 * zu pulsen, aber dezent genug, um nicht wie Text auszusehen.
 */
@Composable
fun SkeletonBar(
    width: Dp?,
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimensions.radiusSM)
) {
    val colors = appColors()
    val pulseAlpha = LocalSkeletonPulseAlpha.current
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .graphicsLayer { alpha = pulseAlpha() }
            .background(colors.textPrimary.copy(alpha = SKELETON_CONTRAST_FACTOR), shape)
    )
}

/** Chip-Platzhalter in Pill-Form fuer die Karten-Ecke - [TmdbChipState.Loading]. */
@Composable
fun SkeletonChip(modifier: Modifier = Modifier) {
    SkeletonBar(
        width = 28.dp,
        height = 14.dp,
        shape = RoundedCornerShape(50),
        modifier = modifier
    )
}

/** Detail-Meta-Platzhalter: Rating-Badge- und Genre-Band-Bar uebereinander, solange TMDB nachlaedt. */
@Composable
fun SkeletonDetailMeta(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SkeletonBar(width = 70.dp, height = 22.dp)
        SkeletonBar(width = 180.dp, height = 16.dp, modifier = Modifier.padding(top = 10.dp))
    }
}
