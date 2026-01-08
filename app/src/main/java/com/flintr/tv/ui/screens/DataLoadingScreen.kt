package com.flintr.tv.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.viewmodel.DataLoadingViewModel
import com.flintr.tv.viewmodel.LoadingStatus
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DataLoadingScreen(
    onLoadingComplete: (CachedData) -> Unit,
    viewModel: DataLoadingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = appColors()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

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

    // Navigate on loading complete
    LaunchedEffect(uiState.loadingComplete) {
        if (uiState.loadingComplete && uiState.cachedData != null) {
            onLoadingComplete(uiState.cachedData!!)
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
                .widthIn(max = if (isLandscape) 400.dp else 500.dp)
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
                    .padding(
                        if (isLandscape) Dimensions.spacingLG else Dimensions.spacingXL
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    if (isLandscape) Dimensions.spacingMD else Dimensions.spacingXL
                )
            ) {
                // Header
                Text(
                    text = "Daten werden geladen...",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isLandscape) 20.sp else MaterialTheme.typography.headlineMedium.fontSize
                    ),
                    color = colors.textPrimary
                )

                // Subtitle
                Text(
                    text = "Bitte warten Sie...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = if (isLandscape) 14.sp else MaterialTheme.typography.bodyMedium.fontSize
                    ),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Dimensions.spacingXS)
                )

                Spacer(modifier = Modifier.height(
                    if (isLandscape) Dimensions.spacingMD else Dimensions.spacingXL
                ))

                // Progress Bar
                LinearProgressIndicator(
                    progress = uiState.overallProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 5.dp else 6.dp)
                        .clip(RoundedCornerShape(Dimensions.radiusPill)),
                    color = colors.accentColor,
                    trackColor = colors.bgHover
                )

                // Progress Text
                val completedSteps = uiState.steps.count { it.status == LoadingStatus.Success }
                val totalSteps = uiState.steps.size
                val numberFormat = NumberFormat.getNumberInstance(Locale.GERMAN)
                Text(
                    text = "${numberFormat.format(completedSteps)} von ${numberFormat.format(totalSteps)} abgeschlossen",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (isLandscape) 12.sp else MaterialTheme.typography.bodySmall.fontSize
                    ),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Dimensions.spacingMD)
                )

                Spacer(modifier = Modifier.height(
                    if (isLandscape) Dimensions.spacingMD else Dimensions.spacingLG
                ))

                // Steps
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    uiState.steps.forEachIndexed { index, step ->
                        StepItem(
                            step = step,
                            index = index,
                            isLandscape = isLandscape,
                            colors = colors,
                            modifier = Modifier.weight(1f)
                        )
                        if (index < uiState.steps.size - 1) {
                            Spacer(modifier = Modifier.width(Dimensions.spacingXS))
                        }
                    }
                }

                // Error Card
                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(
                        if (isLandscape) Dimensions.spacingMD else Dimensions.spacingLG
                    ))

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
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = if (isLandscape) 10.sp else MaterialTheme.typography.bodySmall.fontSize
                            ),
                            maxLines = 2
                        )
                    }

                    Spacer(modifier = Modifier.height(
                        if (isLandscape) Dimensions.spacingMD else Dimensions.spacingLG
                    ))

                    // Retry Button
                    Button(
                        onClick = { viewModel.retry() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimensions.radiusMD),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.buttonPrimary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
                    ) {
                        Text(
                            text = "Erneut versuchen",
                            fontSize = FontSizes.fontSizeXL,
                            fontWeight = FontWeight.W600
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepItem(
    step: com.flintr.tv.viewmodel.LoadingStep,
    index: Int,
    isLandscape: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors,
    modifier: Modifier = Modifier
) {
    val iconSize = if (isLandscape) 24.dp else 32.dp
    val statusIconSize = if (isLandscape) 18.dp else 20.dp
    val padding = if (isLandscape) Dimensions.spacingSM else Dimensions.spacingMD
    val iconLabelSpacing = if (isLandscape) Dimensions.spacingXS else Dimensions.spacingSM

    val (stepIcon, stepIconColor) = when (index) {
        0 -> Icons.Filled.LiveTv to colors.textPrimary
        1 -> Icons.Filled.Movie to colors.textPrimary
        2 -> Icons.Filled.Tv to colors.textPrimary
        else -> Icons.Filled.Info to colors.textPrimary
    }

    val statusData = when (step.status) {
        LoadingStatus.Pending -> {
            Triple(
                Icons.Filled.Circle,
                colors.textSecondary,
                colors.bgCard
            ) to colors.textSecondary
        }
        LoadingStatus.Loading -> {
            Triple(
                Icons.Filled.Refresh,
                colors.accentColor,
                colors.accentColor.copy(alpha = 0.1f)
            ) to colors.textPrimary
        }
        LoadingStatus.Success -> {
            val green = Color(0xFF4CAF50)
            Triple(
                Icons.Filled.CheckCircle,
                green,
                green.copy(alpha = 0.1f)
            ) to colors.textPrimary
        }
        LoadingStatus.Error -> {
            Triple(
                Icons.Filled.Error,
                colors.dangerText,
                colors.dangerBg
            ) to colors.dangerText
        }
    }
    val (statusIcon, statusIconColor, statusBgColor) = statusData.first
    val textColor = statusData.second

    Column(
        modifier = modifier
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXS)
    ) {
        // Icon Container
        Box(
            modifier = Modifier
                .size(iconSize)
                .then(
                    if (step.status == LoadingStatus.Loading || step.status == LoadingStatus.Success || step.status == LoadingStatus.Error) {
                        Modifier
                            .background(
                                color = statusBgColor,
                                shape = RoundedCornerShape(Dimensions.radiusMD)
                            )
                            .padding(padding)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = stepIcon,
                contentDescription = step.label,
                modifier = Modifier.size(iconSize * 0.7f),
                tint = stepIconColor
            )
        }

        // Label
        Text(
            text = step.label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.W500,
                fontSize = if (isLandscape) 12.sp else MaterialTheme.typography.bodyMedium.fontSize
            ),
            color = textColor
        )

        // Status Icon + Count
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step.status != LoadingStatus.Pending) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    modifier = Modifier.size(statusIconSize),
                    tint = statusIconColor
                )
            }

            if (step.itemCount != null && step.status == LoadingStatus.Success) {
                val countColor = if (step.status == LoadingStatus.Success) {
                    Color(0xFF4CAF50)
                } else {
                    colors.textSecondary
                }
                Text(
                    text = step.itemCount.toString(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.W600,
                        fontSize = if (isLandscape) 12.sp else MaterialTheme.typography.bodySmall.fontSize
                    ),
                    color = countColor
                )
            }
        }
    }
}

