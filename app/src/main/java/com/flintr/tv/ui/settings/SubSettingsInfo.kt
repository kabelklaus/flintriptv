package com.flintr.tv.ui.settings

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import android.util.Log
import com.flintr.tv.R
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.ui.utils.onDpadDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.flintr.tv.viewmodel.UpdateViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState

@Composable
fun SettingsInfo(
    onBack: () -> Unit,
    onNavigateLeft: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val colors = appColors()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // Package info state
    var packageInfo by remember { mutableStateOf<PackageInfoData?>(null) }
    
    // ViewModel state
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    // Focus requesters
    val checkUpdatesButtonFocusRequester = remember { FocusRequester() }
    val downloadButtonFocusRequester = remember { FocusRequester() }
    
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
                    title = stringResource(R.string.version),
                    icon = Icons.Default.Info,
                    colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD)
                    ) {
                        packageInfo?.let { info ->
                            InfoRow(
                                label = stringResource(R.string.app_name_label),
                                value = info.appName,
                                colors = colors
                            )
                            InfoRow(
                                label = stringResource(R.string.version_label),
                                value = info.versionName,
                                colors = colors
                            )
                            InfoRow(
                                label = stringResource(R.string.build_number),
                                value = info.versionCode.toString(),
                                colors = colors
                            )
                            InfoRow(
                                label = stringResource(R.string.package_name),
                                value = info.packageName,
                                colors = colors
                            )
                        } ?: run {
                            Text(
                                text = stringResource(R.string.loading_info),
                                fontSize = FontSizes.fontSizeXL,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
                
                // Update Section
                SettingsCard(
                    title = stringResource(R.string.updates),
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
                                packageInfo?.let { info ->
                                    viewModel.checkForUpdates(info.versionName)
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
                                    text = stringResource(R.string.checking_for_updates),
                                    fontSize = FontSizes.fontSizeXL,
                                    fontWeight = FontWeight.W600
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.check_for_updates),
                                    fontSize = FontSizes.fontSizeXL,
                                    fontWeight = FontWeight.W600
                                )
                            }
                        }
                        
                        // Error Message
                        errorMessage?.let { error ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = colors.errorColor.copy(alpha = 0.1f)
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
                                        tint = colors.errorColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = error,
                                        color = colors.textPrimary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        
                        // Download Progress
                        downloadProgress?.let { progress ->
                            if (!progress.isComplete && progress.error == null) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = colors.bgHover
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
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                progress = progress.progress,
                                                color = colors.accentColor
                                            )
                                            Text(
                                                text = stringResource(R.string.download_progress, (progress.progress * 100).toInt()),
                                                color = colors.textPrimary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth(),
                                            progress = progress.progress,
                                            color = colors.accentColor
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Update Info Display
                        updateInfo?.let { info ->
                            if (info.hasUpdate && info.downloadUrl != null) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = colors.successGreen.copy(alpha = 0.1f)
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
                                                tint = colors.successGreen
                                            )
                                            Text(
                                                text = stringResource(R.string.updates_available),
                                                color = colors.successGreen,
                                                fontWeight = FontWeight.W600,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        
                                        if (info.stableVersion != null) {
                                            Text(
                                                text = stringResource(R.string.version_info, info.stableVersion),
                                                color = colors.textPrimary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        
                                        // Download Button
                                        var isDownloadButtonFocused by remember { mutableStateOf(false) }
                                        
                                        Button(
                                            onClick = {
                                                info.downloadUrl?.let { url ->
                                                    info.stableVersion?.let { version ->
                                                        viewModel.downloadUpdate(url, version)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(downloadButtonFocusRequester)
                                                .onFocusChanged { focusState ->
                                                    isDownloadButtonFocused = focusState.isFocused
                                                }
                                                .then(
                                                    if (isDownloadButtonFocused) {
                                                        Modifier.border(
                                                            width = 2.dp,
                                                            color = Color.White,
                                                            shape = RoundedCornerShape(Dimensions.radiusMD)
                                                        )
                                                    } else {
                                                        Modifier
                                                    }
                                                ),
                                            enabled = downloadProgress?.isComplete != true && downloadProgress?.error == null,
                                            shape = RoundedCornerShape(Dimensions.radiusMD),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isDownloadButtonFocused) colors.buttonPrimaryHover else colors.buttonPrimary,
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.download_and_install_update),
                                                fontSize = FontSizes.fontSizeXL,
                                                fontWeight = FontWeight.W600
                                            )
                                        }
                                    }
                                }
                            } else if (!info.hasUpdate) {
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
                                            text = stringResource(R.string.latest_version),
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
                    title = stringResource(R.string.about),
                    icon = Icons.Default.Info,
                    colors = colors
                ) {
                    Text(
                        text = stringResource(R.string.app_name_info),
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
        Log.e("SubSettingsInfo", "Failed to load package info", e)
        null
    }
}

data class PackageInfoData(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int
)


