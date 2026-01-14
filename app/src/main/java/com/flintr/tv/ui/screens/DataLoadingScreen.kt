package com.flintr.tv.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private const val STATUS_ANIMATION_DURATION_MS = 300

private val CheckIcon = Icons.Default.CheckCircle
private val ErrorIcon = Icons.Default.Error

// Helper functions
private fun calculateCompletedSteps(steps: List<com.flintr.tv.viewmodel.LoadingStep>): Int {
    return steps.count { it.status == LoadingStatus.Success }
}

private fun shouldShowGlobalRetry(errorCount: Int): Boolean {
    return errorCount > 1
}

private data class StepDimensions(
    val iconSize: androidx.compose.ui.unit.Dp,
    val iconDisplaySize: androidx.compose.ui.unit.Dp,
    val statusIconSize: androidx.compose.ui.unit.Dp,
    val iconLabelSpacing: androidx.compose.ui.unit.Dp,
    val statusLabelSpacing: androidx.compose.ui.unit.Dp
)

private fun getStepDimensions(isLandscape: Boolean): StepDimensions {
    return StepDimensions(
        iconSize = if (isLandscape) 48.dp else 56.dp,
        iconDisplaySize = if (isLandscape) 28.dp else 32.dp,
        statusIconSize = if (isLandscape) 18.dp else 20.dp,
        iconLabelSpacing = if (isLandscape) Dimensions.spacingXS else Dimensions.spacingSM,
        statusLabelSpacing = Dimensions.spacingXS
    )
}

private fun getStepIcon(index: Int): ImageVector {
    return when (index) {
        0 -> Icons.Default.Tv
        1 -> Icons.Default.Movie
        2 -> Icons.Default.PlayArrow
        else -> Icons.Default.Info
    }
}

private fun getStepColors(
    status: LoadingStatus,
    colors: com.flintr.tv.ui.theme.AppColors
): Pair<Color, Color> {
    return when (status) {
        LoadingStatus.Pending -> colors.textSecondary to colors.bgCard
        LoadingStatus.Loading -> colors.accentColor to colors.accentColor.copy(alpha = 0.1f)
        LoadingStatus.Success -> colors.successColor to colors.successColor.copy(alpha = 0.1f)
        LoadingStatus.Error -> colors.dangerText to colors.dangerBg
    }
}

private fun getStepTextColor(
    status: LoadingStatus,
    colors: com.flintr.tv.ui.theme.AppColors
): Color {
    return when (status) {
        LoadingStatus.Pending -> colors.textSecondary
        LoadingStatus.Loading -> colors.textPrimary
        LoadingStatus.Success -> colors.textPrimary
        LoadingStatus.Error -> colors.dangerText
    }
}

// UI Components for DataLoadingScreen
@Composable
private fun LoadingScreenHeader(
    isLandscape: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    Text(
        text = "Inhalte werden geladen",
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = if (isLandscape) 20.sp else MaterialTheme.typography.headlineMedium.fontSize
        ),
        color = colors.textPrimary,
        textAlign = TextAlign.Center
    )

    Text(
        text = "Bitte warten...",
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = if (isLandscape) 14.sp else MaterialTheme.typography.bodyMedium.fontSize
        ),
        color = colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = Dimensions.spacingXS / 2)
    )
}

@Composable
private fun LoadingProgressSection(
    completedSteps: Int,
    totalSteps: Int,
    overallProgress: Float,
    isLandscape: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Fortschritt",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = if (isLandscape) 12.sp else MaterialTheme.typography.bodySmall.fontSize
            ),
            color = colors.textSecondary
        )
        Text(
            text = "$completedSteps / $totalSteps",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = if (isLandscape) 12.sp else MaterialTheme.typography.bodySmall.fontSize
            ),
            color = colors.textSecondary
        )
    }

    LinearProgressIndicator(
        progress = overallProgress,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isLandscape) 5.dp else 6.dp)
            .clip(RoundedCornerShape(Dimensions.radiusPill)),
        color = colors.accentColor,
        trackColor = colors.bgHover
    )
}

