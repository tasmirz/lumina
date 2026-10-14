package io.github.tasmirz.lumina.ui.reader

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.data.BookRepository
import io.github.tasmirz.lumina.model.Book
import io.github.tasmirz.lumina.model.SceneMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class InBookSearchMode {
    PLAIN, SEMANTIC
}

@Composable
fun InBookSearchDialog(
    showDialog: Boolean,
    book: Book,
    repository: BookRepository?,
    onDismiss: () -> Unit,
    onJumpToMatch: (SceneMatch) -> Unit,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onSearchQueryChange: ((String) -> Unit)? = null,
    onSearchResultsUpdate: ((List<SceneMatch>, Int) -> Unit)? = null
) {
    val context = LocalContext.current
    var inBookSearchQuery by rememberSaveable { mutableStateOf(searchQuery) }
    var inBookSearchMode by rememberSaveable { mutableStateOf(InBookSearchMode.PLAIN) }
    var inBookSearchResults by remember { mutableStateOf<List<SceneMatch>>(emptyList()) }
    var inBookCurrentMatchIndex by rememberSaveable { mutableIntStateOf(0) }
    var isSearchingInBook by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(showDialog) {
        if (showDialog) {
            if (searchQuery.isNotEmpty() && inBookSearchQuery != searchQuery) {
                inBookSearchQuery = searchQuery
            }
            delay(150)
            runCatching { focusRequester.requestFocus() }
        } else {
            keyboardController?.hide()
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery != inBookSearchQuery) {
            inBookSearchQuery = searchQuery
        }
    }

    LaunchedEffect(inBookSearchQuery, inBookSearchMode) {
        val q = inBookSearchQuery.trim()
        if (q.length < 2) {
            inBookSearchResults = emptyList()
            inBookCurrentMatchIndex = 0
            isSearchingInBook = false
            onSearchResultsUpdate?.invoke(emptyList(), 0)
            return@LaunchedEffect
        }
        isSearchingInBook = true
        delay(if (inBookSearchMode == InBookSearchMode.PLAIN) 250 else 300)
        val results = withContext(Dispatchers.IO) {
            if (inBookSearchMode == InBookSearchMode.SEMANTIC) {
                val semanticStopwords = setOf(
                    "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
                    "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but",
                    "by", "can", "could", "did", "do", "does", "doing", "down", "during", "each", "few", "for",
                    "from", "further", "had", "has", "have", "having", "he", "her", "here", "hers", "herself",
                    "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it", "its", "itself", "just",
                    "me", "more", "most", "my", "myself", "no", "nor", "not", "now", "of", "off", "on", "once",
                    "only", "or", "other", "our", "ours", "ourselves", "out", "over", "own", "same", "she", "should",
                    "so", "some", "such", "than", "that", "the", "their", "theirs", "them", "themselves", "then",
                    "there", "these", "they", "this", "those", "through", "to", "too", "under", "until", "up",
                    "very", "was", "we", "were", "what", "when", "where", "which", "while", "who", "whom", "why",
                    "with", "would", "you", "your", "yours", "yourself", "yourselves", "find", "scene", "jump",
                    "show", "tell", "book", "chapter"
                )
                val ftsResults = repository?.searchScenes(book.id, q) ?: emptyList()
                val semanticResults = mutableListOf<SceneMatch>()
                val seenKeys = mutableSetOf<String>()

                ftsResults.forEach {
                    seenKeys.add("${it.chapterIndex}-${it.paragraphIndex}")
                    semanticResults.add(it)
                }

                val cleanTokens = q.lowercase().split(Regex("\\W+"))
                    .map { it.trim() }
                    .filter { it.length >= 2 && !semanticStopwords.contains(it) }
                val searchTokens = if (cleanTokens.isNotEmpty()) cleanTokens else listOf(q.lowercase().trim())

                data class ScoredMatch(val match: SceneMatch, val score: Int)
                val scoredList = mutableListOf<ScoredMatch>()

                for ((cIdx, chap) in book.chapters.withIndex()) {
                    val chapTitleLower = chap.title.lowercase()
                    val chapTitleMatches = searchTokens.count { chapTitleLower.contains(it) }

                    for ((pIdx, para) in chap.paragraphs.withIndex()) {
                        val key = "$cIdx-$pIdx"
                        if (seenKeys.contains(key) || para.startsWith("[IMG:") || para.isBlank()) continue

                        val paraLower = para.lowercase()
                        var matchedTokensCount = 0
                        var totalTokenOccurrences = 0
                        var firstMatchPos = -1

                        for (token in searchTokens) {
                            val idx = paraLower.indexOf(token)
                            if (idx != -1) {
                                matchedTokensCount++
                                if (firstMatchPos == -1 || idx < firstMatchPos) {
                                    firstMatchPos = idx
                                }
                                val isWordBoundary = (idx == 0 || !paraLower[idx - 1].isLetterOrDigit())
                                if (isWordBoundary) totalTokenOccurrences += 2 else totalTokenOccurrences += 1
                            } else if (token.length >= 4) {
                                val stem = token.take(token.length - 2)
                                val stemIdx = paraLower.indexOf(stem)
                                if (stemIdx != -1) {
                                    matchedTokensCount++
                                    if (firstMatchPos == -1 || stemIdx < firstMatchPos) {
                                        firstMatchPos = stemIdx
                                    }
                                    totalTokenOccurrences += 1
                                }
                            }
                        }

                        if (matchedTokensCount > 0) {
                            var score = matchedTokensCount * 40 + totalTokenOccurrences * 10 + chapTitleMatches * 25
                            if (matchedTokensCount == searchTokens.size) {
                                score += 100
                            }

                            val start = maxOf(0, firstMatchPos - 35)
                            val end = minOf(para.length, start + 110)
                            val snippet = (if (start > 0) "..." else "") +
                                    para.substring(start, end).trim() +
                                    (if (end < para.length) "..." else "")

                            scoredList.add(ScoredMatch(SceneMatch(book.id, cIdx, chap.title, pIdx, snippet), score))
                        }
                    }
                }

                scoredList.sortByDescending { it.score }
                for (item in scoredList) {
                    if (semanticResults.size >= 35) break
                    val key = "${item.match.chapterIndex}-${item.match.paragraphIndex}"
                    if (seenKeys.add(key)) {
                        semanticResults.add(item.match)
                    }
                }

                // Expand AI search to characters list and lore
                val allCharacters = repository?.getCharactersForBook(book.id) ?: emptyList()
                val characterMatches = mutableListOf<SceneMatch>()
                for (char in allCharacters) {
                    val combinedText = "${char.name} ${char.role} ${char.summary} ${char.keyEvents} ${char.aliases.joinToString(" ")}"
                    val combinedLower = combinedText.lowercase()
                    val matchesAny = searchTokens.any { combinedLower.contains(it) }
                    if (matchesAny) {
                        val chapIdx = if (char.firstAppearanceChapter.isNotBlank()) {
                            book.chapters.indexOfFirst {
                                it.title.equals(char.firstAppearanceChapter, ignoreCase = true) ||
                                it.title.contains(char.firstAppearanceChapter, ignoreCase = true)
                            }.coerceAtLeast(0)
                        } else 0
                        val chapTitle = book.chapters.getOrNull(chapIdx)?.title ?: "Character Guide"
                        val snippet = "Character: ${char.name} (${char.role}) — ${char.summary}"
                        val key = "char-${char.name.lowercase().trim()}"
                        if (seenKeys.add(key)) {
                            characterMatches.add(SceneMatch(book.id, chapIdx, "Character: ${char.name} • $chapTitle", 0, snippet))
                        }
                    }
                }

                val allLore = repository?.getLoreForBook(book.id) ?: emptyList()
                val loreMatches = mutableListOf<SceneMatch>()
                for (loreItem in allLore) {
                    val combinedText = "${loreItem.title} ${loreItem.category} ${loreItem.description} ${loreItem.keyFacts}"
                    val combinedLower = combinedText.lowercase()
                    val matchesAny = searchTokens.any { combinedLower.contains(it) }
                    if (matchesAny) {
                        val chapIdx = if (loreItem.firstAppearanceChapter.isNotBlank()) {
                            book.chapters.indexOfFirst {
                                it.title.equals(loreItem.firstAppearanceChapter, ignoreCase = true) ||
                                it.title.contains(loreItem.firstAppearanceChapter, ignoreCase = true)
                            }.coerceAtLeast(0)
                        } else 0
                        val chapTitle = book.chapters.getOrNull(chapIdx)?.title ?: "Book Lore"
                        val snippet = "Lore [${loreItem.category}]: ${loreItem.title} — ${loreItem.description}"
                        val key = "lore-${loreItem.title.lowercase().trim()}"
                        if (seenKeys.add(key)) {
                            loreMatches.add(SceneMatch(book.id, chapIdx, "Lore: ${loreItem.title} • $chapTitle", 0, snippet))
                        }
                    }
                }

                // Place Character & Lore cards at the top of semantic search results for direct discovery
                val combinedResults = ArrayList<SceneMatch>(characterMatches.size + loreMatches.size + semanticResults.size)
                combinedResults.addAll(characterMatches)
                combinedResults.addAll(loreMatches)
                combinedResults.addAll(semanticResults)
                combinedResults
            } else {
                val list = mutableListOf<SceneMatch>()
                for ((cIdx, chap) in book.chapters.withIndex()) {
                    for ((pIdx, para) in chap.paragraphs.withIndex()) {
                        if (para.contains(q, ignoreCase = true) && !para.startsWith("[IMG:")) {
                            val start = maxOf(0, para.indexOf(q, ignoreCase = true) - 25)
                            val end = minOf(para.length, start + 90)
                            val snippet = (if (start > 0) "..." else "") + para.substring(start, end).trim() + (if (end < para.length) "..." else "")
                            list.add(SceneMatch(book.id, cIdx, chap.title, pIdx, snippet))
                            if (list.size >= 100) break
                        }
                    }
                    if (list.size >= 100) break
                }
                list
            }
        }
        inBookSearchResults = results
        inBookCurrentMatchIndex = 0
        isSearchingInBook = false
        onSearchResultsUpdate?.invoke(results, 0)
        if (results.isNotEmpty()) {
            onJumpToMatch(results[0])
        }
    }

    val configuration = LocalConfiguration.current
    val layoutDirection = LocalLayoutDirection.current
    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()
    val topCutout = cutoutInsets.calculateTopPadding()
    val topStatusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Safe top inset to avoid camera punch-hole collision even in edge-to-edge / fullscreen mode
    val topSafeInset = maxOf(topCutout, topStatusBar, 36.dp) + 6.dp
    val startCutout = cutoutInsets.calculateStartPadding(layoutDirection)
    val endCutout = cutoutInsets.calculateEndPadding(layoutDirection)
    val navBarInsets = WindowInsets.navigationBars.asPaddingValues()
    val navStart = navBarInsets.calculateStartPadding(layoutDirection)
    val navEnd = navBarInsets.calculateEndPadding(layoutDirection)
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val horizontalSafeStart = maxOf(startCutout, navStart, if (isLandscape) 24.dp else 10.dp)
    val horizontalSafeEnd = maxOf(endCutout, navEnd, if (isLandscape) 44.dp else 10.dp)
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    AnimatedVisibility(
        visible = showDialog,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
            .padding(
                top = topSafeInset,
                start = horizontalSafeStart,
                end = horizontalSafeEnd,
                bottom = 6.dp
            )
            .fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // One-liner Search Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 52.dp)
                        .padding(start = 8.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Mode Toggle Button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (inBookSearchMode == InBookSearchMode.SEMANTIC) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                inBookSearchMode = if (inBookSearchMode == InBookSearchMode.PLAIN) {
                                    InBookSearchMode.SEMANTIC
                                } else {
                                    InBookSearchMode.PLAIN
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = if (inBookSearchMode == InBookSearchMode.SEMANTIC) {
                                    Icons.Default.AutoAwesome
                                } else {
                                    Icons.Default.Search
                                },
                                contentDescription = if (inBookSearchMode == InBookSearchMode.SEMANTIC) "Semantic Search (AI)" else "Text Search",
                                tint = if (inBookSearchMode == InBookSearchMode.SEMANTIC) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (inBookSearchMode == InBookSearchMode.SEMANTIC) "AI" else "Text",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = if (inBookSearchMode == InBookSearchMode.SEMANTIC) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 2. Search Text Input
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (inBookSearchQuery.isEmpty()) {
                            Text(
                                text = if (inBookSearchMode == InBookSearchMode.PLAIN) "Find in book..." else "Search themes...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = inBookSearchQuery,
                            onValueChange = {
                                inBookSearchQuery = it
                                onSearchQueryChange?.invoke(it)
                            },
                            singleLine = true,
                            maxLines = 1,
                            textStyle = TextStyle(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.SansSerif
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (inBookSearchMode == InBookSearchMode.PLAIN) ImeAction.Next else ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    if (inBookSearchResults.isNotEmpty()) {
                                        inBookCurrentMatchIndex = (inBookCurrentMatchIndex + 1) % inBookSearchResults.size
                                        onSearchResultsUpdate?.invoke(inBookSearchResults, inBookCurrentMatchIndex)
                                        onJumpToMatch(inBookSearchResults[inBookCurrentMatchIndex])
                                    }
                                },
                                onSearch = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    if (inBookSearchResults.isNotEmpty()) {
                                        onJumpToMatch(inBookSearchResults[inBookCurrentMatchIndex])
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }

                    // 3. Clear button
                    if (inBookSearchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                inBookSearchQuery = ""
                                inBookSearchResults = emptyList()
                                inBookCurrentMatchIndex = 0
                                onSearchQueryChange?.invoke("")
                                onSearchResultsUpdate?.invoke(emptyList(), 0)
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // 4. Regular / Plain Search controls
                    if (inBookSearchMode == InBookSearchMode.PLAIN && inBookSearchQuery.trim().length >= 2) {
                        val counterText = if (inBookSearchResults.isEmpty()) {
                            if (isSearchingInBook) "..." else "0/0"
                        } else {
                            "${(inBookCurrentMatchIndex + 1).coerceAtMost(inBookSearchResults.size)}/${inBookSearchResults.size}"
                        }
                        Text(
                            text = counterText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 3.dp),
                            maxLines = 1
                        )

                        IconButton(
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                if (inBookSearchResults.isNotEmpty()) {
                                    inBookCurrentMatchIndex = if (inBookCurrentMatchIndex <= 0) inBookSearchResults.size - 1 else inBookCurrentMatchIndex - 1
                                    onSearchResultsUpdate?.invoke(inBookSearchResults, inBookCurrentMatchIndex)
                                    onJumpToMatch(inBookSearchResults[inBookCurrentMatchIndex])
                                }
                            },
                            enabled = inBookSearchResults.isNotEmpty(),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Previous Match",
                                tint = if (inBookSearchResults.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                if (inBookSearchResults.isNotEmpty()) {
                                    inBookCurrentMatchIndex = (inBookCurrentMatchIndex + 1) % inBookSearchResults.size
                                    onSearchResultsUpdate?.invoke(inBookSearchResults, inBookCurrentMatchIndex)
                                    onJumpToMatch(inBookSearchResults[inBookCurrentMatchIndex])
                                }
                            },
                            enabled = inBookSearchResults.isNotEmpty(),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Next Match",
                                tint = if (inBookSearchResults.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 5. Close button (X)
                    IconButton(
                        onClick = {
                            inBookSearchQuery = ""
                            inBookSearchResults = emptyList()
                            inBookCurrentMatchIndex = 0
                            onSearchQueryChange?.invoke("")
                            onSearchResultsUpdate?.invoke(emptyList(), 0)
                            onDismiss()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 6. Semantic Search Results List
                if (inBookSearchMode == InBookSearchMode.SEMANTIC) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    if (isSearchingInBook) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    val screenHeight = configuration.screenHeightDp.dp
                    val maxListHeight = if (imeBottom > 0.dp) {
                        (screenHeight * 0.35f).coerceIn(160.dp, 260.dp)
                    } else {
                        (screenHeight * 0.5f).coerceIn(240.dp, 380.dp)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxListHeight)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (inBookSearchQuery.trim().length >= 2 && !isSearchingInBook) {
                            val count = inBookSearchResults.size
                            Text(
                                text = if (count == 1) "1 result found" else "$count results found",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        if (inBookSearchResults.isEmpty() && inBookSearchQuery.trim().length >= 2 && !isSearchingInBook) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching scenes or concepts found",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Light,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(inBookSearchResults) { match ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                                onJumpToMatch(match)
                                                Toast.makeText(context, "Navigated to ${match.chapterTitle}", Toast.LENGTH_SHORT).show()
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        ),
                                        border = BorderStroke(
                                            0.6.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = match.chapterTitle,
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = match.snippet,
                                                fontWeight = FontWeight.Light,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
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
}
