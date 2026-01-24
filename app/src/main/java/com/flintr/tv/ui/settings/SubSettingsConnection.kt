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
import androidx.compose.ui.res.stringResource
import com.flintr.tv.R
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.theme.SharedColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.flintr.tv.viewmodel.SettingsConnectionViewModel
import com.flintr.tv.utils.formatExpirationDate
import com.flintr.tv.utils.getExpirationDateColor
import com.flintr.tv.data.local.CredentialsRepository

@Composable
fun SettingsConnection(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsConnectionViewModel = hiltViewModel(),
    credentialsRepository: CredentialsRepository,
    onShowSnackbar: (String) -> Unit
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
    
    // String resources for callbacks
    val settingsSavedText = stringResource(R.string.settings_saved)
    
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
    
    // Auto-clear cache refill success message after 5 seconds
    LaunchedEffect(connectionTestState.cacheRefillSuccess) {
        if (connectionTestState.cacheRefillSuccess) {
            delay(5000) // 5 seconds
            viewModel.clearCacheRefillStatus()
        }
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
                    config = ServerSectionConfig(
                        serverUrl = serverUrl,
                        onServerUrlChange = { serverUrl = it },
                        playlistName = playlistName,
                        onPlaylistNameChange = { playlistName = it },
                        serverUrlFocusRequester = serverUrlFocusRequester,
                        playlistNameFocusRequester = playlistNameFocusRequester,
                        colors = colors,
                        onBack = onBack
                    )
                )
                
                UserSection(
                    config = UserSectionConfig(
                        username = username,
                        onUsernameChange = { username = it },
                        password = password,
                        onPasswordChange = { password = it },
                        usernameFocusRequester = usernameFocusRequester,
                        passwordFocusRequester = passwordFocusRequester,
                        colors = colors
                    )
                )
                
                ConnectionSection(
                    config = ConnectionSectionConfig(
                        connectionTestState = connectionTestState,
                        expirationDateText = expirationDateText,
                        expirationDateColor = expirationDateColor,
                        testButtonFocusRequester = testButtonFocusRequester,
                        reloadButtonFocusRequester = reloadButtonFocusRequester,
                        saveButtonFocusRequester = saveButtonFocusRequester,
                        onTestConnection = { viewModel.testConnection(serverUrl, username, password) },
                        onReloadData = { viewModel.reloadServerInfo() },
                        onSaveSettings = {
                            credentialsRepository.saveCredentials(
                                com.flintr.tv.data.local.Credentials(
                                    serverUrl = serverUrl,
                                    username = username,
                                    password = password
                                )
                            )
                            onShowSnackbar(settingsSavedText)
                        },
                        isFormValid = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                        colors = colors
                    )
                )
            }
        }
    }
}

private data class ServerSectionConfig(
    val serverUrl: String,
    val onServerUrlChange: (String) -> Unit,
    val playlistName: String,
    val onPlaylistNameChange: (String) -> Unit,
    val serverUrlFocusRequester: FocusRequester,
    val playlistNameFocusRequester: FocusRequester,
    val colors: com.flintr.tv.ui.theme.AppColors,
    val onBack: () -> Unit
)

@Composable
private fun ServerSection(
    config: ServerSectionConfig
) {
    SettingsCard(
        title = stringResource(R.string.server),
        icon = Icons.Default.Storage,
        colors = config.colors
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            ConnectionTextField(
                config = ConnectionTextFieldConfig(
                    value = config.serverUrl,
                    onValueChange = config.onServerUrlChange,
                    label = stringResource(R.string.server_address),
                    placeholder = stringResource(R.string.server_address_placeholder),
                    leadingIcon = Icons.Default.Link,
                    focusRequester = config.serverUrlFocusRequester,
                    colors = config.colors,
                    onDpadLeft = {
                        Log.i("SettingsNav", "🔙 BACK: SettingsConnection -> SettingsMainScreen (via D-Pad Left)")
                        config.onBack()
                    }
                )
            )
            
            ConnectionTextField(
                config = ConnectionTextFieldConfig(
                    value = config.playlistName,
                    onValueChange = config.onPlaylistNameChange,
                    label = stringResource(R.string.playlist_name_optional),
                    placeholder = stringResource(R.string.playlist_name_placeholder),
                    leadingIcon = Icons.Default.PlaylistPlay,
                    focusRequester = config.playlistNameFocusRequester,
                    colors = config.colors
                )
            )
        }
    }
}

private data class UserSectionConfig(
    val username: String,
    val onUsernameChange: (String) -> Unit,
    val password: String,
    val onPasswordChange: (String) -> Unit,
    val usernameFocusRequester: FocusRequester,
    val passwordFocusRequester: FocusRequester,
    val colors: com.flintr.tv.ui.theme.AppColors
)

