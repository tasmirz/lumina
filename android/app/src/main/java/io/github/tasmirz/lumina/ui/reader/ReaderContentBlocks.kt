package io.github.tasmirz.lumina.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed class PageBlock {
    data class Text(val text: String) : PageBlock()
    data class Table(val token: String) : PageBlock()
}

fun splitPageIntoBlocks(rawText: String): List<PageBlock> {
    if (!rawText.contains("[TABLE:")) {
        return listOf(PageBlock.Text(rawText))
    }
    val blocks = mutableListOf<PageBlock>()
    val tableRegex = "\\[TABLE:[^\\]]+\\]".toRegex()
    var lastIdx = 0
    tableRegex.findAll(rawText).forEach { match ->
        if (match.range.first > lastIdx) {
            val txt = rawText.substring(lastIdx, match.range.first).trim()
            if (txt.isNotEmpty()) {
                blocks.add(PageBlock.Text(txt))
            }
        }
        blocks.add(PageBlock.Table(match.value))
        lastIdx = match.range.last + 1
    }
    if (lastIdx < rawText.length) {
        val txt = rawText.substring(lastIdx).trim()
        if (txt.isNotEmpty()) {
            blocks.add(PageBlock.Text(txt))
        }
    }
    return if (blocks.isEmpty()) listOf(PageBlock.Text(rawText)) else blocks
}

