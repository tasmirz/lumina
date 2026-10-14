package io.github.tasmirz.lumina.ui.components

import io.github.tasmirz.lumina.model.OrbSize
import io.github.tasmirz.lumina.model.OrbMenuSize
import io.github.tasmirz.lumina.model.OrbColor
import io.github.tasmirz.lumina.model.OrbFanDirection
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.model.OrbActionItem
import io.github.tasmirz.lumina.model.ReadingMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants

enum class AssistantVoiceState {
    IDLE,
    LISTENING,
    THINKING,
    RESPONDING
}

private data class OrbAction(
    val icon: ImageVector,
    val label: String,
    val action: () -> Unit
)

@Composable
fun FloatingAssistantOrb(
    readingMode: ReadingMode,
    onToggleReadingMode: () -> Unit,
    isTtsPlaying: Boolean,
    onToggleTts: () -> Unit,
    onOpenToc: () -> Unit = {},
    onOpenNote: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenCharacters: () -> Unit = {},
    onStartVoiceListening: () -> Unit,
    onDismissOrb: () -> Unit,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    isDarkTheme: Boolean = false,
    onToggleThemeMode: () -> Unit = {},
    orbActions: Set<OrbActionItem> = OrbActionItem.entries.toSet(),
    voiceState: AssistantVoiceState = AssistantVoiceState.IDLE,
    voiceQuery: String = "",
    voiceResponse: String = "",
    onDismissVoiceDialog: () -> Unit = {},
    onOpenAdvancedSettings: () -> Unit = onOpenSettings,
    onRetry: () -> Unit = {},
    isSpoilerShield: Boolean = true,
    onToggleSpoilerShield: () -> Unit = {},
    orbSize: OrbSize = OrbSize.NANO,
    orbMenuSize: OrbMenuSize = OrbMenuSize.MEDIUM,
    orbEdgeSnap: Boolean = true,
    orbColor: OrbColor = OrbColor.THEME,
    customOrbColor: Long = 0xFF4F46E5L,
    orbOpacity: Float = 0.85f,
    savedX: Float = -1f,
    savedY: Float = -1f,
    reversePaletteDirection: Boolean = false,
    fanDirection: OrbFanDirection = OrbFanDirection.AUTO,
    snapThreshold: Float = 0.05f,
    onSavePosition: (x: Float, y: Float, isLandscape: Boolean) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    BoxWithConstraints(modifier = modifier.fillMaxSize().zIndex(150f)) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        // Camera notch / display cutout insets to prevent docking under the camera
        val layoutDirection = LocalLayoutDirection.current
        val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()
        val cutoutLeftPx = with(density) { cutoutInsets.calculateLeftPadding(layoutDirection).toPx() }
        val cutoutRightPx = with(density) { cutoutInsets.calculateRightPadding(layoutDirection).toPx() }
        val cutoutTopPx = with(density) { cutoutInsets.calculateTopPadding().toPx() }
        val cutoutBottomPx = with(density) { cutoutInsets.calculateBottomPadding().toPx() }

        val baseOrbColor = MaterialTheme.colorScheme.primary

        val scale = orbSize.scale
        val normalOrbSizeDp = (44 * scale).dp
        val normalOrbSizePx = with(density) { normalOrbSizeDp.toPx() }
        val innerCoreSizeDp = normalOrbSizeDp * 0.72f
        val iconSizeDp = (normalOrbSizeDp * 0.38f).coerceAtLeast(10.dp)

        val dockedWidthDp = orbSize.dockedWidth.dp
        val dockedHeightDp = orbSize.dockedHeight.dp
        val dockedWidthPx = with(density) { dockedWidthDp.toPx() }
        val dockedHeightPx = with(density) { dockedHeightDp.toPx() }

        // Edge snap in portrait mode, free-floating ball in landscape
        val effectiveEdgeSnap = orbEdgeSnap && !isLandscape

        val minDockX = cutoutLeftPx
        val maxDockRightX = (screenWidthPx - dockedWidthPx - cutoutRightPx).coerceAtLeast(minDockX)
        val minFloatingX = -normalOrbSizePx * 0.65f
        val maxFloatingX = screenWidthPx - (normalOrbSizePx * 0.35f)

        val minAllowedX = if (effectiveEdgeSnap) minDockX else minFloatingX
        val maxAllowedX = if (effectiveEdgeSnap) maxDockRightX else maxFloatingX
        val minAllowedY = maxOf(screenHeightPx * 0.05f, cutoutTopPx)
        val maxAllowedY = minOf(screenHeightPx * 0.95f - normalOrbSizePx, screenHeightPx - cutoutBottomPx - normalOrbSizePx).coerceAtLeast(minAllowedY)

        val leftThresholdPx = screenWidthPx * snapThreshold
        val rightThresholdPx = screenWidthPx - (screenWidthPx * snapThreshold) - normalOrbSizePx

        // Initial position: default to right edge dock in portrait if edge snap is enabled and within threshold
        var offsetX by remember(isLandscape, effectiveEdgeSnap, snapThreshold) {
            val initialX = if (savedX >= 0f) {
                if (effectiveEdgeSnap) {
                    val isDockedX = (savedX <= leftThresholdPx) || (savedX >= rightThresholdPx) ||
                        kotlin.math.abs(savedX - minDockX) < 6f || kotlin.math.abs(savedX - maxDockRightX) < 6f
                    if (isDockedX) {
                        val wasOnLeft = savedX < screenWidthPx / 2f
                        if (wasOnLeft) minDockX else maxDockRightX
                    } else {
                        savedX.coerceIn(minAllowedX, maxAllowedX)
                    }
                } else {
                    savedX.coerceIn(minAllowedX, maxAllowedX)
                }
            } else {
                if (effectiveEdgeSnap) maxDockRightX else (screenWidthPx - normalOrbSizePx - with(density) { 12.dp.toPx() }).coerceIn(minAllowedX, maxAllowedX)
            }
            mutableFloatStateOf(initialX)
        }
        var offsetY by remember(isLandscape) {
            val initialY = if (savedY >= 0f) savedY.coerceIn(minAllowedY, maxAllowedY) else {
                (screenHeightPx * 0.70f).coerceIn(minAllowedY, maxAllowedY)
            }
            mutableFloatStateOf(initialY)
        }

        var isDragging by remember { mutableStateOf(false) }
        var isOverBin by remember { mutableStateOf(false) }
        var isWheelExpanded by remember { mutableStateOf(false) }

        // Re-clamp position on orientation, saved coordinates, or screen size changes
        LaunchedEffect(savedX, savedY, screenWidthPx, screenHeightPx, orbEdgeSnap, snapThreshold, isLandscape) {
            val minY = maxOf(screenHeightPx * 0.05f, cutoutTopPx)
            val maxY = minOf(screenHeightPx * 0.95f - normalOrbSizePx, screenHeightPx - cutoutBottomPx - normalOrbSizePx).coerceAtLeast(minY)

            if (savedY >= 0f) {
                offsetY = savedY.coerceIn(minY, maxY)
            } else {
                offsetY = (screenHeightPx * 0.70f).coerceIn(minY, maxY)
            }

            if (effectiveEdgeSnap) {
                if (savedX >= 0f) {
                    val isDockedX = (savedX <= leftThresholdPx) || (savedX >= rightThresholdPx) ||
                        kotlin.math.abs(savedX - minDockX) < 6f || kotlin.math.abs(savedX - maxDockRightX) < 6f
                    if (isDockedX) {
                        val wasOnLeft = savedX < screenWidthPx / 2f
                        offsetX = if (wasOnLeft) minDockX else maxDockRightX
                    } else {
                        offsetX = savedX.coerceIn(minAllowedX, maxAllowedX)
                    }
                } else {
                    offsetX = maxDockRightX
                }
            } else {
                val minX = -normalOrbSizePx * 0.65f
                val maxX = screenWidthPx - (normalOrbSizePx * 0.35f)
                if (savedX >= 0f) {
                    offsetX = savedX.coerceIn(minX, maxX)
                } else {
                    offsetX = (screenWidthPx - normalOrbSizePx - with(density) { 12.dp.toPx() }).coerceIn(minX, maxX)
                }
            }
        }

        // Edge docked state: true only when orb is resting right on the margin in portrait mode with snap enabled
        val isNearLeftEdge = (offsetX + normalOrbSizePx / 2f) < screenWidthPx / 2f
        val isSnappedToLeft = kotlin.math.abs(offsetX - minDockX) < 6f
        val isSnappedToRight = kotlin.math.abs(offsetX - maxDockRightX) < 6f
        val isAtEdge = isSnappedToLeft || isSnappedToRight
        val isDocked = effectiveEdgeSnap && isAtEdge && !isDragging && !isWheelExpanded && voiceState == AssistantVoiceState.IDLE

        var hasLoadedInitialPosition by remember(isLandscape) { mutableStateOf(false) }
        LaunchedEffect(isLandscape) {
            hasLoadedInitialPosition = true
        }

        val currentWidthDp by animateDpAsState(
            targetValue = if (isDocked) dockedWidthDp else normalOrbSizeDp,
            animationSpec = if (!hasLoadedInitialPosition) snap() else spring(stiffness = Spring.StiffnessMediumLow),
            label = "orbWidth"
        )
        val currentHeightDp by animateDpAsState(
            targetValue = if (isDocked) dockedHeightDp else normalOrbSizeDp,
            animationSpec = if (!hasLoadedInitialPosition) snap() else spring(stiffness = Spring.StiffnessMediumLow),
            label = "orbHeight"
        )
        val effectiveWidthDp = if (!hasLoadedInitialPosition) (if (isDocked) dockedWidthDp else normalOrbSizeDp) else currentWidthDp
        val effectiveHeightDp = if (!hasLoadedInitialPosition) (if (isDocked) dockedHeightDp else normalOrbSizeDp) else currentHeightDp

        val currentShape = when {
            isDocked && isNearLeftEdge -> RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp, topStart = 0.dp, bottomStart = 0.dp)
            isDocked && !isNearLeftEdge -> RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 0.dp, bottomEnd = 0.dp)
            else -> CircleShape
        }

        // When wheel is expanded, slide the orb center safely into the visible screen so the wheel never breaks
        val safeOrbMarginPx = with(density) { 6.dp.toPx() }
        val safeMinOrbX = cutoutLeftPx + safeOrbMarginPx
        val safeMaxOrbX = (screenWidthPx - normalOrbSizePx - cutoutRightPx - safeOrbMarginPx).coerceAtLeast(safeMinOrbX)
        val targetDisplayX = when {
            isWheelExpanded -> offsetX.coerceIn(safeMinOrbX, safeMaxOrbX)
            isDocked -> if (isNearLeftEdge) minDockX else maxDockRightX
            else -> offsetX
        }

        val safeOrbMarginYPx = with(density) { 36.dp.toPx() }
        val safeMinOrbY = cutoutTopPx + safeOrbMarginYPx
        val safeMaxOrbY = (screenHeightPx - normalOrbSizePx - cutoutBottomPx - safeOrbMarginYPx).coerceAtLeast(safeMinOrbY)
        val targetDisplayY = if (isWheelExpanded) {
            offsetY.coerceIn(safeMinOrbY, safeMaxOrbY)
        } else {
            offsetY
        }

        val animatedX by animateFloatAsState(
            targetValue = if (isDragging) offsetX else targetDisplayX,
            animationSpec = if (!hasLoadedInitialPosition) snap() else spring(stiffness = Spring.StiffnessMediumLow),
            label = "orbX"
        )
        val animatedY by animateFloatAsState(
            targetValue = if (isDragging) offsetY else targetDisplayY,
            animationSpec = if (!hasLoadedInitialPosition) snap() else spring(stiffness = Spring.StiffnessMediumLow),
            label = "orbY"
        )
        val displayX = if (isDragging || !hasLoadedInitialPosition) targetDisplayX else animatedX
        val displayY = if (isDragging || !hasLoadedInitialPosition) targetDisplayY else animatedY

        // Pulsing animation for listening (strictly gated to avoid CPU and battery drain when idle)
        val pulseScale = if (voiceState == AssistantVoiceState.LISTENING) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val animatedScale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.18f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )
            animatedScale
        } else {
            1.0f
        }

        // Bin zone coordinates (bottom center)
        val binTargetY = screenHeightPx - with(density) { 110.dp.toPx() }
        val binCenterX = screenWidthPx / 2f

        // Drop-to-Delete Target Bin at Bottom Center
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (isOverBin) 72.dp else 56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isOverBin) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                    )
                    .border(
                        width = 2.dp,
                        color = if (isOverBin) Color.White else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Drop here to dismiss assistant",
                    tint = if (isOverBin) Color.White else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(if (isOverBin) 32.dp else 26.dp)
                )
            }
        }

        // Tap-to-expand Smart One-Sided Inward Arc overlay with 35% black backdrop scrim
        if (isWheelExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { isWheelExpanded = false }
            ) {
                // Build active action items based on user settings
                val activeItems = mutableListOf<OrbAction>()
                // 1. Primary Layer 1 Actions (Theme, TTS, Settings)
                if (orbActions.contains(OrbActionItem.THEME_MODE)) {
                    activeItems.add(
                        OrbAction(
                            icon = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            label = if (isDarkTheme) "Light Theme" else "Dark Theme",
                            action = { onToggleThemeMode(); isWheelExpanded = false }
                        )
                    )
                }
                if (orbActions.contains(OrbActionItem.TTS)) {
                    activeItems.add(
                        OrbAction(
                            icon = if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            label = if (isTtsPlaying) "Pause Audio" else "Read Aloud",
                            action = { onToggleTts(); isWheelExpanded = false }
                        )
                    )
                }
                if (orbActions.contains(OrbActionItem.SETTINGS)) {
                    activeItems.add(
                        OrbAction(
                            icon = Icons.Outlined.Tune,
                            label = "Appearance Settings",
                            action = { onOpenSettings(); isWheelExpanded = false }
                        )
                    )
                }

                // 2. Outer Layer 2 Actions (Reading Mode, Fullscreen, Search, Voice, Characters, Notes)
                if (orbActions.contains(OrbActionItem.READING_MODE)) {
                    val (rmIcon, rmLabel) = when (readingMode) {
                        ReadingMode.SCROLL -> Icons.Filled.SwapVert to "Continuous Scroll"
                        ReadingMode.PAGED -> Icons.AutoMirrored.Filled.MenuBook to "Full Paged"
                        ReadingMode.PAGED_SCROLL -> Icons.Filled.UnfoldMore to "Paged + Scroll"
                    }
                    activeItems.add(
                        OrbAction(
                            icon = rmIcon,
                            label = rmLabel,
                            action = { onToggleReadingMode(); isWheelExpanded = false }
                        )
                    )
                }
                if (orbActions.contains(OrbActionItem.FULLSCREEN)) {
                    activeItems.add(
                        OrbAction(
                            icon = if (isFullscreen) Icons.Default.CloseFullscreen else Icons.Default.Fullscreen,
                            label = "Fullscreen",
                            action = { onToggleFullscreen(); isWheelExpanded = false }
                        )
                    )
                }
                if (orbActions.contains(OrbActionItem.SEARCH)) {
                    activeItems.add(
                        OrbAction(
                            icon = Icons.Default.Search,
                            label = "Search in Book",
                            action = { onOpenSearch(); isWheelExpanded = false }
                        )
                    )
                }
                if (orbActions.contains(OrbActionItem.VOICE)) {
                    activeItems.add(
                        OrbAction(
                            icon = Icons.Default.Mic,
                            label = "Voice Assistant",
                            action = { onStartVoiceListening(); isWheelExpanded = false }
                        )
                    )
                }
                if (orbActions.contains(OrbActionItem.CHARACTERS)) {
                    activeItems.add(
                        OrbAction(
                            icon = Icons.Default.Face,
                            label = "Character Guide",
                            action = { onOpenCharacters(); isWheelExpanded = false }
                        )
                    )
                }
                if (orbActions.contains(OrbActionItem.NOTE)) {
                    activeItems.add(
                        OrbAction(
                            icon = Icons.Outlined.EditNote,
                            label = "Notes & Highlights",
                            action = { onOpenNote(); isWheelExpanded = false }
                        )
                    )
                }

                // If somehow empty, provide default Theme & Settings
                if (activeItems.isEmpty()) {
                    activeItems.add(
                        OrbAction(
                            icon = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            label = if (isDarkTheme) "Light Theme" else "Dark Theme",
                            action = { onToggleThemeMode(); isWheelExpanded = false }
                        )
                    )
                    activeItems.add(
                        OrbAction(
                            icon = Icons.Outlined.Tune,
                            label = "Appearance Settings",
                            action = { onOpenSettings(); isWheelExpanded = false }
                        )
                    )
                }

                // Harmonious 2-Layered Concentric Circles Palette Layout
                // Both Layer 1 (inner) and Layer 2 (outer) share the EXACT same center and concentric curvature!
                val totalCount = activeItems.size
                if (totalCount > 0) {
                    // Split items into 2 concentric layers:
                    // Layer 1 (inner) gets 2-3 primary actions. Layer 2 (outer) gets remaining specialized actions.
                    val (layer1Items, layer2Items) = when {
                        totalCount <= 4 -> Pair(activeItems, emptyList())
                        totalCount == 5 -> Pair(activeItems.take(2), activeItems.drop(2))
                        totalCount == 6 -> Pair(activeItems.take(2), activeItems.drop(2))
                        totalCount == 7 -> Pair(activeItems.take(3), activeItems.drop(3))
                        totalCount == 8 -> Pair(activeItems.take(3), activeItems.drop(3))
                        else -> Pair(activeItems.take(3), activeItems.drop(3)) // 3 inner, 6 outer
                    }

                    // Sizing and radius based on OrbMenuSize
                    val scaleFactor = when (orbMenuSize) {
                        OrbMenuSize.COMPACT -> 0.85f
                        OrbMenuSize.MEDIUM -> 1.0f
                        OrbMenuSize.LARGE -> 1.15f
                    }

                    val innerRadiusPx = with(density) { (58 * scaleFactor).dp.toPx() }
                    val outerRadiusPx = with(density) { (104 * scaleFactor).dp.toPx() }

                    val innerItemSizeDp = (orbMenuSize.itemSizeDp + 2).dp
                    val innerIconSizeDp = (orbMenuSize.iconSizeDp + 1).dp
                    val innerItemSizePx = with(density) { innerItemSizeDp.toPx() }

                    val outerItemSizeDp = (orbMenuSize.itemSizeDp - 2).dp.coerceAtLeast(28.dp)
                    val outerIconSizeDp = orbMenuSize.iconSizeDp.dp
                    val outerItemSizePx = with(density) { outerItemSizeDp.toPx() }

                    // Exact center of the expanded circular orb (slides smoothly on-screen)
                    val orbCenterX = displayX + (normalOrbSizePx / 2f)
                    val orbCenterY = displayY + (normalOrbSizePx / 2f)
                    val verticalFraction = (orbCenterY / screenHeightPx).coerceIn(0f, 1f)
                    val horizontalFraction = (orbCenterX / screenWidthPx).coerceIn(0f, 1f)

                    // Determine effective fanning direction based on explicit override or closest edge
                    val effectiveDirection = when (fanDirection) {
                        OrbFanDirection.RIGHT -> if (reversePaletteDirection) OrbFanDirection.LEFT else OrbFanDirection.RIGHT
                        OrbFanDirection.LEFT -> if (reversePaletteDirection) OrbFanDirection.RIGHT else OrbFanDirection.LEFT
                        OrbFanDirection.DOWN -> if (reversePaletteDirection) OrbFanDirection.UP else OrbFanDirection.DOWN
                        OrbFanDirection.UP -> if (reversePaletteDirection) OrbFanDirection.DOWN else OrbFanDirection.UP
                        OrbFanDirection.AUTO -> {
                            val distLeft = orbCenterX
                            val distRight = screenWidthPx - orbCenterX
                            val distTop = orbCenterY
                            val distBottom = screenHeightPx - orbCenterY
                            val minDist = minOf(distLeft, distRight, distTop, distBottom)
                            val naturalDir = when {
                                distTop == minDist -> OrbFanDirection.DOWN
                                distBottom == minDist -> OrbFanDirection.UP
                                distLeft == minDist -> OrbFanDirection.RIGHT
                                else -> OrbFanDirection.LEFT
                            }
                            if (reversePaletteDirection) {
                                when (naturalDir) {
                                    OrbFanDirection.DOWN -> OrbFanDirection.UP
                                    OrbFanDirection.UP -> OrbFanDirection.DOWN
                                    OrbFanDirection.RIGHT -> OrbFanDirection.LEFT
                                    OrbFanDirection.LEFT -> OrbFanDirection.RIGHT
                                    OrbFanDirection.AUTO -> OrbFanDirection.RIGHT
                                }
                            } else {
                                naturalDir
                            }
                        }
                    }

                    fun getSpanDeg(itemsCount: Int, isInner: Boolean, isNearEdge: Boolean): Double {
                        return if (isInner) {
                            when (itemsCount) {
                                1 -> 0.0
                                2 -> 44.0
                                3 -> 80.0
                                4 -> 106.0
                                else -> 122.0
                            }
                        } else {
                            when (itemsCount) {
                                1 -> 0.0
                                2 -> 48.0
                                3 -> 80.0
                                4 -> 108.0
                                5 -> if (isNearEdge) 126.0 else 134.0
                                6 -> if (isNearEdge) 142.0 else 152.0
                                else -> if (isNearEdge) 150.0 else 164.0
                            }
                        }
                    }

                    // Calculate angles for Layer 1 and Layer 2
                    fun computeAngles(itemsCount: Int, isInner: Boolean): List<Double> {
                        if (itemsCount <= 0) return emptyList()
                        return when (effectiveDirection) {
                            OrbFanDirection.RIGHT -> {
                                val isNearEdge = verticalFraction < 0.35f || verticalFraction > 0.65f
                                val tiltDeg = when {
                                    verticalFraction < 0.35f -> (0.35f - verticalFraction) / 0.35f * 52.0
                                    verticalFraction > 0.65f -> (verticalFraction - 0.65f) / 0.35f * -52.0
                                    else -> 0.0
                                }
                                val effectiveCenter = 0.0 + Math.toRadians(tiltDeg)
                                val spanRad = Math.toRadians(getSpanDeg(itemsCount, isInner, isNearEdge))
                                val step = if (itemsCount > 1) spanRad / (itemsCount - 1) else 0.0
                                (0 until itemsCount).map { i ->
                                    effectiveCenter - (spanRad / 2.0) + (i * step)
                                }
                            }
                            OrbFanDirection.LEFT -> {
                                val isNearEdge = verticalFraction < 0.35f || verticalFraction > 0.65f
                                val tiltDeg = when {
                                    verticalFraction < 0.35f -> (0.35f - verticalFraction) / 0.35f * -52.0
                                    verticalFraction > 0.65f -> (verticalFraction - 0.65f) / 0.35f * 52.0
                                    else -> 0.0
                                }
                                val effectiveCenter = Math.PI + Math.toRadians(tiltDeg)
                                val spanRad = Math.toRadians(getSpanDeg(itemsCount, isInner, isNearEdge))
                                val step = if (itemsCount > 1) spanRad / (itemsCount - 1) else 0.0
                                (0 until itemsCount).map { i ->
                                    effectiveCenter + (spanRad / 2.0) - (i * step)
                                }
                            }
                            OrbFanDirection.DOWN -> {
                                // 90° downward from top edge (+Y)
                                val isNearEdge = horizontalFraction < 0.35f || horizontalFraction > 0.65f
                                val tiltDeg = when {
                                    horizontalFraction < 0.35f -> (0.35f - horizontalFraction) / 0.35f * -52.0
                                    horizontalFraction > 0.65f -> (horizontalFraction - 0.65f) / 0.35f * 52.0
                                    else -> 0.0
                                }
                                val effectiveCenter = (Math.PI / 2.0) + Math.toRadians(tiltDeg)
                                val spanRad = Math.toRadians(getSpanDeg(itemsCount, isInner, isNearEdge))
                                val step = if (itemsCount > 1) spanRad / (itemsCount - 1) else 0.0
                                (0 until itemsCount).map { i ->
                                    effectiveCenter + (spanRad / 2.0) - (i * step)
                                }
                            }
                            OrbFanDirection.UP, OrbFanDirection.AUTO -> {
                                // -90° upward from bottom edge (-Y)
                                val isNearEdge = horizontalFraction < 0.35f || horizontalFraction > 0.65f
                                val tiltDeg = when {
                                    horizontalFraction < 0.35f -> (0.35f - horizontalFraction) / 0.35f * 52.0
                                    horizontalFraction > 0.65f -> (horizontalFraction - 0.65f) / 0.35f * -52.0
                                    else -> 0.0
                                }
                                val effectiveCenter = (-Math.PI / 2.0) + Math.toRadians(tiltDeg)
                                val spanRad = Math.toRadians(getSpanDeg(itemsCount, isInner, isNearEdge))
                                val step = if (itemsCount > 1) spanRad / (itemsCount - 1) else 0.0
                                (0 until itemsCount).map { i ->
                                    effectiveCenter - (spanRad / 2.0) + (i * step)
                                }
                            }
                        }
                    }

                    val layer1Angles = computeAngles(layer1Items.size, isInner = true)
                    val layer2Angles = computeAngles(layer2Items.size, isInner = false)

                    // Render Layer 1 (Inner Concentric Circle)
                    layer1Items.forEachIndexed { index, item ->
                        val angle = layer1Angles.getOrElse(index) { 0.0 }
                        val rawItemX = orbCenterX + (innerRadiusPx * cos(angle)).toFloat() - (innerItemSizePx / 2f)
                        val rawItemY = orbCenterY + (innerRadiusPx * sin(angle)).toFloat() - (innerItemSizePx / 2f)

                        // Allow items to fan out along their radial arc at corners without colliding/overlapping
                        val itemX = rawItemX
                        val itemY = rawItemY

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(itemX.roundToInt(), itemY.roundToInt()) }
                                .size(innerItemSizeDp)
                                .shadow(elevation = 5.dp, shape = CircleShape)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                                .border(
                                    width = 1.2.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable { item.action() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(innerIconSizeDp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Render Layer 2 (Outer Concentric Circle)
                    layer2Items.forEachIndexed { index, item ->
                        val angle = layer2Angles.getOrElse(index) { 0.0 }
                        val rawItemX = orbCenterX + (outerRadiusPx * cos(angle)).toFloat() - (outerItemSizePx / 2f)
                        val rawItemY = orbCenterY + (outerRadiusPx * sin(angle)).toFloat() - (outerItemSizePx / 2f)

                        // Allow items to fan out along their radial arc at corners without colliding/overlapping
                        val itemX = rawItemX
                        val itemY = rawItemY

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(itemX.roundToInt(), itemY.roundToInt()) }
                                .size(outerItemSizeDp)
                                .shadow(elevation = 4.dp, shape = CircleShape)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                                    shape = CircleShape
                                )
                                .clickable { item.action() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(outerIconSizeDp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        val currentEffectiveEdgeSnap by rememberUpdatedState(effectiveEdgeSnap)
        val currentSnapThreshold by rememberUpdatedState(snapThreshold)
        val currentMinDockX by rememberUpdatedState(minDockX)
        val currentMaxDockRightX by rememberUpdatedState(maxDockRightX)
        val currentMinAllowedX by rememberUpdatedState(minAllowedX)
        val currentMaxAllowedX by rememberUpdatedState(maxAllowedX)
        val currentMinAllowedY by rememberUpdatedState(minAllowedY)
        val currentMaxAllowedY by rememberUpdatedState(maxAllowedY)
        val currentIsLandscape by rememberUpdatedState(isLandscape)
        val currentBinCenterX by rememberUpdatedState(binCenterX)

        Box(
            modifier = Modifier
                .offset { IntOffset(displayX.roundToInt(), displayY.roundToInt()) }
                .size(width = effectiveWidthDp, height = effectiveHeightDp)
                .scale(if (voiceState == AssistantVoiceState.LISTENING) pulseScale else 1.0f)
                .shadow(
                    elevation = if (isDragging) 6.dp else if (isDocked) 1.5.dp else 3.5.dp,
                    shape = currentShape
                )
                .clip(currentShape)
                .background(
                    if (isDocked) {
                        if (orbColor == OrbColor.THEME) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = (orbOpacity * 0.95f).coerceIn(0.5f, 0.95f))
                        } else if (orbColor == OrbColor.CUSTOM) {
                            Color(customOrbColor).copy(alpha = orbOpacity)
                        } else {
                            Color(orbColor.colorValue).copy(alpha = orbOpacity)
                        }
                    } else {
                        if (orbColor == OrbColor.THEME) {
                            MaterialTheme.colorScheme.surface.copy(alpha = orbOpacity)
                        } else if (orbColor == OrbColor.CUSTOM) {
                            Color(customOrbColor).copy(alpha = orbOpacity)
                        } else {
                            Color(orbColor.colorValue).copy(alpha = orbOpacity)
                        }
                    }
                )
                .border(
                    width = if (isDocked) 0.8.dp else 1.5.dp,
                    color = if (isDocked) {
                        if (orbColor == OrbColor.THEME) {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        } else {
                            Color.White.copy(alpha = 0.35f)
                        }
                    } else baseOrbColor.copy(alpha = 0.45f),
                    shape = currentShape
                )
                .pointerInput(isLandscape, screenWidthPx, screenHeightPx, effectiveEdgeSnap, snapThreshold) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        var isDrag = false
                        val touchSlop = viewConfiguration.touchSlop
                        var totalDragDistance = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!change.pressed) {
                                change.consume()
                                if (isDrag) {
                                    isDragging = false
                                    if (isOverBin) {
                                        onDismissOrb()
                                    } else {
                                        if (currentEffectiveEdgeSnap) {
                                            val leftThresholdPx = screenWidthPx * currentSnapThreshold
                                            val rightThresholdPx = screenWidthPx - (screenWidthPx * currentSnapThreshold) - normalOrbSizePx
                                            if (offsetX <= leftThresholdPx) {
                                                offsetX = currentMinDockX
                                            } else if (offsetX >= rightThresholdPx) {
                                                offsetX = currentMaxDockRightX
                                            }
                                            // Else float where user dropped it
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        } else {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        }
                                        onSavePosition(offsetX, offsetY, currentIsLandscape)
                                    }
                                    isOverBin = false
                                } else {
                                    // Clean tap or long press on orb or docked handle
                                    val pressDuration = System.currentTimeMillis() - downTime
                                    if (pressDuration > 450L) {
                                        isWheelExpanded = false
                                        onStartVoiceListening()
                                    } else {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                        isWheelExpanded = !isWheelExpanded
                                    }
                                }
                                break
                            } else {
                                val dragDelta = change.position - change.previousPosition
                                totalDragDistance += dragDelta.getDistance()

                                if (!isDrag && totalDragDistance > touchSlop) {
                                    isDrag = true
                                    isDragging = true
                                    isWheelExpanded = false
                                }

                                if (isDrag) {
                                    change.consume()
                                    offsetX = (offsetX + dragDelta.x).coerceIn(currentMinAllowedX, currentMaxAllowedX)
                                    offsetY = (offsetY + dragDelta.y).coerceIn(currentMinAllowedY, currentMaxAllowedY)

                                    val orbCenterXPx = offsetX + normalOrbSizePx / 2f
                                    val orbCenterYPx = offsetY + normalOrbSizePx / 2f
                                    val distFromBinXPx = kotlin.math.abs(orbCenterXPx - currentBinCenterX)
                                    val isInBottomZone = orbCenterYPx > screenHeightPx - with(density) { 150.dp.toPx() }
                                    isOverBin = isInBottomZone && distFromBinXPx < with(density) { 95.dp.toPx() }
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isDocked) {
                Box(
                    modifier = Modifier
                        .size(width = (dockedWidthDp * 0.22f).coerceAtLeast(1.5.dp), height = (dockedHeightDp * 0.42f))
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            if (orbColor == OrbColor.THEME) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            else Color.White.copy(alpha = 0.9f)
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(innerCoreSizeDp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (voiceState) {
                            AssistantVoiceState.LISTENING -> Icons.Filled.Mic
                            AssistantVoiceState.THINKING -> Icons.Filled.AutoAwesome
                            AssistantVoiceState.RESPONDING -> Icons.Filled.AutoAwesome
                            AssistantVoiceState.IDLE -> Icons.Default.AutoAwesome
                        },
                        contentDescription = "Lumina Assistant",
                        modifier = Modifier.size(iconSizeDp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Voice Assistant Modal Card (appears on listening, thinking, or responding)
        if (voiceState != AssistantVoiceState.IDLE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onDismissVoiceDialog() },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header row with title and Spoiler Shield toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lumina Companion",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Normal,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FilterChip(
                                selected = isSpoilerShield,
                                onClick = onToggleSpoilerShield,
                                label = {
                                    Text(
                                        text = if (isSpoilerShield) "🛡️ Anti-Spoiler ON" else "📖 Full Book Context",
                                        fontSize = 10.5.sp
                                    )
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Pulsing Wave/Mic Icon
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (voiceState == AssistantVoiceState.LISTENING) Icons.Filled.Mic else Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = when (voiceState) {
                                AssistantVoiceState.LISTENING -> "Listening to your voice..."
                                AssistantVoiceState.THINKING -> "Consulting the story so far..."
                                AssistantVoiceState.RESPONDING -> "Assistant Response"
                                AssistantVoiceState.IDLE -> ""
                            },
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (voiceState == AssistantVoiceState.LISTENING) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (voiceQuery.isNotBlank()) "\"$voiceQuery\"" else "Speak a question or command...",
                                fontFamily = FontFamily.Serif,
                                fontSize = 13.5.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (voiceQuery.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "\"$voiceQuery\"",
                                fontFamily = FontFamily.Serif,
                                fontSize = 13.5.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (voiceResponse.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = voiceResponse,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 13.5.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }

                            val isAiConfigNeeded = voiceResponse.contains("API key", ignoreCase = true) ||
                                                voiceResponse.contains("Gemini", ignoreCase = true) ||
                                                voiceResponse.contains("error", ignoreCase = true) ||
                                                voiceResponse.contains("fail", ignoreCase = true) ||
                                                voiceResponse.contains("configure", ignoreCase = true)
                            if (isAiConfigNeeded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        onDismissVoiceDialog()
                                        onOpenAdvancedSettings()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open Advanced Settings", fontSize = 12.5.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (voiceResponse.isNotBlank() && voiceState == AssistantVoiceState.RESPONDING) {
                                OutlinedButton(
                                    onClick = onRetry,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry", fontSize = 12.sp)
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            TextButton(onClick = onDismissVoiceDialog) {
                                Text("Done", fontFamily = FontFamily.SansSerif)
                            }
                        }
                    }
                }
            }
        }
    }
}
