package com.flintr.tv.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Maße der Fokusdarstellung.
 */
object FocusRing {
    /** Zentral in Dimensions gepflegt, weil fünf Bestandsstellen daran hängen. */
    val width = Dimensions.focusBorderWidth

    /** Trennt den Rahmen von hellem Artwork. */
    val innerEdgeWidth = 1.dp

    /** Zweites, farbunabhängiges Fokussignal. */
    const val focusScale = 1.04f

    const val animationMillis = 200
}

/**
 * Die Rahmenfarbe. Ein Rahmen bedeutet ausschließlich Fokus — niemals
 * Selektion. Auf der blauen Selektionsfüllung weicht er auf Weiß aus, weil
 * Azur dort nur 1,76:1 erreicht, Weiß dagegen 6,5:1.
 */
fun focusRingColor(isFocused: Boolean, onSelectionFill: Boolean): Color = when {
    !isFocused -> Color.Transparent
    onSelectionFill -> Color.White
    else -> SharedColors.accent
}

/**
 * Einheitliche Fokusdarstellung für alle fokussierbaren Elemente.
 *
 * @param isFocused ob das Element gerade den Fokus hält. Selektion gehört
 *   ausdrücklich nicht hierher.
 * @param onSelectionFill ob das Element auf der blauen Selektionsfüllung liegt.
 * @param onArtwork ob hinter dem Rahmen Bildmaterial liegt. Dann wird eine
 *   dunkle Innenkante ergänzt, damit der Rahmen sich auch von hellem Artwork
 *   absetzt. Die Einrückung ist bewusst konstant und nicht an den Fokus
 *   gekoppelt, damit beim Durchsteppen nichts springt.
 */
@Composable
fun Modifier.tvFocusRing(
    isFocused: Boolean,
    onSelectionFill: Boolean = false,
    onArtwork: Boolean = false,
    shape: Shape = RoundedCornerShape(Dimensions.radiusMD)
): Modifier {
    val ringColor by animateColorAsState(
        targetValue = focusRingColor(isFocused, onSelectionFill),
        animationSpec = tween(durationMillis = FocusRing.animationMillis),
        label = "focusRingColor"
    )
    val elementScale by animateFloatAsState(
        targetValue = if (isFocused) FocusRing.focusScale else 1f,
        animationSpec = tween(durationMillis = FocusRing.animationMillis),
        label = "focusRingScale"
    )

    return this
        .scale(elementScale)
        .border(width = FocusRing.width, color = ringColor, shape = shape)
        .then(
            if (onArtwork) {
                Modifier
                    .padding(FocusRing.width)
                    .border(
                        width = FocusRing.innerEdgeWidth,
                        color = DarkColors.bgPreview,
                        shape = shape
                    )
                    .padding(FocusRing.innerEdgeWidth)
            } else {
                Modifier
            }
        )
}
