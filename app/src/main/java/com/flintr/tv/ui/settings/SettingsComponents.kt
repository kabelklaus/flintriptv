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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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
 * Reusable text field component for connection settings
 */
@Composable
fun ConnectionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    colors: com.flintr.tv.ui.theme.AppColors,
    onDpadLeft: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onDpadLeft != null) {
                    Modifier.onDpadDirection(onLeft = { onDpadLeft(); true })
                } else {
                    Modifier
                }
            )
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            ),
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
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

/**
 * Reusable password field component for connection settings
 */
@Composable
fun ConnectionPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    var passwordVisible by remember { mutableStateOf(false) }
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Kennwort") },
        placeholder = { Text("Ihr Kennwort") },
        modifier = modifier
            .fillMaxWidth()
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

/**
 * Primary action button with focus border and loading state
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingText: String = "Wird geladen...",
    focusRequester: FocusRequester? = null,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
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
        enabled = enabled,
        shape = RoundedCornerShape(Dimensions.radiusMD),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) colors.buttonPrimaryHover else colors.buttonPrimary,
            contentColor = Color.White,
            disabledContainerColor = colors.buttonPrimary.copy(alpha = 0.6f),
            disabledContentColor = Color.White.copy(alpha = 0.6f)
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
                text = loadingText,
                fontSize = FontSizes.fontSizeXL,
                fontWeight = FontWeight.W600
            )
        } else {
            Text(
                text = text,
                fontSize = FontSizes.fontSizeXL,
                fontWeight = FontWeight.W600
            )
        }
    }
}

/**
 * Secondary action button (outlined) with focus highlight
 */
@Composable
fun SecondaryActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    var isFocused by remember { mutableStateOf(false) }
    
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        shape = RoundedCornerShape(Dimensions.radiusMD),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isFocused) colors.accentColor else colors.textPrimary
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = if (isFocused) 2.dp else 1.dp
        ),
        contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) colors.accentColor else colors.iconColor
        )
        Spacer(modifier = Modifier.width(Dimensions.spacingXS))
        Text(
            text = text,
            fontSize = FontSizes.fontSizeXL,
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
