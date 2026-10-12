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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
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

private val PRECOMPILED_CODE_KEYWORDS = listOf(
    Regex("""\bimport\s+[a-zA-Z_]"""),
    Regex("""\bfrom\s+[a-zA-Z_.]+\s+import\b"""),
    Regex("""\bclass\s+[a-zA-Z_][a-zA-Z0-9_]*\s*[:\(]"""),
    Regex("""\bdef\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\("""),
    Regex("""\breturn\b"""),
    Regex("""\btorch\."""),
    Regex("""\bnn\.Module\b"""),
    Regex("""\bself\."""),
    Regex("""\bprint\s*\("""),
    Regex("""\boptimizer\."""),
    Regex("""\bloss\."""),
    Regex("""\bnp\."""),
    Regex("""\brange\s*\("""),
    Regex("""\blen\s*\("""),
    Regex("""\bitertools\."""),
    Regex("""\brandom\."""),
    Regex("""#\s+.*"""),
    Regex("""\bfor\s+[a-zA-Z_][a-zA-Z0-9_,\s]*\s+in\s+"""),
    Regex("""\bwhile\s+.*:"""),
    Regex("""\bif\s+.*:"""),
    Regex("""\belif\s+.*:"""),
    Regex("""\belse\s*:"""),
    Regex("""\bwith\s+.*:"""),
    Regex("""\btry\s*:"""),
    Regex("""\bexcept\s*.*:""")
)
private val ASSIGN_PATTERN = Regex("""\b[a-zA-Z_][a-zA-Z0-9_]*\s*=\s*[\[\{\(\"\'0-9a-zA-Z_]""")
private val isCodeBlockCache = android.util.LruCache<String, Boolean>(512)

fun isCodeBlock(text: String): Boolean {
    val s = text.trim()
    if (s.length < 15) return false
    if (s.startsWith("```")) return true
    val cached = isCodeBlockCache.get(s)
    if (cached != null) return cached

    var matchCount = 0
    for (kw in PRECOMPILED_CODE_KEYWORDS) {
        if (kw.containsMatchIn(s)) {
            matchCount++
            if (matchCount >= 2) {
                isCodeBlockCache.put(s, true)
                return true
            }
        }
    }

    val assignments = ASSIGN_PATTERN.findAll(s).count()
    val result = assignments >= 2
    isCodeBlockCache.put(s, result)
    return result
}