@Composable
private fun UserSection(
    config: UserSectionConfig
) {
    SettingsCard(
        title = stringResource(R.string.user),
        icon = Icons.Default.Person,
        colors = config.colors
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            ConnectionTextField(
                config = ConnectionTextFieldConfig(
                    value = config.username,
                    onValueChange = config.onUsernameChange,
                    label = stringResource(R.string.username),
                    placeholder = stringResource(R.string.your_username),
                    leadingIcon = Icons.Default.Person,
                    focusRequester = config.usernameFocusRequester,
                    colors = config.colors
                )
            )
            
            ConnectionPasswordField(
                value = config.password,
                onValueChange = config.onPasswordChange,
                focusRequester = config.passwordFocusRequester,
                colors = config.colors
            )
        }
    }
}

private data class ConnectionSectionConfig(
    val connectionTestState: com.flintr.tv.viewmodel.ConnectionTestState,
    val expirationDateText: String?,
    val expirationDateColor: androidx.compose.ui.graphics.Color,
    val testButtonFocusRequester: FocusRequester,
    val reloadButtonFocusRequester: FocusRequester,
    val saveButtonFocusRequester: FocusRequester,
    val onTestConnection: () -> Unit,
    val onReloadData: () -> Unit,
    val onSaveSettings: () -> Unit,
    val isFormValid: Boolean,
    val colors: com.flintr.tv.ui.theme.AppColors
)

@Composable
private fun ConnectionSection(
    config: ConnectionSectionConfig
) {
    SettingsCard(
        title = stringResource(R.string.connection),
        icon = Icons.Default.Settings,
        colors = config.colors
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            PrimaryActionButton(
                config = PrimaryActionButtonConfig(
                    text = stringResource(R.string.test_connection),
                    onClick = config.onTestConnection,
                    enabled = !config.connectionTestState.isLoading && config.isFormValid,
                    isLoading = config.connectionTestState.isLoading,
                    loadingText = stringResource(R.string.testing_connection),
                    focusRequester = config.testButtonFocusRequester,
                    colors = config.colors
                )
            )
            
            config.connectionTestState.error?.let { error ->
                StatusCard(
                    message = error,
                    icon = Icons.Default.Error,
                    iconTint = config.colors.dangerText,
                    backgroundColor = config.colors.dangerBg,
                    textColor = config.colors.dangerText
                )
            }
            
            config.connectionTestState.serverInfo?.let {
                StatusCard(
                    message = stringResource(R.string.connection_successful),
                    icon = Icons.Default.CheckCircle,
                    iconTint = SharedColors.successGreen,
                    backgroundColor = SharedColors.successGreen.copy(alpha = 0.1f),
                    textColor = SharedColors.successGreen
                )
            }
            
            config.expirationDateText?.let { info ->
                StatusCard(
                    message = info,
                    icon = Icons.Default.CalendarToday,
                    iconTint = config.expirationDateColor,
                    backgroundColor = config.colors.bgHover,
                    textColor = config.expirationDateColor
                )
            }
            
            // Cache refill status
            if (config.connectionTestState.isRefillingCache) {
                StatusCard(
                    message = config.connectionTestState.cacheRefillMessage ?: "Cache wird aktualisiert...",
                    icon = Icons.Default.Refresh,
                    iconTint = config.colors.textPrimary,
                    backgroundColor = config.colors.bgHover,
                    textColor = config.colors.textPrimary
                )
            } else if (config.connectionTestState.cacheRefillSuccess && config.connectionTestState.cacheRefillMessage != null) {
                StatusCard(
                    message = config.connectionTestState.cacheRefillMessage,
                    icon = Icons.Default.CheckCircle,
                    iconTint = SharedColors.successGreen,
                    backgroundColor = SharedColors.successGreen.copy(alpha = 0.1f),
                    textColor = SharedColors.successGreen
                )
            }
            
            SecondaryActionButton(
                config = SecondaryActionButtonConfig(
                    text = stringResource(R.string.reload_data),
                    icon = Icons.Default.Refresh,
                    onClick = config.onReloadData,
                    enabled = !config.connectionTestState.isRefillingCache,
                    focusRequester = config.reloadButtonFocusRequester,
                    colors = config.colors
                )
            )
            
            SecondaryActionButton(
                config = SecondaryActionButtonConfig(
                    text = stringResource(R.string.save_settings),
                    icon = Icons.Default.Save,
                    onClick = config.onSaveSettings,
                    focusRequester = config.saveButtonFocusRequester,
                    colors = config.colors
                )
            )
        }
    }
}

