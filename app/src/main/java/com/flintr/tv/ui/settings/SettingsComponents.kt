package com.flintr.tv.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.flintr.tv.R
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.ui.utils.onDpadDirection

/**
 * Reusable card component for settings sections
 */
@Composable
fun SettingsCard(
    title: String,
    icon: ImageVector,
    colors: com.flintr.tv.ui.theme.AppColors,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius),
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
                    shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius)
                )
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.listItemHoverPadding),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingLG),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(28.dp),
                    tint = colors.iconColor
                )
                Text(
                    text = title,
                    fontSize = FontSizes.fontSizeXL,
                    fontWeight = FontWeight.W600,
                    color = colors.textPrimary
                )
            }
            
            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.spacingMD)
                    .padding(top = 0.dp),
                content = content
            )
        }
    }
}

/**
 * Reusable switch setting component for settings screens
 */
@Composable
fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onDpadCenter { onCheckedChange(!checked) }
            .padding(vertical = Dimensions.spacingXS),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = FontSizes.fontSizeXL,
            color = if (isFocused) colors.accentColor else colors.textPrimary,
            fontWeight = if (isFocused) FontWeight.W600 else FontWeight.Normal
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accentColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colors.cardBorder
            )
        )
    }
}

/**
 * Intercepts D-Pad Up/Down before the text field consumes them for cursor movement,
 * so vertical navigation always moves focus to the next/previous element in the chain.
 */
private fun Modifier.onDpadVerticalFocusChange(
    onDpadUp: (() -> Unit)?,
    onDpadDown: (() -> Unit)?
): Modifier {
    if (onDpadUp == null && onDpadDown == null) {
        return this
    }
    return this.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            return@onPreviewKeyEvent false
        }
        when (event.key) {
            Key.DirectionUp -> onDpadUp?.let { it(); true } ?: false
            Key.DirectionDown -> onDpadDown?.let { it(); true } ?: false
            else -> false
        }
    }
}

/**
 * Configuration data class for ConnectionTextField
 */
data class ConnectionTextFieldConfig(
    val value: String,
    val onValueChange: (String) -> Unit,
    val label: String,
    val placeholder: String,
    val leadingIcon: ImageVector,
    val focusRequester: FocusRequester? = null,
    val colors: com.flintr.tv.ui.theme.AppColors,
    val onDpadLeft: (() -> Unit)? = null,
    val onDpadUp: (() -> Unit)? = null,
    val onDpadDown: (() -> Unit)? = null
)

/**
 * Reusable text field component for connection settings
 */
@Composable
fun ConnectionTextField(
    config: ConnectionTextFieldConfig,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = config.value,
        onValueChange = config.onValueChange,
        label = { Text(config.label) },
        placeholder = { Text(config.placeholder) },
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (config.onDpadLeft != null) {
                    Modifier.onDpadDirection(onLeft = { config.onDpadLeft(); true })
                } else {
                    Modifier
                }
            )
            .onDpadVerticalFocusChange(config.onDpadUp, config.onDpadDown)
            .then(
                if (config.focusRequester != null) {
                    Modifier.focusRequester(config.focusRequester)
                } else {
                    Modifier
                }
            ),
        leadingIcon = {
            Icon(
                imageVector = config.leadingIcon,
                contentDescription = null
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(Dimensions.radiusMD),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = config.colors.accentColor,
            unfocusedBorderColor = config.colors.cardBorder,
            focusedLabelColor = config.colors.textPrimary,
            unfocusedLabelColor = config.colors.textSecondary,
            focusedTextColor = config.colors.textPrimary,
            unfocusedTextColor = config.colors.textPrimary,
            cursorColor = config.colors.accentColor,
            focusedLeadingIconColor = config.colors.accentColor,
            unfocusedLeadingIconColor = config.colors.iconColor
        )
    )
}

/**
 * Reusable password field component for connection settings
 */
@Composable
fun ConnectionPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    colors: com.flintr.tv.ui.theme.AppColors,
    onDpadUp: (() -> Unit)? = null,
    onDpadDown: (() -> Unit)? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.password)) },
        placeholder = { Text(stringResource(R.string.your_password)) },
        keyboardOptions = KeyboardOptions(
            autoCorrect = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        modifier = modifier
            .fillMaxWidth()
            .onDpadVerticalFocusChange(onDpadUp, onDpadDown)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            ),
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
                    contentDescription = if (passwordVisible) stringResource(R.string.hide_password) else stringResource(R.string.show_password)
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

/**
 * Configuration data class for PrimaryActionButton
 */
data class PrimaryActionButtonConfig(
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val isLoading: Boolean = false,
    val loadingText: String,
    val focusRequester: FocusRequester? = null,
    val colors: com.flintr.tv.ui.theme.AppColors
)

/**
 * Primary action button with focus border and loading state
 */
@Composable
fun PrimaryActionButton(
    config: PrimaryActionButtonConfig,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Button(
        onClick = config.onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (config.focusRequester != null) {
                    Modifier.focusRequester(config.focusRequester)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(Dimensions.radiusMD)
                    )
                } else {
                    Modifier
                }
            ),
        enabled = config.enabled,
        shape = RoundedCornerShape(Dimensions.radiusMD),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) config.colors.buttonPrimaryHover else config.colors.buttonPrimary,
            contentColor = Color.White,
            disabledContainerColor = config.colors.buttonPrimary.copy(alpha = 0.6f),
            disabledContentColor = Color.White.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
    ) {
        if (config.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(Dimensions.spacingXS))
            Text(
                text = config.loadingText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.W600
            )
        } else {
            Text(
                text = config.text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.W600
            )
        }
    }
}

/**
 * Configuration data class for SecondaryActionButton
 */
data class SecondaryActionButtonConfig(
    val text: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val focusRequester: FocusRequester? = null,
    val enabled: Boolean = true,
    val colors: com.flintr.tv.ui.theme.AppColors
)

/**
 * Secondary action button (outlined) with focus highlight
 */
@Composable
fun SecondaryActionButton(
    config: SecondaryActionButtonConfig,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    OutlinedButton(
        onClick = config.onClick,
        enabled = config.enabled,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (config.focusRequester != null) {
                    Modifier.focusRequester(config.focusRequester)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        shape = RoundedCornerShape(Dimensions.radiusMD),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isFocused) config.colors.accentColor else config.colors.textPrimary
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = if (isFocused) 2.dp else 1.dp
        ),
        contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
    ) {
        Icon(
            imageVector = config.icon,
            contentDescription = null,
            tint = if (isFocused) config.colors.accentColor else config.colors.iconColor
        )
        Spacer(modifier = Modifier.width(Dimensions.spacingXS))
        Text(
            text = config.text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isFocused) FontWeight.W600 else FontWeight.Normal
        )
    }
}

/**
 * Status card for displaying connection results (error, success, info)
 */
@Composable
fun StatusCard(
    message: String,
    icon: ImageVector,
    iconTint: Color,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        modifier = modifier.fillMaxWidth(),
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
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                color = textColor,
                fontWeight = FontWeight.W500,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
