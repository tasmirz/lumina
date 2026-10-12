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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed class PageBlock {
    data class Text(val text: String) : PageBlock()
    data class Table(val token: String) : PageBlock()
    data class Image(val path: String) : PageBlock()
    data object Divider : PageBlock()
}

fun splitPageIntoBlocks(rawText: String): List<PageBlock> {
    if (!rawText.contains("[TABLE:") && !rawText.contains("[IMG:") && !rawText.contains("[DIVIDER]")) {
        return listOf(PageBlock.Text(rawText))
    }
    val blocks = mutableListOf<PageBlock>()
    var lastIdx = 0
    var scanIdx = 0
    while (scanIdx < rawText.length) {
        val nextTable = rawText.indexOf("[TABLE:", scanIdx)
        val nextImg = rawText.indexOf("[IMG:", scanIdx)
        val nextDivider = rawText.indexOf("[DIVIDER]", scanIdx)

        if (nextTable == -1 && nextImg == -1 && nextDivider == -1) break

        val minPos = listOfNotNull(
            if (nextTable != -1) nextTable else null,
            if (nextImg != -1) nextImg else null,
            if (nextDivider != -1) nextDivider else null
        ).minOrNull() ?: break

        if (minPos == nextDivider) {
            if (nextDivider > lastIdx) {
                val txt = rawText.substring(lastIdx, nextDivider).trim()
                if (txt.isNotEmpty()) blocks.add(PageBlock.Text(txt))
            }
            blocks.add(PageBlock.Divider)
            lastIdx = nextDivider + "[DIVIDER]".length
            scanIdx = lastIdx
        } else if (minPos == nextImg) {
            val end = rawText.indexOf(']', nextImg)
            if (end != -1) {
                if (nextImg > lastIdx) {
                    val txt = rawText.substring(lastIdx, nextImg).trim()
                    if (txt.isNotEmpty()) {
                        blocks.add(PageBlock.Text(txt))
                    }
                }
                val imgToken = rawText.substring(nextImg, end + 1)
                val imgPath = imgToken.removePrefix("[IMG:").removeSuffix("]")
                blocks.add(PageBlock.Image(imgPath))
                lastIdx = end + 1
                scanIdx = end + 1
            } else {
                scanIdx = nextImg + 5
            }
        } else {
            val start = nextTable
            var depth = 0
            var inString = false
            var escape = false
            var end = -1
            for (i in start until rawText.length) {
                val ch = rawText[i]
                if (inString) {
                    if (escape) {
                        escape = false
                    } else if (ch == '\\') {
                        escape = true
                    } else if (ch == '"') {
                        inString = false
                    }
                } else {
                    if (ch == '"') {
                        inString = true
                    } else if (ch == '[') {
                        depth++
                    } else if (ch == ']') {
                        depth--
                        if (depth == 0) {
                            end = i
                            break
                        }
                    }
                }
            }

            if (end != -1) {
                if (start > lastIdx) {
                    val txt = rawText.substring(lastIdx, start).trim()
                    if (txt.isNotEmpty()) {
                        blocks.add(PageBlock.Text(txt))
                    }
                }
                val tableToken = rawText.substring(start, end + 1)
                blocks.add(PageBlock.Table(tableToken))
                lastIdx = end + 1
                scanIdx = end + 1
            } else {
                scanIdx = start + 7
            }
        }
    }
    if (lastIdx < rawText.length) {
        val txt = rawText.substring(lastIdx).trim()
        if (txt.isNotEmpty()) {
            blocks.add(PageBlock.Text(txt))
        }
    }
    return if (blocks.isEmpty()) listOf(PageBlock.Text(rawText)) else blocks
}

data class TableParsedInfo(
    val headers: List<String>,
    val rows: List<List<String>>,
    val caption: String,
    val isContinuation: Boolean
)

private val tableJsonCache = android.util.LruCache<String, TableParsedInfo>(64)

