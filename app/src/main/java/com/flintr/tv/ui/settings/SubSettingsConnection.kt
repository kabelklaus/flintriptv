package com.flintr.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import android.util.Log
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.utils.onDpadDirection
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
    var passwordVisible by remember { mutableStateOf(false) }
    
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
                // Server Section
                SettingsCard(
                    title = "Server",
                    icon = Icons.Default.Storage,
                    colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                    ) {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Serveradresse") },
                            placeholder = { Text("http://example.com") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onDpadDirection(
                                    onLeft = {
                                        Log.i("SettingsNav", "🔙 BACK: SettingsConnection -> SettingsMainScreen (via D-Pad Left)")
                                        onBack()
                                        true
                                    }
                                )
                                .focusRequester(serverUrlFocusRequester),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(Dimensions.radiusMD),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentColor,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedLabelColor = colors.textPrimary,
                                unfocusedLabelColor = colors.textSecondary,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                cursorColor = colors.accentColor,
                                focusedLeadingIconColor = colors.accentColor,
                                unfocusedLeadingIconColor = colors.iconColor
                            )
                        )
                        
                        OutlinedTextField(
                            value = playlistName,
                            onValueChange = { playlistName = it },
                            label = { Text("Playlist-Name (optional)") },
                            placeholder = { Text("Meine Playlist") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(playlistNameFocusRequester),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PlaylistPlay,
                                    contentDescription = null
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(Dimensions.radiusMD),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentColor,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedLabelColor = colors.textPrimary,
                                unfocusedLabelColor = colors.textSecondary,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                cursorColor = colors.accentColor,
                                focusedLeadingIconColor = colors.accentColor,
                                unfocusedLeadingIconColor = colors.iconColor
                            )
                        )
                    }
                }
                
                // User Section
                SettingsCard(
                    title = "Benutzer",
                    icon = Icons.Default.Person,
                    colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Benutzername") },
                            placeholder = { Text("Ihr Benutzername") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(usernameFocusRequester),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(Dimensions.radiusMD),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentColor,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedLabelColor = colors.textPrimary,
                                unfocusedLabelColor = colors.textSecondary,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                cursorColor = colors.accentColor,
                                focusedLeadingIconColor = colors.accentColor,
                                unfocusedLeadingIconColor = colors.iconColor
                            )
                        )
                        
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Kennwort") },
                            placeholder = { Text("Ihr Kennwort") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(passwordFocusRequester),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (passwordVisible) "Passwort verbergen" else "Passwort anzeigen"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(Dimensions.radiusMD),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentColor,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedLabelColor = colors.textPrimary,
                                unfocusedLabelColor = colors.textSecondary,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                cursorColor = colors.accentColor,
                                focusedLeadingIconColor = colors.accentColor,
                                unfocusedLeadingIconColor = colors.iconColor
                            )
                        )
                    }
                }
                
                // Connection Section
                SettingsCard(
                    title = "Verbindung",
                    icon = Icons.Default.Settings,
                    colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                    ) {
                        // Test Connection Button
                        var isTestButtonFocused by remember { mutableStateOf(false) }
                        
                        Button(
                            onClick = {
                                viewModel.testConnection(serverUrl, username, password)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(testButtonFocusRequester)
                                .onFocusChanged { focusState ->
                                    isTestButtonFocused = focusState.isFocused
                                }
                                .then(
                                    if (isTestButtonFocused) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = Color.White,
                                            shape = RoundedCornerShape(Dimensions.radiusMD)
                                        )
                                    } else {
                                        Modifier
                                    }
                                ),
                            enabled = !connectionTestState.isLoading && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                            shape = RoundedCornerShape(Dimensions.radiusMD),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTestButtonFocused) colors.buttonPrimaryHover else colors.buttonPrimary,
                                contentColor = Color.White,
                                disabledContainerColor = colors.buttonPrimary.copy(alpha = 0.6f),
                                disabledContentColor = Color.White.copy(alpha = 0.6f)
                            ),
                            contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
                        ) {
                            if (connectionTestState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(Dimensions.spacingXS))
                                Text(
                                    text = "Wird getestet...",
                                    fontSize = FontSizes.fontSizeXL,
                                    fontWeight = FontWeight.W600
                                )
                            } else {
                                Text(
                                    text = "Verbindung testen",
                                    fontSize = FontSizes.fontSizeXL,
                                    fontWeight = FontWeight.W600
                                )
                            }
                        }
                        
                        // Connection Result
                        connectionTestState.error?.let { error ->
                            ConnectionResult.Error(error)
                        }?.let { result ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = colors.dangerBg
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(Dimensions.radiusMD)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Dimensions.spacingMD),
                                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = colors.dangerText
                                    )
                                    Text(
                                        text = result.message,
                                        color = colors.dangerText,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        
                        connectionTestState.serverInfo?.let {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = SharedColors.successGreen.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(Dimensions.radiusMD)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Dimensions.spacingMD),
                                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SharedColors.successGreen
                                    )
                                    Text(
                                        text = "Verbindung erfolgreich!",
                                        color = SharedColors.successGreen,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        
                        // Account Expiration Info
                        expirationDateText?.let { info ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = colors.bgHover
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(Dimensions.radiusMD)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Dimensions.spacingMD),
                                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = expirationDateColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = info,
                                        color = expirationDateColor,
                                        fontWeight = FontWeight.W500,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        
                        // Reload Data Button
                        var isReloadButtonFocused by remember { mutableStateOf(false) }
                        
                        OutlinedButton(
                            onClick = {
                                // Mock: Show snackbar
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(reloadButtonFocusRequester)
                                .onFocusChanged { focusState ->
                                    isReloadButtonFocused = focusState.isFocused
                                },
                            shape = RoundedCornerShape(Dimensions.radiusMD),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isReloadButtonFocused) colors.accentColor else colors.textPrimary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                width = if (isReloadButtonFocused) 2.dp else 1.dp
                            ),
                            contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = if (isReloadButtonFocused) colors.accentColor else colors.iconColor
                            )
                            Spacer(modifier = Modifier.width(Dimensions.spacingXS))
                            Text(
                                text = "Daten neu laden",
                                fontSize = FontSizes.fontSizeXL,
                                fontWeight = if (isReloadButtonFocused) FontWeight.W600 else FontWeight.Normal
                            )
                        }
                        
                        // Save Button
                        var isSaveButtonFocused by remember { mutableStateOf(false) }
                        
                        OutlinedButton(
                            onClick = {
                                // Mock: Show snackbar
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(saveButtonFocusRequester)
                                .onFocusChanged { focusState ->
                                    isSaveButtonFocused = focusState.isFocused
                                },
                            shape = RoundedCornerShape(Dimensions.radiusMD),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isSaveButtonFocused) colors.accentColor else colors.textPrimary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                width = if (isSaveButtonFocused) 2.dp else 1.dp
                            ),
                            contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = if (isSaveButtonFocused) colors.accentColor else colors.iconColor
                            )
                            Spacer(modifier = Modifier.width(Dimensions.spacingXS))
                            Text(
                                text = "Einstellungen speichern",
                                fontSize = FontSizes.fontSizeXL,
                                fontWeight = if (isSaveButtonFocused) FontWeight.W600 else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class ConnectionResult(val message: String) {
    class Success(message: String) : ConnectionResult(message)
    class Error(message: String) : ConnectionResult(message)
}

