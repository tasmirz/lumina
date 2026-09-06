package org.protidhoni.lumina

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
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
import org.protidhoni.lumina.ui.settings.AdvancedSettingsScreen
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
            val themeFamily by bookRepository.themeFamily.collectAsStateWithLifecycle()
            val themeVariant by bookRepository.themeVariant.collectAsStateWithLifecycle()
            val backgroundTexture by bookRepository.backgroundTexture.collectAsStateWithLifecycle()
            val customBgUri by bookRepository.customBgUri.collectAsStateWithLifecycle()
            val orbActionItems by bookRepository.orbActionItems.collectAsStateWithLifecycle()
            val textAlignment by bookRepository.textAlignmentMode.collectAsStateWithLifecycle()
            val letterSpacing by bookRepository.letterSpacing.collectAsStateWithLifecycle()
            val fontSize by bookRepository.fontSize.collectAsStateWithLifecycle()
            val typeface by bookRepository.typefaceMode.collectAsStateWithLifecycle()
            val lineHeight by bookRepository.lineHeightMultiplier.collectAsStateWithLifecycle()
            val showFloatingAssistant by bookRepository.showFloatingAssistant.collectAsStateWithLifecycle()
            val geminiApiKey by bookRepository.geminiApiKey.collectAsStateWithLifecycle()
            val quickThemes by bookRepository.quickThemes.collectAsStateWithLifecycle()
            val quickFonts by bookRepository.quickFonts.collectAsStateWithLifecycle()
            val orbActionOrder by bookRepository.orbActionOrder.collectAsStateWithLifecycle()
            val aiProvider by bookRepository.aiProvider.collectAsStateWithLifecycle()
            val aiBaseUrl by bookRepository.aiBaseUrl.collectAsStateWithLifecycle()
            val aiModel by bookRepository.aiModel.collectAsStateWithLifecycle()
            val wishlistBooks by bookRepository.wishlistBooks.collectAsStateWithLifecycle()

            var currentTab by rememberSaveable {
                mutableStateOf(
                    if (bookRepository.getLastTab() == ScreenTab.READER.name) ScreenTab.READER else ScreenTab.LIBRARY
                )
            }
            var showBookmarksSheet by rememberSaveable { mutableStateOf(false) }
            var showAppearanceSheet by rememberSaveable { mutableStateOf(false) }
            var showAdvancedSettingsScreen by rememberSaveable { mutableStateOf(false) }
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

            LuminaReaderTheme(themeFamily = themeFamily, themeVariant = themeVariant) {
                val showBottomNav = !isFullscreen && (currentTab == ScreenTab.LIBRARY)

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                        when (currentTab) {
                            ScreenTab.LIBRARY -> {
                                LibraryScreen(
                                    books = books,
                                    activeBook = activeBook,
                                    wishlistBooks = wishlistBooks,
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
                                    },
                                    onAddToWishlist = { title, author, notes ->
                                        bookRepository.addToWishlist(title, author, notes)
                                    },
                                    onRemoveFromWishlist = { id ->
                                        bookRepository.removeFromWishlist(id)
                                    },
                                    onShareReadingList = {
                                        bookRepository.shareReadingList(this@MainActivity)
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
                                    onUpdateBookmark = { bookmark ->
                                        bookRepository.updateBookmark(bookmark)
                                    },
                                    onLookupWord = { word ->
                                        coroutineScope.launch {
                                            activeWordDefinition = DictionaryService.lookup(word)
                                        }
                                    },
                                    onOpenAppearance = { showAppearanceSheet = true },
                                    onOpenBookmarks = { showBookmarksSheet = true },
                                    showFloatingAssistant = showFloatingAssistant,
                                    onToggleFloatingAssistant = { bookRepository.setShowFloatingAssistant(it) },
                                    activeOrbActions = orbActionItems,
                                    backgroundTexture = backgroundTexture,
                                    customBgUri = customBgUri,
                                    textAlignment = textAlignment,
                                    letterSpacing = letterSpacing,
                                    geminiApiKey = geminiApiKey,
                                    aiProvider = aiProvider,
                                    aiBaseUrl = aiBaseUrl,
                                    aiModel = aiModel,
                                    onOpenAdvancedSettings = { showAdvancedSettingsScreen = true }
                                )
                            }
                        }

                        // Truly Floating Navigation Bar (overlay, does not block background)
                        if (showBottomNav) {
                            val bottomInset = max(
                                max(
                                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                                    WindowInsets.mandatorySystemGestures.asPaddingValues().calculateBottomPadding()
                                ),
                                16.dp
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 28.dp, end = 28.dp, bottom = bottomInset + 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(28.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 6.dp,
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                    modifier = Modifier.widthIn(max = 380.dp)
                                ) {
                                    NavigationBar(
                                        containerColor = Color.Transparent,
                                        tonalElevation = 0.dp,
                                        windowInsets = WindowInsets(0, 0, 0, 0),
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
                                onShare = {
                                    bookRepository.shareHighlights(this@MainActivity)
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
                                quickFonts = quickFonts,
                                themeFamily = themeFamily,
                                onThemeFamilyChange = { bookRepository.setThemeFamily(it) },
                                themeVariant = themeVariant,
                                onThemeVariantChange = { bookRepository.setThemeVariant(it) },
                                quickThemes = quickThemes,
                                themeMode = themeMode,
                                onThemeChange = { bookRepository.setThemeMode(it) },
                                showAssistant = showFloatingAssistant,
                                onToggleAssistant = { bookRepository.setShowFloatingAssistant(it) },
                                onOpenAdvancedSettings = { showAdvancedSettingsScreen = true },
                                onDismiss = { showAppearanceSheet = false }
                            )
                        }

                        // Dedicated Full-Screen Advanced Settings Page
                        if (showAdvancedSettingsScreen) {
                            AdvancedSettingsScreen(
                                showFloatingOrb = showFloatingAssistant,
                                onToggleFloatingOrb = { bookRepository.setShowFloatingAssistant(it) },
                                activeOrbActions = orbActionItems,
                                onToggleOrbAction = { bookRepository.toggleOrbActionItem(it) },
                                orbActionOrder = orbActionOrder,
                                onReorderOrbAction = { from, to -> bookRepository.reorderOrbAction(from, to) },
                                themeFamily = themeFamily,
                                onThemeFamilyChange = { bookRepository.setThemeFamily(it) },
                                themeVariant = themeVariant,
                                onThemeVariantChange = { bookRepository.setThemeVariant(it) },
                                quickThemes = quickThemes,
                                onToggleQuickTheme = { bookRepository.toggleQuickTheme(it) },
                                backgroundTexture = backgroundTexture,
                                onBackgroundTextureChange = { bookRepository.setBackgroundTexture(it) },
                                customBgUri = customBgUri,
                                onCustomBgUriChange = { bookRepository.setCustomBgUri(it) },
                                typeface = typeface,
                                onTypefaceChange = { bookRepository.setTypefaceMode(it) },
                                quickFonts = quickFonts,
                                onToggleQuickFont = { bookRepository.toggleQuickFont(it) },
                                lineHeight = lineHeight,
                                onLineHeightChange = { bookRepository.setLineHeight(it) },
                                letterSpacing = letterSpacing,
                                onLetterSpacingChange = { bookRepository.setLetterSpacing(it) },
                                textAlignment = textAlignment,
                                onTextAlignmentChange = { bookRepository.setTextAlignmentMode(it) },
                                aiProvider = aiProvider,
                                onAiProviderChange = { bookRepository.setAiProvider(it) },
                                aiBaseUrl = aiBaseUrl,
                                onAiBaseUrlChange = { bookRepository.setAiBaseUrl(it) },
                                aiModel = aiModel,
                                onAiModelChange = { bookRepository.setAiModel(it) },
                                geminiApiKey = geminiApiKey,
                                onGeminiApiKeyChange = { bookRepository.setGeminiApiKey(it) },
                                onBack = { showAdvancedSettingsScreen = false }
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
