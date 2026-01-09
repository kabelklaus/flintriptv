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
                // Header
                Text(
                    text = "Inhalte werden geladen",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isLandscape) 20.sp else MaterialTheme.typography.headlineMedium.fontSize
                    ),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                // Subtitle
                Text(
                    text = "Bitte warten...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = if (isLandscape) 14.sp else MaterialTheme.typography.bodyMedium.fontSize
                    ),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Dimensions.spacingXS / 2)
                )

                Spacer(modifier = Modifier.height(Dimensions.spacingXS / 2))

                // Progress Text (über der Progress Bar)
                val completedSteps = uiState.steps.count { it.status == LoadingStatus.Success }
                val totalSteps = uiState.steps.size
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

                Spacer(modifier = Modifier.height(
                    if (isLandscape) Dimensions.spacingLG / 2 else Dimensions.spacingXL / 2
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
                            onRetry = { viewModel.retryStep(index) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = Dimensions.spacingXS)
                        )
                    }
                }

                // Global Retry Button (nur bei Fehlern, wenn mehrere Schritte fehlgeschlagen sind)
                val errorCount = uiState.steps.count { it.status == LoadingStatus.Error }
                if (errorCount > 1) {
                    Spacer(modifier = Modifier.height(
                        if (isLandscape) Dimensions.spacingLG else Dimensions.spacingXL
                    ))

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
                            text = "Alle erneut versuchen",
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
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconSize = if (isLandscape) 48.dp else 56.dp // Größerer Container
    val iconDisplaySize = if (isLandscape) 28.dp else 32.dp // Feste Icon-Größe
    val statusIconSize = if (isLandscape) 18.dp else 20.dp
    val iconLabelSpacing = if (isLandscape) Dimensions.spacingXS else Dimensions.spacingSM
    val statusLabelSpacing = Dimensions.spacingXS

    // Step Icon - gleiche Icons wie in MainNavbar.kt
    val stepIcon: ImageVector = when (index) {
        0 -> Icons.Default.Tv          // TV-Kanäle (wie MainNavbar "TV")
        1 -> Icons.Default.Movie       // Filme (wie MainNavbar "Movies")
        2 -> Icons.Default.PlayArrow   // Serien (wie MainNavbar "Series")
        else -> Icons.Default.Info
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon Container (immer sichtbar, animierte Farben)
        val (iconColor, iconBgColor) = when (step.status) {
            LoadingStatus.Pending -> colors.textSecondary to colors.bgCard
            LoadingStatus.Loading -> colors.accentColor to colors.accentColor.copy(alpha = 0.1f)
            LoadingStatus.Success -> {
                colors.successColor to colors.successColor.copy(alpha = 0.1f)
            }
            LoadingStatus.Error -> colors.dangerText to colors.dangerBg
        }
        
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
                .size(iconSize)
                .background(
                    color = animatedIconBgColor,
                    shape = RoundedCornerShape(Dimensions.radiusMD)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = stepIcon,
                contentDescription = step.label,
                modifier = Modifier.size(iconDisplaySize),
                tint = animatedIconColor
            )
        }

        Spacer(modifier = Modifier.height(iconLabelSpacing))

        // Label (animierte Textfarbe)
        AnimatedContent(
            targetState = step.status,
            transitionSpec = {
                fadeIn(animationSpec = tween(STATUS_ANIMATION_DURATION_MS)) togetherWith
                        fadeOut(animationSpec = tween(STATUS_ANIMATION_DURATION_MS))
            },
            label = "labelColor"
        ) { status ->
            val currentTextColor = when (status) {
                LoadingStatus.Pending -> colors.textSecondary
                LoadingStatus.Loading -> colors.textPrimary
                LoadingStatus.Success -> colors.textPrimary
                LoadingStatus.Error -> colors.dangerText
            }
            
            Text(
                text = step.label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.W500,
                    fontSize = if (isLandscape) 12.sp else MaterialTheme.typography.bodyMedium.fontSize
                ),
                color = currentTextColor,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(statusLabelSpacing))

        // Status Widget (animiert)
        AnimatedContent(
            targetState = step.status,
            transitionSpec = {
                fadeIn(animationSpec = tween(STATUS_ANIMATION_DURATION_MS)) togetherWith
                        fadeOut(animationSpec = tween(STATUS_ANIMATION_DURATION_MS))
            },
            label = "statusWidget"
        ) { status ->
            when (status) {
                LoadingStatus.Pending -> {
                    // Nicht sichtbar
                    Spacer(modifier = Modifier.size(statusIconSize))
                }
                LoadingStatus.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(statusIconSize),
                        strokeWidth = 2.dp,
                        color = colors.accentColor
                    )
                }
                LoadingStatus.Success -> {
                    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.GERMAN) }
                    val formattedCount = step.itemCount?.let { numberFormat.format(it) } ?: "0"
                    
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
                LoadingStatus.Error -> {
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
            }
        }
    }
}
