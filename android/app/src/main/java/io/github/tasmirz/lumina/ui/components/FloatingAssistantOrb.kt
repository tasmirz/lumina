package io.github.tasmirz.lumina.ui.components

import io.github.tasmirz.lumina.model.OrbSize
import io.github.tasmirz.lumina.model.OrbMenuSize
import io.github.tasmirz.lumina.model.OrbColor
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    orbOpacity: Float = 0.85f,
    savedX: Float = -1f,
    savedY: Float = -1f,
    onSavePosition: (x: Float, y: Float, isLandscape: Boolean) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    // Camera notch / display cutout insets to prevent docking under the camera
    val layoutDirection = LocalLayoutDirection.current
    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()
    val cutoutLeftPx = with(density) { cutoutInsets.calculateLeftPadding(layoutDirection).toPx() }
    val cutoutRightPx = with(density) { cutoutInsets.calculateRightPadding(layoutDirection).toPx() }
    val cutoutTopPx = with(density) { cutoutInsets.calculateTopPadding().toPx() }
    val cutoutBottomPx = with(density) { cutoutInsets.calculateBottomPadding().toPx() }

    val baseOrbColor = MaterialTheme.colorScheme.primary

    val scale = orbSize.scale
    val dockedWidthDp = orbSize.dockedWidth.dp
    val dockedHeightDp = orbSize.dockedHeight.dp
    val normalOrbSizeDp = (44 * scale).dp
    val normalOrbSizePx = with(density) { normalOrbSizeDp.toPx() }

    // Initial position: docked safely on edge within height bounds and outside cutout
    val defaultDockedWidthPx = with(density) { dockedWidthDp.toPx() }
    val defaultDockedHeightPx = with(density) { dockedHeightDp.toPx() }
    var offsetX by remember {
        mutableFloatStateOf(
            if (savedX >= 0f) savedX else (screenWidthPx - defaultDockedWidthPx - cutoutRightPx).coerceAtLeast(cutoutLeftPx)
        )
    }
    var offsetY by remember {
        val initialY = if (savedY >= 0f) savedY else {
            (screenHeightPx * 0.70f).coerceIn(
                maxOf(screenHeightPx * 0.15f, cutoutTopPx),
                minOf(screenHeightPx * 0.85f - defaultDockedHeightPx, screenHeightPx - cutoutBottomPx - defaultDockedHeightPx).coerceAtLeast(screenHeightPx * 0.15f)
            )
        }
        mutableFloatStateOf(initialY)
    }

    var isDragging by remember { mutableStateOf(false) }
    var isOverBin by remember { mutableStateOf(false) }
    var isWheelExpanded by remember { mutableStateOf(false) }

    val isDocked = orbEdgeSnap && !isDragging && !isWheelExpanded && voiceState == AssistantVoiceState.IDLE
    val isNearLeftEdge = offsetX < screenWidthPx / 2f

    val currentWidthDp by animateDpAsState(
        targetValue = if (isDocked) dockedWidthDp else normalOrbSizeDp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "orbWidth"
    )
    val currentHeightDp by animateDpAsState(
        targetValue = if (isDocked) dockedHeightDp else normalOrbSizeDp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    )

    val orbWidthPx = with(density) { currentWidthDp.toPx() }
    val orbHeightPx = with(density) { currentHeightDp.toPx() }

    // Re-clamp position on orientation, saved coordinates, or screen size changes so the orb is never lost or under the notch
    LaunchedEffect(savedX, savedY, screenWidthPx, screenHeightPx, orbEdgeSnap, isLandscape) {
        val minX = cutoutLeftPx
        val maxX = (screenWidthPx - orbWidthPx - cutoutRightPx).coerceAtLeast(minX)
        val minY = maxOf(screenHeightPx * 0.15f, cutoutTopPx)
        val maxY = minOf(screenHeightPx * 0.85f - orbHeightPx, screenHeightPx - cutoutBottomPx - orbHeightPx).coerceAtLeast(minY)

        if (savedX >= 0f && savedY >= 0f) {
            offsetY = savedY.coerceIn(minY, maxY)
            if (orbEdgeSnap) {
                offsetX = if (savedX < screenWidthPx / 2f) minX else maxX
            } else {
                offsetX = savedX.coerceIn(minX, maxX)
            }
        } else {
            offsetY = offsetY.coerceIn(minY, maxY)
            if (orbEdgeSnap) {
                offsetX = if (offsetX < screenWidthPx / 2f) minX else maxX
            } else {
                offsetX = offsetX.coerceIn(minX, maxX)
            }
        }
    }

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

    Box(modifier = modifier.fillMaxSize().zIndex(150f)) {
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
                if (orbActions.contains(OrbActionItem.THEME_MODE)) {
                    activeItems.add(
                        OrbAction(
                            icon = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            label = if (isDarkTheme) "Light Theme" else "Dark Theme",
                            action = { onToggleThemeMode(); isWheelExpanded = false }
                        )
                    )
                }
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
                if (orbActions.contains(OrbActionItem.TTS)) {
                    activeItems.add(
                        OrbAction(
                            icon = if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            label = if (isTtsPlaying) "Pause Audio" else "Read Aloud",
                            action = { onToggleTts(); isWheelExpanded = false }
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
                if (orbActions.contains(OrbActionItem.NOTE)) {
                    activeItems.add(
                        OrbAction(
                            icon = Icons.Outlined.EditNote,
                            label = "Notes & Highlights",
                            action = { onOpenNote(); isWheelExpanded = false }
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

                // 2-Ring Concentric Palette:
                // Tier 1 (Inner): up to 3 core reading items
                // Tier 2 (Outer): up to 6 tool items (3 + 6 = 9 items total)
                val tier1Items = activeItems.take(minOf(3, activeItems.size))
                val tier2Items = activeItems.drop(tier1Items.size).take(minOf(6, activeItems.size - tier1Items.size))
                val tier3Items = activeItems.drop(tier1Items.size + tier2Items.size)

                val minDockXForMenu = cutoutLeftPx
                val maxDockXForMenu = (screenWidthPx - with(density) { dockedWidthDp.toPx() } - cutoutRightPx).coerceAtLeast(minDockXForMenu)
                val currentOrbX = if (isDragging) offsetX else (if (orbEdgeSnap) (if (offsetX < screenWidthPx / 2f) minDockXForMenu else maxDockXForMenu) else offsetX)
                val currentOrbY = offsetY
                val orbCenterX = currentOrbX + with(density) { (if (isDocked) dockedWidthDp / 2f else normalOrbSizeDp / 2f).toPx() }
                val orbCenterY = currentOrbY + with(density) { (if (isDocked) dockedHeightDp / 2f else normalOrbSizeDp / 2f).toPx() }
                val isRightSide = orbCenterX > (screenWidthPx / 2f)

                val verticalFraction = (orbCenterY / screenHeightPx).coerceIn(0f, 1f)

                // Dynamically tilt arc away from screen edges so items never break or collide with status bar or bottom dock
                val tiltDeg = when {
                    verticalFraction < 0.30f -> (0.30f - verticalFraction) / 0.30f * 28.0 // Tilt downward into screen
                    verticalFraction > 0.70f -> (verticalFraction - 0.70f) / 0.30f * 28.0 // Tilt upward into screen
                    else -> 0.0
                }
                // Screen coordinates: positive rotation is clockwise (downward), negative is counter-clockwise (upward)
                val centerAngle = if (isRightSide) {
                    if (verticalFraction < 0.35f) Math.PI - Math.toRadians(tiltDeg)
                    else Math.PI + Math.toRadians(tiltDeg)
                } else {
                    if (verticalFraction < 0.35f) 0.0 + Math.toRadians(tiltDeg)
                    else 0.0 - Math.toRadians(tiltDeg)
                }

                val radiusScale = 1.0f // Never shrink radius in landscape so buttons don't compress
                val innerRadius = with(density) { (orbMenuSize.innerRadiusDp * radiusScale).dp.toPx() }
                val middleRadius = with(density) { (orbMenuSize.outerRadiusDp * radiusScale).dp.toPx() }
                val outerRadius = with(density) { ((orbMenuSize.outerRadiusDp + 46) * radiusScale).dp.toPx() }

                val innerItemSizeDp = (orbMenuSize.itemSizeDp - 2).dp
                val innerItemSizePx = with(density) { innerItemSizeDp.toPx() }
                val middleItemSizeDp = orbMenuSize.itemSizeDp.dp
                val middleItemSizePx = with(density) { middleItemSizeDp.toPx() }
                val outerItemSizeDp = orbMenuSize.itemSizeDp.dp
                val outerItemSizePx = with(density) { outerItemSizeDp.toPx() }
                val menuIconSize = orbMenuSize.iconSizeDp.dp

                // Uncompressed bounds in landscape: allow arc to fan inward without squishing buttons together
                val innerMinYBound = if (isLandscape) -with(density) { 16.dp.toPx() } else with(density) { 36.dp.toPx() }
                val innerMaxYBound = if (isLandscape) screenHeightPx + with(density) { 16.dp.toPx() } - innerItemSizePx else screenHeightPx - innerItemSizePx - with(density) { 48.dp.toPx() }

                val middleMinYBound = if (isLandscape) -with(density) { 20.dp.toPx() } else with(density) { 36.dp.toPx() }
                val middleMaxYBound = if (isLandscape) screenHeightPx + with(density) { 20.dp.toPx() } - middleItemSizePx else screenHeightPx - middleItemSizePx - with(density) { 48.dp.toPx() }

                val outerMinYBound = if (isLandscape) -with(density) { 24.dp.toPx() } else with(density) { 36.dp.toPx() }
                val outerMaxYBound = if (isLandscape) screenHeightPx + with(density) { 24.dp.toPx() } - outerItemSizePx else screenHeightPx - outerItemSizePx - with(density) { 48.dp.toPx() }

                // 1. Tier 1: Inner Ring Placement (up to 3 items)
                val innerSpanDeg = when (tier1Items.size) {
                    1 -> 0.0
                    2 -> 38.0
                    else -> 64.0
                }
                val innerSpanRad = Math.toRadians(innerSpanDeg)
                val innerStep = if (tier1Items.size > 1) innerSpanRad / (tier1Items.size - 1) else 0.0

                tier1Items.forEachIndexed { index, item ->
                    val angle = if (isRightSide) {
                        centerAngle + (innerSpanRad / 2.0) - (index * innerStep)
                    } else {
                        centerAngle - (innerSpanRad / 2.0) + (index * innerStep)
                    }
                    val itemX = (orbCenterX + (innerRadius * cos(angle)).toFloat() - (innerItemSizePx / 2f))
                        .coerceIn(with(density) { 8.dp.toPx() }, screenWidthPx - innerItemSizePx - with(density) { 8.dp.toPx() })
                    val itemY = (orbCenterY + (innerRadius * sin(angle)).toFloat() - (innerItemSizePx / 2f))
                        .coerceIn(innerMinYBound, innerMaxYBound)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(itemX.roundToInt(), itemY.roundToInt()) }
                            .size(innerItemSizeDp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape)
                            .clickable { item.action() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(menuIconSize),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 2. Tier 2: Middle Ring Placement (up to 6 items)
                val middleSpanDeg = when (tier2Items.size) {
                    1 -> 0.0
                    2 -> 34.0
                    3 -> 56.0
                    4 -> 76.0
                    5 -> 94.0
                    else -> 110.0
                }
                val middleSpanRad = Math.toRadians(middleSpanDeg)
                val middleStep = if (tier2Items.size > 1) middleSpanRad / (tier2Items.size - 1) else 0.0

                val tier2Angles = (0 until tier2Items.size).map { index ->
                    if (isRightSide) {
                        centerAngle + (middleSpanRad / 2.0) - (index * middleStep)
                    } else {
                        centerAngle - (middleSpanRad / 2.0) + (index * middleStep)
                    }
                }

                tier2Items.forEachIndexed { index, item ->
                    val angle = tier2Angles[index]
                    val itemX = (orbCenterX + (middleRadius * cos(angle)).toFloat() - (middleItemSizePx / 2f))
                        .coerceIn(with(density) { 8.dp.toPx() }, screenWidthPx - middleItemSizePx - with(density) { 8.dp.toPx() })
                    val itemY = (orbCenterY + (middleRadius * sin(angle)).toFloat() - (middleItemSizePx / 2f))
                        .coerceIn(middleMinYBound, middleMaxYBound)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(itemX.roundToInt(), itemY.roundToInt()) }
                            .size(middleItemSizeDp)
                            .shadow(5.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f), CircleShape)
                            .clickable { item.action() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(menuIconSize),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // 3. Tier 3: Outer Ring Placement — Centered in the angular gaps between Tier 2 items
                if (tier3Items.isNotEmpty()) {
                    val gapAngles = if (tier2Angles.size >= 2) {
                        (0 until tier2Angles.size - 1).map { i ->
                            (tier2Angles[i] + tier2Angles[i + 1]) / 2.0
                        }
                    } else emptyList()

                    val tier3Angles: List<Double> = when {
                        gapAngles.isNotEmpty() && tier3Items.size <= gapAngles.size -> {
                            if (tier3Items.size == gapAngles.size) {
                                gapAngles
                            } else if (tier3Items.size == 1) {
                                listOf(gapAngles[gapAngles.size / 2])
                            } else {
                                listOf(gapAngles.first(), gapAngles.last())
                            }
                        }
                        else -> {
                            val tier3SpanDeg = when (tier3Items.size) {
                                1 -> 0.0
                                2 -> 50.0
                                3 -> 78.0
                                4 -> 98.0
                                else -> 116.0
                            }
                            val tier3SpanRad = Math.toRadians(tier3SpanDeg)
                            val tier3Step = if (tier3Items.size > 1) tier3SpanRad / (tier3Items.size - 1) else 0.0
                            (0 until tier3Items.size).map { index ->
                                if (isRightSide) {
                                    centerAngle + (tier3SpanRad / 2.0) - (index * tier3Step)
                                } else {
                                    centerAngle - (tier3SpanRad / 2.0) + (index * tier3Step)
                                }
                            }
                        }
                    }

                    tier3Items.forEachIndexed { index, item ->
                        val angle = tier3Angles.getOrElse(index) { centerAngle }
                        val itemX = (orbCenterX + (outerRadius * cos(angle)).toFloat() - (outerItemSizePx / 2f))
                            .coerceIn(with(density) { 8.dp.toPx() }, screenWidthPx - outerItemSizePx - with(density) { 8.dp.toPx() })
                        val itemY = (orbCenterY + (outerRadius * sin(angle)).toFloat() - (outerItemSizePx / 2f))
                            .coerceIn(outerMinYBound, outerMaxYBound)

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(itemX.roundToInt(), itemY.roundToInt()) }
                                .size(outerItemSizeDp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                                .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f), CircleShape)
                                .clickable { item.action() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(menuIconSize),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }

        val minDockX = cutoutLeftPx
        val maxDockX = (screenWidthPx - orbWidthPx - cutoutRightPx).coerceAtLeast(minDockX)
        val minDockY = maxOf(screenHeightPx * 0.15f, cutoutTopPx)
        val maxDockY = minOf(screenHeightPx * 0.85f - orbHeightPx, screenHeightPx - cutoutBottomPx - orbHeightPx).coerceAtLeast(minDockY)

        val animatedX by animateFloatAsState(
            targetValue = if (isDragging) offsetX else (if (orbEdgeSnap) (if (isNearLeftEdge) minDockX else maxDockX) else offsetX),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "orbX"
        )

        val currentShape = when {
            isDocked && isNearLeftEdge -> RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp, topStart = 0.dp, bottomStart = 0.dp)
            isDocked && !isNearLeftEdge -> RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 0.dp, bottomEnd = 0.dp)
            else -> CircleShape
        }

        // The Floating Assistant: 2-Layer Concentric Circle Design / Fluid Edge Dock
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedX.roundToInt(), offsetY.roundToInt()) }
                .size(width = currentWidthDp, height = currentHeightDp)
                .scale(if (voiceState == AssistantVoiceState.LISTENING) pulseScale else 1.0f)
                .shadow(
                    elevation = if (isDragging) 6.dp else if (isDocked) 1.5.dp else 4.dp,
                    shape = currentShape
                )
                .clip(currentShape)
                .background(
                    if (isDocked) {
                        if (orbColor == OrbColor.THEME) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = (orbOpacity * 0.95f).coerceIn(0.5f, 0.95f))
                        } else {
                            Color(orbColor.colorValue).copy(alpha = orbOpacity)
                        }
                    } else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
                .border(
                    width = if (isDocked) 0.8.dp else 1.5.dp,
                    color = if (isDocked) {
                        if (orbColor == OrbColor.THEME) {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        } else {
                            Color.White.copy(alpha = 0.35f)
                        }
                    } else baseOrbColor.copy(alpha = 0.35f),
                    shape = currentShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            isWheelExpanded = !isWheelExpanded
                        },
                        onLongPress = {
                            isWheelExpanded = false
                            onStartVoiceListening()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            isWheelExpanded = false
                        },
                        onDragEnd = {
                            isDragging = false
                            if (isOverBin) {
                                onDismissOrb()
                            } else if (orbEdgeSnap) {
                                // Snap to nearest safe edge outside cutout
                                offsetX = if (offsetX < screenWidthPx / 2f) minDockX else maxDockX
                                onSavePosition(offsetX, offsetY, isLandscape)
                            } else {
                                // Free floating within cutout-safe bounds
                                offsetX = offsetX.coerceIn(minDockX, maxDockX)
                                onSavePosition(offsetX, offsetY, isLandscape)
                            }
                            isOverBin = false
                        },
                        onDragCancel = {
                            isDragging = false
                            isOverBin = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = (offsetX + dragAmount.x).coerceIn(minDockX, maxDockX)
                            val newY = (offsetY + dragAmount.y).coerceIn(minDockY, maxDockY)
                            offsetX = newX
                            offsetY = newY

                            val orbCenterXPx = offsetX + orbWidthPx / 2f
                            val orbCenterYPx = offsetY + orbHeightPx / 2f
                            val distFromBinXPx = kotlin.math.abs(orbCenterXPx - binCenterX)
                            val isInBottomZone = orbCenterYPx > screenHeightPx - with(density) { 150.dp.toPx() }
                            isOverBin = isInBottomZone && distFromBinXPx < with(density) { 95.dp.toPx() }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isDocked) {
                Box(
                    modifier = Modifier
                        .size(width = (dockedWidthDp * 0.22f).coerceAtLeast(2.5.dp), height = (dockedHeightDp * 0.42f))
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            if (orbColor == OrbColor.THEME) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            else Color.White.copy(alpha = 0.9f)
                        )
                )
            } else {
                // Concentric inner circle with slight size difference (36dp inside 48dp)
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                        )
                        .shadow(elevation = 2.dp, shape = CircleShape),
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
                        modifier = Modifier.size(19.dp),
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
                                fontWeight = FontWeight.SemiBold,
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
                            fontWeight = FontWeight.Medium,
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
