package io.github.tasmirz.lumina.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.model.Bookmark
import io.github.tasmirz.lumina.model.HighlightColor
import io.github.tasmirz.lumina.util.AnnotatedTextCache

internal data class FormattedTextSpan(
    val start: Int,
    val end: Int,
    val isSubscript: Boolean = false,
    val isSuperscript: Boolean = false,
    val inlineImageId: String? = null
)

data class InlineImageItem(
    val id: String,
    val path: String
)

internal data class ParsedFormattedText(
    val plainText: String,
    val spans: List<FormattedTextSpan>,
    val inlineImages: List<InlineImageItem> = emptyList()
)

private val TAG_REGEX = "\\[(sub|sup)\\](.*?)\\[/\\1\\]|\\[INLINE_IMG:([^\\]]+)\\]".toRegex()

fun extractInlineImages(text: String): List<InlineImageItem> {
    if (!text.contains("[INLINE_IMG:")) return emptyList()
    val list = mutableListOf<InlineImageItem>()
    var count = 0
    "\\[INLINE_IMG:([^\\]]+)\\]".toRegex().findAll(text).forEach { m ->
        list.add(InlineImageItem(id = "inline_img_${count++}", path = m.groupValues[1]))
    }
    return list
}

internal fun parseTextFormatting(rawText: String): ParsedFormattedText {
    if (!rawText.contains("[sub]") && !rawText.contains("[sup]") && !rawText.contains("[INLINE_IMG:")) {
        return ParsedFormattedText(rawText, emptyList(), emptyList())
    }

    val sb = java.lang.StringBuilder(rawText.length)
    val spans = mutableListOf<FormattedTextSpan>()
    val inlineImages = mutableListOf<InlineImageItem>()
    var currentIndex = 0
    var imgCounter = 0
    var lastTokenWasInlineImg = false

    TAG_REGEX.findAll(rawText).forEach { matchResult ->
        val range = matchResult.range
        if (range.first > currentIndex) {
            var chunk = rawText.substring(currentIndex, range.first)
            if (lastTokenWasInlineImg && chunk.isNotEmpty()) {
                val trimmedLeading = chunk.trimStart()
                if (trimmedLeading.isNotEmpty() && trimmedLeading[0] in ",.:;?!)]}") {
                    // Punctuation immediately following inline math: strip space so punctuation
                    // binds directly to the inline symbol and TextAlign.Justify doesn't create wide gaps
                    chunk = trimmedLeading
                } else if (!chunk[0].isWhitespace() && chunk[0] !in ",.:;?!)]}") {
                    // Preceding inline image has no trailing space before next word: insert single space
                    chunk = " $chunk"
                } else if (chunk.startsWith("  ")) {
                    // Collapse multiple spaces down to a single space
                    chunk = chunk.replaceFirst("^[ \t]+".toRegex(), " ")
                }
            }
            sb.append(chunk)
            lastTokenWasInlineImg = false
        }
        val tagType = matchResult.groupValues[1]
        val imgPath = matchResult.groupValues[3]

        if (imgPath.isNotBlank()) {
            val imgId = "inline_img_${imgCounter++}"
            inlineImages.add(InlineImageItem(id = imgId, path = imgPath))

            // Ensure clean breathing room before the inline math/image symbol:
            if (sb.isNotEmpty()) {
                val lastChar = sb.last()
                if (!lastChar.isWhitespace() && lastChar != '(' && lastChar != '[' && lastChar != '{' && lastChar != '—' && lastChar != '–' && lastChar != '-') {
                    // Preceded immediately by a non-whitespace character (e.g. word or digit): ensure single space
                    sb.append(' ')
                } else if (lastChar == ' ') {
                    // Collapse multiple spaces down to a single space
                    while (sb.length > 1 && sb.last() == ' ' && sb[sb.length - 2] == ' ') {
                        sb.deleteCharAt(sb.length - 1)
                    }
                    // Collapse space after opening bracket like "( " -> "("
                    if (sb.length >= 2 && sb[sb.length - 2] in "([{" && sb.last() == ' ') {
                        sb.deleteCharAt(sb.length - 1)
                    }
                }
            }

            val start = sb.length
            sb.append("\uFFFC") // Object replacement character for inline content
            val end = sb.length
            spans.add(FormattedTextSpan(start, end, inlineImageId = imgId))
            lastTokenWasInlineImg = true
        } else {
            val content = matchResult.groupValues[2]
            val start = sb.length
            sb.append(content)
            val end = sb.length
            if (end > start) {
                spans.add(FormattedTextSpan(start, end, isSubscript = (tagType == "sub"), isSuperscript = (tagType == "sup")))
            }
            lastTokenWasInlineImg = false
        }
        currentIndex = range.last + 1
    }

    if (currentIndex < rawText.length) {
        var remaining = rawText.substring(currentIndex)
        if (lastTokenWasInlineImg && remaining.isNotEmpty()) {
            val trimmedLeading = remaining.trimStart()
            if (trimmedLeading.isNotEmpty() && trimmedLeading[0] in ",.:;?!)]}") {
                remaining = trimmedLeading
            } else if (!remaining[0].isWhitespace() && remaining[0] !in ",.:;?!)]}") {
                remaining = " $remaining"
            } else if (remaining.startsWith("  ")) {
                remaining = remaining.replaceFirst("^[ \t]+".toRegex(), " ")
            }
        }
        sb.append(remaining)
    }

    return ParsedFormattedText(sb.toString(), spans, inlineImages)
}

