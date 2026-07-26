package com.flintr.tv.ui.utils

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.focus.FocusRequester
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.flintr.tv.utils.error.reportControlFlow

/**
 * Utility functions for TV and touch interaction handling
 */
object InteractionUtils {
    
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

private const val FOCUS_REQUEST_DELAY_MS = 50L

/**
 * Safely request focus on a FocusRequester with a delay to ensure it's fully initialized.
 * This prevents IllegalStateException when requesting focus before the FocusRequester is ready.
 * 
 * @param logTag Tag for logging (default: "TVFocus")
 * @param targetName Name of the target component for logging purposes
 */
suspend fun FocusRequester.safeRequestFocus(
    logTag: String = "TVFocus",
    targetName: String
) {
    delay(FOCUS_REQUEST_DELAY_MS)
    try {
        requestFocus()
        Log.i(logTag, "🎯 Focus set to $targetName")
    } catch (e: IllegalStateException) {
        Log.w(logTag, "⚠️ $targetName FocusRequester not initialized yet")
        reportControlFlow("InteractionUtils.$targetName", e)
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
 * Unified modifier for handling clicks from both Key (D-Pad Center/Enter) and Touch events
 * This is the recommended way to handle clicks in TV apps that also support touch displays
 * 
 * @param onClick Called when the component is clicked via Key or Touch
 * @param enabled Whether the click handler is enabled (default: true)
 */
fun Modifier.onClick(
    onClick: () -> Unit,
    enabled: Boolean = true
): Modifier {
    return if (enabled) {
        this
            .onDpadCenter(onClick)
            .clickable(onClick = onClick)
    } else {
        this
    }
}

/**
 * Data class for D-Pad direction handlers
 */
private data class DpadHandlers(
    val onLeft: (() -> Boolean)? = null,
    val onRight: (() -> Boolean)? = null,
    val onUp: (() -> Boolean)? = null,
    val onDown: (() -> Boolean)? = null
) {
    fun hasAnyHandler(): Boolean {
        return onLeft != null || onRight != null || onUp != null || onDown != null
    }
}

/**
 * Handle a single D-Pad key event
 */
private fun handleDpadKeyEvent(
    key: Key,
    handlers: DpadHandlers
): Boolean {
    return when (key) {
        Key.DirectionLeft -> invokeHandler(handlers.onLeft, "D-Pad Left pressed")
        Key.DirectionRight -> invokeHandler(handlers.onRight, "D-Pad Right pressed")
        Key.DirectionUp -> invokeHandler(handlers.onUp, "D-Pad Up pressed")
        Key.DirectionDown -> invokeHandler(handlers.onDown, "D-Pad Down pressed")
        else -> false
    }
}

/**
 * Invoke a D-Pad handler if it exists
 */
private fun invokeHandler(handler: (() -> Boolean)?, logMessage: String): Boolean {
    if (handler == null) {
        return false
    }
    Log.d("TVFocusUtils", logMessage)
    return handler.invoke()
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
    val handlers = DpadHandlers(onLeft, onRight, onUp, onDown)
    
    if (!handlers.hasAnyHandler()) {
        // No handlers, don't intercept events at all
        return this
    }
    
    return this.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            return@onKeyEvent false
        }
        
        // Only consume event if handler explicitly returns true
        // If false, let Compose handle default navigation
        handleDpadKeyEvent(event.key, handlers)
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

private const val LONG_PRESS_BLOCKING_PERIOD_MS = 800L

/**
 * Internal state class for a single input type (key or touch)
 */
private class InputPressState {
    var job: Job? = null
    var timerStartTime: Long? = null
    var success: Boolean = false
    var blockUntilTime: Long? = null
    var consumeNextRelease: Boolean = false
    
    fun isInBlockingPeriod(now: Long): Boolean {
        return blockUntilTime != null && now < blockUntilTime!!
    }
    
    fun reset() {
        job?.cancel()
        job = null
        timerStartTime = null
        success = false
        blockUntilTime = null
        consumeNextRelease = false
    }
    
    fun resetTimer() {
        job?.cancel()
        job = null
        timerStartTime = null
    }
}

/**
 * Internal state class for long press handling
 */
private class LongPressState {
    val keyState = InputPressState()
    val touchState = InputPressState()
    
    fun isInBlockingPeriod(now: Long): Boolean {
        return keyState.isInBlockingPeriod(now)
    }
    
    fun isInTouchBlockingPeriod(now: Long): Boolean {
        return touchState.isInBlockingPeriod(now)
    }
    
    fun reset() {
        keyState.reset()
        touchState.reset()
    }
    
    fun resetTimer() {
        keyState.resetTimer()
    }
    
    fun resetTouchTimer() {
        touchState.resetTimer()
    }
}

/**
 * Create key event handler for long press detection
 */
private fun createKeyEventHandler(
    state: LongPressState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    durationMillis: Long,
    currentOnLongPress: State<() -> Unit>,
    currentOnNormalClick: State<(() -> Unit)?>
): (androidx.compose.ui.input.key.KeyEvent) -> Boolean {
    return { event ->
        when (event.key) {
            Key.DirectionCenter, Key.Enter -> {
                val now = System.currentTimeMillis()
                if (state.isInBlockingPeriod(now)) {
                    val remaining = state.keyState.blockUntilTime!! - now
                    Log.d("ContentCardLongPress", "[Block] Event ignoriert - Blocking-Periode aktiv (noch ${remaining}ms)")
                    true
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> handleKeyDown(
                            event,
                            state,
                            coroutineScope,
                            durationMillis,
                            currentOnLongPress
                        )
                        KeyEventType.KeyUp -> handleKeyUp(event, state, now, currentOnNormalClick)
                        else -> false
                    }
                }
            }
            else -> false
        }
    }
}

/**
 * Create touch event handler modifier for long press detection
 */
private fun Modifier.createTouchEventHandler(
    state: LongPressState,
    coroutineScope: CoroutineScope,
    durationMillis: Long,
    currentOnLongPress: State<() -> Unit>,
    currentOnNormalClick: State<(() -> Unit)?>
): Modifier {
    return this.pointerInput(durationMillis) {
        detectTapGestures(
            onPress = { _ ->
                val now = System.currentTimeMillis()
                
                if (state.isInTouchBlockingPeriod(now)) {
                    val remaining = state.touchState.blockUntilTime!! - now
                    Log.d("ContentCardLongPress", "[TouchBlock] Touch ignoriert - Blocking-Periode aktiv (noch ${remaining}ms)")
                    return@detectTapGestures
                }
                
                // Use coroutineScope from Composable, but the gesture lifecycle will cancel
                // the job automatically when the gesture ends (via tryAwaitRelease)
                handleTouchDown(state, coroutineScope, durationMillis, currentOnLongPress)
                
                val released = tryAwaitRelease()
                if (released) {
                    val touchUpNow = System.currentTimeMillis()
                    val handled = handleTouchUp(state, touchUpNow, currentOnNormalClick)
                    
                    if (!handled) {
                        // Normal tap if not handled by long press logic
                        val elapsed = state.touchState.timerStartTime?.let { touchUpNow - it } ?: 0
                        Log.d("TVFocusUtils", "🔵 SHORT PRESS TOUCH erkannt (${elapsed}ms)")
                    }
                } else {
                    // Gesture was cancelled, so we just reset the timer
                    state.resetTouchTimer()
                    Log.d("TVFocusUtils", "🔴 TOUCH GESTURE abgebrochen")
                }
            }
        )
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
fun Modifier.onLongPress(
    onLongPress: () -> Unit,
    onNormalClick: (() -> Unit)? = null,
    durationMillis: Long = 300L
): Modifier = composed {
    val state = remember { LongPressState() }
    val coroutineScope = rememberCoroutineScope()
    val currentOnLongPress = rememberUpdatedState(onLongPress)
    val currentOnNormalClick = rememberUpdatedState(onNormalClick)

    val keyEventHandler = createKeyEventHandler(
        state,
        coroutineScope,
        durationMillis,
        currentOnLongPress,
        currentOnNormalClick
    )
    val touchHandler = Modifier.createTouchEventHandler(
        state = state,
        coroutineScope = coroutineScope,
        durationMillis = durationMillis,
        currentOnLongPress = currentOnLongPress,
        currentOnNormalClick = currentOnNormalClick
    )

    this
        .onKeyEvent(keyEventHandler)
        .then(touchHandler)
}

/**
 * Handle KeyDown event for long press detection
 */
private fun handleKeyDown(
    event: androidx.compose.ui.input.key.KeyEvent,
    state: LongPressState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    durationMillis: Long,
    currentOnLongPress: State<() -> Unit>
): Boolean {
    val keyName = InteractionUtils.getKeyName(event.key)
    Log.d("ContentCardLongPress", "[KeyDown] $keyName gedrückt - Starte Long-Press Timer (${durationMillis}ms)")
    
    val keyState = state.keyState
    
    // Reset state
    keyState.success = false
    keyState.blockUntilTime = null
    keyState.consumeNextRelease = false
    
    // Cancel existing timer if any
    val existingJob = keyState.job
    if (existingJob != null && existingJob.isActive) {
        Log.d("ContentCardLongPress", "[KeyDown] Vorheriger Timer wird abgebrochen")
        existingJob.cancel()
    }
    
    // Start long press timer
    keyState.timerStartTime = System.currentTimeMillis()
    keyState.job = coroutineScope.launch {
        Log.d("ContentCardLongPress", "[Timer] Timer gestartet, warte ${durationMillis}ms...")
        delay(durationMillis)
        val elapsed = System.currentTimeMillis() - (keyState.timerStartTime ?: 0)
        Log.d("ContentCardLongPress", "[Timer] ✅ Long-Press erkannt! (${elapsed}ms vergangen)")
        Log.d("ContentCardLongPress", "[Callback] Rufe onLongPress() auf")
        keyState.success = true
        keyState.consumeNextRelease = true
        keyState.blockUntilTime = System.currentTimeMillis() + LONG_PRESS_BLOCKING_PERIOD_MS
        Log.d("ContentCardLongPress", "[Block] Blocking-Periode gestartet (${LONG_PRESS_BLOCKING_PERIOD_MS}ms)")
        Log.d("ContentCardLongPress", "[Block] consumeNextKeyUp Flag gesetzt - nächster KeyUp wird konsumiert")
        currentOnLongPress.value.invoke()
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
    currentOnNormalClick: State<(() -> Unit)?>
): Boolean {
    val keyName = InteractionUtils.getKeyName(event.key)
    val keyState = state.keyState
    val elapsed = keyState.timerStartTime?.let { System.currentTimeMillis() - it } ?: 0
    
    return when {
        keyState.consumeNextRelease -> {
            Log.d("ContentCardLongPress", "[KeyUp] $keyName losgelassen nach ${elapsed}ms - consumeNextKeyUp Flag aktiv, verhindere normalen Click")
            // Reset only key-related state to avoid interfering with touch events
            keyState.resetTimer()
            keyState.success = false
            keyState.blockUntilTime = null
            keyState.consumeNextRelease = false
            true
        }
        keyState.success || state.isInBlockingPeriod(now) -> {
            Log.d("ContentCardLongPress", "[KeyUp] $keyName losgelassen nach ${elapsed}ms - Long-Press war erfolgreich, verhindere normalen Click")
            state.resetTimer()
            if (keyState.success) {
                keyState.success = false
            }
            true
        }
        keyState.job != null && keyState.job!!.isActive -> {
            Log.d("ContentCardLongPress", "[KeyUp] $keyName losgelassen nach ${elapsed}ms - Timer wird abgebrochen (zu früh)")
            state.resetTimer()
            if (currentOnNormalClick.value != null) {
                Log.d("ContentCardLongPress", "[KeyUp] Rufe onNormalClick() auf (Timer abgebrochen)")
                currentOnNormalClick.value?.invoke()
                true
            } else {
                false
            }
        }
        else -> {
            Log.d("ContentCardLongPress", "[KeyUp] $keyName losgelassen nach ${elapsed}ms - Kein aktiver Timer")
            // Fallback: Call onNormalClick if no timer was active (edge case)
            if (currentOnNormalClick.value != null) {
                Log.d("InteractionUtils", "🔵 SHORT PRESS KEY (Fallback - kein Timer)")
                currentOnNormalClick.value?.invoke()
                true
            } else {
                false
            }
        }
    }
}

/**
 * Handle Touch Down event for long press detection
 * The job will be cancelled automatically when the gesture ends (via tryAwaitRelease in onPress)
 */
private fun handleTouchDown(
    state: LongPressState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    durationMillis: Long,
    currentOnLongPress: State<() -> Unit>
) {
    Log.d("TVFocusUtils", "👆 TOUCH DOWN erkannt - Starte Long-Press Timer (${durationMillis}ms)")
    Log.d("ContentCardLongPress", "[TouchDown] Touch erkannt - Starte Long-Press Timer (${durationMillis}ms)")
    
    val touchState = state.touchState
    
    // Reset touch state
    touchState.success = false
    touchState.blockUntilTime = null
    touchState.consumeNextRelease = false
    
    // Cancel existing touch timer if any
    val existingJob = touchState.job
    if (existingJob != null && existingJob.isActive) {
        Log.d("ContentCardLongPress", "[TouchDown] Vorheriger Touch-Timer wird abgebrochen")
        existingJob.cancel()
    }
    
    // Start long press timer
    // Note: The job will be cancelled when the gesture ends (via tryAwaitRelease in onPress)
    touchState.timerStartTime = System.currentTimeMillis()
    touchState.job = coroutineScope.launch {
        Log.d("ContentCardLongPress", "[TouchTimer] Timer gestartet, warte ${durationMillis}ms...")
        delay(durationMillis)
        val elapsed = System.currentTimeMillis() - (touchState.timerStartTime ?: 0)
        Log.d("TVFocusUtils", "🟢 LONG PRESS TOUCH erkannt! (${elapsed}ms)")
        Log.d("ContentCardLongPress", "[TouchTimer] ✅ Long-Press erkannt! (${elapsed}ms vergangen)")
        Log.d("ContentCardLongPress", "[TouchCallback] Rufe onLongPress() auf")
        touchState.success = true
        touchState.consumeNextRelease = true
        touchState.blockUntilTime = System.currentTimeMillis() + LONG_PRESS_BLOCKING_PERIOD_MS
        Log.d("ContentCardLongPress", "[TouchBlock] Blocking-Periode gestartet (${LONG_PRESS_BLOCKING_PERIOD_MS}ms)")
        Log.d("ContentCardLongPress", "[TouchBlock] consumeNextTouchUp Flag gesetzt - nächster TouchUp wird konsumiert")
        currentOnLongPress.value.invoke()
    }
}

/**
 * Handle Touch Up event for long press detection
 */
private fun handleTouchUp(
    state: LongPressState,
    now: Long,
    currentOnNormalClick: State<(() -> Unit)?>
): Boolean {
    val touchState = state.touchState
    val elapsed = touchState.timerStartTime?.let { System.currentTimeMillis() - it } ?: 0
    
    return when {
        touchState.consumeNextRelease -> {
            Log.d("TVFocusUtils", "🟢 LONG PRESS TOUCH UP (${elapsed}ms - consumeNextRelease)")
            Log.d("ContentCardLongPress", "[TouchUp] Touch losgelassen nach ${elapsed}ms - consumeNextTouchUp Flag aktiv, verhindere normalen Click")
            // Reset only touch-related state to avoid interfering with key events
            touchState.resetTimer()
            touchState.success = false
            touchState.blockUntilTime = null
            touchState.consumeNextRelease = false
            true
        }
        touchState.success || state.isInTouchBlockingPeriod(now) -> {
            Log.d("TVFocusUtils", "🟢 LONG PRESS TOUCH UP (${elapsed}ms - success/blocking)")
            Log.d("ContentCardLongPress", "[TouchUp] Touch losgelassen nach ${elapsed}ms - Long-Press war erfolgreich, verhindere normalen Click")
            state.resetTouchTimer()
            if (touchState.success) {
                touchState.success = false
            }
            true
        }
        touchState.job != null && touchState.job!!.isActive -> {
            Log.d("TVFocusUtils", "🔵 SHORT PRESS TOUCH erkannt (${elapsed}ms - Timer abgebrochen)")
            Log.d("ContentCardLongPress", "[TouchUp] Touch losgelassen nach ${elapsed}ms - Timer wird abgebrochen (zu früh)")
            state.resetTouchTimer()
            if (currentOnNormalClick.value != null) {
                Log.d("ContentCardLongPress", "[TouchUp] Rufe onNormalClick() auf (Timer abgebrochen)")
                currentOnNormalClick.value?.invoke()
                true
            } else {
                false
            }
        }
        else -> {
            Log.d("ContentCardLongPress", "[TouchUp] Touch losgelassen nach ${elapsed}ms - Kein aktiver Timer")
            // Fallback: Call onNormalClick if no timer was active (edge case)
            if (currentOnNormalClick.value != null) {
                Log.d("InteractionUtils", "🔵 SHORT PRESS TOUCH (Fallback - kein Timer)")
                currentOnNormalClick.value?.invoke()
                true
            } else {
                false
            }
        }
    }
}

// Vertical swipe gesture constants
private const val MIN_VERTICAL_SWIPE_DISTANCE_DP = 50f
private const val MAX_HORIZONTAL_DRIFT_DP = 20f
private const val MIN_VERTICAL_SWIPE_VELOCITY_PX_PER_S = 500f

/**
 * Converts dp to pixels (approximate, using density)
 */
private fun Float.dpToPx(density: Float): Float {
    return this * density
}

/**
 * Calculates vertical swipe velocity in pixels per second
 * @return Velocity in px/s, or null if calculation is invalid
 */
private fun calculateVerticalSwipeVelocity(
    startY: Float,
    startTime: Long,
    currentY: Float,
    currentTime: Long
): Float? {
    val deltaY = kotlin.math.abs(currentY - startY)
    val deltaTime = currentTime - startTime
    
    return if (deltaY > 0 && deltaTime > 0) {
        deltaY / deltaTime * 1000 // px/s
    } else {
        null
    }
}

/**
 * Checks if swipe is primarily vertical (min vertical distance, max horizontal drift)
 */
private fun isVerticalSwipe(
    startX: Float,
    startY: Float,
    currentX: Float,
    currentY: Float,
    density: Float
): Boolean {
    val verticalDistance = kotlin.math.abs(currentY - startY)
    val horizontalDistance = kotlin.math.abs(currentX - startX)
    
    val minVerticalPx = MIN_VERTICAL_SWIPE_DISTANCE_DP.dpToPx(density)
    val maxHorizontalPx = MAX_HORIZONTAL_DRIFT_DP.dpToPx(density)
    
    return verticalDistance >= minVerticalPx && horizontalDistance <= maxHorizontalPx
}

/**
 * Checks if swipe velocity threshold is reached and determines direction
 * @return true if swipe threshold is met and callback should be called, false otherwise
 */
private fun checkSwipeVelocityAndDirection(
    startY: Float,
    currentY: Float,
    startTime: Long,
    currentTime: Long,
    onSwipeUp: (() -> Unit)?,
    onSwipeDown: (() -> Unit)?
): Boolean {
    val velocity = calculateVerticalSwipeVelocity(startY, startTime, currentY, currentTime)
    if (velocity == null || velocity <= MIN_VERTICAL_SWIPE_VELOCITY_PX_PER_S) {
        return false
    }
    
    val isSwipeUp = currentY < startY
    return when {
        isSwipeUp && onSwipeUp != null -> {
            onSwipeUp()
            true
        }
        !isSwipeUp && onSwipeDown != null -> {
            onSwipeDown()
            true
        }
        else -> false
    }
}

/**
 * Processes vertical swipe drag events and detects if swipe velocity threshold is reached
 * @return true if swipe was detected and callback was called, false otherwise
 */
private suspend fun AwaitPointerEventScope.processVerticalSwipeDrag(
    initialDrag: PointerInputChange,
    startX: Float,
    startY: Float,
    startTime: Long,
    density: Float,
    onSwipeUp: (() -> Unit)?,
    onSwipeDown: (() -> Unit)?
): Boolean {
    var lastDrag: PointerInputChange = initialDrag
    
    while (true) {
        val currentX = lastDrag.position.x
        val currentY = lastDrag.position.y
        val currentTime = lastDrag.uptimeMillis
        
        if (isVerticalSwipe(startX, startY, currentX, currentY, density) &&
            checkSwipeVelocityAndDirection(startY, currentY, startTime, currentTime, onSwipeUp, onSwipeDown)) {
            return true
        }
        
        val dragEvent = awaitDragOrCancellation(lastDrag.id)
        if (dragEvent == null) {
            return false
        }
        lastDrag = dragEvent
    }
}

/**
 * Creates a modifier for vertical swipe gesture detection
 * Detects vertical swipes (min 50dp vertical, max 20dp horizontal drift)
 * 
 * @param onSwipeUp Called when user swipes up (min velocity threshold must be met)
 * @param onSwipeDown Called when user swipes down (min velocity threshold must be met)
 */
@Suppress("ReturnFromAwaitPointerEventScope")
fun Modifier.createVerticalSwipeGestureModifier(
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null
): Modifier {
    if (onSwipeUp == null && onSwipeDown == null) {
        return this
    }
    
    return this.composed {
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        
        this.pointerInput(onSwipeUp, onSwipeDown, density) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val swipeStartX = down.position.x
                    val swipeStartY = down.position.y
                    val swipeStartTime = down.uptimeMillis
                    
                    val drag = awaitDragOrCancellation(down.id)
                    if (drag != null) {
                        processVerticalSwipeDrag(
                            drag,
                            swipeStartX,
                            swipeStartY,
                            swipeStartTime,
                            density,
                            onSwipeUp,
                            onSwipeDown
                        )
                    }
                }
            }
        }
    }
}
