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
    var ignoreKeyEvents by remember { mutableStateOf(true) }
    val focusRequesters = remember { menuOptions.indices.map { FocusRequester() } }

    // Combined LaunchedEffect: handle menu state and focus initialization
    LaunchedEffect(isOpen) {
        if (isOpen) {
            Log.d("ContentCardMenu", "[State] isOpen = true")
            Log.d("ContentCardMenu", "[Dialog] Menü wird geöffnet")
            Log.d("ContentCardMenu", "[Dialog] Anzahl Optionen: ${menuOptions.size}")
            isMenuReady = false
            ignoreKeyEvents = true
            // Short delay to wait for next frame instead of long arbitrary delay
            delay(16)
            Log.d("ContentCardMenu", "[Focus] Menü ist jetzt bereit - setze isMenuReady = true")
            isMenuReady = true
            Log.d("ContentCardMenu", "[Focus] Setze Focus auf erstes Item (Index 0) nach Delay")
            focusRequesters.firstOrNull()?.requestFocus()
            // Grace period: ignore key events for 500ms after menu opens to prevent
            // the key event that triggered the long press from immediately selecting an option
            delay(500)
            ignoreKeyEvents = false
            Log.d("ContentCardMenu", "[GracePeriod] Grace Period beendet - Key-Events werden jetzt verarbeitet")
        } else {
            Log.d("ContentCardMenu", "[State] isOpen = false")
            isMenuReady = false
            ignoreKeyEvents = true
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
                        config = MenuOptionItemConfig(
                            text = option,
                            isFocused = focusedIndex == index,
                            isEnabled = isMenuReady,
                            ignoreKeyEvents = ignoreKeyEvents,
                            focusRequester = focusRequesters[index],
                            onClick = {
                                if (!isMenuReady) {
                                    Log.d("ContentCardMenu", "[Option] Option $index Klick ignoriert - Menü noch nicht bereit")
                                    return@MenuOptionItemConfig
                                }
                                Log.d("ContentCardMenu", "[Option] Option $index ausgewählt: '$option'")
                                Log.d("ContentCardMenu", "[Callback] Rufe onOptionSelected($index) auf")
                                onOptionSelected(index)
                                Log.d("ContentCardMenu", "[Dialog] Menü wird nach Option-Auswahl geschlossen")
                                onDismiss()
                            },
                            onFocusChanged = { isFocused ->
                                if (isFocused) {
                                    val previousIndex = focusedIndex
                                    focusedIndex = index
                                    Log.d("ContentCardMenu", "[Focus] Focus gewechselt: Index $previousIndex -> $index ('$option')")
                                }
                            }
                        ),
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

private data class MenuOptionItemConfig(
    val text: String,
    val isFocused: Boolean,
    val isEnabled: Boolean,
    val ignoreKeyEvents: Boolean,
    val focusRequester: FocusRequester,
    val onClick: () -> Unit,
    val onFocusChanged: (Boolean) -> Unit
)

@Composable
private fun MenuOptionItem(
    config: MenuOptionItemConfig,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    // Log focus changes
    LaunchedEffect(config.isFocused) {
        if (config.isFocused) {
            Log.d("ContentCardMenu", "[MenuItem] '${config.text}' hat jetzt Focus")
        }
    }
    val backgroundColor by animateColorAsState(
        targetValue = if (config.isFocused) colors.accentColorHoverBg else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "menuItemBackground"
    )

    val borderColor by animateColorAsState(
        targetValue = if (config.isFocused) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "menuItemBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(config.focusRequester)
            .onFocusChanged { focusState ->
                config.onFocusChanged(focusState.isFocused)
            }
            .then(
                if (config.isEnabled) {
                    Modifier
                        .focusable()
                        .onDpadCenter {
                            // Ignore clicks during grace period or if menu is not ready
                            if (config.ignoreKeyEvents) {
                                Log.d("ContentCardMenu", "[MenuItem] '${config.text}' - onDpadCenter ignoriert (Grace Period aktiv)")
                                return@onDpadCenter
                            }
                            if (config.isEnabled) {
                                config.onClick()
                            } else {
                                Log.d("ContentCardMenu", "[MenuItem] '${config.text}' - onDpadCenter ignoriert (isEnabled = false)")
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
            text = config.text,
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