/**
 * Builds an [AnnotatedString] applying styles, drop cap, highlights, subscripts/superscripts,
 * and search match markings.
 * Employs LRU caching for performance on pure paragraphs.
 */
fun buildHighlightedAnnotatedString(
    text: String,
    matchingBookmarks: List<Bookmark>,
    onBookmarkClick: ((Bookmark) -> Unit)? = null,
    isDropCap: Boolean = false,
    dropCapFontFamily: FontFamily = FontFamily.Serif,
    dropCapFontSize: TextUnit = TextUnit.Unspecified,
    dropCapColor: Color = Color.Unspecified,
    baseFontFamily: FontFamily,
    baseFontSize: TextUnit,
    baseTextColor: Color,
    searchQuery: String = "",
    isActiveSearchMatch: Boolean = false
): AnnotatedString {
    // Fast path: cached lookup for standard paragraphs without highlights, drop caps, or search query
    if (!isDropCap && matchingBookmarks.isEmpty() && searchQuery.isBlank()) {
        val cacheKey = "${text.hashCode()}_${baseFontSize.value}_${baseFontFamily.hashCode()}_${baseTextColor.value}"
        val cached = AnnotatedTextCache.get(cacheKey)
        if (cached != null) return cached

        val parsed = parseTextFormatting(text)
        val res = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    fontFamily = baseFontFamily,
                    fontSize = baseFontSize,
                    color = baseTextColor
                )
            ) {
                append(parsed.plainText)
            }
            parsed.spans.forEach { span ->
                if (span.inlineImageId != null) {
                    addStringAnnotation(
                        tag = "androidx.compose.foundation.text.inlineContent",
                        annotation = span.inlineImageId,
                        start = span.start,
                        end = span.end
                    )
                } else {
                    val style = if (span.isSubscript) {
                        SpanStyle(
                            baselineShift = BaselineShift.Subscript,
                            fontSize = baseFontSize * 0.72f
                        )
                    } else {
                        SpanStyle(
                            baselineShift = BaselineShift.Superscript,
                            fontSize = baseFontSize * 0.72f
                        )
                    }
                    addStyle(style, start = span.start, end = span.end)
                }
            }
            val dividerLines = parsed.plainText.split("\n")
            var lineOffset = 0
            for (dLine in dividerLines) {
                val trimmed = dLine.trim()
                if (trimmed == "⁂" || trimmed == "* * *" || trimmed == "***" || trimmed == "---" || trimmed == "— ⁂ —" || trimmed == "• • •") {
                    val start = parsed.plainText.indexOf(dLine, lineOffset)
                    if (start >= 0) {
                        val end = start + dLine.length
                        addStyle(ParagraphStyle(textAlign = TextAlign.Center), start = start, end = end)
                    }
                }
                lineOffset += dLine.length + 1
            }
        }
        AnnotatedTextCache.put(cacheKey, res)
        return res
    }

    val parsed = parseTextFormatting(text)
    val plainText = parsed.plainText

    return buildAnnotatedString {
        if (isDropCap && plainText.length > 40 && !plainText.startsWith("[IMG:")) {
            val dropChar = plainText.take(1)
            val rest = plainText.drop(1)
            withStyle(
                SpanStyle(
                    fontFamily = dropCapFontFamily,
                    fontSize = dropCapFontSize,
                    fontWeight = FontWeight.Normal,
                    color = dropCapColor
                )
            ) {
                append(dropChar)
            }
            withStyle(
                SpanStyle(
                    fontFamily = baseFontFamily,
                    fontSize = baseFontSize,
                    color = baseTextColor
                )
            ) {
                append(rest)
            }
        } else {
            withStyle(
                SpanStyle(
                    fontFamily = baseFontFamily,
                    fontSize = baseFontSize,
                    color = baseTextColor
                )
            ) {
                append(plainText)
            }
        }

        // Apply subscript, superscript, and inline image spans
        parsed.spans.forEach { span ->
            if (span.inlineImageId != null) {
                addStringAnnotation(
                    tag = "androidx.compose.foundation.text.inlineContent",
                    annotation = span.inlineImageId,
                    start = span.start,
                    end = span.end
                )
            } else {
                val style = if (span.isSubscript) {
                    SpanStyle(
                        baselineShift = BaselineShift.Subscript,
                        fontSize = baseFontSize * 0.72f
                    )
                } else {
                    SpanStyle(
                        baselineShift = BaselineShift.Superscript,
                        fontSize = baseFontSize * 0.72f
                    )
                }
                addStyle(style, start = span.start, end = span.end)
            }
        }

        matchingBookmarks.forEach { bm ->
            if (bm.isHighlight) {
                val quote = bm.quote.trim()
                if (quote.isNotEmpty()) {
                    if (plainText.contains(quote, ignoreCase = true)) {
                        var searchIndex = 0
                        while (searchIndex < plainText.length) {
                            val idx = plainText.indexOf(quote, searchIndex, ignoreCase = true)
                            if (idx == -1) break
                            val end = (idx + quote.length).coerceAtMost(plainText.length)
                            val bg = when (bm.color) {
                                HighlightColor.GOLD -> Color(0x66F59E0B)
                                HighlightColor.ROSE -> Color(0x66F43F5E)
                                HighlightColor.SAGE -> Color(0x6610B981)
                            }
                            addStyle(
                                SpanStyle(
                                    background = bg,
                                    textDecoration = if (bm.note.isNotBlank()) TextDecoration.Underline else TextDecoration.None
                                ),
                                start = idx,
                                end = end
                            )
                            if (onBookmarkClick != null) {
                                addLink(
                                    clickable = LinkAnnotation.Clickable(
                                        tag = bm.id.toString(),
                                        linkInteractionListener = {
                                            onBookmarkClick(bm)
                                        }
                                    ),
                                    start = idx,
                                    end = end
                                )
                            }
                            searchIndex = end
                        }
                    }
                }
            }
        }

        // In-book search matches highlighting
        if (searchQuery.isNotBlank() && plainText.contains(searchQuery, ignoreCase = true)) {
            val q = searchQuery.trim()
            if (q.isNotEmpty()) {
                var searchIndex = 0
                while (searchIndex < plainText.length) {
                    val idx = plainText.indexOf(q, searchIndex, ignoreCase = true)
                    if (idx == -1) break
                    val end = (idx + q.length).coerceAtMost(plainText.length)
                    val matchBg = if (isActiveSearchMatch) Color(0xBBF59E0B) else Color(0x55FFC107)
                    addStyle(
                        SpanStyle(
                            background = matchBg,
                            fontWeight = FontWeight.Normal
                        ),
                        start = idx,
                        end = end
                    )
                    searchIndex = end
                }
            }
        }

        val dividerLines = plainText.split("\n")
        var lineOffset = 0
        for (dLine in dividerLines) {
            val trimmed = dLine.trim()
            if (trimmed == "⁂" || trimmed == "* * *" || trimmed == "***" || trimmed == "---" || trimmed == "— ⁂ —" || trimmed == "• • •") {
                val start = plainText.indexOf(dLine, lineOffset)
                if (start >= 0) {
                    val end = start + dLine.length
                    addStyle(ParagraphStyle(textAlign = TextAlign.Center), start = start, end = end)
                }
            }
            lineOffset += dLine.length + 1
        }
    }
}

