package io.github.tasmirz.lumina.ui.library

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.tasmirz.lumina.data.BookRepository
import io.github.tasmirz.lumina.model.Book
import io.github.tasmirz.lumina.model.SceneMatch
import io.github.tasmirz.lumina.model.WishlistBook
import io.github.tasmirz.lumina.ui.components.BookContextMenuSheet
import io.github.tasmirz.lumina.ui.components.BookCoverImage

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    books: List<Book>,
    activeBook: Book? = null,
    repository: BookRepository? = null,
    wishlistBooks: List<WishlistBook> = emptyList(),
    completedBookIds: Set<String> = emptySet(),
    onToggleCompleted: (String) -> Unit = {},
    onBookSelect: (String) -> Unit,
    onNavigateToScene: ((bookId: String, chapterIndex: Int) -> Unit)? = null,
    onDeleteBook: (String) -> Unit = {},
    onResetProgress: (String) -> Unit = {},
    onAddEpubClick: () -> Unit,
    onAddToWishlist: (title: String, author: String, notes: String) -> Unit = { _, _, _ -> },
    onRemoveFromWishlist: (String) -> Unit = {},
    onShareReadingList: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeBackgroundTask by (repository?.activeBackgroundTask?.collectAsState(initial = null) ?: remember { mutableStateOf(null) })
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0: All, 1: Read, 2: Wishlist
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var deepSearchResults by remember { mutableStateOf<List<SceneMatch>>(emptyList()) }
    var isSearchingDeep by remember { mutableStateOf(false) }
    var showAddWishlistDialog by remember { mutableStateOf(false) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }
    var bookForContextMenu by remember { mutableStateOf<Book?>(null) }


    val filteredBooks = remember(books, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.trim().lowercase()
            books.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.author.contains(q, ignoreCase = true) ||
                it.id.contains(q, ignoreCase = true)
            }.sortedWith(compareByDescending<Book> {
                when {
                    it.title.equals(q, ignoreCase = true) -> 1000
                    it.title.startsWith(q, ignoreCase = true) -> 500
                    it.author.equals(q, ignoreCase = true) -> 400
                    it.title.contains(q, ignoreCase = true) -> 300
                    it.author.contains(q, ignoreCase = true) -> 200
                    else -> 0
                }
            }.thenBy { it.title })
        }
    }

    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length >= 2 && repository != null) {
            isSearchingDeep = true
            delay(300)
            deepSearchResults = try {
                withContext(Dispatchers.IO) {
                    repository.searchAllBooks(q)
                }
            } catch (e: Exception) {
                emptyList()
            }
            isSearchingDeep = false
        } else {
            deepSearchResults = emptyList()
            isSearchingDeep = false
        }
    }

    val readBooks = remember(books, completedBookIds) {
        books.filter { it.progress >= 95 || completedBookIds.contains(it.id) }
    }

    val configuration = LocalConfiguration.current
    // Use compact height (< 500dp) as the trigger for scrollable header,
    // rather than orientation — this correctly handles landscape phones,
    // split-screen, and any other short-height layout.
    val isLandscape = configuration.screenHeightDp < 500
    val gridMinSize = if (isLandscape) 124.dp else 140.dp


    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        if (searchQuery.isNotBlank()) {
            // Universal Search Results View
            if (isLandscape) {
                // Compact height: header scrolls inside the column
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "lib-header") {
                        LibraryHeader(
                            booksCount = books.size,
                            readCount = readBooks.size,
                            wishlistCount = wishlistBooks.size,
                            selectedTab = selectedTab,
                            onShareReadingList = onShareReadingList,
                            onAddBookClick = onAddEpubClick,
                            onAddWishlistClick = { showAddWishlistDialog = true },
                            showAddButton = books.isNotEmpty() || selectedTab == 2,
                            isLandscape = true
                        )
                    }
                    item(key = "lib-tabs") {
                        LibraryTabChips(
                            selectedTab = selectedTab,
                            onSelectTab = { selectedTab = it },
                            booksCount = books.size,
                            readCount = readBooks.size,
                            wishlistCount = wishlistBooks.size,
                            isLandscape = true
                        )
                    }
                    item(key = "lib-search") {
                        LibrarySearchBar(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            isLandscape = true
                        )
                    }

                if (isSearchingDeep) {
                    item(key = "search-progress") {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                if (filteredBooks.isNotEmpty()) {
                    item(key = "matching-books-title") {
                        Text(
                            text = "MATCHING BOOKS (${filteredBooks.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 2.dp)
                        )
                    }
                    items(filteredBooks, key = { "search-book-${it.id}" }) { book ->
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onBookSelect(book.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BookCoverImage(
                                        source = book.coverUrl,
                                        titleFallback = book.title,
                                        authorFallback = book.author,
                                        modifier = Modifier
                                            .width(44.dp)
                                            .height(64.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = book.title,
                                            fontFamily = FontFamily.Serif,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = book.author,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (book.id.isNotBlank() && (book.id.endsWith(".epub", ignoreCase = true) || book.id.contains("/"))) {
                                            Text(
                                                text = book.id.substringAfterLast('/'),
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (deepSearchResults.isNotEmpty()) {
                    item(key = "deep-matches-title") {
                        Text(
                            text = "DEEP SCENE MATCHES (${deepSearchResults.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp)
                        )
                    }
                    items(deepSearchResults, key = { "scene-${it.bookId}-${it.chapterIndex}-${it.paragraphIndex}" }) { match ->
                        val matchedBook = books.find { it.id == match.bookId }
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (onNavigateToScene != null) {
                                            onNavigateToScene(match.bookId, match.chapterIndex)
                                        } else {
                                            onBookSelect(match.bookId)
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = matchedBook?.title ?: "Book",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = match.chapterTitle.ifBlank { "Chapter ${match.chapterIndex + 1}" },
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = match.snippet,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = FontFamily.Serif,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (filteredBooks.isEmpty() && deepSearchResults.isEmpty() && !isSearchingDeep) {
                    item(key = "no-matches") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No matches found for \"$searchQuery\"",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
                // Portrait: header/tabs/search pinned at top, results scroll below
                Column(modifier = Modifier.fillMaxSize()) {
                    LibraryHeader(
                        booksCount = books.size,
                        readCount = readBooks.size,
                        wishlistCount = wishlistBooks.size,
                        selectedTab = selectedTab,
                        onShareReadingList = onShareReadingList,
                        onAddBookClick = onAddEpubClick,
                        onAddWishlistClick = { showAddWishlistDialog = true },
                        showAddButton = books.isNotEmpty() || selectedTab == 2,
                        isLandscape = false
                    )
                    LibraryTabChips(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        booksCount = books.size,
                        readCount = readBooks.size,
                        wishlistCount = wishlistBooks.size,
                        isLandscape = false
                    )
                    LibrarySearchBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        isLandscape = false
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSearchingDeep) {
                            item(key = "search-progress-p") {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        if (filteredBooks.isNotEmpty()) {
                            item(key = "matching-books-title-p") {
                                Text("MATCHING BOOKS (${filteredBooks.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 2.dp))
                            }
                            items(filteredBooks, key = { "search-book-p-${it.id}" }) { book ->
                                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                    Card(modifier = Modifier.fillMaxWidth().clickable { onBookSelect(book.id) }, shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            BookCoverImage(source = book.coverUrl, titleFallback = book.title, authorFallback = book.author,
                                                modifier = Modifier.width(44.dp).height(64.dp).clip(RoundedCornerShape(6.dp)))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(book.title, fontFamily = FontFamily.Serif, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(book.author, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (deepSearchResults.isNotEmpty()) {
                            item(key = "deep-matches-title-p") {
                                Text(
                                    text = "DEEP SCENE MATCHES (${deepSearchResults.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp)
                                )
                            }
                            items(deepSearchResults, key = { "scene-p-${it.bookId}-${it.chapterIndex}-${it.paragraphIndex}" }) { match ->
                                val matchedBook = books.find { it.id == match.bookId }
                                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (onNavigateToScene != null) {
                                                    onNavigateToScene(match.bookId, match.chapterIndex)
                                                } else {
                                                    onBookSelect(match.bookId)
                                                }
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = matchedBook?.title ?: "Book",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = match.chapterTitle.ifBlank { "Chapter ${match.chapterIndex + 1}" },
                                                    fontSize = 10.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = match.snippet,
                                                fontSize = 11.5.sp,
                                                lineHeight = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontFamily = FontFamily.Serif,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (filteredBooks.isEmpty() && deepSearchResults.isEmpty() && !isSearchingDeep) {
                            item(key = "no-results-p") {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No matches found for \"$searchQuery\"", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (selectedTab == 1) {
            // Read (Completed) View
            if (isLandscape) {
                // Compact height: header/tabs/search scroll inside grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = gridMinSize),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "lib-header") {
                        LibraryHeader(
                            booksCount = books.size,
                            readCount = readBooks.size,
                            wishlistCount = wishlistBooks.size,
                            selectedTab = selectedTab,
                            onShareReadingList = onShareReadingList,
                            onAddBookClick = onAddEpubClick,
                            onAddWishlistClick = { showAddWishlistDialog = true },
                            showAddButton = books.isNotEmpty() || selectedTab == 2,
                            isLandscape = true
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "lib-tabs") {
                        LibraryTabChips(
                            selectedTab = selectedTab,
                            onSelectTab = { selectedTab = it },
                            booksCount = books.size,
                            readCount = readBooks.size,
                            wishlistCount = wishlistBooks.size,
                            isLandscape = true
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "lib-search") {
                        LibrarySearchBar(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            isLandscape = true
                        )
                    }
                    if (readBooks.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "empty-read") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No Completed Books Yet",
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Books with 95%+ progress or marked as read will appear here.",
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = readBooks,
                            key = { "read-${it.id}" },
                            contentType = { "read-book-card" }
                        ) { b ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onBookSelect(b.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Box {
                                        BookCoverImage(
                                            source = b.coverUrl,
                                            titleFallback = b.title,
                                            authorFallback = b.author,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f / 1.30f)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(9.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    "READ",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = b.title,
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = b.author,
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Portrait: static header/tabs/search pinned at top
                Column(modifier = Modifier.fillMaxSize()) {
                    LibraryHeader(
                        booksCount = books.size,
                        readCount = readBooks.size,
                        wishlistCount = wishlistBooks.size,
                        selectedTab = selectedTab,
                        onShareReadingList = onShareReadingList,
                        onAddBookClick = onAddEpubClick,
                        onAddWishlistClick = { showAddWishlistDialog = true },
                        showAddButton = books.isNotEmpty() || selectedTab == 2,
                        isLandscape = false
                    )
                    LibraryTabChips(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        booksCount = books.size,
                        readCount = readBooks.size,
                        wishlistCount = wishlistBooks.size,
                        isLandscape = false
                    )
                    LibrarySearchBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        isLandscape = false
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridMinSize),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 150.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (readBooks.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "empty-read") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No Completed Books Yet",
                                            fontFamily = FontFamily.Serif,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Books with 95%+ progress or marked as read will appear here.",
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                items = readBooks,
                                key = { "read-${it.id}" },
                                contentType = { "read-book-card" }
                            ) { b ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onBookSelect(b.id) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Box {
                                            BookCoverImage(
                                                source = b.coverUrl,
                                                titleFallback = b.title,
                                                authorFallback = b.author,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1f / 1.30f)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(9.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        "READ",
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = b.title,
                                            fontFamily = FontFamily.Serif,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = b.author,
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (selectedTab == 2) {
            // Wishlist View
            if (isLandscape) {
                // Compact height: header scrolls with content
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "lib-header") {
                        LibraryHeader(
                            booksCount = books.size,
                            readCount = readBooks.size,
                            wishlistCount = wishlistBooks.size,
                            selectedTab = selectedTab,
                            onShareReadingList = onShareReadingList,
                            onAddBookClick = onAddEpubClick,
                            onAddWishlistClick = { showAddWishlistDialog = true },
                            showAddButton = books.isNotEmpty() || selectedTab == 2,
                            isLandscape = true
                        )
                    }
                    item(key = "lib-tabs") {
                        LibraryTabChips(
                            selectedTab = selectedTab,
                            onSelectTab = { selectedTab = it },
                            booksCount = books.size,
                            readCount = readBooks.size,
                            wishlistCount = wishlistBooks.size,
                            isLandscape = true
                        )
                    }
                    item(key = "lib-search") {
                        LibrarySearchBar(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            isLandscape = true
                        )
                    }
                    if (wishlistBooks.isEmpty()) {
                        item(key = "empty-wishlist") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(imageVector = Icons.Default.Favorite, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Your Wishlist is Empty", fontFamily = FontFamily.Serif, fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Tap + above to add titles you wish to read next.", fontFamily = FontFamily.SansSerif,
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        items(items = wishlistBooks, key = { it.id }, contentType = { "wishlist-card" }) { item ->
                            Card(shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, fontFamily = FontFamily.Serif, fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                        if (item.author.isNotBlank()) Text(item.author, fontFamily = FontFamily.SansSerif,
                                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (item.note.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(item.note, fontFamily = FontFamily.SansSerif, fontSize = 11.sp,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                color = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                    IconButton(onClick = { onRemoveFromWishlist(item.id) }) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Portrait: static header/tabs/search pinned at top
                Column(modifier = Modifier.fillMaxSize()) {
                    LibraryHeader(
                        booksCount = books.size,
                        readCount = readBooks.size,
                        wishlistCount = wishlistBooks.size,
                        selectedTab = selectedTab,
                        onShareReadingList = onShareReadingList,
                        onAddBookClick = onAddEpubClick,
                        onAddWishlistClick = { showAddWishlistDialog = true },
                        showAddButton = books.isNotEmpty() || selectedTab == 2,
                        isLandscape = false
                    )
                    LibraryTabChips(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        booksCount = books.size,
                        readCount = readBooks.size,
                        wishlistCount = wishlistBooks.size,
                        isLandscape = false
                    )
                    LibrarySearchBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        isLandscape = false
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (wishlistBooks.isEmpty()) {
                            item(key = "empty-wishlist") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(imageVector = Icons.Default.Favorite, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("Your Wishlist is Empty", fontFamily = FontFamily.Serif, fontSize = 18.sp,
                                            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Tap + above to add titles you wish to read next.", fontFamily = FontFamily.SansSerif,
                                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        } else {
                            items(items = wishlistBooks, key = { it.id }, contentType = { "wishlist-card" }) { item ->
                                Card(shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.title, fontFamily = FontFamily.Serif, fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                            if (item.author.isNotBlank()) Text(item.author, fontFamily = FontFamily.SansSerif,
                                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (item.note.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(item.note, fontFamily = FontFamily.SansSerif, fontSize = 11.sp,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                    color = MaterialTheme.colorScheme.secondary)
                                            }
                                        }
                                        IconButton(onClick = { onRemoveFromWishlist(item.id) }) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Gallery View (selectedTab == 0)
            if (isLandscape) {
                // Compact height: header scrolls with content inside the grid
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        coroutineScope.launch {
                            try { repository?.refreshLibrary() } finally { isRefreshing = false }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridMinSize),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "lib-header") {
                            LibraryHeader(
                                booksCount = books.size,
                                readCount = readBooks.size,
                                wishlistCount = wishlistBooks.size,
                                selectedTab = selectedTab,
                                onShareReadingList = onShareReadingList,
                                onAddBookClick = onAddEpubClick,
                                onAddWishlistClick = { showAddWishlistDialog = true },
                                showAddButton = books.isNotEmpty() || selectedTab == 2,
                                isLandscape = true
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "lib-tabs") {
                            LibraryTabChips(
                                selectedTab = selectedTab,
                                onSelectTab = { selectedTab = it },
                                booksCount = books.size,
                                readCount = readBooks.size,
                                wishlistCount = wishlistBooks.size,
                                isLandscape = true
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "lib-search") {
                            LibrarySearchBar(
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                isLandscape = true
                            )
                        }

                    if (books.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "empty-library") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.UploadFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Your Library is Empty",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Import an EPUB from your device or download books to start reading.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = onAddEpubClick,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add Books")
                                    }
                                }
                            }
                        }
                    } else {
                        if (activeBook != null) {
                            // Current Read Hero Section
                            item(span = { GridItemSpan(maxLineSpan) }, key = "current-read") {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "CURRENT READ",
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 1.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "TAP TO OPEN",
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 0.5.sp,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.clickable { onBookSelect(activeBook.id) }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onBookSelect(activeBook.id) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(if (isLandscape) 10.dp else 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Real Cover Thumbnail
                                            BookCoverImage(
                                                source = activeBook.coverUrl,
                                                titleFallback = activeBook.title,
                                                authorFallback = activeBook.author,
                                                modifier = Modifier
                                                    .width(if (isLandscape) 54.dp else 68.dp)
                                                    .height(if (isLandscape) 76.dp else 96.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )

                                            Spacer(modifier = Modifier.width(14.dp))

                                            // Details
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "LAST READ ${activeBook.lastRead.uppercase()}",
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    letterSpacing = 0.5.sp,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = activeBook.title,
                                                    fontFamily = FontFamily.Serif,
                                                    fontSize = if (isLandscape) 15.sp else 17.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${activeBook.author} • ${activeBook.chapters.getOrNull(activeBook.currentChapter)?.title ?: "Chapter 1"}",
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(modifier = Modifier.height(8.dp))

                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                ) {
                                                    val frac = (activeBook.progress / 100f).coerceIn(0f, 1f)
                                                    if (frac > 0f) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth(fraction = frac)
                                                                .fillMaxHeight()
                                                                .background(MaterialTheme.colorScheme.secondary)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "${activeBook.progress}% complete",
                                                        fontFamily = FontFamily.SansSerif,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Normal,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = activeBook.readTimeLeft,
                                                        fontFamily = FontFamily.SansSerif,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Normal,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }, key = "all-books-header") {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "ALL BOOKS",
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!activeBackgroundTask.isNullOrBlank()) {
                                    TooltipBox(
                                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                                        tooltip = {
                                            PlainTooltip {
                                                Text(text = activeBackgroundTask ?: "")
                                            }
                                        },
                                        state = rememberTooltipState()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    android.widget.Toast.makeText(context, activeBackgroundTask, android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.5.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = activeBackgroundTask,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Book Cards in Grid
                        items(
                            items = books,
                            key = { it.id },
                            contentType = { "book-card" }
                        ) { book ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = { onBookSelect(book.id) },
                                        onLongClick = { bookForContextMenu = book }
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    BookCoverImage(
                                        source = book.coverUrl,
                                        titleFallback = book.title,
                                        authorFallback = book.author,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f / 1.30f)
                                            .clip(RoundedCornerShape(8.dp))
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = book.title,
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = book.author,
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        val frac = (book.progress / 100f).coerceIn(0f, 1f)
                                        if (frac > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(fraction = frac)
                                                    .fillMaxHeight()
                                                    .background(MaterialTheme.colorScheme.secondary)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${book.progress}%",
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (book.progress > 0) MaterialTheme.colorScheme.secondary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        // "Add EPUB" Dashed Card
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onAddEpubClick() }
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.UploadFile,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Add Book",
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Online catalog or file",
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
                // Portrait: static header/tabs/search pinned at top, PullToRefresh below
                Column(modifier = Modifier.fillMaxSize()) {
                    LibraryHeader(
                        booksCount = books.size,
                        readCount = readBooks.size,
                        wishlistCount = wishlistBooks.size,
                        selectedTab = selectedTab,
                        onShareReadingList = onShareReadingList,
                        onAddBookClick = onAddEpubClick,
                        onAddWishlistClick = { showAddWishlistDialog = true },
                        showAddButton = books.isNotEmpty() || selectedTab == 2,
                        isLandscape = false
                    )
                    LibraryTabChips(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        booksCount = books.size,
                        readCount = readBooks.size,
                        wishlistCount = wishlistBooks.size,
                        isLandscape = false
                    )
                    LibrarySearchBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        isLandscape = false
                    )
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            coroutineScope.launch {
                                try { repository?.refreshLibrary() } finally { isRefreshing = false }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = gridMinSize),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (books.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "empty-library-p") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Icon(imageVector = Icons.Default.UploadFile, contentDescription = null,
                                                modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text("Your Library is Empty", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Import an EPUB from your device or download books to start reading.",
                                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Button(onClick = onAddEpubClick, shape = RoundedCornerShape(12.dp)) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Add Books")
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (activeBook != null) {
                                    item(span = { GridItemSpan(maxLineSpan) }, key = "current-read-p") {
                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text("CURRENT READ", fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("TAP TO OPEN", fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.clickable { onBookSelect(activeBook.id) })
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Card(modifier = Modifier.fillMaxWidth().clickable { onBookSelect(activeBook.id) }, shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    BookCoverImage(source = activeBook.coverUrl, titleFallback = activeBook.title, authorFallback = activeBook.author,
                                                        modifier = Modifier.width(68.dp).height(96.dp).clip(RoundedCornerShape(8.dp)))
                                                    Spacer(modifier = Modifier.width(14.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("LAST READ ${activeBook.lastRead.uppercase()}", fontFamily = FontFamily.SansSerif, fontSize = 10.sp, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.secondary)
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(activeBook.title, fontFamily = FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text("${activeBook.author} • ${activeBook.chapters.getOrNull(activeBook.currentChapter)?.title ?: "Chapter 1"}",
                                                            fontFamily = FontFamily.SansSerif, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                                            val frac = (activeBook.progress / 100f).coerceIn(0f, 1f)
                                                            if (frac > 0f) Box(modifier = Modifier.fillMaxWidth(fraction = frac).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text("${activeBook.progress}% complete", fontFamily = FontFamily.SansSerif, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(activeBook.readTimeLeft, fontFamily = FontFamily.SansSerif, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                item(span = { GridItemSpan(maxLineSpan) }, key = "all-books-header-p") {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("ALL BOOKS", fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (!activeBackgroundTask.isNullOrBlank()) {
                                            TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                                                tooltip = { PlainTooltip { Text(activeBackgroundTask ?: "") } }, state = rememberTooltipState()) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { android.widget.Toast.makeText(context, activeBackgroundTask, android.widget.Toast.LENGTH_SHORT).show() }.padding(horizontal = 4.dp, vertical = 2.dp)) {
                                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
                                                    Icon(imageVector = Icons.Default.Info, contentDescription = activeBackgroundTask, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                                items(items = books, key = { it.id }, contentType = { "book-card" }) { book ->
                                    Card(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { onBookSelect(book.id) }, onLongClick = { bookForContextMenu = book }),
                                        shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            BookCoverImage(source = book.coverUrl, titleFallback = book.title, authorFallback = book.author,
                                                modifier = Modifier.fillMaxWidth().aspectRatio(1f / 1.30f).clip(RoundedCornerShape(8.dp)))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(book.title, fontFamily = FontFamily.Serif, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(book.author, fontFamily = FontFamily.SansSerif, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                                val frac = (book.progress / 100f).coerceIn(0f, 1f)
                                                if (frac > 0f) Box(modifier = Modifier.fillMaxWidth(fraction = frac).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text("${book.progress}%", fontFamily = FontFamily.SansSerif, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (book.progress > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant))
                                            }
                                        }
                                    }
                                }
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        .clickable { onAddEpubClick() }.padding(14.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(40.dp)) {
                                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Add Book", fontFamily = FontFamily.SansSerif, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Online catalog or file", fontFamily = FontFamily.SansSerif, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add to Wishlist Dialog
        if (showAddWishlistDialog) {
            var title by remember { mutableStateOf("") }
            var author by remember { mutableStateOf("") }
            var notes by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddWishlistDialog = false },
                title = {
                    Text(
                        text = "Add to Wishlist",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Book Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = author,
                            onValueChange = { author = it },
                            label = { Text("Author (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes / Recommendation") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onAddToWishlist(title.trim(), author.trim(), notes.trim())
                                showAddWishlistDialog = false
                            }
                        },
                        enabled = title.isNotBlank()
                    ) {
                        Text("Add Title")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddWishlistDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Long-press Context Menu Sheet
        if (bookForContextMenu != null) {
            val targetBook = bookForContextMenu!!
            BookContextMenuSheet(
                book = targetBook,
                isCompleted = completedBookIds.contains(targetBook.id) || targetBook.progress >= 95,
                onToggleCompleted = { onToggleCompleted(targetBook.id) },
                onShare = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, targetBook.title)
                        putExtra(Intent.EXTRA_TEXT, "Reading \"${targetBook.title}\" by ${targetBook.author} on Lumina Reader.")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Book"))
                },
                onViewDetails = {
                    onBookSelect(targetBook.id)
                },
                onResetProgress = {
                    onResetProgress(targetBook.id)
                },
                onDelete = {
                    bookToDelete = targetBook
                },
                onDismiss = { bookForContextMenu = null }
            )
        }

        // Confirmation dialog for removing book
        if (bookToDelete != null) {
            val target = bookToDelete!!
            AlertDialog(
                onDismissRequest = { bookToDelete = null },
                title = {
                    Text(
                        text = "Remove Book",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to remove \"${target.title}\" from your gallery?",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteBook(target.id)
                            bookToDelete = null
                        }
                    ) {
                        Text(
                            text = "Remove",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { bookToDelete = null }) {
                        Text(
                            text = "Cancel",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}

@Composable
private fun LibraryHeader(
    booksCount: Int,
    readCount: Int,
    wishlistCount: Int,
    selectedTab: Int,
    onShareReadingList: () -> Unit,
    onAddBookClick: () -> Unit,
    onAddWishlistClick: () -> Unit,
    showAddButton: Boolean,
    isLandscape: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isLandscape) 4.dp else 20.dp,
                vertical = if (isLandscape) 8.dp else 16.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Lumina",
                fontFamily = FontFamily.Serif,
                fontSize = if (isLandscape) 22.sp else 28.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = when (selectedTab) {
                    0 -> "Your Digital Sanctuary • $booksCount Books"
                    1 -> "Finished Volumes • $readCount Completed"
                    else -> "Curated Want to Read • $wishlistCount Titles"
                },
                fontFamily = FontFamily.SansSerif,
                fontSize = if (isLandscape) 11.sp else 12.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onShareReadingList,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share Reading List",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (showAddButton) {
                IconButton(
                    onClick = {
                        if (selectedTab == 2) onAddWishlistClick() else onAddBookClick()
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Book", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun LibraryTabChips(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    booksCount: Int,
    readCount: Int,
    wishlistCount: Int,
    isLandscape: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isLandscape) 4.dp else 20.dp,
                vertical = if (isLandscape) 2.dp else 4.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedTab == 0,
            onClick = { onSelectTab(0) },
            label = { Text("Gallery ($booksCount)", fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                selectedLabelColor = MaterialTheme.colorScheme.secondary
            )
        )
        FilterChip(
            selected = selectedTab == 1,
            onClick = { onSelectTab(1) },
            label = { Text("Read ($readCount)", fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                selectedLabelColor = MaterialTheme.colorScheme.secondary
            )
        )
        FilterChip(
            selected = selectedTab == 2,
            onClick = { onSelectTab(2) },
            label = { Text("Wishlist ($wishlistCount)", fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                selectedLabelColor = MaterialTheme.colorScheme.secondary
            )
        )
    }
}

@Composable
private fun LibrarySearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isLandscape: Boolean
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text("Search books, authors, files, or text...", fontSize = 12.sp) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        maxLines = 1,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            focusedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isLandscape) 4.dp else 20.dp,
                vertical = if (isLandscape) 2.dp else 4.dp
            )
    )
}

