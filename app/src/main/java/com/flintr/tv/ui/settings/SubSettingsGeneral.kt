package com.flintr.tv.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import android.util.Log
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.data.local.CategoryVisibilityService
import androidx.compose.runtime.collectAsState
import com.flintr.tv.ui.settings.SecondaryActionButton
import com.flintr.tv.ui.settings.SecondaryActionButtonConfig

/**
 * Reusable component for displaying label-value pairs in settings
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = FontSizes.fontSizeXL,
            color = colors.textPrimary
        )
        Text(
            text = value,
            fontSize = FontSizes.fontSizeXL,
            fontWeight = FontWeight.W500,
            color = colors.textSecondary
        )
    }
}

/**
 * Language settings section
 */
@Composable
private fun LanguageSection(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    focusRequester: FocusRequester,
    onBack: () -> Unit
) {
    SettingsCard(
        title = "Sprache",
        icon = Icons.Default.Language,
        colors = colors
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            LanguageSelector(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = onLanguageSelected,
                colors = colors,
                focusRequester = focusRequester,
                onBack = onBack
            )
        }
    }
}

/**
 * Theme settings section
 */
@Composable
private fun ThemeSection(
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    SettingsCard(
        title = "Theme",
        icon = Icons.Default.Palette,
        colors = colors
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            SwitchSetting(
                label = "Dunkel",
                checked = isDarkTheme,
                onCheckedChange = onDarkThemeChange,
                colors = colors
            )
        }
    }
}

/**
 * Cache settings section
 */
@Composable
private fun CacheSection(
    cacheSizeMB: Int,
    onClearCache: () -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    focusRequester: FocusRequester
) {
    SettingsCard(
        title = "Cache",
        icon = Icons.Default.Storage,
        colors = colors
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            InfoRow(
                label = "Cache-Größe",
                value = "$cacheSizeMB MB",
                colors = colors
            )
            
            SecondaryActionButton(
                config = SecondaryActionButtonConfig(
                    text = "Cache leeren",
                    icon = Icons.Default.Delete,
                    onClick = onClearCache,
                    focusRequester = focusRequester,
                    colors = colors
                )
            )
        }
    }
}

/**
 * Hidden categories settings section
 */
@Composable
private fun HiddenCategoriesSection(
    hiddenCategoriesCount: Int,
    onNavigateToHiddenCategories: () -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    focusRequester: FocusRequester
) {
    SettingsCard(
        title = "Versteckte Kategorien",
        icon = Icons.Default.VisibilityOff,
        colors = colors
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
        ) {
            InfoRow(
                label = "Anzahl versteckter Kategorien",
                value = "$hiddenCategoriesCount",
                colors = colors
            )
            
            SecondaryActionButton(
                config = SecondaryActionButtonConfig(
                    text = "Kategorien verwalten",
                    icon = Icons.Default.Settings,
                    onClick = {
                        Log.i("SettingsNav", "➡️ NAVIGATE: SettingsGeneral -> SubSettingsHiddenCategories")
                        onNavigateToHiddenCategories()
                    },
                    focusRequester = focusRequester,
                    colors = colors
                )
            )
        }
    }
}

@Composable
fun SettingsGeneral(
    onBack: () -> Unit,
    categoryVisibilityService: CategoryVisibilityService,
    onNavigateToHiddenCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    val scrollState = rememberScrollState()
    
    // Mock state
    var selectedLanguage by remember { mutableStateOf(AppLanguage.Deutsch) }
    var isDarkTheme by remember { mutableStateOf(false) }
    var cacheSizeMB by remember { mutableStateOf(0) }
    
    // Get hidden categories count from StateFlow
    val hiddenCategories by categoryVisibilityService.hiddenCategories.collectAsState()
    val hiddenCategoriesCount = hiddenCategories.values.sumOf { it.size }
    
    // Focus requesters
    val languageFocusRequester = remember { FocusRequester() }
    val clearCacheButtonFocusRequester = remember { FocusRequester() }
    val manageCategoriesButtonFocusRequester = remember { FocusRequester() }
    
    // Set initial focus
    LaunchedEffect(Unit) {
        languageFocusRequester.requestFocus()
    }
    
    // Handle Back button using BackHandler (works even when IME is visible)
    BackHandler(enabled = true) {
        Log.i("SettingsNav", "🔙 BACK: SettingsGeneral -> SettingsMainScreen (via BackHandler)")
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
                LanguageSection(
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = { selectedLanguage = it },
                    colors = colors,
                    focusRequester = languageFocusRequester,
                    onBack = {
                        Log.i("SettingsNav", "🔙 BACK: SettingsGeneral -> SettingsMainScreen (via D-Pad Left)")
                        onBack()
                    }
                )
                
                ThemeSection(
                    isDarkTheme = isDarkTheme,
                    onDarkThemeChange = { isDarkTheme = it },
                    colors = colors
                )
                
                CacheSection(
                    cacheSizeMB = cacheSizeMB,
                    onClearCache = {
                        cacheSizeMB = 0
                        // Mock: Show snackbar
                    },
                    colors = colors,
                    focusRequester = clearCacheButtonFocusRequester
                )
                
                HiddenCategoriesSection(
                    hiddenCategoriesCount = hiddenCategoriesCount,
                    onNavigateToHiddenCategories = onNavigateToHiddenCategories,
                    colors = colors,
                    focusRequester = manageCategoriesButtonFocusRequester
                )
            }
        }
    }
}

@Composable
private fun LanguageSelector(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    focusRequester: FocusRequester,
    onBack: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accentColor else colors.cardBorder,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onDpadDirection(
                onLeft = {
                    onBack()
                    true
                }
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onDpadCenter { isExpanded = !isExpanded }
    ) {
        OutlinedTextField(
            value = selectedLanguage.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Sprache auswählen") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(Dimensions.radiusMD),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = borderColor,
                unfocusedBorderColor = borderColor,
                focusedLabelColor = colors.textPrimary,
                unfocusedLabelColor = colors.textSecondary,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedTrailingIconColor = colors.accentColor,
                unfocusedTrailingIconColor = colors.iconColor
            )
        )
        
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            modifier = Modifier
                .background(colors.bgCard)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(Dimensions.radiusMD))
        ) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName) },
                    onClick = {
                        onLanguageSelected(language)
                        isExpanded = false
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = if (selectedLanguage == language) colors.accentColor else colors.textPrimary
                    )
                )
            }
        }
    }
}

enum class AppLanguage(val displayName: String) {
    Deutsch("Deutsch"),
    English("English"),
    Francais("Français"),
    Espanol("Español")
}

