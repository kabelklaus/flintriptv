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

@Composable
fun SettingsGeneral(
    onBack: () -> Unit,
    onNavigateLeft: () -> Unit,
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
                // Language Section
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
                            onLanguageSelected = { selectedLanguage = it },
                            colors = colors,
                            focusRequester = languageFocusRequester,
                            onBack = {
                                Log.i("SettingsNav", "🔙 BACK: SettingsGeneral -> SettingsMainScreen (via D-Pad Left)")
                                onBack()
                            }
                        )
                    }
                }
                
                // Theme Section
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
                            onCheckedChange = { isDarkTheme = it },
                            colors = colors
                        )
                    }
                }
                
                // Cache Section
                SettingsCard(
                    title = "Cache",
                    icon = Icons.Default.Storage,
                    colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                    ) {
                        // Cache Size Display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cache-Größe",
                                fontSize = FontSizes.fontSizeXL,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "$cacheSizeMB MB",
                                fontSize = FontSizes.fontSizeXL,
                                fontWeight = FontWeight.W500,
                                color = colors.textSecondary
                            )
                        }
                        
                        // Clear Cache Button
                        var isClearCacheButtonFocused by remember { mutableStateOf(false) }
                        
                        OutlinedButton(
                            onClick = {
                                cacheSizeMB = 0
                                // Mock: Show snackbar
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(clearCacheButtonFocusRequester)
                                .onFocusChanged { focusState ->
                                    isClearCacheButtonFocused = focusState.isFocused
                                },
                            shape = RoundedCornerShape(Dimensions.radiusMD),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isClearCacheButtonFocused) colors.accentColor else colors.textPrimary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                width = if (isClearCacheButtonFocused) 2.dp else 1.dp
                            ),
                            contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = if (isClearCacheButtonFocused) colors.accentColor else colors.iconColor
                            )
                            Spacer(modifier = Modifier.width(Dimensions.spacingXS))
                            Text(
                                text = "Cache leeren",
                                fontSize = FontSizes.fontSizeXL,
                                fontWeight = if (isClearCacheButtonFocused) FontWeight.W600 else FontWeight.Normal
                            )
                        }
                    }
                }
                
                // Hidden Categories Section
                SettingsCard(
                    title = "Versteckte Kategorien",
                    icon = Icons.Default.VisibilityOff,
                    colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                    ) {
                        // Hidden Categories Count Display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Anzahl versteckter Kategorien",
                                fontSize = FontSizes.fontSizeXL,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "$hiddenCategoriesCount",
                                fontSize = FontSizes.fontSizeXL,
                                fontWeight = FontWeight.W500,
                                color = colors.textSecondary
                            )
                        }
                        
                        // Manage Categories Button
                        var isManageCategoriesButtonFocused by remember { mutableStateOf(false) }
                        
                        OutlinedButton(
                            onClick = {
                                Log.i("SettingsNav", "➡️ NAVIGATE: SettingsGeneral -> SubSettingsHiddenCategories")
                                onNavigateToHiddenCategories()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(manageCategoriesButtonFocusRequester)
                                .onFocusChanged { focusState ->
                                    isManageCategoriesButtonFocused = focusState.isFocused
                                },
                            shape = RoundedCornerShape(Dimensions.radiusMD),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isManageCategoriesButtonFocused) colors.accentColor else colors.textPrimary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                width = if (isManageCategoriesButtonFocused) 2.dp else 1.dp
                            ),
                            contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = if (isManageCategoriesButtonFocused) colors.accentColor else colors.iconColor
                            )
                            Spacer(modifier = Modifier.width(Dimensions.spacingXS))
                            Text(
                                text = "Kategorien verwalten",
                                fontSize = FontSizes.fontSizeXL,
                                fontWeight = if (isManageCategoriesButtonFocused) FontWeight.W600 else FontWeight.Normal
                            )
                        }
                    }
                }
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