@Composable
fun rememberInlineContentMap(
    inlineImages: List<InlineImageItem>,
    baseFontSize: Int
): Map<String, InlineTextContent> = rememberInlineContentMap(inlineImages, baseFontSize.toFloat())

@Composable
fun rememberInlineContentMap(
    inlineImages: List<InlineImageItem>,
    baseFontSize: Float
): Map<String, InlineTextContent> {
    if (inlineImages.isEmpty()) return emptyMap()
    return remember(inlineImages, baseFontSize) {
        inlineImages.associate { item ->
            val cleanPath = item.path.removePrefix("file://")
            val decodedPath = try { java.net.URLDecoder.decode(cleanPath, "UTF-8") } catch (_: Exception) { cleanPath }
            val file = if (java.io.File(cleanPath).exists()) java.io.File(cleanPath) else java.io.File(decodedPath)
            val absPath = file.absolutePath

            val bmp = try {
                if (file.exists() && file.length() > 0) {
                    val cached = io.github.tasmirz.lumina.ui.components.bitmapCache.get(absPath)
                        ?: io.github.tasmirz.lumina.ui.components.bitmapCache.get(item.path)
                    if (cached != null) {
                        cached
                    } else {
                        val decoded = io.github.tasmirz.lumina.ui.components.decodeSampledInlineBitmap(file, maxDimension = 360)
                        if (decoded != null) {
                            io.github.tasmirz.lumina.ui.components.bitmapCache.put(absPath, decoded)
                            io.github.tasmirz.lumina.ui.components.bitmapCache.put(item.path, decoded)
                            if (decoded.height > 0) {
                                val ar = (decoded.width.toFloat() / decoded.height.toFloat()).coerceIn(0.4f, 8.0f)
                                io.github.tasmirz.lumina.ui.components.inlineAspectRatios[absPath] = ar
                                io.github.tasmirz.lumina.ui.components.inlineAspectRatios[item.path] = ar
                            }
                        }
                        decoded
                    }
                } else null
            } catch (_: Exception) { null }

            val aspectRatio = if (bmp != null && bmp.height > 0) {
                (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.4f, 8.0f)
            } else {
                io.github.tasmirz.lumina.ui.components.inlineAspectRatios[absPath]
                    ?: io.github.tasmirz.lumina.ui.components.inlineAspectRatios[item.path]
                    ?: io.github.tasmirz.lumina.ui.components.inlineAspectRatios[cleanPath]
                    ?: 1.0f
            }
            val h = (baseFontSize * 1.0f).sp
            val rawWValue = (baseFontSize * 1.0f * aspectRatio).coerceAtMost(baseFontSize * 3.5f)
            val horizontalPaddingSp = (baseFontSize * 0.14f).coerceIn(2.5f, 5.0f)
            val placeholderWidth = (rawWValue + horizontalPaddingSp * 2).sp

            item.id to InlineTextContent(
                Placeholder(
                    width = placeholderWidth,
                    height = h,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                )
            ) {
                val paddingDp = with(LocalDensity.current) { horizontalPaddingSp.sp.toDp() }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Math",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = paddingDp)
                    )
                }
            }
        }
    }
}
