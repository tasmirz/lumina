package io.github.tasmirz.lumina.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import io.github.tasmirz.lumina.data.LuminaDownloadService
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.data.OnlineBookItem
import io.github.tasmirz.lumina.data.OnlineCatalogSource
import io.github.tasmirz.lumina.data.OnlineEpubService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookSheet(
    onDismiss: () -> Unit,
    onBookDownloaded: (OnlineBookItem) -> Unit,
    onBrowseFiles: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedSource by remember { mutableStateOf(OnlineCatalogSource.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<OnlineBookItem>>(OnlineEpubService.curatedClassics) }
    var isSearching by remember { mutableStateOf(false) }
    var downloadingBookId by remember { mutableStateOf<String?>(null) }
    val downloadStates by LuminaDownloadService.downloadStates.collectAsState()
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val searchCache = remember { mutableMapOf<String, List<OnlineBookItem>>() }

    fun runSearch(query: String, source: OnlineCatalogSource) {
        searchJob?.cancel()
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            searchResults = if (source == OnlineCatalogSource.ALL) OnlineEpubService.curatedClassics
                else OnlineEpubService.curatedClassics.filter { it.source == source }
            isSearching = false
            return
        }
        val cacheKey = "${source.name}::${cleanQuery.lowercase()}"
        val cached = searchCache[cacheKey]
        if (cached != null) {
            searchResults = cached
            isSearching = false
            return
        }
        searchJob = coroutineScope.launch {
            kotlinx.coroutines.delay(350)
            isSearching = true
            val results = OnlineEpubService.searchBooks(cleanQuery, source)
            searchResults = results
            if (results.isNotEmpty()) {
                searchCache[cacheKey] = results
            }
            isSearching = false
        }
    }

    LaunchedEffect(selectedSource) {
        runSearch(searchQuery, selectedSource)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
        ) {
            // Search Input Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    runSearch(it, selectedSource)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                placeholder = {
                    Text(
                        "Search books or authors...",
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            runSearch("", selectedSource)
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.secondary
                )
            )

            // Source Filter Chips (Horizontal Scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OnlineCatalogSource.entries.forEach { source ->
                    val isSelected = selectedSource == source
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSource = source },
                        label = {
                            Text(
                                text = source.displayName,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // Results List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isSearching) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .padding(bottom = 6.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    }

                    if (searchResults.isEmpty() && !isSearching) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No books found. Try another query or source.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(searchResults, key = { it.id }) { book ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Book Cover Thumbnail
                                    BookCoverImage(
                                        source = book.coverUrl,
                                        titleFallback = book.title,
                                        authorFallback = book.author,
                                        modifier = Modifier
                                            .size(width = 46.dp, height = 66.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Details
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = book.title,
                                            fontFamily = FontFamily.Serif,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = book.author,
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        // Tag badge
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = book.tag.ifBlank { book.source.displayName.uppercase() },
                                                fontFamily = FontFamily.SansSerif,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Normal,
                                                letterSpacing = 0.5.sp,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        val dState = downloadStates[book.id]
                                        val isFailed = dState?.isFailed == true
                                        val failureReason = dState?.errorMessage
                                        if (isFailed && !failureReason.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = failureReason,
                                                fontFamily = FontFamily.SansSerif,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.error,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // 1-Tap Download Button with Live State Feedback
                                    val dState = downloadStates[book.id]
                                    val isDownloading = (downloadingBookId == book.id) || (dState != null && !dState.isComplete && !dState.isFailed)
                                    val isDownloaded = dState?.isComplete == true
                                    val isFailed = dState?.isFailed == true

                                    IconButton(
                                        onClick = {
                                            if (!isDownloading && !isDownloaded) {
                                                downloadingBookId = book.id
                                                onBookDownloaded(book)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isFailed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                                    isDownloaded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                }
                                            )
                                    ) {
                                        when {
                                            isDownloading -> {
                                                val progress = dState?.progress ?: 0
                                                if (progress > 0) {
                                                    CircularProgressIndicator(
                                                        progress = { progress / 100f },
                                                        modifier = Modifier.size(20.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                } else {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                            }
                                            isDownloaded -> {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Downloaded",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            isFailed -> {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    contentDescription = "Retry Download",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            else -> {
                                                Icon(
                                                    Icons.Default.Download,
                                                    contentDescription = "Download",
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(20.dp)
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

            // Bottom Action: Upload from Device Button (Matching Stitch Design)
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onBrowseFiles()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Icon(
                    Icons.Default.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Upload from device",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
