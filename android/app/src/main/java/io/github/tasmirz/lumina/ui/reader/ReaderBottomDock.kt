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
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.tasmirz.lumina.model.ReadingMode
import io.github.tasmirz.lumina.ui.components.AssistantVoiceState
import kotlin.math.roundToInt
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
    onJumpToPreviousLocation: () -> Unit = onToggleNavBar,
    onJumpToLastRead: () -> Unit = onJumpToPreviousLocation,
    onSeekToProgress: (Int) -> Unit = {},
    onForceSavePosition: () -> String,
    onBackToLibrary: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenBookmarks: () -> Unit,
    onOpenReadingSettings: () -> Unit,
    landscapeHideProgress: Boolean = true,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    var isLongPressingProgress by remember { mutableStateOf(false) }
    val progressHoldAnim = remember { Animatable(0f) }
    var showReadTillFeedback by remember { mutableStateOf(false) }
    var readTillFeedbackText by remember { mutableStateOf("") }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPercent by remember { mutableFloatStateOf(currentProgressPct.toFloat()) }
    var railWidthPx by remember { mutableFloatStateOf(1f) }
    var hasSnappedHaptic by remember { mutableStateOf(false) }
    var lastTapRailTime by remember { mutableLongStateOf(0L) }

    val currentOnSeekToProgress by rememberUpdatedState(onSeekToProgress)
    val currentOnToggleNavBar by rememberUpdatedState(onToggleNavBar)
    val currentOnJumpToPreviousLocation by rememberUpdatedState(onJumpToPreviousLocation)
    val currentOnJumpToLastRead by rememberUpdatedState(onJumpToLastRead)
    val currentOnForceSavePosition by rememberUpdatedState(onForceSavePosition)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isVisible = (if (isLandscape) {
        if (readingMode == ReadingMode.PAGED || readingMode == ReadingMode.PAGED_SCROLL) {
            if (landscapeHideProgress) false else isUiVisible
        } else {
            isUiVisible
        }
    } else {
        (isUiVisible || readingMode == ReadingMode.PAGED || readingMode == ReadingMode.PAGED_SCROLL)
    }) && voiceState == AssistantVoiceState.IDLE

    LaunchedEffect(showReadTillFeedback) {
        if (showReadTillFeedback) {
            delay(1200)
            showReadTillFeedback = false
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
            .padding(start = 24.dp, end = 24.dp, bottom = progressBottomInset + 6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 5.dp,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var isLongPress = false
                        val saveJob = coroutineScope.launch {
                            delay(400)
                            isLongPress = true
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            val savedFeedback = currentOnForceSavePosition()
                            readTillFeedbackText = savedFeedback
                            showReadTillFeedback = true
                        }
                        val up = waitForUpOrCancellation()
                        saveJob.cancel()
                        if (up != null && !isLongPress) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            currentOnToggleNavBar()
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dynamic rail height: expands from 3.5.dp to 7.dp while scrubbing or long-pressing
                val animatedRailHeight by animateDpAsState(
                    targetValue = if (isScrubbing || isLongPressingProgress) 6.dp else 3.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "railHeight"
                )

                // Top Progress Bar Extension: YouTube-like seekable progress rail with snapping to saved / last read position
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayedPct = if (isScrubbing) {
                        scrubPercent.roundToInt().coerceIn(0, 100)
                    } else if (isLongPressingProgress && progressHoldAnim.value > 0.05f) {
                        (progressHoldAnim.value * 100).toInt()
                    } else {
                        currentProgressPct
                    }

                    Text(
                        text = "$displayedPct%",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 10.sp,
                        fontWeight = if (isScrubbing || isLongPressingProgress) FontWeight.Normal else FontWeight.Light,
                        color = if (isScrubbing || isLongPressingProgress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // YouTube-like seekable rail with magnetic snap to last read / saved location
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(18.dp)
                            .onGloballyPositioned { coordinates ->
                                railWidthPx = coordinates.size.width.toFloat().coerceAtLeast(1f)
                            }
                            .pointerInput(currentReadTillPct, currentProgressPct) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val downX = down.position.x
                                    val downTime = System.currentTimeMillis()
                                    var isDrag = false

                                    fun calculatePercentFromX(x: Float): Float {
                                        if (railWidthPx <= 10f) return currentProgressPct.toFloat()
                                        val raw = (x / railWidthPx).coerceIn(0f, 1f) * 100f
                                        val snapThreshold = 3.5f
                                        val snapTarget = currentReadTillPct.toFloat()
                                        return if (kotlin.math.abs(raw - snapTarget) <= snapThreshold) {
                                            if (!hasSnappedHaptic) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                hasSnappedHaptic = true
                                            }
                                            snapTarget
                                        } else {
                                            hasSnappedHaptic = false
                                            raw
                                        }
                                    }

                                    // Consume initial touch on seek rail so surface doesn't trigger
                                    down.consume()
                                    isScrubbing = true
                                    scrubPercent = calculatePercentFromX(downX)

                                    // Track pointer movement
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val currentPointer = event.changes.firstOrNull() ?: break
                                        if (!currentPointer.pressed) break

                                        val currentX = currentPointer.position.x
                                        if (kotlin.math.abs(currentX - downX) > 6f) {
                                            isDrag = true
                                        }
                                        scrubPercent = calculatePercentFromX(currentX)
                                        currentPointer.consume()
                                    }

                                    val elapsed = System.currentTimeMillis() - downTime

                                    if (isDrag || elapsed >= 200) {
                                        isScrubbing = false
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        currentOnSeekToProgress(scrubPercent.roundToInt().coerceIn(0, 100))
                                    } else {
                                        // Quick tap on seek rail:
                                        // Double-tap detects 2 rapid taps within 350ms to return to last read area
                                        isScrubbing = false
                                        val now = System.currentTimeMillis()
                                        if (now - lastTapRailTime <= 350L) {
                                            lastTapRailTime = 0L
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            readTillFeedbackText = "Returned to last read: ${currentReadTillPct}%"
                                            showReadTillFeedback = true
                                            currentOnJumpToLastRead()
                                        } else {
                                            lastTapRailTime = now
                                            val tapPct = if (railWidthPx > 10f) (downX / railWidthPx).coerceIn(0f, 1f) * 100f else currentProgressPct.toFloat()
                                            val snapThreshold = 5.0f
                                            if (kotlin.math.abs(tapPct - currentReadTillPct.toFloat()) <= snapThreshold) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                currentOnSeekToProgress(currentReadTillPct)
                                            } else {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                currentOnSeekToProgress(tapPct.roundToInt().coerceIn(0, 100))
                                            }
                                        }
                                    }

                                    coroutineScope.launch {
                                        progressHoldAnim.animateTo(0f, tween(150))
                                        isLongPressingProgress = false
                                        isScrubbing = false
                                    }
                                }
                            },
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

                        // Filled progress bar (current scroll/page position or active scrub position)
                        val activePct = if (isScrubbing) scrubPercent else currentProgressPct.toFloat()
                        val currentFraction = (activePct / 100f).coerceIn(0.01f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(currentFraction)
                                .height(animatedRailHeight)
                                .clip(RoundedCornerShape(animatedRailHeight / 2))
                                .background(if (isScrubbing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                        )

                        // Charging animation beam while holding down to save location (only active after 350ms)
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

                        // Read-till marker pip / notch if ahead of current progress or during scrub
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(readTillFraction)
                                .height(animatedRailHeight + 4.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isScrubbing && kotlin.math.abs(scrubPercent - currentReadTillPct) <= 3.5f) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(if (isScrubbing && kotlin.math.abs(scrubPercent - currentReadTillPct) <= 3.5f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                            )
                        }

                        // YouTube-style Scrubber Thumb (visible when dragging or interacting)
                        if (isScrubbing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(currentFraction)
                                    .height(14.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLongPressingProgress && progressHoldAnim.value > 0.05f) {
                            "Saving..."
                        } else if (isScrubbing) {
                            "Seeking"
                        } else {
                            if (readTimeLeft.isNotBlank()) readTimeLeft else activeChapterTitle
                        },
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 9.5.sp,
                        fontWeight = if (isScrubbing || isLongPressingProgress) FontWeight.Normal else FontWeight.Light,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isScrubbing || isLongPressingProgress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                                fontWeight = FontWeight.Normal,
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
                            // 3. Search in Book
                            IconButton(onClick = onOpenSearch) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search in Book",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // 4. Highlights & Bookmarks
                            IconButton(onClick = onOpenBookmarks) {
                                Icon(
                                    imageVector = Icons.Default.Bookmarks,
                                    contentDescription = "Highlights",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // 5. Reading Settings
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
