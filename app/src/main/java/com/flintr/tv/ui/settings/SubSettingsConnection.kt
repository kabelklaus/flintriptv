package com.flintr.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import android.util.Log
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.theme.SharedColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.flintr.tv.viewmodel.SettingsConnectionViewModel
import com.flintr.tv.utils.formatExpirationDate
import com.flintr.tv.utils.getExpirationDateColor
import com.flintr.tv.data.local.CredentialsRepository

@Composable
fun SettingsConnection(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsConnectionViewModel = hiltViewModel(),
    credentialsRepository: CredentialsRepository
) {
    val colors = appColors()
    val scrollState = rememberScrollState()
    
    // ViewModel state
    val connectionTestState by viewModel.connectionTestState.collectAsState()
    
    // Load saved credentials and populate form fields
    val savedCredentials = remember { credentialsRepository.getCredentials() }
    
    // Form state - initialize with saved credentials if available
    var serverUrl by remember { mutableStateOf(savedCredentials?.serverUrl ?: "") }
    var playlistName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(savedCredentials?.username ?: "") }
    var password by remember { mutableStateOf(savedCredentials?.password ?: "") }
    
    // Format expiration date if available
    val expirationDateText = formatExpirationDate(connectionTestState.serverInfo?.userInfo?.expDate)
    val expirationDateColor = getExpirationDateColor(
        expDate = connectionTestState.serverInfo?.userInfo?.expDate,
        normalColor = colors.textPrimary,
        warningColor = SharedColors.orange,
        expiredColor = colors.dangerText
    )
    
    // Focus requesters
    val serverUrlFocusRequester = remember { FocusRequester() }
    val playlistNameFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val testButtonFocusRequester = remember { FocusRequester() }
    val reloadButtonFocusRequester = remember { FocusRequester() }
    val saveButtonFocusRequester = remember { FocusRequester() }
    
    // Set initial focus
    LaunchedEffect(Unit) {
        serverUrlFocusRequester.requestFocus()
    }
    
    // Handle Back button using BackHandler (works even when IME is visible)
    BackHandler(enabled = true) {
        Log.i("SettingsNav", "🔙 BACK: SettingsConnection -> SettingsMainScreen (via BackHandler)")
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
            // Responsive container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
            ) {
                ServerSection(
                    serverUrl = serverUrl,
                    onServerUrlChange = { serverUrl = it },
                    playlistName = playlistName,
                    onPlaylistNameChange = { playlistName = it },
                    serverUrlFocusRequester = serverUrlFocusRequester,
                    playlistNameFocusRequester = playlistNameFocusRequester,
                    colors = colors,
                    onBack = onBack
                )
                
                UserSection(
                    username = username,
                    onUsernameChange = { username = it },
                    password = password,
                    onPasswordChange = { password = it },
                    usernameFocusRequester = usernameFocusRequester,
                    passwordFocusRequester = passwordFocusRequester,
                    colors = colors
                )
                
                ConnectionSection(
                    connectionTestState = connectionTestState,
                    expirationDateText = expirationDateText,
                    expirationDateColor = expirationDateColor,
                    testButtonFocusRequester = testButtonFocusRequester,
                    reloadButtonFocusRequester = reloadButtonFocusRequester,
                    saveButtonFocusRequester = saveButtonFocusRequester,
                    onTestConnection = { viewModel.testConnection(serverUrl, username, password) },
                    isFormValid = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun ServerSection(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    playlistName: String,
    onPlaylistNameChange: (String) -> Unit,
    serverUrlFocusRequester: FocusRequester,
    playlistNameFocusRequester: FocusRequester,
    colors: com.flintr.tv.ui.theme.AppColors,
    onBack: () -> Unit
) {
    SettingsCard(
        title = "Server",
        icon = Icons.Default.Storage,
        colors = colors
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            ConnectionTextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                label = "Serveradresse",
                placeholder = "http://example.com",
                leadingIcon = Icons.Default.Link,
                focusRequester = serverUrlFocusRequester,
                colors = colors,
                onDpadLeft = {
                    Log.i("SettingsNav", "🔙 BACK: SettingsConnection -> SettingsMainScreen (via D-Pad Left)")
                    onBack()
                }
            )
            
            ConnectionTextField(
                value = playlistName,
                onValueChange = onPlaylistNameChange,
                label = "Playlist-Name (optional)",
                placeholder = "Meine Playlist",
                leadingIcon = Icons.Default.PlaylistPlay,
                focusRequester = playlistNameFocusRequester,
                colors = colors
            )
        }
    }
}

@Composable
private fun UserSection(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    usernameFocusRequester: FocusRequester,
    passwordFocusRequester: FocusRequester,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    SettingsCard(
        title = "Benutzer",
        icon = Icons.Default.Person,
        colors = colors
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            ConnectionTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = "Benutzername",
                placeholder = "Ihr Benutzername",
                leadingIcon = Icons.Default.Person,
                focusRequester = usernameFocusRequester,
                colors = colors
            )
            
            ConnectionPasswordField(
                value = password,
                onValueChange = onPasswordChange,
                focusRequester = passwordFocusRequester,
                colors = colors
            )
        }
    }
}

@Composable
private fun ConnectionSection(
    connectionTestState: com.flintr.tv.viewmodel.ConnectionTestState,
    expirationDateText: String?,
    expirationDateColor: androidx.compose.ui.graphics.Color,
    testButtonFocusRequester: FocusRequester,
    reloadButtonFocusRequester: FocusRequester,
    saveButtonFocusRequester: FocusRequester,
    onTestConnection: () -> Unit,
    isFormValid: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    SettingsCard(
        title = "Verbindung",
        icon = Icons.Default.Settings,
        colors = colors
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            PrimaryActionButton(
                text = "Verbindung testen",
                onClick = onTestConnection,
                enabled = !connectionTestState.isLoading && isFormValid,
                isLoading = connectionTestState.isLoading,
                loadingText = "Wird getestet...",
                focusRequester = testButtonFocusRequester,
                colors = colors
            )
            
            connectionTestState.error?.let { error ->
                StatusCard(
                    message = error,
                    icon = Icons.Default.Error,
                    iconTint = colors.dangerText,
                    backgroundColor = colors.dangerBg,
                    textColor = colors.dangerText
                )
            }
            
            connectionTestState.serverInfo?.let {
                StatusCard(
                    message = "Verbindung erfolgreich!",
                    icon = Icons.Default.CheckCircle,
                    iconTint = SharedColors.successGreen,
                    backgroundColor = SharedColors.successGreen.copy(alpha = 0.1f),
                    textColor = SharedColors.successGreen
                )
            }
            
            expirationDateText?.let { info ->
                StatusCard(
                    message = info,
                    icon = Icons.Default.CalendarToday,
                    iconTint = expirationDateColor,
                    backgroundColor = colors.bgHover,
                    textColor = expirationDateColor
                )
            }
            
            SecondaryActionButton(
                text = "Daten neu laden",
                icon = Icons.Default.Refresh,
                onClick = { /* Mock: Show snackbar */ },
                focusRequester = reloadButtonFocusRequester,
                colors = colors
            )
            
            SecondaryActionButton(
                text = "Einstellungen speichern",
                icon = Icons.Default.Save,
                onClick = { /* Mock: Show snackbar */ },
                focusRequester = saveButtonFocusRequester,
                colors = colors
            )
        }
    }
}

