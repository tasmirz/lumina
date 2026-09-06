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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class AssistantVoiceState {
    IDLE,
    LISTENING,
    THINKING,
    RESPONDING
}

@Composable
fun FloatingAssistantOrb(
    isTtsPlaying: Boolean,
    onToggleTts: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenNote: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartVoiceListening: () -> Unit,
    onDismissOrb: () -> Unit,
    voiceState: AssistantVoiceState = AssistantVoiceState.IDLE,
    voiceQuery: String = "",
    voiceResponse: String = "",
    onDismissVoiceDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    val orbSizeDp = 58.dp
    val orbSizePx = with(density) { orbSizeDp.toPx() }

    // Initial position: docked at bottom right
    var offsetX by remember { mutableFloatStateOf(screenWidthPx - orbSizePx - with(density) { 20.dp.toPx() }) }
    var offsetY by remember { mutableFloatStateOf(screenHeightPx - orbSizePx - with(density) { 110.dp.toPx() }) }

    var isDragging by remember { mutableStateOf(false) }
    var isOverBin by remember { mutableStateOf(false) }
    var isWheelExpanded by remember { mutableStateOf(false) }

    // Pulsing animation for listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Bin zone coordinates (bottom center)
    val binTargetY = screenHeightPx - with(density) { 100.dp.toPx() }
    val binTargetXRange = (screenWidthPx / 2f - with(density) { 60.dp.toPx() })..(screenWidthPx / 2f + with(density) { 60.dp.toPx() })

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
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    )
                    .border(
                        width = 2.dp,
                        color = if (isOverBin) Color.White else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Drop here to dismiss assistant",
                    tint = if (isOverBin) Color.White else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(if (isOverBin) 32.dp else 24.dp)
                )
            }
        }

        // Tap-to-expand Radial Wheel overlay
        if (isWheelExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isWheelExpanded = false }
            ) {
                val wheelRadius = with(density) { 82.dp.toPx() }
                val items = listOf(
                    Triple(
                        if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isTtsPlaying) "Pause" else "Read",
                        { onToggleTts(); isWheelExpanded = false }
                    ),
                    Triple(Icons.Outlined.EditNote, "Note", { onOpenNote(); isWheelExpanded = false }),
                    Triple(Icons.AutoMirrored.Filled.FormatListBulleted, "TOC", { onOpenToc(); isWheelExpanded = false }),
                    Triple(Icons.Outlined.Tune, "Settings", { onOpenSettings(); isWheelExpanded = false })
                )

                items.forEachIndexed { index, (icon, label, action) ->
                    // 4 items arranged evenly (angles: -135°, -45°, 45°, 135° or 0°, 90°, 180°, 270°)
                    val angleDeg = 180.0 + (index * 90.0) // distributes in arc
                    val rad = Math.toRadians(angleDeg)
                    val itemX = (offsetX + (orbSizePx / 2f) + (wheelRadius * cos(rad)).toFloat() - with(density) { 24.dp.toPx() })
                        .coerceIn(with(density) { 16.dp.toPx() }, screenWidthPx - with(density) { 56.dp.toPx() })
                    val itemY = (offsetY + (orbSizePx / 2f) + (wheelRadius * sin(rad)).toFloat() - with(density) { 24.dp.toPx() })
                        .coerceIn(with(density) { 60.dp.toPx() }, screenHeightPx - with(density) { 90.dp.toPx() })

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(itemX.roundToInt(), itemY.roundToInt()) }
                            .size(48.dp)
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { action() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // The Floating Assistant Orb
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(orbSizeDp)
                .scale(if (voiceState == AssistantVoiceState.LISTENING) pulseScale else 1.0f)
                .shadow(
                    elevation = if (isDragging) 12.dp else 6.dp,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
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
                                with(density) { 12.dp.toPx() },
                                screenWidthPx - orbSizePx - with(density) { 12.dp.toPx() }
                            )
                            val newY = (offsetY + dragAmount.y).coerceIn(
                                with(density) { 40.dp.toPx() },
                                screenHeightPx - orbSizePx - with(density) { 40.dp.toPx() }
                            )
                            offsetX = newX
                            offsetY = newY

                            isOverBin = (offsetY > binTargetY - with(density) { 50.dp.toPx() }) &&
                                    (offsetX + orbSizePx / 2f in binTargetXRange)
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
                    AssistantVoiceState.IDLE -> Icons.Filled.AutoAwesome
                },
                contentDescription = "Lumina Assistant",
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        // Voice Assistant Modal Card (appears on listening, thinking, or responding)
        if (voiceState != AssistantVoiceState.IDLE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
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
                                else -> ""
                            },
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (voiceQuery.isNotBlank()) {
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
