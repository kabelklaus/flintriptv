package com.flintr.tv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.viewmodel.LoginViewModel
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Returns standardized text field colors for login form
 */
@Composable
private fun loginTextFieldColors(colors: com.flintr.tv.ui.theme.AppColors): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accentColor,
        unfocusedBorderColor = colors.cardBorder,
        focusedLabelColor = colors.accentColor,
        unfocusedLabelColor = colors.textSecondary,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        cursorColor = colors.accentColor,
        focusedPlaceholderColor = colors.textSecondary,
        unfocusedPlaceholderColor = colors.textSecondary,
        focusedLeadingIconColor = colors.accentColor,
        unfocusedLeadingIconColor = colors.iconColor
    )
}

/**
 * Configuration data class for LoginTextField to reduce parameter count
 */
data class LoginTextFieldConfig(
    val label: String,
    val placeholder: String,
    val leadingIcon: ImageVector,
    val enabled: Boolean = true,
    val focusRequester: FocusRequester? = null,
    val onGloballyPositioned: (() -> Unit)? = null,
    val trailingIcon: @Composable (() -> Unit)? = null,
    val visualTransformation: VisualTransformation = VisualTransformation.None
)

/**
 * State holder for a single form field
 */
data class FormFieldState(
    val value: String,
    val onValueChange: (String) -> Unit
)

/**
 * State holder for the entire login form
 */
data class LoginFormState(
    val serverUrl: FormFieldState,
    val username: FormFieldState,
    val password: FormFieldState,
    val isLoading: Boolean,
    val error: String?,
    val onLogin: () -> Unit
)

/**
 * State holder for focus management in login form
 */
data class LoginFocusState(
    val serverUrl: FocusRequester,
    val username: FocusRequester,
    val password: FocusRequester,
    val button: FocusRequester,
    val hasRequestedInitialFocus: Boolean,
    val onInitialFocusSet: () -> Unit
)

/**
 * Reusable login text field component
 */
@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    config: LoginTextFieldConfig,
    colors: com.flintr.tv.ui.theme.AppColors,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(config.label) },
        placeholder = { Text(config.placeholder) },
        leadingIcon = {
            Icon(
                imageVector = config.leadingIcon,
                contentDescription = null
            )
        },
        trailingIcon = config.trailingIcon,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (config.focusRequester != null) {
                    Modifier.focusRequester(config.focusRequester)
                } else {
                    Modifier
                }
            )
            .then(
                if (config.onGloballyPositioned != null) {
                    Modifier.onGloballyPositioned { config.onGloballyPositioned() }
                } else {
                    Modifier
                }
            ),
        singleLine = true,
        enabled = config.enabled,
        shape = RoundedCornerShape(Dimensions.radiusMD),
        colors = loginTextFieldColors(colors),
        visualTransformation = config.visualTransformation
    )
}

/**
 * Returns button colors based on focus and form validation state
 */
@Composable
private fun loginButtonColors(
    isFocused: Boolean,
    isFormValid: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors
): ButtonColors {
    return ButtonDefaults.buttonColors(
        containerColor = when {
            isFocused && isFormValid -> colors.buttonPrimaryHover
            !isFormValid -> colors.buttonPrimary.copy(alpha = 0.6f)
            else -> colors.buttonPrimary
        },
        contentColor = Color.White,
        disabledContainerColor = colors.buttonPrimary.copy(alpha = 0.6f),
        disabledContentColor = Color.White.copy(alpha = 0.6f)
    )
}

/**
 * Returns button elevation based on focus and form validation state
 */
@Composable
private fun loginButtonElevation(
    isFocused: Boolean,
    isFormValid: Boolean
): ButtonElevation {
    return ButtonDefaults.buttonElevation(
        defaultElevation = if (isFocused && isFormValid) 6.dp else if (!isFormValid) 0.dp else 2.dp,
        pressedElevation = if (isFocused && isFormValid) 8.dp else if (!isFormValid) 0.dp else 4.dp,
        disabledElevation = 0.dp
    )
}

/**
 * Login button with focus animation and loading state
 */
