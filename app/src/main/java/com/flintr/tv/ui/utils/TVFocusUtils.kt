package com.flintr.tv.ui.utils

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.geometry.Offset
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
 * Internal state class for long press handling
 */
private class LongPressState {
    // Key event state
    var longPressJob: Job? = null
    var timerStartTime: Long? = null
    var longPressSuccess: Boolean = false
    var blockUntilTime: Long? = null
    var consumeNextKeyUp: Boolean = false
    
    // Touch event state
    var touchLongPressJob: Job? = null
    var touchTimerStartTime: Long? = null
    var touchLongPressSuccess: Boolean = false
    var touchBlockUntilTime: Long? = null
    var consumeNextTouchUp: Boolean = false
    
    fun isInBlockingPeriod(now: Long): Boolean {
        return blockUntilTime != null && now < blockUntilTime!!
    }
    
    fun isInTouchBlockingPeriod(now: Long): Boolean {
        return touchBlockUntilTime != null && now < touchBlockUntilTime!!
    }
    
    fun reset() {
        longPressJob?.cancel()
        longPressJob = null
        timerStartTime = null
        longPressSuccess = false
        blockUntilTime = null
        consumeNextKeyUp = false
        
        touchLongPressJob?.cancel()
        touchLongPressJob = null
        touchTimerStartTime = null
        touchLongPressSuccess = false
        touchBlockUntilTime = null
        consumeNextTouchUp = false
    }
    
    fun resetTimer() {
        longPressJob?.cancel()
        longPressJob = null
        timerStartTime = null
    }
    
    fun resetTouchTimer() {
        touchLongPressJob?.cancel()
        touchLongPressJob = null
        touchTimerStartTime = null
    }
}

/**
 * Composable modifier for handling long press (300ms) on D-Pad Center/Enter and Touch
 * Detects when D-Pad Center or Enter is held down for 300ms, or when touch is held for 300ms
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
    val state = remember { LongPressState() }
    val coroutineScope = rememberCoroutineScope()
    
    return this
        // Key event handling (Remote/D-Pad)
        .onKeyEvent { event ->
            when (event.key) {
                Key.DirectionCenter, Key.Enter -> {
                    val now = System.currentTimeMillis()
                    if (state.isInBlockingPeriod(now)) {
                        val remaining = state.blockUntilTime!! - now
                        Log.d("ContentCardLongPress", "[Block] Event ignoriert - Blocking-Periode aktiv (noch ${remaining}ms)")
                        true
                    } else {
                        when (event.type) {
                            KeyEventType.KeyDown -> handleKeyDown(event, state, coroutineScope, durationMillis, onLongPress)
                            KeyEventType.KeyUp -> handleKeyUp(event, state, now, onNormalClick)
                            else -> false
                        }
                    }
                }
                else -> false
            }
        }
        // Touch event handling
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { offset ->
                    val now = System.currentTimeMillis()
                    
                    if (state.isInTouchBlockingPeriod(now)) {
                        val remaining = state.touchBlockUntilTime!! - now
                        Log.d("ContentCardLongPress", "[TouchBlock] Touch ignoriert - Blocking-Periode aktiv (noch ${remaining}ms)")
                        return@detectTapGestures
                    }
                    
                    handleTouchDown(state, coroutineScope, durationMillis, onLongPress)
                    
                    tryAwaitRelease()
                    val touchUpNow = System.currentTimeMillis()
                    val handled = handleTouchUp(state, touchUpNow, onNormalClick)
                    
                    if (!handled) {
                        // Normal tap if not handled by long press logic
                        Log.d("ContentCardLongPress", "[TouchUp] Normaler Tap erkannt")
                    }
                }
            )
        }
}

/**
 * Handle KeyDown event for long press detection
 */
private fun handleKeyDown(
    event: androidx.compose.ui.input.key.KeyEvent,
    state: LongPressState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    durationMillis: Long,
    onLongPress: () -> Unit
): Boolean {
    val keyName = TVFocusUtils.getKeyName(event.key)
    Log.d("ContentCardLongPress", "[KeyDown] $keyName gedrückt - Starte Long-Press Timer (${durationMillis}ms)")
    
    // Reset state
    state.longPressSuccess = false
    state.blockUntilTime = null
    state.consumeNextKeyUp = false
    
    // Cancel existing timer if any
    val existingJob = state.longPressJob
    if (existingJob != null && existingJob.isActive) {
        Log.d("ContentCardLongPress", "[KeyDown] Vorheriger Timer wird abgebrochen")
        existingJob.cancel()
    }
    
    // Start long press timer
    state.timerStartTime = System.currentTimeMillis()
    state.longPressJob = coroutineScope.launch {
        Log.d("ContentCardLongPress", "[Timer] Timer gestartet, warte ${durationMillis}ms...")
        delay(durationMillis)
        val elapsed = System.currentTimeMillis() - (state.timerStartTime ?: 0)
        Log.d("ContentCardLongPress", "[Timer] ✅ Long-Press erkannt! (${elapsed}ms vergangen)")
        Log.d("ContentCardLongPress", "[Callback] Rufe onLongPress() auf")
        state.longPressSuccess = true
        state.consumeNextKeyUp = true
        state.blockUntilTime = System.currentTimeMillis() + 800
        Log.d("ContentCardLongPress", "[Block] Blocking-Periode gestartet (800ms)")
        Log.d("ContentCardLongPress", "[Block] consumeNextKeyUp Flag gesetzt - nächster KeyUp wird konsumiert")
        onLongPress()
    }
    return true
}