@Composable
fun ReaderTableCard(
    tableToken: String,
    modifier: Modifier = Modifier,
    isPagedMode: Boolean = true
) {
    val jsonStr = tableToken.removePrefix("[TABLE:").removeSuffix("]")
    val parsedData = remember(jsonStr) {
        val cached = tableJsonCache.get(jsonStr)
        if (cached != null) return@remember cached
        try {
            val obj = JSONObject(jsonStr)
            val cap = obj.optString("caption").ifBlank { obj.optString("title") }
            val cont = obj.optBoolean("isContinuation", false)
            val hArr = obj.optJSONArray("headers")
            val hList = if (hArr != null) List(hArr.length()) { i -> hArr.getString(i) } else emptyList()
            val rArr = obj.optJSONArray("rows")
            val rList = if (rArr != null) {
                List(rArr.length()) { r ->
                    val rowArr = rArr.getJSONArray(r)
                    List(rowArr.length()) { c -> rowArr.getString(c) }
                }
            } else emptyList()
            val info = TableParsedInfo(hList, rList, cap, cont)
            tableJsonCache.put(jsonStr, info)
            info
        } catch (_: Exception) {
            TableParsedInfo(emptyList(), emptyList(), "", false)
        }
    }

    val headers = parsedData.headers
    val rows = parsedData.rows
    val caption = parsedData.caption
    val isContinuation = parsedData.isContinuation

    if (headers.isEmpty() && rows.isEmpty()) return

    val totalCols = remember(headers, rows) {
        maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
    }
    if (totalCols == 0) return

    val isEquationTable = headers.isEmpty() && rows.isNotEmpty() && rows.all { r ->
        r.any { cell -> cell.contains("__IMG_TOKEN_START__") || cell.contains("[IMG:") || cell.contains("[INLINE_IMG:") }
    }

    val colWidths = remember(headers, rows, totalCols, isEquationTable) {
        (0 until totalCols).map { c ->
            val hasImgInCol = rows.any { it.getOrNull(c)?.let { cell -> cell.contains("__IMG_TOKEN_START__") || cell.contains("[IMG:") || cell.contains("[INLINE_IMG:") } == true }
            val maxLen = maxOf(
                headers.getOrNull(c)?.length ?: 0,
                rows.maxOfOrNull { it.getOrNull(c)?.length ?: 0 } ?: 0
            )
            if (isEquationTable) {
                if (hasImgInCol) {
                    540.dp
                } else {
                    maxOf(48.dp, minOf(80.dp, (maxLen * 10.0f).dp))
                }
            } else if (totalCols == 1) {
                320.dp
            } else if (totalCols == 2) {
                if (hasImgInCol) {
                    320.dp
                } else if (maxLen <= 8) {
                    maxOf(52.dp, (maxLen * 8.5f).dp)
                } else {
                    maxOf(140.dp, minOf(300.dp, (maxLen * 6.0f).dp))
                }
            } else {
                if (hasImgInCol) {
                    280.dp
                } else if (maxLen <= 8) {
                    maxOf(56.dp, (maxLen * 7.5f).dp)
                } else {
                    maxOf(130.dp, minOf(300.dp, (maxLen * 6.2f).dp))
                }
            }
        }
    }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxTableHeight = if (isPagedMode) (screenHeight * 0.62f).coerceAtLeast(200.dp) else androidx.compose.ui.unit.Dp.Unspecified

    val isSingleColCentered = headers.isEmpty() && totalCols == 1 && !isEquationTable
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)

    val titleText = when {
        caption.isNotBlank() && isContinuation -> "$caption (cont.)"
        caption.isNotBlank() -> caption
        isContinuation -> "Table (cont.)"
        else -> ""
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        if (titleText.isNotBlank()) {
            Text(
                text = titleText,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            ),
            border = BorderStroke(1.dp, outlineColor)
        ) {
        val vScrollState = rememberScrollState()
        val hScrollState = rememberScrollState()

        val tableContainerModifier = if (isPagedMode) {
            Modifier
                .heightIn(max = maxTableHeight)
                .verticalScroll(vScrollState)
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(tableContainerModifier)
                .padding(vertical = 6.dp, horizontal = 8.dp),
            contentAlignment = if (isSingleColCentered) Alignment.Center else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isSingleColCentered) Modifier else Modifier.horizontalScroll(hScrollState)),
                contentAlignment = if (isSingleColCentered) Alignment.Center else Alignment.CenterStart
            ) {
                Column(
                    modifier = if (isSingleColCentered) Modifier.fillMaxWidth() else Modifier.wrapContentWidth(),
                    horizontalAlignment = if (isSingleColCentered) Alignment.CenterHorizontally else Alignment.Start
                ) {
                    if (headers.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f), shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                            verticalAlignment = Alignment.Top
                        ) {
                            for (c in 0 until totalCols) {
                                val headerText = headers.getOrNull(c) ?: ""
                                Box(
                                    modifier = Modifier
                                        .width(colWidths[c])
                                        .fillMaxHeight()
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.TopStart
                                ) {
                                    TableCellContent(
                                        text = headerText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (c < totalCols - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(0.8.dp)
                                            .fillMaxHeight()
                                            .background(outlineColor)
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(outlineColor)
                        )
                    }

                    rows.forEachIndexed { rowIdx, rowCells ->
                        val rowBg = if (!isSingleColCentered && rowIdx % 2 == 1) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                        } else {
                            Color.Transparent
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .background(rowBg),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (isSingleColCentered) Arrangement.Center else Arrangement.Start
                        ) {
                            for (c in 0 until totalCols) {
                                val cellText = rowCells.getOrNull(c) ?: ""
                                val cellAlignment = when {
                                    isEquationTable && c == 1 -> Alignment.CenterEnd
                                    isEquationTable || isSingleColCentered -> Alignment.Center
                                    else -> Alignment.TopStart
                                }
                                Box(
                                    modifier = Modifier
                                        .then(if (isSingleColCentered) Modifier.fillMaxWidth() else Modifier.width(colWidths[c]))
                                        .fillMaxHeight()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = cellAlignment
                                ) {
                                    TableCellContent(
                                        text = cellText,
                                        style = if (isSingleColCentered || isEquationTable) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Normal,
                                        letterSpacing = if (isSingleColCentered) 1.5.sp else 0.sp,
                                        textAlign = if (isSingleColCentered) TextAlign.Center else if (isEquationTable && c == 1) TextAlign.End else TextAlign.Start,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                                        modifier = if (isSingleColCentered) Modifier.fillMaxWidth() else Modifier
                                    )
                                }
                                if (!isSingleColCentered && c < totalCols - 1) {
                                    val isEquationColDivider = isEquationTable && c == 0 && totalCols == 2
                                    if (!isEquationColDivider) {
                                        Box(
                                            modifier = Modifier
                                                .width(0.8.dp)
                                                .fillMaxHeight()
                                                .background(outlineColor)
                                        )
                                    }
                                }
                            }
                        }
                        if (!isSingleColCentered && !isEquationTable && rowIdx < rows.size - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.6.dp)
                                    .background(outlineColor.copy(alpha = 0.40f))
                            )
                        }
                    }
                }
            }
        }
    }
}
}

