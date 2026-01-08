package com.flintr.tv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = appColors()

    val serverUrlFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val buttonFocusRequester = remember { FocusRequester() }

    val scrollState = rememberScrollState()

    // Fade-in animation on screen start
    var isVisible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "fadeIn"
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Navigate to data loading screen on successful login
    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            viewModel.resetLoginSuccess()
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.spacingXL),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .alpha(alpha)
                .verticalScroll(scrollState),
            shape = RoundedCornerShape(Dimensions.radiusXL),
            colors = CardDefaults.cardColors(
                containerColor = colors.bgCard
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = colors.cardBorder,
                        shape = RoundedCornerShape(Dimensions.radiusXL)
                    )
                    .padding(Dimensions.spacingXL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
            ) {
                Text(
                    text = "Anmeldung",
                    style = MaterialTheme.typography.headlineMedium
                )

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://example.com") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(serverUrlFocusRequester),
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(Dimensions.radiusMD),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentColor,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedLabelColor = colors.textPrimary,
                        unfocusedLabelColor = colors.textSecondary,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accentColor,
                        focusedPlaceholderColor = colors.textSecondary,
                        unfocusedPlaceholderColor = colors.textSecondary
                    )
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Benutzername") },
                    placeholder = { Text("Ihr Benutzername") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(usernameFocusRequester),
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(Dimensions.radiusMD),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentColor,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedLabelColor = colors.textPrimary,
                        unfocusedLabelColor = colors.textSecondary,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accentColor,
                        focusedPlaceholderColor = colors.textSecondary,
                        unfocusedPlaceholderColor = colors.textSecondary
                    )
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Passwort") },
                    placeholder = { Text("Ihr Passwort") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(Dimensions.radiusMD),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentColor,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedLabelColor = colors.textPrimary,
                        unfocusedLabelColor = colors.textSecondary,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accentColor,
                        focusedPlaceholderColor = colors.textSecondary,
                        unfocusedPlaceholderColor = colors.textSecondary
                    )
                )

                if (uiState.error != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = colors.dangerBg
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimensions.radiusMD)
                    ) {
                        Text(
                            text = uiState.error ?: "",
                            color = colors.dangerText,
                            modifier = Modifier.padding(Dimensions.spacingMD),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimensions.spacingXL))

                var isButtonFocused by remember { mutableStateOf(false) }
                
                Button(
                    onClick = {
                        viewModel.login(serverUrl.trim(), username.trim(), password)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(buttonFocusRequester)
                        .onFocusChanged { focusState ->
                            isButtonFocused = focusState.isFocused
                        }
                        .then(
                            if (isButtonFocused) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(Dimensions.radiusMD)
                                )
                            } else {
                                Modifier
                            }
                        ),
                    enabled = !uiState.isLoading && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    shape = RoundedCornerShape(Dimensions.radiusMD),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isButtonFocused) colors.accentColor else colors.buttonPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = colors.buttonPrimary.copy(alpha = 0.6f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    ),
                    contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(Dimensions.spacingXS))
                        Text(
                            text = "Wird angemeldet...",
                            fontSize = FontSizes.fontSizeXL,
                            fontWeight = FontWeight.W600
                        )
                    } else {
                        Text(
                            text = "Anmelden",
                            fontSize = FontSizes.fontSizeXL,
                            fontWeight = FontWeight.W600
                        )
                    }
                }
            }
        }
    }
}