fun formatCodeBlock(text: String): String {
    val s = text.trim()
    if (s.count { it == '\n' } >= 3) return s

    val chars = s.toCharArray()
    val out = StringBuilder()
    var depth = 0
    var inQuote = false
    var quoteChar = ' '
    var inComment = false
    var i = 0

    val keywords = listOf(
        "class ", "def ", "return ", "import ", "from ", "for ", "while ",
        "if ", "elif ", "else:", "with ", "try:", "except ", "finally:",
        "super()."
    )

    val assignRegex = Regex("""^[a-zA-Z_][a-zA-Z0-9_]{0,30}\s*=\s*[^=]""")
    val callRegex = Regex("""^[a-zA-Z_][a-zA-Z0-9_]*\.[a-zA-Z_][a-zA-Z0-9_]*\s*\(""")

    while (i < chars.size) {
        val c = chars[i]

        if (inComment) {
            if (c == '\n') {
                inComment = false
                out.append(c)
                i++
                continue
            }
            if (i > 0 && chars[i - 1] == ' ') {
                val tail = s.substring(i)
                val hasResumeKw = keywords.any { tail.startsWith(it) }
                val hasResumeAssign = assignRegex.containsMatchIn(tail)
                if (hasResumeKw || hasResumeAssign) {
                    inComment = false
                    out.append('\n')
                    continue
                }
            }
            out.append(c)
            i++
            continue
        }

        if (inQuote) {
            out.append(c)
            if (c == '\\' && i + 1 < chars.size) {
                i++
                out.append(chars[i])
            } else if (c == quoteChar) {
                inQuote = false
            }
            i++
            continue
        }

        if (c == '#') {
            inComment = true
            if (out.isNotEmpty() && out.last() != '\n') {
                out.append('\n')
            }
            out.append(c)
            i++
            continue
        }

        if (c == '"' || c == '\'') {
            inQuote = true
            quoteChar = c
            out.append(c)
            i++
            continue
        }

        if (c == '(' || c == '[' || c == '{') {
            depth++
            out.append(c)
            i++
            continue
        }

        if (c == ')' || c == ']' || c == '}') {
            depth = maxOf(0, depth - 1)
            out.append(c)
            i++
            continue
        }

        if (depth == 0 && (c == ' ' || c == ':')) {
            var prevChar = ' '
            for (k in out.length - 1 downTo 0) {
                if (out[k] != ' ') {
                    prevChar = out[k]
                    break
                }
            }

            val isAfterOp = prevChar in "=+-*/,([{\\"

            if (!isAfterOp) {
                val tail = s.substring(i + 1).trimStart()

                val hasKw = keywords.any { tail.startsWith(it) }
                val hasAssign = assignRegex.containsMatchIn(tail) && !tail.startsWith("==")
                val hasCall = callRegex.containsMatchIn(tail) && (prevChar in ")]\"'0123456789" || prevChar.isLetterOrDigit())
                val isComp = tail.startsWith("for ") && (prevChar in "\"\'0123456789])}")

                if ((hasKw || hasAssign || hasCall) && !isComp) {
                    if (c == ':') {
                        out.append(":\n")
                        while (i + 1 < chars.size && chars[i + 1] == ' ') i++
                        i++
                        continue
                    } else {
                        out.append('\n')
                        i++
                        continue
                    }
                }
            }
        }

        out.append(c)
        i++
    }

    val rawLines = out.toString().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    val indented = mutableListOf<String>()
    var indentLevel = 0

    for (line in rawLines) {
        val curIndent = when {
            line.startsWith("elif ") || line.startsWith("else:") || line.startsWith("except ") || line.startsWith("finally:") -> maxOf(0, indentLevel - 1)
            line.startsWith("class ") -> 0
            line.startsWith("def ") -> minOf(indentLevel, 1)
            else -> indentLevel
        }

        indented.add("    ".repeat(curIndent) + line)

        indentLevel = when {
            line.endsWith(":") -> curIndent + 1
            line.startsWith("return ") || line == "pass" || line == "break" || line == "continue" -> maxOf(0, indentLevel - 1)
            else -> curIndent
        }
    }

    return indented.joinToString("\n")
}

@Composable
fun ReaderCodeCard(
    code: String,
    modifier: Modifier = Modifier
) {
    val formattedCode = remember(code) { formatCodeBlock(code) }
    val hScrollState = rememberScrollState()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(hScrollState)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = formattedCode,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)
            )
        }
    }
}

