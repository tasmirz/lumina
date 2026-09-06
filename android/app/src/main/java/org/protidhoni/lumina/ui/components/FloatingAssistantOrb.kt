package org.protidhoni.lumina.ui.components

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.protidhoni.lumina.model.OrbActionItem
import org.protidhoni.lumina.model.ReadingMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    onOpenToc: () -> Unit,
    onOpenNote: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartVoiceListening: () -> Unit,
    onDismissOrb: () -> Unit,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    orbActions: Set<OrbActionItem> = setOf(
        OrbActionItem.READING_MODE,
        OrbActionItem.TTS,
        OrbActionItem.NOTE,
        OrbActionItem.TOC,
        OrbActionItem.SETTINGS
    ),
    voiceState: AssistantVoiceState = AssistantVoiceState.IDLE,
    voiceQuery: String = "",
    voiceResponse: String = "",
    onDismissVoiceDialog: () -> Unit = {},
    onOpenAdvancedSettings: () -> Unit = onOpenSettings,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    // Compact Orb Size (46dp)
    val orbSizeDp = 46.dp
    val orbSizePx = with(density) { orbSizeDp.toPx() }

    // Initial position: docked at bottom right
    var offsetX by remember { mutableFloatStateOf(screenWidthPx - orbSizePx - with(density) { 16.dp.toPx() }) }
    var offsetY by remember { mutableFloatStateOf(screenHeightPx - orbSizePx - with(density) { 110.dp.toPx() }) }

    var isDragging by remember { mutableStateOf(false) }
    var isOverBin by remember { mutableStateOf(false) }
    var isWheelExpanded by remember { mutableStateOf(false) }

    // Pulsing animation for listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Bin zone coordinates (bottom center)
    val binTargetY = screenHeightPx - with(density) { 110.dp.toPx() }
    val binCenterX = screenWidthPx / 2f

    Box(modifier = modifier.fillMaxSize()) {
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
                if (orbActions.contains(OrbActionItem.READING_MODE)) {
                    activeItems.add(
                        OrbAction(
                            icon = if (readingMode == ReadingMode.SCROLL) Icons.Filled.SwapVert else Icons.AutoMirrored.Filled.MenuBook,
                            label = if (readingMode == ReadingMode.SCROLL) "Scroll Mode" else "Paged Mode",
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
                if (orbActions.contains(OrbActionItem.NOTE)) {
                    activeItems.add(
                        OrbAction(
                            icon = Icons.Outlined.EditNote,
                            label = "Notes & Highlights",
                            action = { onOpenNote(); isWheelExpanded = false }
                        )
                    )
                }
                if (orbActions.contains(OrbActionItem.TOC)) {
                    activeItems.add(
                        OrbAction(
                            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                            label = "Table of Contents",
                            action = { onOpenToc(); isWheelExpanded = false }
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
                if (orbActions.contains(OrbActionItem.VOICE)) {
                    activeItems.add(
                        OrbAction(
                            icon = Icons.Default.Mic,
                            label = "Voice Assistant",
                            action = { onStartVoiceListening(); isWheelExpanded = false }
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

                // If somehow empty, provide default TOC & Settings
                if (activeItems.isEmpty()) {
                    activeItems.add(
                        OrbAction(
                            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                            label = "Table of Contents",
                            action = { onOpenToc(); isWheelExpanded = false }
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

                val wheelRadius = with(density) { 92.dp.toPx() }
                val orbCenterX = offsetX + (orbSizePx / 2f)
                val orbCenterY = offsetY + (orbSizePx / 2f)
                val isRightSide = orbCenterX > (screenWidthPx / 2f)

                // Fan strictly inward into the screen away from boundaries
                val centerAngle = if (isRightSide) Math.PI else 0.0
                val arcSpanRad = Math.toRadians(135.0)
                val startAngle = if (activeItems.size > 1) {
                    centerAngle - (arcSpanRad / 2.0)
                } else {
                    centerAngle
                }
                val angleStep = if (activeItems.size > 1) arcSpanRad / (activeItems.size - 1) else 0.0

                activeItems.forEachIndexed { index, item ->
                    val angle = startAngle + (index * angleStep)
                    val itemSizePx = with(density) { 42.dp.toPx() }
                    val itemX = (orbCenterX + (wheelRadius * cos(angle)).toFloat() - (itemSizePx / 2f))
                        .coerceIn(with(density) { 16.dp.toPx() }, screenWidthPx - itemSizePx - with(density) { 16.dp.toPx() })
                    val itemY = (orbCenterY + (wheelRadius * sin(angle)).toFloat() - (itemSizePx / 2f))
                        .coerceIn(with(density) { 40.dp.toPx() }, screenHeightPx - itemSizePx - with(density) { 60.dp.toPx() })

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(itemX.roundToInt(), itemY.roundToInt()) }
                            .size(42.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape)
                            .clickable { item.action() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // The Floating Assistant Orb: 1:1 Direct Finger Tracking with zero drag offset
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(orbSizeDp)
                .scale(if (voiceState == AssistantVoiceState.LISTENING) pulseScale else 1.0f)
                .shadow(
                    elevation = if (isDragging) 6.dp else 2.5.dp,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = 0.92f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = CircleShape
                )
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
                            }
                            isOverBin = false
                        },
                        onDragCancel = {
                            isDragging = false
                            isOverBin = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = (offsetX + dragAmount.x).coerceIn(
                                with(density) { 10.dp.toPx() },
                                screenWidthPx - orbSizePx - with(density) { 10.dp.toPx() }
                            )
                            val newY = (offsetY + dragAmount.y).coerceIn(
                                with(density) { 32.dp.toPx() },
                                screenHeightPx - orbSizePx - with(density) { 12.dp.toPx() }
                            )
                            offsetX = newX
                            offsetY = newY

                            val orbCenterXPx = offsetX + orbSizePx / 2f
                            val orbCenterYPx = offsetY + orbSizePx / 2f
                            val distFromBinXPx = kotlin.math.abs(orbCenterXPx - binCenterX)
                            val isInBottomZone = orbCenterYPx > screenHeightPx - with(density) { 150.dp.toPx() }
                            isOverBin = isInBottomZone && distFromBinXPx < with(density) { 95.dp.toPx() }
                        }
                    )
                }
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
                },
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
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
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
                        .fillMaxWidth(0.88f)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Pulsing Wave/Mic Icon
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (voiceState == AssistantVoiceState.LISTENING) Icons.Filled.Mic else Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = when (voiceState) {
                                AssistantVoiceState.LISTENING -> "Listening to your voice..."
                                AssistantVoiceState.THINKING -> "Consulting the story so far..."
                                AssistantVoiceState.RESPONDING -> "Lumina Assistant"
                                AssistantVoiceState.IDLE -> ""
                            },
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (voiceState == AssistantVoiceState.LISTENING) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (voiceQuery.isNotBlank()) "\"$voiceQuery\"" else "Speak a question or command...",
                                fontFamily = FontFamily.Serif,
                                fontSize = 14.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (voiceQuery.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "\"$voiceQuery\"",
                                fontFamily = FontFamily.Serif,
                                fontSize = 14.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (voiceResponse.isNotBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = voiceResponse,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
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
                                Spacer(modifier = Modifier.height(14.dp))
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
                                    Text("Open Advanced Settings", fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
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
