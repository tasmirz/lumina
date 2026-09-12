package io.github.tasmirz.lumina.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import io.github.tasmirz.lumina.model.Bookmark
import io.github.tasmirz.lumina.model.HighlightColor
import io.github.tasmirz.lumina.util.AnnotatedTextCache

/**
 * Builds an [AnnotatedString] applying styles, drop cap, highlights, and search match markings.
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
        val res = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    fontFamily = baseFontFamily,
                    fontSize = baseFontSize,
                    color = baseTextColor
                )
            ) {
                append(text)
            }
        }
        AnnotatedTextCache.put(cacheKey, res)
        return res
    }

    return buildAnnotatedString {
        if (isDropCap && text.length > 40 && !text.startsWith("[IMG:")) {
            val dropChar = text.take(1)
            val rest = text.drop(1)
            withStyle(
                SpanStyle(
                    fontFamily = dropCapFontFamily,
                    fontSize = dropCapFontSize,
                    fontWeight = FontWeight.Medium,
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
                append(text)
            }
        }

        matchingBookmarks.forEach { bm ->
            if (bm.isHighlight) {
                val quote = bm.quote.trim()
                if (quote.isNotEmpty()) {
                    if (text.contains(quote, ignoreCase = true)) {
                        var searchIndex = 0
                        while (searchIndex < text.length) {
                            val idx = text.indexOf(quote, searchIndex, ignoreCase = true)
                            if (idx == -1) break
                            val end = (idx + quote.length).coerceAtMost(text.length)
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
        if (searchQuery.isNotBlank() && text.contains(searchQuery, ignoreCase = true)) {
            val q = searchQuery.trim()
            if (q.isNotEmpty()) {
                var searchIndex = 0
                while (searchIndex < text.length) {
                    val idx = text.indexOf(q, searchIndex, ignoreCase = true)
                    if (idx == -1) break
                    val end = (idx + q.length).coerceAtMost(text.length)
                    val matchBg = if (isActiveSearchMatch) Color(0xBBF59E0B) else Color(0x55FFC107)
                    addStyle(
                        SpanStyle(
                            background = matchBg,
                            fontWeight = if (isActiveSearchMatch) FontWeight.Bold else FontWeight.Normal
                        ),
                        start = idx,
                        end = end
                    )
                    searchIndex = end
                }
            }
        }
    }
}
