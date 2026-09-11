package io.github.tasmirz.lumina.ui.reader

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.model.ReadingMode
import io.github.tasmirz.lumina.ui.components.AssistantVoiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReaderBottomDock(
    isUiVisible: Boolean,
    readingMode: ReadingMode,
    voiceState: AssistantVoiceState,
    progressBottomInset: Dp,
    showNavBarInReader: Boolean,
    currentProgressPct: Int,
    currentReadTillPct: Int,
    readTimeLeft: String,
    activeChapterTitle: String,
    onToggleNavBar: () -> Unit,
    onForceSavePosition: () -> String,
    onBackToLibrary: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenReadingSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    var isLongPressingProgress by remember { mutableStateOf(false) }
    val progressHoldAnim = remember { Animatable(0f) }
    var showReadTillFeedback by remember { mutableStateOf(false) }
    var readTillFeedbackText by remember { mutableStateOf("") }

    val currentOnToggleNavBar by rememberUpdatedState(onToggleNavBar)
    val currentOnForceSavePosition by rememberUpdatedState(onForceSavePosition)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isVisible = (if (isLandscape) isUiVisible else (isUiVisible || readingMode == ReadingMode.PAGED)) && voiceState == AssistantVoiceState.IDLE

    LaunchedEffect(showReadTillFeedback) {
        if (showReadTillFeedback) {
            delay(800)
            showReadTillFeedback = false
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp, bottom = progressBottomInset + 10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Consume taps to prevent passing through to reader */ }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dynamic rail height: expands from 3.5.dp to 7.dp while long-pressing
                val animatedRailHeight by animateDpAsState(
                    targetValue = if (isLongPressingProgress) 7.dp else 3.5.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "railHeight"
                )

                // Top Progress Bar Extension: Pure Display, tapping toggles bottom nav dock, long press charges up & saves last read position
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startTime = System.currentTimeMillis()
                                var completed = false

                                val animJob = coroutineScope.launch {
                                    // Delay 200ms before starting the charging animation so quick taps don't trigger it
                                    delay(200)
                                    isLongPressingProgress = true
                                    progressHoldAnim.snapTo(0f)
                                    progressHoldAnim.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(durationMillis = 500, easing = LinearEasing)
                                    )
                                    // Long-press hold completed! Save position and trigger feedback
                                    completed = true
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                                    val savedFeedback = currentOnForceSavePosition()
                                    readTillFeedbackText = savedFeedback
                                    showReadTillFeedback = true
                                }

                                val up = waitForUpOrCancellation()
                                animJob.cancel()
                                isLongPressingProgress = false

                                if (up != null && !completed) {
                                    val elapsed = System.currentTimeMillis() - startTime
                                    if (elapsed < 300) {
                                        currentOnToggleNavBar()
                                    }
                                }
                                coroutineScope.launch {
                                    progressHoldAnim.animateTo(0f, tween(150))
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isLongPressingProgress && progressHoldAnim.value > 0.05f) {
                            "${(progressHoldAnim.value * 100).toInt()}%"
                        } else {
                            "${currentProgressPct}%"
                        },
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLongPressingProgress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    // Pure Display Progress Rail with charging animation on hold
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Background rail
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(animatedRailHeight)
                                .clip(RoundedCornerShape(animatedRailHeight / 2))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        )
                        // Read-till progress indicator (if ahead of current reading position)
                        val readTillFraction = (currentReadTillPct.toFloat() / 100f).coerceIn(0f, 1f)
                        if (currentReadTillPct > currentProgressPct) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(readTillFraction)
                                    .height(animatedRailHeight)
                                    .clip(RoundedCornerShape(animatedRailHeight / 2))
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
                            )
                        }
                        // Filled progress bar (current scroll/page position)
                        val currentFraction = (currentProgressPct.toFloat() / 100f).coerceIn(0.01f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(currentFraction)
                                .height(animatedRailHeight)
                                .clip(RoundedCornerShape(animatedRailHeight / 2))
                                .background(MaterialTheme.colorScheme.secondary)
                        )

                        // Charging animation beam while holding down to save location (only active after 200ms)
                        val holdFraction = progressHoldAnim.value
                        if (isLongPressingProgress || holdFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(holdFraction)
                                    .height(animatedRailHeight)
                                    .clip(RoundedCornerShape(animatedRailHeight / 2))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary,
                                                MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    )
                            )
                        }

                        // Read-till marker pip if ahead of current progress
                        if (currentReadTillPct > currentProgressPct) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(readTillFraction)
                                    .height(animatedRailHeight + 4.dp),
                                    contentAlignment = Alignment.CenterEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isLongPressingProgress) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isLongPressingProgress && progressHoldAnim.value > 0.05f) {
                            "Saving..."
                        } else {
                            if (readTimeLeft.isNotBlank()) readTimeLeft else activeChapterTitle
                        },
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 10.5.sp,
                        fontWeight = if (isLongPressingProgress) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isLongPressingProgress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Animated In-Place Read-Till Saved Feedback Pill
                AnimatedVisibility(
                    visible = showReadTillFeedback,
                    enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(250)) + shrinkVertically(animationSpec = tween(250))
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = readTillFeedbackText,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Expandable Navigation Row directly attached below progress bar
                AnimatedVisibility(
                    visible = showNavBarInReader,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Library
                            IconButton(onClick = onBackToLibrary) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = "Library",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // 2. Table of Contents
                            IconButton(onClick = onOpenToc) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = "Contents",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // 3. Highlights & Bookmarks
                            IconButton(onClick = onOpenBookmarks) {
                                Icon(
                                    imageVector = Icons.Default.Bookmarks,
                                    contentDescription = "Highlights",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // 4 (old 5). Reading Settings
                            IconButton(onClick = onOpenReadingSettings) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Reading Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