@Composable
private fun LoadingStepsRow(
    steps: List<com.flintr.tv.viewmodel.LoadingStep>,
    isLandscape: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors,
    onRetryStep: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        steps.forEachIndexed { index, step ->
            StepItem(
                step = step,
                index = index,
                isLandscape = isLandscape,
                colors = colors,
                onRetry = { onRetryStep(index) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Dimensions.spacingXS)
            )
        }
    }
}

@Composable
private fun GlobalRetryButton(
    isLandscape: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors,
    onRetry: () -> Unit
) {
    Spacer(modifier = Modifier.height(
        if (isLandscape) Dimensions.spacingLG else Dimensions.spacingXL
    ))

    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.radiusMD),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.buttonPrimary,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
    ) {
        Text(
            text = "Alle erneut versuchen",
            fontSize = FontSizes.fontSizeXL,
            fontWeight = FontWeight.W600
        )
    }
}

@Composable
fun DataLoadingScreen(
    onLoadingComplete: (CachedData) -> Unit,
    viewModel: DataLoadingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = appColors()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // Fade-in animation on screen start
    var isVisible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
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
                .alpha(alpha),
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
                LoadingScreenHeader(isLandscape, colors)

                Spacer(modifier = Modifier.height(Dimensions.spacingXS / 2))

                val completedSteps = calculateCompletedSteps(uiState.steps)
                val totalSteps = uiState.steps.size
                LoadingProgressSection(
                    completedSteps = completedSteps,
                    totalSteps = totalSteps,
                    overallProgress = uiState.overallProgress,
                    isLandscape = isLandscape,
                    colors = colors
                )

                Spacer(modifier = Modifier.height(
                    if (isLandscape) Dimensions.spacingLG / 2 else Dimensions.spacingXL / 2
                ))

                LoadingStepsRow(
                    steps = uiState.steps,
                    isLandscape = isLandscape,
                    colors = colors,
                    onRetryStep = { index -> viewModel.retryStep(index) }
                )

                val errorCount = uiState.steps.count { it.status == LoadingStatus.Error }
                if (shouldShowGlobalRetry(errorCount)) {
                    GlobalRetryButton(
                        isLandscape = isLandscape,
                        colors = colors,
                        onRetry = { viewModel.retry() }
                    )
                }
            }
        }
    }
}

// UI Components for StepItem
@Composable
private fun StepIconContainer(
    step: com.flintr.tv.viewmodel.LoadingStep,
    icon: ImageVector,
    dimensions: StepDimensions,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    val (iconColor, iconBgColor) = getStepColors(step.status, colors)
    
    val animatedIconColor by animateColorAsState(
        targetValue = iconColor,
        animationSpec = tween(STATUS_ANIMATION_DURATION_MS, easing = EaseInOut),
        label = "iconColor"
    )
    
    val animatedIconBgColor by animateColorAsState(
        targetValue = iconBgColor,
        animationSpec = tween(STATUS_ANIMATION_DURATION_MS, easing = EaseInOut),
        label = "iconBgColor"
    )
    
    Box(
        modifier = Modifier
            .size(dimensions.iconSize)
            .background(
                color = animatedIconBgColor,
                shape = RoundedCornerShape(Dimensions.radiusMD)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = step.label,
            modifier = Modifier.size(dimensions.iconDisplaySize),
            tint = animatedIconColor
        )
    }
}