@Composable
private fun LoginButton(
    onClick: () -> Unit,
    isLoading: Boolean,
    isFormValid: Boolean,
    modifier: Modifier = Modifier,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    var isButtonFocused by remember { mutableStateOf(false) }
    
    // Animated scale for focus
    val scale by animateFloatAsState(
        targetValue = if (isButtonFocused && isFormValid) 1.02f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "buttonScale"
    )
    
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focusState ->
                isButtonFocused = focusState.isFocused
            }
            .then(
                if (isButtonFocused && isFormValid) {
                    Modifier
                        .border(
                            width = 2.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(Dimensions.radiusMD)
                        )
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(Dimensions.radiusMD),
                            spotColor = colors.accentColor.copy(alpha = 0.4f)
                        )
                } else {
                    Modifier
                }
            ),
        enabled = !isLoading,
        shape = RoundedCornerShape(Dimensions.radiusMD),
        colors = loginButtonColors(
            isFocused = isButtonFocused,
            isFormValid = isFormValid,
            colors = colors
        ),
        elevation = loginButtonElevation(
            isFocused = isButtonFocused,
            isFormValid = isFormValid
        ),
        contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
    ) {
        if (isLoading) {
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

/**
 * Error card component for displaying login errors
 */
@Composable
private fun LoginErrorCard(
    error: String,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = colors.dangerBg
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.radiusMD)
    ) {
        Text(
            text = error,
            color = colors.dangerText,
            modifier = Modifier.padding(Dimensions.spacingMD),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Requests initial focus safely, handling the case where the component is not yet ready
 */
private fun requestInitialFocus(
    focusRequester: FocusRequester,
    hasRequestedInitialFocus: Boolean,
    onInitialFocusSet: () -> Unit
) {
    if (hasRequestedInitialFocus) return
    try {
        focusRequester.requestFocus()
        onInitialFocusSet()
    } catch (_: IllegalStateException) {
        // Component not ready for focus yet
    }
}

/**
 * Password visibility toggle button
 */
@Composable
private fun PasswordVisibilityToggle(
    passwordVisible: Boolean,
    onToggle: () -> Unit
) {
    val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
    val description = if (passwordVisible) "Passwort verbergen" else "Passwort anzeigen"

    IconButton(onClick = onToggle) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

/**
 * Form content for login screen - contains all input fields and submit button
 */
@Composable
private fun LoginFormContent(
    formState: LoginFormState,
    focusState: LoginFocusState,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    var passwordVisible by remember { mutableStateOf(false) }

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
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXS)
    ) {
        LoginTextField(
            value = formState.serverUrl.value,
            onValueChange = formState.serverUrl.onValueChange,
            config = LoginTextFieldConfig(
                label = "Serveradresse",
                placeholder = "http://example.com",
                leadingIcon = Icons.Default.Dns,
                enabled = !formState.isLoading,
                focusRequester = focusState.serverUrl,
                onGloballyPositioned = {
                    requestInitialFocus(
                        focusState.serverUrl,
                        focusState.hasRequestedInitialFocus,
                        focusState.onInitialFocusSet
                    )
                }
            ),
            colors = colors
        )

        LoginTextField(
            value = formState.username.value,
            onValueChange = formState.username.onValueChange,
            config = LoginTextFieldConfig(
                label = "Benutzername",
                placeholder = "Ihr Benutzername",
                leadingIcon = Icons.Default.Person,
                enabled = !formState.isLoading,
                focusRequester = focusState.username
            ),
            colors = colors
        )

        val visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()

        LoginTextField(
            value = formState.password.value,
            onValueChange = formState.password.onValueChange,
            config = LoginTextFieldConfig(
                label = "Passwort",
                placeholder = "Ihr Passwort",
                leadingIcon = Icons.Default.Lock,
                enabled = !formState.isLoading,
                focusRequester = focusState.password,
                trailingIcon = { PasswordVisibilityToggle(passwordVisible) { passwordVisible = !passwordVisible } },
                visualTransformation = visualTransformation
            ),
            colors = colors
        )

        if (formState.error != null) {
            LoginErrorCard(error = formState.error, colors = colors)
        }

        Spacer(modifier = Modifier.height(Dimensions.spacingMD))

        val isFormValid = formState.serverUrl.value.isNotBlank() &&
                formState.username.value.isNotBlank() &&
                formState.password.value.isNotBlank()

        LoginButton(
            onClick = {
                if (isFormValid) {
                    formState.onLogin()
                }
            },
            isLoading = formState.isLoading,
            isFormValid = isFormValid,
            modifier = Modifier.focusRequester(focusState.button),
            colors = colors
        )
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    initialServerUrl: String = "",
    initialUsername: String = "",
    initialPassword: String = "",
    viewModel: LoginViewModel = hiltViewModel()
) {
    var serverUrl by rememberSaveable(initialServerUrl) { mutableStateOf(initialServerUrl) }
    var username by rememberSaveable(initialUsername) { mutableStateOf(initialUsername) }
    var password by rememberSaveable(initialPassword) { mutableStateOf(initialPassword) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = appColors()

    val serverUrlFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val buttonFocusRequester = remember { FocusRequester() }

    val scrollState = rememberScrollState()

    // Track if initial focus has been set to prevent re-focusing on layout changes
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

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
            LoginFormContent(
                formState = LoginFormState(
                    serverUrl = FormFieldState(serverUrl) { serverUrl = it },
                    username = FormFieldState(username) { username = it },
                    password = FormFieldState(password) { password = it },
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    onLogin = { viewModel.login(serverUrl.trim(), username.trim(), password) }
                ),
                focusState = LoginFocusState(
                    serverUrl = serverUrlFocusRequester,
                    username = usernameFocusRequester,
                    password = passwordFocusRequester,
                    button = buttonFocusRequester,
                    hasRequestedInitialFocus = hasRequestedInitialFocus,
                    onInitialFocusSet = { hasRequestedInitialFocus = true }
                ),
                colors = colors
            )
        }
    }
}
