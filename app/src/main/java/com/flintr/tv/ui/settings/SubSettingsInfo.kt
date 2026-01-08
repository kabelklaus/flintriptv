package com.flintr.tv.ui.settings

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import android.util.Log
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.ui.utils.onDpadDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsInfo(
    onBack: () -> Unit,
    onNavigateLeft: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    
    // Package info state
    var packageInfo by remember { mutableStateOf<PackageInfoData?>(null) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    
    // Focus requesters
    val checkUpdatesButtonFocusRequester = remember { FocusRequester() }
    
    // Load package info
    LaunchedEffect(Unit) {
        packageInfo = loadPackageInfo(context)
        checkUpdatesButtonFocusRequester.requestFocus()
    }
    
    // Handle Back button using BackHandler (works even when IME is visible)
    BackHandler(enabled = true) {
        Log.i("SettingsNav", "🔙 BACK: SettingsInfo -> SettingsMainScreen (via BackHandler)")
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
                // Version Section
                SettingsCard(
                    title = "Version",
                    icon = Icons.Default.Info,
                    colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                    ) {
                        packageInfo?.let { info ->
                            InfoRow(
                                label = "App Name",
                                value = info.appName,
                                colors = colors
                            )
                            InfoRow(
                                label = "Version",
                                value = info.versionName,
                                colors = colors
                            )
                            InfoRow(
                                label = "Build Number",
                                value = info.versionCode.toString(),
                                colors = colors
                            )
                            InfoRow(
                                label = "Package Name",
                                value = info.packageName,
                                colors = colors
                            )
                        } ?: run {
                            Text(
                                text = "Lade Informationen...",
                                fontSize = FontSizes.fontSizeXL,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
                
                // Update Section
                SettingsCard(
                    title = "Updates",
                    icon = Icons.Default.SystemUpdate,
                    colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                    ) {
                        // Check for Updates Button
                        var isCheckUpdatesButtonFocused by remember { mutableStateOf(false) }
                        
                        Button(
                            onClick = {
                                isCheckingUpdates = true
                                // Mock update check
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(2000) // Simulate API call
                                    isCheckingUpdates = false
                                    updateInfo = UpdateInfo(
                                        hasUpdate = true,
                                        stableVersion = "1.1.0",
                                        betaVersion = "1.1.0-beta.1"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onDpadDirection(
                                    onLeft = {
                                        Log.i("SettingsNav", "🔙 BACK: SettingsInfo -> SettingsMainScreen (via D-Pad Left)")
                                        onBack()
                                        true
                                    }
                                )
                                .focusRequester(checkUpdatesButtonFocusRequester)
                                .onFocusChanged { focusState ->
                                    isCheckUpdatesButtonFocused = focusState.isFocused
                                }
                                .then(
                                    if (isCheckUpdatesButtonFocused) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = Color.White,
                                            shape = RoundedCornerShape(Dimensions.radiusMD)
                                        )
                                    } else {
                                        Modifier
                                    }
                                ),
                            enabled = !isCheckingUpdates,
                            shape = RoundedCornerShape(Dimensions.radiusMD),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCheckUpdatesButtonFocused) colors.buttonPrimaryHover else colors.buttonPrimary,
                                contentColor = Color.White,
                                disabledContainerColor = colors.buttonPrimary.copy(alpha = 0.6f),
                                disabledContentColor = Color.White.copy(alpha = 0.6f)
                            ),
                            contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
                        ) {
                            if (isCheckingUpdates) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(Dimensions.spacingXS))
                                Text(
                                    text = "Suche nach Updates...",
                                    fontSize = FontSizes.fontSizeXL,
                                    fontWeight = FontWeight.W600
                                )
                            } else {
                                Text(
                                    text = "Nach Updates suchen",
                                    fontSize = FontSizes.fontSizeXL,
                                    fontWeight = FontWeight.W600
                                )
                            }
                        }
                        
                        // Update Info Display
                        updateInfo?.let { info ->
                            if (info.hasUpdate) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF10B981).copy(alpha = 0.1f)
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(Dimensions.radiusMD)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Dimensions.spacingMD),
                                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSM)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SystemUpdate,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981)
                                            )
                                            Text(
                                                text = "Updates verfügbar",
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.W600,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        
                                        if (info.stableVersion != null) {
                                            Text(
                                                text = "Stable: ${info.stableVersion}",
                                                color = colors.textPrimary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        
                                        if (info.betaVersion != null) {
                                            Text(
                                                text = "Beta: ${info.betaVersion}",
                                                color = colors.textPrimary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            } else {
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
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = colors.accentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Sie verwenden die neueste Version",
                                            color = colors.textPrimary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // About Section
                SettingsCard(
                    title = "Über",
                    icon = Icons.Default.Info,
                    colors = colors
                ) {
                    Text(
                        text = "IPTV Player App",
                        fontSize = FontSizes.fontSizeXL,
                        color = colors.textPrimary
                    )
                }
            }
        }
    }
}

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
            color = colors.textSecondary
        )
        Text(
            text = value,
            fontSize = FontSizes.fontSizeXL,
            fontWeight = FontWeight.W500,
            color = colors.textPrimary
        )
    }
}

private suspend fun loadPackageInfo(context: Context): PackageInfoData? = withContext(Dispatchers.IO) {
    try {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val packageInfo: PackageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        
        val appName = packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, 0)
        ).toString()
        
        PackageInfoData(
            appName = appName,
            packageName = packageName,
            versionName = packageInfo.versionName ?: "Unknown",
            versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

data class PackageInfoData(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int
)

data class UpdateInfo(
    val hasUpdate: Boolean,
    val stableVersion: String? = null,
    val betaVersion: String? = null
)

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

