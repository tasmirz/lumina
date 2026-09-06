package org.protidhoni.lumina

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.protidhoni.lumina.data.BookRepository
import org.protidhoni.lumina.data.DictionaryService
import org.protidhoni.lumina.data.EpubParser
import org.protidhoni.lumina.data.OnlineEpubService
import org.protidhoni.lumina.model.WordDefinition
import org.protidhoni.lumina.theme.LuminaReaderTheme
import org.protidhoni.lumina.ui.components.AddBookSheet
import org.protidhoni.lumina.ui.components.AppearanceSheet
import org.protidhoni.lumina.ui.components.BookmarksSheet
import org.protidhoni.lumina.ui.components.DictionarySheet
import org.protidhoni.lumina.ui.library.LibraryScreen
import org.protidhoni.lumina.ui.reader.ReaderScreen
import kotlinx.coroutines.launch

enum class ScreenTab {
    LIBRARY,
    READER
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bookRepository = BookRepository(applicationContext)

        setContent {
            val books by bookRepository.books.collectAsStateWithLifecycle()
            val activeBookId by bookRepository.activeBookId.collectAsStateWithLifecycle()
            val activeBook = books.find { it.id == activeBookId } ?: books.firstOrNull() ?: bookRepository.getActiveBook()
            val bookmarks by bookRepository.bookmarks.collectAsStateWithLifecycle()
            val readingMode by bookRepository.readingMode.collectAsStateWithLifecycle()
            val themeMode by bookRepository.themeMode.collectAsStateWithLifecycle()
            val fontSize by bookRepository.fontSize.collectAsStateWithLifecycle()
            val typeface by bookRepository.typefaceMode.collectAsStateWithLifecycle()
            val lineHeight by bookRepository.lineHeightMultiplier.collectAsStateWithLifecycle()
            val showFloatingAssistant by bookRepository.showFloatingAssistant.collectAsStateWithLifecycle()
            val geminiApiKey by bookRepository.geminiApiKey.collectAsStateWithLifecycle()

            var currentTab by rememberSaveable {
                mutableStateOf(
                    if (bookRepository.getLastTab() == ScreenTab.READER.name) ScreenTab.READER else ScreenTab.LIBRARY
                )
            }
            var showBookmarksSheet by rememberSaveable { mutableStateOf(false) }
            var showAppearanceSheet by rememberSaveable { mutableStateOf(false) }
            var showAddBookSheet by rememberSaveable { mutableStateOf(false) }
            var showNavBarInReader by rememberSaveable { mutableStateOf(false) }
            var isReaderUiVisible by rememberSaveable { mutableStateOf(true) }
            var isFullscreen by rememberSaveable { mutableStateOf(false) }
            var activeWordDefinition by remember { mutableStateOf<WordDefinition?>(null) }

            LaunchedEffect(currentTab) {
                bookRepository.setLastTab(currentTab.name)
            }

            val coroutineScope = rememberCoroutineScope()

            // EPUB File Picker Launcher
            val epubPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val parsedBook = EpubParser.parseEpub(stream, uri.lastPathSegment ?: "Imported.epub", applicationContext)
                            bookRepository.addBook(parsedBook)
                            currentTab = ScreenTab.READER
                            Toast.makeText(this, "Added \"${parsedBook.title}\" to Library", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(this, "Failed to parse EPUB file", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            LuminaReaderTheme(themeMode = themeMode) {
                val showBottomNav = !isFullscreen && (
                    currentTab == ScreenTab.LIBRARY ||
                    (currentTab == ScreenTab.READER && showNavBarInReader && isReaderUiVisible)
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (showBottomNav) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.height(54.dp)
                            ) {
                                NavigationBarItem(
                                    selected = currentTab == ScreenTab.LIBRARY,
                                    onClick = { currentTab = ScreenTab.LIBRARY },
                                    icon = {
                                        Icon(
                                            Icons.Default.AutoStories,
                                            contentDescription = "Library",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    alwaysShowLabel = false
                                )
                                NavigationBarItem(
                                    selected = currentTab == ScreenTab.READER,
                                    onClick = { currentTab = ScreenTab.READER },
                                    icon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = "Read",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    alwaysShowLabel = false
                                )
                                NavigationBarItem(
                                    selected = false,
                                    onClick = { showBookmarksSheet = true },
                                    icon = {
                                        Icon(
                                            Icons.Default.Bookmarks,
                                            contentDescription = "Highlights",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    alwaysShowLabel = false
                                )
                                NavigationBarItem(
                                    selected = false,
                                    onClick = { showAppearanceSheet = true },
                                    icon = {
                                        Icon(
                                            Icons.Default.Tune,
                                            contentDescription = "Appearance",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    alwaysShowLabel = false
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        when (currentTab) {
                            ScreenTab.LIBRARY -> {
                                LibraryScreen(
                                    books = books,
                                    activeBook = activeBook,
                                    onBookSelect = { bookId ->
                                        bookRepository.setActiveBook(bookId)
                                        currentTab = ScreenTab.READER
                                    },
                                    onDeleteBook = { bookId ->
                                        bookRepository.removeBook(bookId)
                                    },
                                    onResetProgress = { bookId ->
                                        bookRepository.updateReadingPosition(bookId, 0, 0, 0, 0)
                                    },
                                    onAddEpubClick = {
                                        showAddBookSheet = true
                                    }
                                )
                            }
                            ScreenTab.READER -> {
                                ReaderScreen(
                                    book = activeBook,
                                    bookmarks = bookmarks,
                                    readingMode = readingMode,
                                    fontSize = fontSize,
                                    typeface = typeface,
                                    lineHeightMultiplier = lineHeight,
                                    isFullscreen = isFullscreen,
                                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                                    showNavBarInReader = showNavBarInReader,
                                    onToggleNavBar = { showNavBarInReader = !showNavBarInReader },
                                    onControlsVisibilityChange = { isReaderUiVisible = it },
                                    onModeChange = { bookRepository.setReadingMode(it) },
                                    onBackToLibrary = { currentTab = ScreenTab.LIBRARY },
                                    onPositionChange = { chap, page, scroll, pct ->
                                        bookRepository.updateReadingPosition(activeBook.id, chap, page, scroll, pct)
                                    },
                                    onAddBookmark = { quote, color ->
                                        bookRepository.addBookmark(quote, color)
                                    },
                                    onRemoveBookmark = { bookmarkId ->
                                        bookRepository.removeBookmark(bookmarkId)
                                    },
                                    onLookupWord = { word ->
                                        coroutineScope.launch {
                                            activeWordDefinition = DictionaryService.lookup(word)
                                        }
                                    },
                                    onOpenAppearance = { showAppearanceSheet = true },
                                    showFloatingAssistant = showFloatingAssistant,
                                    onToggleFloatingAssistant = { bookRepository.setShowFloatingAssistant(it) },
                                    geminiApiKey = geminiApiKey
                                )
                            }
                        }

                        // Dictionary Bottom Sheet
                        DictionarySheet(
                            definition = activeWordDefinition,
                            onDismiss = { activeWordDefinition = null }
                        )

                        // Bookmarks Bottom Sheet
                        if (showBookmarksSheet) {
                            BookmarksSheet(
                                bookmarks = bookmarks,
                                onNavigate = { mark ->
                                    val book = books.find { it.title == mark.bookTitle }
                                    if (book != null) {
                                        bookRepository.setActiveBook(book.id)
                                    }
                                    showBookmarksSheet = false
                                    currentTab = ScreenTab.READER
                                },
                                onDelete = { id ->
                                    bookRepository.removeBookmark(id)
                                },
                                onDismiss = { showBookmarksSheet = false }
                            )
                        }

                        // Appearance Settings Bottom Sheet
                        if (showAppearanceSheet) {
                            AppearanceSheet(
                                fontSize = fontSize,
                                onFontSizeChange = { bookRepository.setFontSize(it) },
                                typeface = typeface,
                                onTypefaceChange = { bookRepository.setTypefaceMode(it) },
                                themeMode = themeMode,
                                onThemeChange = { bookRepository.setThemeMode(it) },
                                showAssistant = showFloatingAssistant,
                                onToggleAssistant = { bookRepository.setShowFloatingAssistant(it) },
                                geminiApiKey = geminiApiKey,
                                onGeminiApiKeyChange = { bookRepository.setGeminiApiKey(it) },
                                onDismiss = { showAppearanceSheet = false }
                            )
                        }

                        // Add Book Sheet (Multi-Source Online Catalog + Device Storage)
                        if (showAddBookSheet) {
                            AddBookSheet(
                                onDismiss = { showAddBookSheet = false },
                                onBrowseFiles = {
                                    showAddBookSheet = false
                                    epubPickerLauncher.launch("*/*")
                                },
                                onBookDownloaded = { onlineBook ->
                                    coroutineScope.launch {
                                        Toast.makeText(this@MainActivity, "Downloading \"${onlineBook.title}\"...", Toast.LENGTH_SHORT).show()
                                        val stream = OnlineEpubService.downloadEpubStream(onlineBook.epubDownloadUrl)
                                        if (stream != null) {
                                            val parsed = EpubParser.parseEpub(stream, "${onlineBook.title}.epub", applicationContext)
                                            val finalBook = if (onlineBook.coverUrl.isNotBlank() && parsed.coverUrl.startsWith("http")) {
                                                parsed.copy(coverUrl = onlineBook.coverUrl)
                                            } else parsed
                                            bookRepository.addBook(finalBook)
                                            showAddBookSheet = false
                                            currentTab = ScreenTab.READER
                                            Toast.makeText(this@MainActivity, "Opened \"${finalBook.title}\"", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(this@MainActivity, "Download failed. Please check connection.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
