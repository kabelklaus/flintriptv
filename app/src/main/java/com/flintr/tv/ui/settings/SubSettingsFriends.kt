package com.flintr.tv.ui.settings

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flintr.tv.R
import com.flintr.tv.data.model.FriendEntry
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.safeRequestFocus
import com.flintr.tv.viewmodel.SettingsFriendsViewModel
import kotlinx.coroutines.delay

@Composable
fun SettingsFriends(
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsFriendsViewModel = hiltViewModel()
) {
    val colors = appColors()
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    var username by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var usernameCopied by remember { mutableStateOf(false) }

    val usernameFocusRequester = remember { FocusRequester() }
    val aliasFocusRequester = remember { FocusRequester() }
    val addButtonFocusRequester = remember { FocusRequester() }
    val copyButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        usernameFocusRequester.safeRequestFocus(targetName = "FriendUsernameField")
    }

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            username = ""
            alias = ""
        }
    }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        val message = uiState.errorMessage ?: uiState.successMessage
        if (message != null) {
            onShowSnackbar(message)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(usernameCopied) {
        if (usernameCopied) {
            delay(2000L)
            usernameCopied = false
        }
    }

    BackHandler(enabled = true) {
        Log.i("SettingsNav", "🔙 BACK: SettingsFriends -> SettingsMainScreen (via BackHandler)")
        onBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPreview)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(Dimensions.spacingXL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
            ) {
                SettingsCard(
                    title = stringResource(R.string.friends),
                    icon = Icons.Default.PersonAdd,
                    colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                    ) {
                        uiState.currentUsername?.let { currentUsername ->
                            CurrentUsernameCard(
                                username = currentUsername,
                                isCopied = usernameCopied,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(currentUsername))
                                    usernameCopied = true
                                },
                                focusRequester = copyButtonFocusRequester
                            )
                        }

                        ConnectionTextField(
                            config = ConnectionTextFieldConfig(
                                value = username,
                                onValueChange = {
                                    username = it
                                    viewModel.clearMessages()
                                },
                                label = stringResource(R.string.username),
                                placeholder = stringResource(R.string.friend_username_placeholder),
                                leadingIcon = Icons.Default.Person,
                                focusRequester = usernameFocusRequester,
                                colors = colors,
                                onDpadLeft = {
                                    Log.i("SettingsNav", "🔙 BACK: SettingsFriends -> SettingsMainScreen (via D-Pad Left)")
                                    onBack()
                                }
                            )
                        )

                        ConnectionTextField(
                            config = ConnectionTextFieldConfig(
                                value = alias,
                                onValueChange = {
                                    alias = it
                                    viewModel.clearMessages()
                                },
                                label = stringResource(R.string.friend_alias_optional),
                                placeholder = stringResource(R.string.friend_alias_placeholder),
                                leadingIcon = Icons.Default.Group,
                                focusRequester = aliasFocusRequester,
                                colors = colors
                            )
                        )

                        PrimaryActionButton(
                            config = PrimaryActionButtonConfig(
                                text = stringResource(R.string.add_friend),
                                onClick = {
                                    viewModel.addFriend(username, alias)
                                },
                                enabled = username.trim().isNotBlank() && !uiState.isSaving,
                                isLoading = uiState.isSaving,
                                loadingText = stringResource(R.string.friend_is_being_added),
                                focusRequester = addButtonFocusRequester,
                                colors = colors
                            )
                        )
                    }
                }

                SettingsCard(
                    title = stringResource(R.string.my_friends),
                    icon = Icons.Default.Group,
                    colors = colors
                ) {
                    when {
                        uiState.isLoading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        uiState.friends.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.no_friends_added_yet),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary
                            )
                        }

                        else -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSM)
                            ) {
                                uiState.friends.forEach { friend ->
                                    FriendListItem(friend = friend)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentUsernameCard(
    username: String,
    isCopied: Boolean,
    onCopy: () -> Unit,
    focusRequester: FocusRequester
) {
    val colors = appColors()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.radiusMD),
        colors = CardDefaults.cardColors(
            containerColor = colors.accentColorHoverBg
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.spacingMD),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colors.accentColor
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXXS)
                ) {
                    Text(
                        text = stringResource(R.string.your_share_username),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.W600,
                        color = colors.textPrimary
                    )
                    Text(
                        text = username,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.W600,
                        color = colors.textPrimary
                    )
                }
            }

            SecondaryActionButton(
                config = SecondaryActionButtonConfig(
                    text = if (isCopied) {
                        stringResource(R.string.username_copied)
                    } else {
                        stringResource(R.string.copy_username)
                    },
                    icon = Icons.Default.ContentCopy,
                    onClick = onCopy,
                    focusRequester = focusRequester,
                    colors = colors
                )
            )
        }
    }
}

@Composable
private fun FriendListItem(
    friend: FriendEntry
) {
    val colors = appColors()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.radiusMD),
        colors = CardDefaults.cardColors(containerColor = colors.bgPreview)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.spacingMD),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXXS)
        ) {
            Text(
                text = friend.alias ?: friend.username,
                fontSize = FontSizes.fontSizeXL,
                fontWeight = FontWeight.W600,
                color = colors.textPrimary
            )

            if (!friend.alias.isNullOrBlank()) {
                Text(
                    text = friend.username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
        }
    }
}
