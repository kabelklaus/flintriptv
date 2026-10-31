package com.flintr.tv.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.utils.safeRequestFocus

private const val LOG_TAG_PLAYER_ERROR_DIALOG = "PlayerErrorDialog"

/**
 * Error dialog shown when playback fails
 * @param errorMessage Error message to display
 * @param onRetry Callback when retry button is clicked
 * @param onDismiss Callback when dismiss/back button is clicked
 */
@Composable
fun PlayerErrorDialog(
    errorMessage: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    errorMessage?.let { message ->
        val retryFocusRequester = remember { FocusRequester() }
        val dismissFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            val focused = retryFocusRequester.safeRequestFocus(
                LOG_TAG_PLAYER_ERROR_DIALOG,
                "RETRY_BUTTON"
            )
            if (!focused) {
                dismissFocusRequester.safeRequestFocus(
                    LOG_TAG_PLAYER_ERROR_DIALOG,
                    "DISMISS_BUTTON_FALLBACK"
                )
            }
        }

        Card(
            modifier = modifier
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Wiedergabefehler",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.focusRequester(retryFocusRequester)
                    ) {
                        Text("Erneut versuchen")
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(dismissFocusRequester)
                    ) {
                        Text("Zurück")
                    }
                }
            }
        }
    }
}
