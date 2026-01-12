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
import com.flintr.tv.BuildConfig
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onClick
import kotlinx.coroutines.delay

/**
 * Helper function for debug logging that only logs in DEBUG builds
 */
private inline fun debugLog(tag: String = "PopupMenu", message: () -> String) {
    if (BuildConfig.DEBUG) {
        Log.d(tag, message())
    }
}

/**
 * Enum representing the type of menu
 */
enum class MenuType {
    CONTENT,
    CATEGORY
}

/**
 * Handles menu opening logic including state changes, focus initialization, and grace period
 */
private suspend fun handleMenuOpening(
    focusRequesters: List<FocusRequester>,
    menuOptions: List<String>,
    onStateChange: (ready: Boolean, ignore: Boolean) -> Unit
) {
    debugLog { "[State] isOpen = true" }
    debugLog { "[Dialog] Menü wird geöffnet" }
    debugLog { "[Dialog] Anzahl Optionen: ${menuOptions.size}" }
    onStateChange(false, true)
    // Short delay to wait for next frame instead of long arbitrary delay
    delay(16)
    debugLog { "[Focus] Menü ist jetzt bereit - setze isMenuReady = true" }
    debugLog { "[Focus] Setze Focus auf erstes Item (Index 0) nach Delay" }
    onStateChange(true, true)
    focusRequesters.firstOrNull()?.requestFocus()
    // Grace period: ignore key events for 500ms after menu opens to prevent
    // the key event that triggered the long press from immediately selecting an option
    delay(500)
    onStateChange(true, false)
    debugLog { "[GracePeriod] Grace Period beendet - Key-Events werden jetzt verarbeitet" }
}

/**
 * Handles menu closing logic
 */
private fun handleMenuClosing(
    onStateChange: (ready: Boolean, ignore: Boolean) -> Unit
) {
    debugLog { "[State] isOpen = false" }
    onStateChange(false, true)
}

/**
 * Handles option selection with readiness check
 */
private fun handleOptionSelection(
    index: Int,
    option: String,
    isMenuReady: Boolean,
    onOptionSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isMenuReady) {
        debugLog { "[Option] Option $index Klick ignoriert - Menü noch nicht bereit" }
        return
    }
    debugLog { "[Option] Option $index ausgewählt: '$option'" }
    debugLog { "[Callback] Rufe onOptionSelected($index) auf" }
    onOptionSelected(index)
    debugLog { "[Dialog] Menü wird nach Option-Auswahl geschlossen" }
    onDismiss()
}

/**
 * Handles focus change for menu option
 */
private fun handleFocusChange(
    isFocused: Boolean,
    index: Int,
    option: String,
    currentFocusedIndex: Int,
    onFocusIndexChange: (Int) -> Unit
) {
    if (!isFocused) return
    
    val previousIndex = currentFocusedIndex
    onFocusIndexChange(index)
    debugLog { "[Focus] Focus gewechselt: Index $previousIndex -> $index ('$option')" }
}

/**
 * Handles menu option item click with grace period check
 */
private fun handleMenuOptionItemClick(
    config: MenuOptionItemConfig
) {
    if (config.ignoreKeyEvents) {
        debugLog { "[MenuItem] '${config.text}' - onClick ignoriert (Grace Period aktiv)" }
        return
    }
    debugLog { "[MenuItem] '${config.text}' - onClick aufgerufen" }
    config.onClick()
}

/**
 * Extension function to conditionally apply menu item modifier
 */
private fun Modifier.conditionalMenuItemModifier(
    config: MenuOptionItemConfig
): Modifier {
    return if (config.isEnabled) {
        this
            .focusable()
            .onClick(
                onClick = { handleMenuOptionItemClick(config) },
                enabled = config.isEnabled
            )
    } else {
        this
    }
}

/**
 * TV-optimized popup menu dialog
 * Opens on long press and provides menu options
 */
@Composable
fun PopupMenu(
    isOpen: Boolean,
    menuOptions: List<String>,
    onDismiss: () -> Unit,
    onOptionSelected: (Int) -> Unit,
    menuType: MenuType? = null
) {
    debugLog { "[Render] PopupMenu aufgerufen: isOpen = $isOpen, menuType = $menuType, menuOptions = $menuOptions" }
    if (!isOpen) {
        debugLog { "[Render] isOpen = false - Menü wird nicht gerendert" }
        return
    }
    debugLog { "[Render] Menü wird gerendert (isOpen = true)" }

    val colors = appColors()
    var focusedIndex by remember { mutableStateOf(0) }
    var isMenuReady by remember { mutableStateOf(false) }
    var ignoreKeyEvents by remember { mutableStateOf(true) }
    val focusRequesters = remember { menuOptions.indices.map { FocusRequester() } }

    // Combined LaunchedEffect: handle menu state and focus initialization
    LaunchedEffect(isOpen) {
        when {
            isOpen -> handleMenuOpening(focusRequesters, menuOptions) { ready, ignore ->
                isMenuReady = ready
                ignoreKeyEvents = ignore
            }
            else -> handleMenuClosing { ready, ignore ->
                isMenuReady = ready
                ignoreKeyEvents = ignore
            }
        }
    }

    Dialog(onDismissRequest = {
        debugLog { "[Dialog] onDismissRequest aufgerufen (z.B. durch Klick außerhalb)" }
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .width(400.dp)
                .onBackButton {
                    debugLog { "[BackButton] Back-Button gedrückt - Menü wird geschlossen" }
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
                                handleOptionSelection(
                                    index = index,
                                    option = option,
                                    isMenuReady = isMenuReady,
                                    onOptionSelected = onOptionSelected,
                                    onDismiss = onDismiss
                                )
                            },
                            onFocusChanged = { isFocused ->
                                handleFocusChange(
                                    isFocused = isFocused,
                                    index = index,
                                    option = option,
                                    currentFocusedIndex = focusedIndex,
                                    onFocusIndexChange = { focusedIndex = it }
                                )
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
            debugLog { "[MenuItem] '${config.text}' hat jetzt Focus" }
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
            .conditionalMenuItemModifier(config),
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