@Composable
fun ReaderTableCard(
    tableToken: String,
    modifier: Modifier = Modifier
) {
    val jsonStr = tableToken.removePrefix("[TABLE:").removeSuffix("]")
    val (headers, rows) = remember(jsonStr) {
        try {
            val obj = JSONObject(jsonStr)
            val hArr = obj.optJSONArray("headers")
            val hList = if (hArr != null) List(hArr.length()) { i -> hArr.getString(i) } else emptyList()
            val rArr = obj.optJSONArray("rows")
            val rList = if (rArr != null) {
                List(rArr.length()) { r ->
                    val rowArr = rArr.getJSONArray(r)
                    List(rowArr.length()) { c -> rowArr.getString(c) }
                }
            } else emptyList()
            hList to rList
        } catch (_: Exception) {
            emptyList<String>() to emptyList<List<String>>()
        }
    }

    if (headers.isEmpty() && rows.isEmpty()) return

    val totalCols = remember(headers, rows) {
        maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
    }
    if (totalCols == 0) return

    val colWidths = remember(headers, rows, totalCols) {
        (0 until totalCols).map { c ->
            val maxLen = maxOf(
                headers.getOrNull(c)?.length ?: 0,
                rows.maxOfOrNull { it.getOrNull(c)?.length ?: 0 } ?: 0
            )
            if (totalCols <= 2) {
                maxOf(140.dp, minOf(190.dp, (maxLen * 6.0f).dp.coerceAtLeast(140.dp)))
            } else {
                maxOf(120.dp, minOf(240.dp, (maxLen * 6.5f).dp.coerceAtLeast(120.dp)))
            }
        }
    }

    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        ),
        border = BorderStroke(1.dp, outlineColor)
    ) {
        val hScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(hScrollState)
                .padding(vertical = 6.dp, horizontal = 8.dp)
        ) {
            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                if (headers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f), shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (c in 0 until totalCols) {
                            val headerText = headers.getOrNull(c) ?: ""
                            Box(
                                modifier = Modifier
                                    .width(colWidths[c])
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = headerText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (c < totalCols - 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(0.8.dp)
                                        .background(outlineColor)
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = outlineColor
                    )
                }

                rows.forEachIndexed { rowIdx, rowCells ->
                    val rowBg = if (rowIdx % 2 == 1) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                    } else {
                        Color.Transparent
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (c in 0 until totalCols) {
                            val cellText = rowCells.getOrNull(c) ?: ""
                            Box(
                                modifier = Modifier
                                    .width(colWidths[c])
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = cellText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Light,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)
                                )
                            }
                            if (c < totalCols - 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(0.8.dp)
                                        .background(outlineColor)
                                )
                            }
                        }
                    }
                    if (rowIdx < rows.size - 1) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = outlineColor.copy(alpha = 0.25f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderDivider(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⁂",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun ReaderVerseCard(
    verse: String,
    fontSize: TextUnit,
    fontFamily: FontFamily,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)
    ) {
        Text(
            text = verse,
            fontFamily = fontFamily,
            fontSize = fontSize * 0.95f,
            lineHeight = fontSize * 1.38f,
            color = textColor,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun ReaderQuoteCard(
    quote: String,
    fontSize: TextUnit,
    fontFamily: FontFamily,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
            )
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 3.dp.toPx()
                )
            }
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Text(
            text = quote,
            fontFamily = fontFamily,
            fontSize = fontSize * 0.95f,
            fontStyle = FontStyle.Italic,
            lineHeight = fontSize * 1.4f,
            color = textColor,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun InlineReaderImage(
    bitmap: Bitmap?,
    imgPath: String,
    widthFraction: Float,
    modifier: Modifier = Modifier,
    onOpenLightbox: (Bitmap, String) -> Unit,
    onTap: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val coroutineScope = rememberCoroutineScope()
    var isPinching by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isPinching || scale > 1.05f) 50f else 0f)
            .padding(vertical = 16.dp)
            .pointerInput(bitmap, imgPath) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var zoom = 1f
                    var rot = 0f
                    var pan = Offset.Zero
                    var transformed = false
                    val touchSlop = viewConfiguration.touchSlop
                    var totalDrag = Offset.Zero

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.changes.size > 1) {
                            transformed = true
                            isPinching = true
                            val zoomChange = event.calculateZoom()
                            val rotChange = event.calculateRotation()
                            val panChange = event.calculatePan()

                            zoom = (zoom * zoomChange).coerceIn(0.5f, 6.0f)
                            rot += rotChange
                            pan += panChange

                            scale = zoom
                            rotation = rot
                            offset = pan

                            event.changes.forEach { it.consume() }
                        } else if (transformed) {
                            val panChange = event.calculatePan()
                            pan += panChange
                            offset = pan
                            event.changes.forEach { it.consume() }
                        } else {
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                totalDrag += change.position - change.previousPosition
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (transformed) {
                        isPinching = false
                        coroutineScope.launch {
                            val springSpec = spring<Float>(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                            val animScale = Animatable(scale)
                            val animRot = Animatable(rotation)
                            val animX = Animatable(offset.x)
                            val animY = Animatable(offset.y)

                            launch { animScale.animateTo(1f, springSpec) { scale = value } }
                            launch { animRot.animateTo(0f, springSpec) { rotation = value } }
                            launch { animX.animateTo(0f, springSpec) { offset = Offset(value, offset.y) } }
                            launch { animY.animateTo(0f, springSpec) { offset = Offset(offset.x, value) } }
                        }
                    } else {
                        if (totalDrag.getDistance() < touchSlop) {
                            if (bitmap != null) {
                                onOpenLightbox(bitmap, imgPath)
                            } else {
                                onTap()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            val isBlockDiagram = bitmap.width >= 500 && bitmap.height >= 200
            val density = LocalDensity.current
            val imageModifier = if (isBlockDiagram) {
                Modifier
                    .fillMaxWidth(widthFraction)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            } else {
                val intrinsicWidthDp = with(density) { bitmap.width.toDp() }
                val intrinsicHeightDp = with(density) { bitmap.height.toDp() }
                Modifier
                    .widthIn(max = intrinsicWidthDp.coerceAtMost(360.dp).coerceAtLeast(32.dp))
                    .heightIn(max = intrinsicHeightDp.coerceAtMost(240.dp).coerceAtLeast(24.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Illustration",
                contentScale = if (isBlockDiagram) ContentScale.FillWidth else ContentScale.Fit,
                modifier = imageModifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = rotation
                        translationX = offset.x
                        translationY = offset.y
                        shadowElevation = if (isPinching) 16f else 0f
                    }
            )
        } else {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(160.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Illustration",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