/**
 * Handle KeyUp event for long press detection
 */
private fun handleKeyUp(
    event: androidx.compose.ui.input.key.KeyEvent,
    state: LongPressState,
    now: Long,
    onNormalClick: (() -> Unit)?
): Boolean {
    val keyName = TVFocusUtils.getKeyName(event.key)
    val elapsed = state.timerStartTime?.let { System.currentTimeMillis() - it } ?: 0
    
    return when {
        state.consumeNextKeyUp -> {
            Log.d("ContentCardLongPress", "[KeyUp] $keyName losgelassen nach ${elapsed}ms - consumeNextKeyUp Flag aktiv, verhindere normalen Click")
            state.reset()
            true
        }
        state.longPressSuccess || state.isInBlockingPeriod(now) -> {
            Log.d("ContentCardLongPress", "[KeyUp] $keyName losgelassen nach ${elapsed}ms - Long-Press war erfolgreich, verhindere normalen Click")
            state.resetTimer()
            if (state.longPressSuccess) {
                state.longPressSuccess = false
            }
            true
        }
        state.longPressJob != null && state.longPressJob!!.isActive -> {
            Log.d("ContentCardLongPress", "[KeyUp] $keyName losgelassen nach ${elapsed}ms - Timer wird abgebrochen (zu früh)")
            state.resetTimer()
            if (onNormalClick != null) {
                Log.d("ContentCardLongPress", "[KeyUp] Rufe onNormalClick() auf (Timer abgebrochen)")
                onNormalClick()
                true
            } else {
                false
            }
        }
        else -> {
            Log.d("ContentCardLongPress", "[KeyUp] $keyName losgelassen nach ${elapsed}ms - Kein aktiver Timer")
            false
        }
    }
}

/**
 * Handle Touch Down event for long press detection
 */
private fun handleTouchDown(
    state: LongPressState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    durationMillis: Long,
    onLongPress: () -> Unit
) {
    Log.d("ContentCardLongPress", "[TouchDown] Touch erkannt - Starte Long-Press Timer (${durationMillis}ms)")
    
    // Reset touch state
    state.touchLongPressSuccess = false
    state.touchBlockUntilTime = null
    state.consumeNextTouchUp = false
    
    // Cancel existing touch timer if any
    val existingJob = state.touchLongPressJob
    if (existingJob != null && existingJob.isActive) {
        Log.d("ContentCardLongPress", "[TouchDown] Vorheriger Touch-Timer wird abgebrochen")
        existingJob.cancel()
    }
    
    // Start long press timer
    state.touchTimerStartTime = System.currentTimeMillis()
    state.touchLongPressJob = coroutineScope.launch {
        Log.d("ContentCardLongPress", "[TouchTimer] Timer gestartet, warte ${durationMillis}ms...")
        delay(durationMillis)
        val elapsed = System.currentTimeMillis() - (state.touchTimerStartTime ?: 0)
        Log.d("ContentCardLongPress", "[TouchTimer] ✅ Long-Press erkannt! (${elapsed}ms vergangen)")
        Log.d("ContentCardLongPress", "[TouchCallback] Rufe onLongPress() auf")
        state.touchLongPressSuccess = true
        state.consumeNextTouchUp = true
        state.touchBlockUntilTime = System.currentTimeMillis() + 800
        Log.d("ContentCardLongPress", "[TouchBlock] Blocking-Periode gestartet (800ms)")
        Log.d("ContentCardLongPress", "[TouchBlock] consumeNextTouchUp Flag gesetzt - nächster TouchUp wird konsumiert")
        onLongPress()
    }
}

/**
 * Handle Touch Up event for long press detection
 */
private fun handleTouchUp(
    state: LongPressState,
    now: Long,
    onNormalClick: (() -> Unit)?
): Boolean {
    val elapsed = state.touchTimerStartTime?.let { System.currentTimeMillis() - it } ?: 0
    
    return when {
        state.consumeNextTouchUp -> {
            Log.d("ContentCardLongPress", "[TouchUp] Touch losgelassen nach ${elapsed}ms - consumeNextTouchUp Flag aktiv, verhindere normalen Click")
            state.reset()
            true
        }
        state.touchLongPressSuccess || state.isInTouchBlockingPeriod(now) -> {
            Log.d("ContentCardLongPress", "[TouchUp] Touch losgelassen nach ${elapsed}ms - Long-Press war erfolgreich, verhindere normalen Click")
            state.resetTouchTimer()
            if (state.touchLongPressSuccess) {
                state.touchLongPressSuccess = false
            }
            true
        }
        state.touchLongPressJob != null && state.touchLongPressJob!!.isActive -> {
            Log.d("ContentCardLongPress", "[TouchUp] Touch losgelassen nach ${elapsed}ms - Timer wird abgebrochen (zu früh)")
            state.resetTouchTimer()
            if (onNormalClick != null) {
                Log.d("ContentCardLongPress", "[TouchUp] Rufe onNormalClick() auf (Timer abgebrochen)")
                onNormalClick()
                true
            } else {
                false
            }
        }
        else -> {
            Log.d("ContentCardLongPress", "[TouchUp] Touch losgelassen nach ${elapsed}ms - Kein aktiver Timer")
            false
        }
    }
}

