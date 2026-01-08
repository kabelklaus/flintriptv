package com.flintr.tv.ui.utils

import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import android.util.Log

/**
 * Utility functions for TV focus handling
 */
object TVFocusUtils {
    
    /**
     * Get readable key name for logging
     */
    fun getKeyName(key: Key): String {
        return when (key) {
            Key.DirectionUp -> "DirectionUp"
            Key.DirectionDown -> "DirectionDown"
            Key.DirectionLeft -> "DirectionLeft"
            Key.DirectionRight -> "DirectionRight"
            Key.DirectionCenter -> "DirectionCenter"
            Key.Enter -> "Enter"
            Key.Back -> "Back"
            Key.Home -> "Home"
            Key.Menu -> "Menu"
            Key.VolumeUp -> "VolumeUp"
            Key.VolumeDown -> "VolumeDown"
            Key.VolumeMute -> "VolumeMute"
            Key.ChannelUp -> "ChannelUp"
            Key.ChannelDown -> "ChannelDown"
            else -> "Unknown($key)"
        }
    }
    
    /**
     * Modifier for TV-optimized focus handling with visual feedback
     * Correct order: onFocusChanged -> focusable
     */
    fun Modifier.tvFocusable(
        onFocusChanged: ((Boolean) -> Unit)? = null
    ): Modifier {
        return this
            .onFocusChanged { focusState ->
                onFocusChanged?.invoke(focusState.isFocused)
            }
            .focusable()
    }
    
    /**
     * Modifier for TV-optimized focus handling with D-Pad key event support
     * Correct order: onFocusChanged -> focusable -> key handlers
     */
    fun Modifier.tvFocusableWithKeys(
        onFocusChanged: ((Boolean) -> Unit)? = null,
        onKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null
    ): Modifier {
        return this
            .onFocusChanged { focusState ->
                onFocusChanged?.invoke(focusState.isFocused)
            }
            .focusable()
            .then(
                if (onKeyEvent != null) {
                    Modifier.onKeyEvent { event ->
                        onKeyEvent(event)
                    }
                } else {
                    Modifier
                }
            )
    }
    
    
    /**
     * Check if device is TV
     */
    fun isTV(context: android.content.Context): Boolean {
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
}

/**
 * Modifier for handling D-Pad center/enter key press
 * Only intercepts Center and Enter keys, all other keys pass through
 */
fun Modifier.onDpadCenter(
    onPress: () -> Unit
): Modifier {
    return this.onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.DirectionCenter,
                Key.Enter -> {
                    Log.d("TVFocusUtils", "D-Pad center/Enter pressed")
                    onPress()
                    true
                }
                else -> false // Don't intercept other keys
            }
        } else {
            false
        }
    }
}

/**
 * Modifier for handling D-Pad direction keys (Left, Right, Up, Down)
 * Returns true if the key event was handled, false otherwise
 * 
 * Important: Only intercepts events if a handler is provided and returns true.
 * If handler returns false or no handler is provided, the event is passed through
 * to allow Compose's default navigation to work.
 */
fun Modifier.onDpadDirection(
    onLeft: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null
): Modifier {
    // Only apply onKeyEvent if at least one handler is provided
    val hasHandlers = onLeft != null || onRight != null || onUp != null || onDown != null
    
    return if (hasHandlers) {
        this.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                val handled = when (event.key) {
                    Key.DirectionLeft -> {
                        if (onLeft != null) {
                            Log.d("TVFocusUtils", "D-Pad Left pressed")
                            onLeft.invoke()
                        } else {
                            false
                        }
                    }
                    Key.DirectionRight -> {
                        if (onRight != null) {
                            Log.d("TVFocusUtils", "D-Pad Right pressed")
                            onRight.invoke()
                        } else {
                            false
                        }
                    }
                    Key.DirectionUp -> {
                        if (onUp != null) {
                            Log.d("TVFocusUtils", "D-Pad Up pressed")
                            onUp.invoke()
                        } else {
                            false
                        }
                    }
                    Key.DirectionDown -> {
                        if (onDown != null) {
                            Log.d("TVFocusUtils", "D-Pad Down pressed")
                            onDown.invoke()
                        } else {
                            false
                        }
                    }
                    else -> false
                }
                // Only consume event if handler explicitly returns true
                // If false, let Compose handle default navigation
                handled
            } else {
                false
            }
        }
    } else {
        // No handlers, don't intercept events at all
        this
    }
}

/**
 * Modifier for handling Back button press
 * Intercepts Back key and calls the provided handler
 */
fun Modifier.onBackButton(
    onBack: () -> Unit
): Modifier {
    return this.onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
            Log.d("TVFocusUtils", "Back button pressed")
            onBack()
            true
        } else {
            false
        }
    }
}