@Composable
private fun StepLabel(
    label: String,
    status: LoadingStatus,
    isLandscape: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            fadeIn(animationSpec = tween(STATUS_ANIMATION_DURATION_MS)) togetherWith
                    fadeOut(animationSpec = tween(STATUS_ANIMATION_DURATION_MS))
        },
        label = "labelColor"
    ) { currentStatus ->
        val currentTextColor = getStepTextColor(currentStatus, colors)
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.W500,
                fontSize = if (isLandscape) 12.sp else MaterialTheme.typography.bodyMedium.fontSize
            ),
            color = currentTextColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StepItem(
    step: com.flintr.tv.viewmodel.LoadingStep,
    index: Int,
    isLandscape: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = getStepDimensions(isLandscape)
    val stepIcon = getStepIcon(index)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StepIconContainer(
            step = step,
            icon = stepIcon,
            dimensions = dimensions,
            colors = colors
        )

        Spacer(modifier = Modifier.height(dimensions.iconLabelSpacing))

        StepLabel(
            label = step.label,
            status = step.status,
            isLandscape = isLandscape,
            colors = colors
        )

        Spacer(modifier = Modifier.height(dimensions.statusLabelSpacing))

        StepStatusWidget(
            status = step.status,
            itemCount = step.itemCount,
            isLandscape = isLandscape,
            colors = colors,
            statusIconSize = dimensions.statusIconSize,
            onRetry = onRetry
        )
    }
}

// Status Widget Components
@Composable
private fun StepStatusWidget(
    status: LoadingStatus,
    itemCount: Int?,
    isLandscape: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors,
    statusIconSize: androidx.compose.ui.unit.Dp,
    onRetry: () -> Unit
) {
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            fadeIn(animationSpec = tween(STATUS_ANIMATION_DURATION_MS)) togetherWith
                    fadeOut(animationSpec = tween(STATUS_ANIMATION_DURATION_MS))
        },
        label = "statusWidget"
    ) { currentStatus ->
        when (currentStatus) {
            LoadingStatus.Pending -> PendingStatusWidget(statusIconSize)
            LoadingStatus.Loading -> LoadingStatusWidget(statusIconSize, colors)
            LoadingStatus.Success -> SuccessStatusWidget(itemCount, isLandscape, statusIconSize, colors)
            LoadingStatus.Error -> ErrorStatusWidget(isLandscape, statusIconSize, colors, onRetry)
        }
    }
}

@Composable
private fun PendingStatusWidget(statusIconSize: androidx.compose.ui.unit.Dp) {
    Spacer(modifier = Modifier.size(statusIconSize))
}

@Composable
private fun LoadingStatusWidget(
    statusIconSize: androidx.compose.ui.unit.Dp,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    CircularProgressIndicator(
        modifier = Modifier.size(statusIconSize),
        strokeWidth = 2.dp,
        color = colors.accentColor
    )
}

@Composable
private fun SuccessStatusWidget(
    itemCount: Int?,
    isLandscape: Boolean,
    statusIconSize: androidx.compose.ui.unit.Dp,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.GERMAN) }
    val formattedCount = itemCount?.let { numberFormat.format(it) } ?: "0"
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            if (isLandscape) 4.dp else Dimensions.spacingXS
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = CheckIcon,
            contentDescription = null,
            modifier = Modifier.size(statusIconSize),
            tint = colors.successColor
        )
        Text(
            text = formattedCount,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.W600,
                fontSize = if (isLandscape) 12.sp else MaterialTheme.typography.bodySmall.fontSize
            ),
            color = colors.successColor
        )
    }
}

@Composable
private fun ErrorStatusWidget(
    isLandscape: Boolean,
    statusIconSize: androidx.compose.ui.unit.Dp,
    colors: com.flintr.tv.ui.theme.AppColors,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXS)
    ) {
        Icon(
            imageVector = ErrorIcon,
            contentDescription = null,
            modifier = Modifier.size(statusIconSize),
            tint = colors.dangerText
        )
        TextButton(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = Dimensions.spacingSM, vertical = Dimensions.spacingXS),
            colors = ButtonDefaults.textButtonColors(
                contentColor = colors.dangerText
            )
        ) {
            Text(
                text = "Wiederholen",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = if (isLandscape) 10.sp else 11.sp,
                    fontWeight = FontWeight.W600
                )
            )
        }
    }
}
