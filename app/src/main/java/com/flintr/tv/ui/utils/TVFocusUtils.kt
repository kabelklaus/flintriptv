package com.flintr.tv.ui.utils

import androidx.compose.foundation.focusable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/**
 * Composable modifier for handling long press (300ms) on D-Pad Center/Enter
 * Detects when D-Pad Center or Enter is held down for 300ms
 * 
 * Usage: Use this modifier inside a composable that has access to coroutine scope
 * 
 * @param onLongPress Called when long press is detected (after durationMillis)
 * @param onNormalClick Optional callback for normal click when timer is cancelled early
 * @param durationMillis Duration in milliseconds for long press detection
 */
@Composable
fun Modifier.onLongPress(
    onLongPress: () -> Unit,
    onNormalClick: (() -> Unit)? = null,
    durationMillis: Long = 300L
): Modifier {
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var timerStartTime by remember { mutableStateOf<Long?>(null) }
    var longPressSuccess by remember { mutableStateOf(false) }
    var blockUntilTime by remember { mutableStateOf<Long?>(null) }
    var consumeNextKeyUp by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    return this.onKeyEvent { event ->
        when (event.key) {
            Key.DirectionCenter, Key.Enter -> {
                // Check if we're in blocking period after successful long press
                val now = System.currentTimeMillis()
                if (blockUntilTime != null && now < blockUntilTime!!) {
                    val remaining = blockUntilTime!! - now
                    Log.d("ContentCardLongPress", "[Block] Event ignoriert - Blocking-Periode aktiv (noch ${remaining}ms)")
                    true // Consume event during blocking period
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            val keyName = TVFocusUtils.getKeyName(event.key)
                            Log.d("ContentCardLongPress", "[KeyDown] $keyName gedrückt - Starte Long-Press Timer (${durationMillis}ms)")
                            
                            // Reset success flag, blocking period and consume flag
                            longPressSuccess = false
                            blockUntilTime = null
                            consumeNextKeyUp = false
                            
                            // Cancel existing timer if any
                            val existingJob = longPressJob
                            if (existingJob != null && existingJob.isActive) {
                                Log.d("ContentCardLongPress", "[KeyDown] Vorheriger Timer wird abgebrochen")
                                existingJob.cancel()
                            }
                            
                            // Start long press timer
                            timerStartTime = System.currentTimeMillis()
                            longPressJob = coroutineScope.launch {
                                Log.d("ContentCardLongPress", "[Timer] Timer gestartet, warte ${durationMillis}ms...")
                                delay(durationMillis)
                                val elapsed = System.currentTimeMillis() - (timerStartTime ?: 0)
                                Log.d("ContentCardLongPress", "[Timer] ✅ Long-Press erkannt! (${elapsed}ms vergangen)")
                                Log.d("ContentCardLongPress", "[Callback] Rufe onLongPress() auf")
                                longPressSuccess = true
                                consumeNextKeyUp = true // Consume next KeyUp to prevent normal click
                                // Block events for 800ms after successful long press
                                blockUntilTime = System.currentTimeMillis() + 800
                                Log.d("ContentCardLongPress", "[Block] Blocking-Periode gestartet (800ms)")
                                Log.d("ContentCardLongPress", "[Block] consumeNextKeyUp Flag gesetzt - nächster KeyUp wird konsumiert")
                                onLongPress()
                            }
                            true // Consume event to prevent onDpadCenter from handling it immediately
                        }
                        KeyEventType.KeyUp -> {
                            val keyName = TVFocusUtils.getKeyName(event.key)
                            val elapsed = timerStartTime?.let { System.currentTimeMillis() - it } ?: 0
                            
                            // Always consume KeyUp if consumeNextKeyUp flag is set (even after blocking period)
                            if (consumeNextKeyUp) {
                                Log.d("ContentCardLongPress", "[KeyUp] $keyName losgelassen nach ${elapsed}ms - consumeNextKeyUp Flag aktiv, verhindere normalen Click")
                                longPressJob?.cancel()
                                longPressJob = null
                                timerStartTime = null
                                consumeNextKeyUp = false // Reset flag after consuming
                                longPressSuccess = false
                                blockUntilTime = null
                                true // Consume event to prevent normal click
                            } else if (longPressSuccess || (blockUntilTime != null && now < blockUntilTime!!)) {
                                Log.d("ContentCardLongPress", "[KeyUp] $keyName losgelassen nach ${elapsed}ms - Long-Press war erfolgreich, verhindere normalen Click")
                                longPressJob?.cancel()
                                longPressJob = null
                                timerStartTime = null
                                if (!longPressSuccess) {
                                    // Still in blocking period, keep it active
                                } else {
                                    longPressSuccess = false
                                }
                                true // Consume event to prevent normal click
                            } else if (longPressJob != null && longPressJob!!.isActive) {
                                Log.d("ContentCardLongPress", "[KeyUp] $keyName losgelassen nach ${elapsed}ms - Timer wird abgebrochen (zu früh)")
                                longPressJob?.cancel()
                                longPressJob = null
                                timerStartTime = null
                                // Trigger normal click if callback provided
                                if (onNormalClick != null) {
                                    Log.d("ContentCardLongPress", "[KeyUp] Rufe onNormalClick() auf (Timer abgebrochen)")
                                    onNormalClick()
                                    true // Consume event since we handled it
                                } else {
                                    false // Let normal click work via other handlers
                                }
                            } else {
                                Log.d("ContentCardLongPress", "[KeyUp] $keyName losgelassen nach ${elapsed}ms - Kein aktiver Timer")
                                false // Let normal click work
                            }
                        }
                        else -> false
                    }
                }
            }
            else -> false
        }
    }
}