private val tableCellCache = android.util.LruCache<String, androidx.compose.ui.text.AnnotatedString>(256)

@Composable
private fun TableCellContent(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = 0.sp,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val imgMatch = remember(text) {
        "__IMG_TOKEN_START__(.*?)__IMG_TOKEN_END__|\\[IMG:([^\\]]+)\\]|\\[INLINE_IMG:([^\\]]+)\\]".toRegex().find(text)
    }

    if (imgMatch != null) {
        val imgPath = imgMatch.groupValues[1].ifBlank { imgMatch.groupValues[2].ifBlank { imgMatch.groupValues[3] } }.trim()
        val remainingText = remember(text, imgMatch) {
            text.replace(imgMatch.value, "").trim()
        }
        val bmp = io.github.tasmirz.lumina.ui.components.rememberBookImage(imgPath, isInlineImage = true)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = modifier.fillMaxWidth()
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Equation",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .then(if (remainingText.isNotBlank()) Modifier.weight(1f, fill = false) else Modifier.fillMaxWidth())
                        .heightIn(min = 48.dp, max = 220.dp)
                        .padding(end = if (remainingText.isNotBlank()) 6.dp else 0.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .width(80.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                )
            }
            if (remainingText.isNotBlank()) {
                val cleanedRemaining = remember(remainingText) { io.github.tasmirz.lumina.data.EpubParser.cleanLatexMath(remainingText) }
                val parsed = remember(cleanedRemaining) { parseTextFormatting(cleanedRemaining) }
                val baseFontSize = style.fontSize
                val annot = remember(cleanedRemaining, baseFontSize, color) {
                    val cacheKey = "$cleanedRemaining|${baseFontSize.value}|${color.value}"
                    tableCellCache.get(cacheKey) ?: run {
                        val built = if (parsed.spans.isNotEmpty()) {
                            buildAnnotatedString {
                                append(parsed.plainText)
                                parsed.spans.forEach { span ->
                                    if (span.isSubscript) {
                                        addStyle(
                                            SpanStyle(
                                                baselineShift = BaselineShift.Subscript,
                                                fontSize = if (baseFontSize.isSpecified) baseFontSize * 0.75f else 9.5.sp
                                            ),
                                            span.start,
                                            span.end
                                        )
                                    } else if (span.isSuperscript) {
                                        addStyle(
                                            SpanStyle(
                                                baselineShift = BaselineShift.Superscript,
                                                fontSize = if (baseFontSize.isSpecified) baseFontSize * 0.75f else 9.5.sp
                                            ),
                                            span.start,
                                            span.end
                                        )
                                    }
                                }
                            }
                        } else {
                            androidx.compose.ui.text.AnnotatedString(cleanedRemaining)
                        }
                        tableCellCache.put(cacheKey, built)
                        built
                    }
                }
                Text(
                    text = annot,
                    style = style,
                    fontWeight = fontWeight,
                    letterSpacing = letterSpacing,
                    textAlign = textAlign,
                    color = color
                )
            }
        }
        return
    }

    val cleanedText = remember(text) {
        if (text.contains("\\") || text.contains("_{") || text.contains("^{")) {
            io.github.tasmirz.lumina.data.EpubParser.cleanLatexMath(text)
        } else {
            text
        }
    }
    val parsed = remember(cleanedText) { parseTextFormatting(cleanedText) }
    val baseFontSize = style.fontSize
    val annot = remember(cleanedText, baseFontSize, color) {
        val cacheKey = "$cleanedText|${baseFontSize.value}|${color.value}"
        tableCellCache.get(cacheKey) ?: run {
            val built = if (parsed.spans.isNotEmpty()) {
                buildAnnotatedString {
                    append(parsed.plainText)
                    parsed.spans.forEach { span ->
                        if (span.isSubscript) {
                            addStyle(
                                SpanStyle(
                                    baselineShift = BaselineShift.Subscript,
                                    fontSize = if (baseFontSize.isSpecified) baseFontSize * 0.75f else 9.5.sp
                                ),
                                span.start,
                                span.end
                            )
                        } else if (span.isSuperscript) {
                            addStyle(
                                SpanStyle(
                                    baselineShift = BaselineShift.Superscript,
                                    fontSize = if (baseFontSize.isSpecified) baseFontSize * 0.75f else 9.5.sp
                                ),
                                span.start,
                                span.end
                            )
                        }
                    }
                }
            } else {
                androidx.compose.ui.text.AnnotatedString(cleanedText)
            }
            tableCellCache.put(cacheKey, built)
            built
        }
    }

    Text(
        text = annot,
        style = style,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        textAlign = textAlign,
        color = color,
        modifier = modifier
    )
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
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.20f))
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
            .zIndex(if (isPinching || scale > 1.05f) 500f else 0f)
            .padding(vertical = if (isPinching || scale > 1.05f) 0.dp else 16.dp)
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
                        if (scale >= 1.30f && bitmap != null) {
                            scale = 1f
                            rotation = 0f
                            offset = Offset.Zero
                            onOpenLightbox(bitmap, imgPath)
                        } else {
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
            val isSmallIcon = bitmap.width < 100 && bitmap.height < 100
            val isTallPortrait = !isSmallIcon && (bitmap.height.toFloat() / bitmap.width.toFloat() > 1.45f)
            val density = LocalDensity.current
            val baseModifier = when {
                isSmallIcon -> {
                    val intrinsicWidthDp = with(density) { bitmap.width.toDp() }
                    val intrinsicHeightDp = with(density) { bitmap.height.toDp() }
                    Modifier
                        .widthIn(max = intrinsicWidthDp.coerceAtMost(100.dp).coerceAtLeast(24.dp))
                        .heightIn(max = intrinsicHeightDp.coerceAtMost(100.dp).coerceAtLeast(24.dp))
                }
                isTallPortrait -> {
                    Modifier
                        .fillMaxWidth(widthFraction)
                        .heightIn(max = 500.dp)
                }
                else -> {
                    Modifier
                        .fillMaxWidth(1.0f)
                }
            }
            val finalImageModifier = if (scale > 1.02f || isPinching) {
                baseModifier
            } else {
                baseModifier.clip(RoundedCornerShape(if (isSmallIcon) 4.dp else 8.dp))
            }

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Illustration",
                contentScale = ContentScale.Fit,
                modifier = finalImageModifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = rotation
                        translationX = offset.x
                        translationY = offset.y
                        shadowElevation = 0f
                    }
            )
        } else {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(140.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Illustration",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        val name = imgPath.substringAfterLast('/').substringBeforeLast('.')
                        if (name.isNotBlank()) {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = name.replace('_', ' ').take(32),
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        }
    }
}