@Composable
fun ReaderTableCard(
    tableToken: String,
    modifier: Modifier = Modifier,
    isPagedMode: Boolean = true,
    imageFilterMode: io.github.tasmirz.lumina.model.ImageFilterMode = io.github.tasmirz.lumina.model.ImageFilterMode.ORIGINAL,
    onOpenLightbox: ((Bitmap, String) -> Unit)? = null
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

    val isEquationTable = caption.isBlank() && headers.isEmpty() && rows.isNotEmpty() && totalCols <= 2 && rows.any { r ->
        r.any { cell -> cell.contains("__IMG_TOKEN_START__") || cell.contains("[IMG:") || cell.contains("[INLINE_IMG:") }
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val usableWidth = (screenWidth - 24.dp).coerceAtLeast(280.dp)

    val colWidths = remember(headers, rows, totalCols, isEquationTable, screenWidth) {
        (0 until totalCols).map { c ->
            val hasImgInCol = rows.any { it.getOrNull(c)?.let { cell -> cell.contains("__IMG_TOKEN_START__") || cell.contains("[IMG:") || cell.contains("[INLINE_IMG:") } == true }
            val maxLen = maxOf(
                headers.getOrNull(c)?.length ?: 0,
                rows.maxOfOrNull { it.getOrNull(c)?.length ?: 0 } ?: 0
            )
            if (isEquationTable) {
                if (totalCols == 1) {
                    usableWidth
                } else {
                    if (hasImgInCol) {
                        (screenWidth - 80.dp).coerceAtLeast(240.dp)
                    } else {
                        maxOf(44.dp, minOf(72.dp, (maxLen * 9.0f).dp))
                    }
                }
            } else if (totalCols == 1) {
                320.dp
            } else if (totalCols == 2) {
                if (hasImgInCol) {
                    320.dp
                } else if (maxLen <= 8) {
                    maxOf(64.dp, (maxLen * 8.5f).dp)
                } else {
                    maxOf(150.dp, minOf(340.dp, (maxLen * 6.8f).dp))
                }
            } else {
                if (hasImgInCol) {
                    280.dp
                } else if (maxLen <= 8) {
                    maxOf(68.dp, (maxLen * 8.2f).dp)
                } else {
                    maxOf(140.dp, minOf(360.dp, (maxLen * 7.0f).dp))
                }
            }
        }
    }

    val totalTableWidth = remember(colWidths) { colWidths.fold(0.dp) { acc, w -> acc + w } }

    val isCodeTable = !isEquationTable && headers.isEmpty() && totalCols == 1 && run {
        val combinedText = rows.joinToString("\n") { it.getOrNull(0) ?: "" }
        isCodeBlock(combinedText)
    }

    val isSingleColCentered = headers.isEmpty() && totalCols == 1 && !isEquationTable && !isCodeTable && rows.all { r ->
        r.getOrNull(0)?.let { it.length < 50 && !isCodeBlock(it) && !it.contains("\n") } == true
    }

    val tableWidth = if (isSingleColCentered) usableWidth else maxOf(usableWidth, totalTableWidth)

    val maxTableHeight = if (isPagedMode && !isEquationTable) (screenHeight * 0.62f).coerceAtLeast(200.dp) else androidx.compose.ui.unit.Dp.Unspecified

    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)

    val titleText = when {
        caption.isNotBlank() && isContinuation -> "$caption (cont.)"
        caption.isNotBlank() -> caption
        isContinuation -> "Table (cont.)"
        else -> ""
    }

    if (isCodeTable) {
        val codeText = rows.joinToString("\n") { it.getOrNull(0) ?: "" }
        Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
            ReaderCodeCard(code = codeText)
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = if (isEquationTable) 6.dp else 10.dp)) {
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
                containerColor = if (isEquationTable) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            ),
            border = if (isEquationTable) null else BorderStroke(1.dp, outlineColor)
        ) {
            val vScrollState = rememberScrollState()
            val hScrollState = rememberScrollState()

            val tableContainerModifier = if (isPagedMode && !isEquationTable) {
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
                    .padding(
                        vertical = if (isEquationTable) 4.dp else 6.dp,
                        horizontal = if (isEquationTable) 0.dp else 8.dp
                    ),
                contentAlignment = if (isSingleColCentered || isEquationTable) Alignment.Center else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isSingleColCentered) Modifier else Modifier.horizontalScroll(hScrollState)),
                    contentAlignment = if (isSingleColCentered || isEquationTable) Alignment.Center else Alignment.CenterStart
                ) {
                    Column(
                        modifier = if (isSingleColCentered) Modifier.fillMaxWidth() else Modifier.width(tableWidth),
                        horizontalAlignment = if (isSingleColCentered || isEquationTable) Alignment.CenterHorizontally else Alignment.Start
                    ) {
                        if (headers.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .width(tableWidth)
                                    .drawBehind {
                                        drawLine(outlineColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                                    }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f), shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                                verticalAlignment = Alignment.Top
                            ) {
                                for (c in 0 until totalCols) {
                                    val headerText = headers.getOrNull(c) ?: ""
                                    val showColDivider = c < totalCols - 1
                                    Box(
                                        modifier = Modifier
                                            .width(colWidths[c])
                                            .then(
                                                if (showColDivider) {
                                                    Modifier.drawBehind {
                                                        drawLine(outlineColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 0.8.dp.toPx())
                                                    }
                                                } else Modifier
                                            )
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        contentAlignment = Alignment.TopStart
                                    ) {
                                        TableCellContent(
                                            text = headerText,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            imageFilterMode = imageFilterMode
                                        )
                                    }
                                }
                            }
                        }

                        rows.forEachIndexed { rowIdx, rowCells ->
                            val rowBg = if (!isSingleColCentered && !isEquationTable && rowIdx % 2 == 1) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                            } else {
                                Color.Transparent
                            }
                            val isLastRow = rowIdx == rows.size - 1
                            Row(
                                modifier = Modifier
                                    .width(tableWidth)
                                    .then(
                                        if (!isSingleColCentered && !isEquationTable && !isLastRow) {
                                            Modifier.drawBehind {
                                                drawLine(outlineColor.copy(alpha = 0.40f), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.6.dp.toPx())
                                            }
                                        } else Modifier
                                    )
                                    .background(rowBg),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = if (isSingleColCentered || (isEquationTable && totalCols == 1)) Arrangement.Center else Arrangement.Start
                            ) {
                                for (c in 0 until totalCols) {
                                    val cellText = rowCells.getOrNull(c) ?: ""
                                    val cellAlignment = when {
                                        isEquationTable && c == 1 -> Alignment.CenterEnd
                                        isEquationTable || isSingleColCentered -> Alignment.Center
                                        else -> Alignment.TopStart
                                    }
                                    val showColDivider = !isSingleColCentered && !isEquationTable && c < totalCols - 1 && !(isEquationTable && c == 0 && totalCols == 2)
                                    Box(
                                        modifier = Modifier
                                            .then(
                                                if (isSingleColCentered || (isEquationTable && totalCols == 1)) {
                                                    Modifier.fillMaxWidth()
                                                } else {
                                                    Modifier.width(colWidths[c])
                                                }
                                            )
                                            .then(
                                                if (showColDivider) {
                                                    Modifier.drawBehind {
                                                        drawLine(outlineColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 0.8.dp.toPx())
                                                    }
                                                } else Modifier
                                            )
                                            .padding(
                                                horizontal = if (isEquationTable) 4.dp else 10.dp,
                                                vertical = if (isEquationTable) 4.dp else 8.dp
                                            ),
                                        contentAlignment = cellAlignment
                                    ) {
                                         val cellIsCode = isCodeBlock(cellText)
                                        TableCellContent(
                                            text = cellText,
                                            style = if (isSingleColCentered || isEquationTable) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Normal,
                                            letterSpacing = if (isSingleColCentered && !cellIsCode) 1.5.sp else 0.sp,
                                            textAlign = if (cellIsCode) TextAlign.Start else if (isSingleColCentered || (isEquationTable && totalCols == 1)) TextAlign.Center else if (isEquationTable && c == 1) TextAlign.End else TextAlign.Start,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                                            modifier = if (isSingleColCentered || (isEquationTable && totalCols == 1)) Modifier.fillMaxWidth() else Modifier,
                                            isEquation = isEquationTable,
                                            imageFilterMode = imageFilterMode,
                                            onOpenLightbox = onOpenLightbox
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val tableCellCache = android.util.LruCache<String, androidx.compose.ui.text.AnnotatedString>(256)
private val TABLE_IMG_REGEX = "__IMG_TOKEN_START__(.*?)__IMG_TOKEN_END__|\\[IMG:([^\\]]+)\\]|\\[INLINE_IMG:([^\\]]+)\\]".toRegex()

@Composable
fun ReaderFormulaRow(
    bullet: String = "",
    imgPath: String,
    fontSize: Int,
    fontFamily: FontFamily = FontFamily.Default,
    imageFilterMode: io.github.tasmirz.lumina.model.ImageFilterMode = io.github.tasmirz.lumina.model.ImageFilterMode.ORIGINAL,
    onOpenLightbox: (Bitmap, String) -> Unit,
    modifier: Modifier = Modifier
) = ReaderFormulaRow(bullet, imgPath, fontSize.toFloat(), fontFamily, imageFilterMode, onOpenLightbox, modifier)

@Composable
fun ReaderFormulaRow(
    bullet: String = "",
    imgPath: String,
    fontSize: Float,
    fontFamily: FontFamily = FontFamily.Default,
    imageFilterMode: io.github.tasmirz.lumina.model.ImageFilterMode = io.github.tasmirz.lumina.model.ImageFilterMode.ORIGINAL,
    onOpenLightbox: (Bitmap, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanPath = remember(imgPath) { imgPath.removePrefix("file://").replace("\\/", "/") }
    val bitmap = io.github.tasmirz.lumina.ui.components.rememberBookImage(cleanPath, isInlineImage = true)
    val ar = if (bitmap != null && bitmap.height > 0) {
        bitmap.width.toFloat() / bitmap.height.toFloat()
    } else 3.5f

    // Calculate height so formula text matches surrounding body font size (~16sp)
    val fontScale = (fontSize / 16f).coerceIn(0.85f, 2.5f)
    val targetHeight = (28 * fontScale).dp.coerceIn(24.dp, 56.dp)
    val targetWidth = (targetHeight.value * ar).dp
    val colorFilter = io.github.tasmirz.lumina.ui.components.getColorFilterForMode(imageFilterMode)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (fontSize * 0.30f).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bullet.isNotBlank()) {
            Text(
                text = "$bullet  ",
                fontFamily = fontFamily,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .horizontalScroll(rememberScrollState())
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Mathematical Expression",
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .size(width = targetWidth, height = targetHeight)
                        .clickable { onOpenLightbox(bitmap, cleanPath) }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 140.dp, height = targetHeight)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            RoundedCornerShape(4.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun TableCellContent(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = 0.sp,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    isEquation: Boolean = false,
    imageFilterMode: io.github.tasmirz.lumina.model.ImageFilterMode = io.github.tasmirz.lumina.model.ImageFilterMode.ORIGINAL,
    onOpenLightbox: ((Bitmap, String) -> Unit)? = null
) {
    val imgMatch = remember(text) {
        TABLE_IMG_REGEX.find(text)
    }

    if (imgMatch != null) {
        val rawImgPath = imgMatch.groupValues[1].ifBlank { imgMatch.groupValues[2].ifBlank { imgMatch.groupValues[3] } }.trim()
        val imgPath = remember(rawImgPath) { rawImgPath.replace("\\/", "/") }
        val remainingText = remember(text, imgMatch) {
            text.replace(imgMatch.value, "").trim()
        }
        val bmp = io.github.tasmirz.lumina.ui.components.rememberBookImage(imgPath, isInlineImage = true)
        val colorFilter = io.github.tasmirz.lumina.ui.components.getColorFilterForMode(imageFilterMode)

        val fontScale = (style.fontSize.value / 16f).coerceIn(0.85f, 2.5f)
        val ar = if (bmp != null && bmp.height > 0) {
            bmp.width.toFloat() / bmp.height.toFloat()
        } else {
            3.5f
        }

        val targetHeight = when {
            ar > 7.0f -> (54 * fontScale).dp
            ar > 4.5f -> (72 * fontScale).dp
            ar > 2.2f -> (96 * fontScale).dp
            else -> (124 * fontScale).dp
        }
        val targetWidth = (targetHeight.value * ar).dp.coerceIn(60.dp, 580.dp)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isEquation) Arrangement.Center else Arrangement.Start,
            modifier = modifier.then(if (isEquation) Modifier.wrapContentSize() else Modifier.fillMaxWidth())
        ) {
            if (bmp != null) {
                val clickableModifier = if (onOpenLightbox != null) {
                    Modifier.clickable { onOpenLightbox(bmp, imgPath) }
                } else {
                    Modifier
                }
                if (isEquation) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Equation",
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter,
                        modifier = Modifier
                            .size(width = targetWidth, height = targetHeight)
                            .then(clickableModifier)
                    )
                } else {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Table image",
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter,
                        modifier = Modifier
                            .then(if (remainingText.isNotBlank()) Modifier.weight(1f, fill = false) else Modifier.fillMaxWidth())
                            .heightIn(min = 48.dp, max = 220.dp)
                            .padding(end = if (remainingText.isNotBlank()) 6.dp else 0.dp)
                            .then(clickableModifier)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .height(if (isEquation) targetHeight else 36.dp)
                        .width(if (isEquation) targetWidth else 80.dp)
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
    val isCode = remember(cleanedText) { isCodeBlock(cleanedText) }
    val textToRender = remember(cleanedText, isCode) {
        if (isCode) formatCodeBlock(cleanedText) else cleanedText
    }
    val parsed = remember(textToRender) { parseTextFormatting(textToRender) }
    val baseFontSize = style.fontSize
    val effectiveStyle = if (isCode) {
        style.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = if (style.fontSize.isSpecified) style.fontSize * 0.90f else 12.sp,
            letterSpacing = 0.sp
        )
    } else {
        style
    }
    val annot = remember(textToRender, baseFontSize, color, isCode) {
        val cacheKey = "$textToRender|${baseFontSize.value}|${color.value}|$isCode"
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
                androidx.compose.ui.text.AnnotatedString(textToRender)
            }
            tableCellCache.put(cacheKey, built)
            built
        }
    }

    Text(
        text = annot,
        style = effectiveStyle,
        fontWeight = fontWeight,
        letterSpacing = if (isCode) 0.sp else letterSpacing,
        textAlign = if (isCode) TextAlign.Start else textAlign,
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
    imageFilterMode: io.github.tasmirz.lumina.model.ImageFilterMode = io.github.tasmirz.lumina.model.ImageFilterMode.ORIGINAL,
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

            val colorFilter = io.github.tasmirz.lumina.ui.components.getColorFilterForMode(imageFilterMode)
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Illustration",
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
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
