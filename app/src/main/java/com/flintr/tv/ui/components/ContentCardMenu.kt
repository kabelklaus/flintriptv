package com.flintr.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.util.Log
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onDpadCenter
import kotlinx.coroutines.delay

/**
 * TV-optimized menu dialog for ContentCard
 * Opens on long press and provides menu options
 */
@Composable
fun ContentCardMenu(
    isOpen: Boolean,
    menuOptions: List<String>,
    onDismiss: () -> Unit,
    onOptionSelected: (Int) -> Unit
) {
    if (!isOpen) return

    val colors = appColors()
    var focusedIndex by remember { mutableStateOf(0) }
    var isMenuReady by remember { mutableStateOf(false) }
    val focusRequesters = remember { menuOptions.indices.map { FocusRequester() } }

    // Combined LaunchedEffect: handle menu state and focus initialization
    LaunchedEffect(isOpen) {
        if (isOpen) {
            Log.d("ContentCardMenu", "[State] isOpen = true")
            Log.d("ContentCardMenu", "[Dialog] Menü wird geöffnet")
            Log.d("ContentCardMenu", "[Dialog] Anzahl Optionen: ${menuOptions.size}")
            isMenuReady = false
            // Short delay to wait for next frame instead of long arbitrary delay
            delay(16)
            Log.d("ContentCardMenu", "[Focus] Menü ist jetzt bereit - setze isMenuReady = true")
            isMenuReady = true
            Log.d("ContentCardMenu", "[Focus] Setze Focus auf erstes Item (Index 0) nach Delay")
            focusRequesters.firstOrNull()?.requestFocus()
        } else {
            Log.d("ContentCardMenu", "[State] isOpen = false")
            isMenuReady = false
        }
    }

    Dialog(onDismissRequest = {
        Log.d("ContentCardMenu", "[Dialog] onDismissRequest aufgerufen (z.B. durch Klick außerhalb)")
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .width(400.dp)
                .onBackButton {
                    Log.d("ContentCardMenu", "[BackButton] Back-Button gedrückt - Menü wird geschlossen")
                    onDismiss()
                },
            shape = RoundedCornerShape(Dimensions.radiusMD),
            colors = CardDefaults.cardColors(
                containerColor = colors.bgCard
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.spacingMD)
            ) {
                // Dialog title
                Text(
                    text = "Menü",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = Dimensions.spacingMD)
                )

                // Menu options
                menuOptions.forEachIndexed { index, option ->
                    MenuOptionItem(
                        text = option,
                        isFocused = focusedIndex == index,
                        isEnabled = isMenuReady,
                        onClick = {
                            if (!isMenuReady) {
                                Log.d("ContentCardMenu", "[Option] Option $index Klick ignoriert - Menü noch nicht bereit")
                                return@MenuOptionItem
                            }
                            Log.d("ContentCardMenu", "[Option] Option $index ausgewählt: '$option'")
                            Log.d("ContentCardMenu", "[Callback] Rufe onOptionSelected($index) auf")
                            onOptionSelected(index)
                            Log.d("ContentCardMenu", "[Dialog] Menü wird nach Option-Auswahl geschlossen")
                            onDismiss()
                        },
                        focusRequester = focusRequesters[index],
                        onFocusChanged = { isFocused ->
                            if (isFocused) {
                                val previousIndex = focusedIndex
                                focusedIndex = index
                                Log.d("ContentCardMenu", "[Focus] Focus gewechselt: Index $previousIndex -> $index ('$option')")
                            }
                        },
                        colors = colors
                    )

                    if (index < menuOptions.size - 1) {
                        Spacer(modifier = Modifier.height(Dimensions.spacingXS))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuOptionItem(
    text: String,
    isFocused: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    // Log focus changes
    LaunchedEffect(isFocused) {
        if (isFocused) {
            Log.d("ContentCardMenu", "[MenuItem] '$text' hat jetzt Focus")
        }
    }
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) colors.accentColorHoverBg else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "menuItemBackground"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "menuItemBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                onFocusChanged(focusState.isFocused)
            }
            .then(
                if (isEnabled) {
                    Modifier
                        .focusable()
                        .onDpadCenter {
                            // Double-check: ignore clicks if menu is not ready (defensive programming)
                            if (isEnabled) {
                                onClick()
                            } else {
                                Log.d("ContentCardMenu", "[MenuItem] '$text' - onDpadCenter ignoriert (isEnabled = false)")
                            }
                        }
                } else {
                    Modifier // Not focusable until menu is ready
                }
            ),
        shape = RoundedCornerShape(Dimensions.radiusSM),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = Dimensions.categoryBorderWidth,
            color = borderColor
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimensions.spacingMD,
                    vertical = Dimensions.spacingSM
                )
        )
    }
}
