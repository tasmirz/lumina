package io.github.tasmirz.lumina

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import io.github.tasmirz.lumina.data.BookRepository
import io.github.tasmirz.lumina.data.DictionaryService
import io.github.tasmirz.lumina.data.EpubParser
import io.github.tasmirz.lumina.data.OnlineEpubService
import io.github.tasmirz.lumina.model.WordDefinition
import io.github.tasmirz.lumina.theme.LuminaReaderTheme
import io.github.tasmirz.lumina.ui.components.AddBookSheet
import io.github.tasmirz.lumina.ui.components.AppearanceSheet
import io.github.tasmirz.lumina.ui.components.BookmarksSheet
import io.github.tasmirz.lumina.ui.components.DictionarySheet
import io.github.tasmirz.lumina.ui.library.LibraryScreen
import io.github.tasmirz.lumina.ui.reader.ReaderScreen
import io.github.tasmirz.lumina.ui.settings.AdvancedSettingsScreen
import kotlinx.coroutines.launch
import java.io.File

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View

enum class ScreenTab {
    LIBRARY,
    READER
}

class MainActivity : ComponentActivity() {

    override fun onWindowStartingActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        if (type == ActionMode.TYPE_FLOATING) {
            // Suppress Android's floating action mode ("Copy | Select all") in favor of Lumina's SelectionMenuPill
            return object : ActionMode() {
                override fun setTitle(title: CharSequence?) {}
                override fun setTitle(resId: Int) {}
                override fun setSubtitle(subtitle: CharSequence?) {}
                override fun setSubtitle(resId: Int) {}
                override fun setCustomView(view: View?) {}
                override fun invalidate() {}
                override fun finish() {}
                override fun getMenu(): Menu = object : Menu {
                    override fun add(title: CharSequence?): MenuItem? = null
                    override fun add(titleRes: Int): MenuItem? = null
                    override fun add(groupId: Int, itemId: Int, order: Int, title: CharSequence?): MenuItem? = null
                    override fun add(groupId: Int, itemId: Int, order: Int, titleRes: Int): MenuItem? = null
                    override fun addSubMenu(title: CharSequence?) = null
                    override fun addSubMenu(titleRes: Int) = null
                    override fun addSubMenu(groupId: Int, itemId: Int, order: Int, title: CharSequence?) = null
                    override fun addSubMenu(groupId: Int, itemId: Int, order: Int, titleRes: Int) = null
                    override fun addIntentOptions(groupId: Int, itemId: Int, order: Int, caller: android.content.ComponentName?, specifics: Array<out android.content.Intent>?, intent: android.content.Intent?, flags: Int, outSpecificItems: Array<out MenuItem>?) = 0
                    override fun removeItem(id: Int) {}
                    override fun removeGroup(groupId: Int) {}
                    override fun clear() {}
                    override fun setGroupCheckable(groupId: Int, checkable: Boolean, exclusive: Boolean) {}
                    override fun setGroupVisible(groupId: Int, visible: Boolean) {}
                    override fun setGroupEnabled(groupId: Int, enabled: Boolean) {}
                    override fun hasVisibleItems() = false
                    override fun findItem(id: Int): MenuItem? = null
                    override fun size() = 0
                    override fun getItem(index: Int): MenuItem? = null
                    override fun close() {}
                    override fun performShortcut(keyCode: Int, event: android.view.KeyEvent?, flags: Int) = false
                    override fun isShortcutKey(keyCode: Int, event: android.view.KeyEvent?) = false
                    override fun performIdentifierAction(id: Int, flags: Int) = false
                    override fun setQwertyMode(isQwerty: Boolean) {}
                }
                override fun getTitle(): CharSequence? = null
                override fun getSubtitle(): CharSequence? = null
                override fun getCustomView(): View? = null
                override fun getMenuInflater(): android.view.MenuInflater = this@MainActivity.menuInflater
            }
        }
        return super.onWindowStartingActionMode(callback, type)
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.compose.foundation.ComposeFoundationFlags.isNewContextMenuEnabled = false
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
            val paragraphSpacing by bookRepository.paragraphSpacingMultiplier.collectAsStateWithLifecycle()
            val showFloatingAssistant by bookRepository.showFloatingAssistant.collectAsStateWithLifecycle()
            val orbSize by bookRepository.orbSize.collectAsStateWithLifecycle()
            val orbMenuSize by bookRepository.orbMenuSize.collectAsStateWithLifecycle()
            val orbColor by bookRepository.orbColor.collectAsStateWithLifecycle()
            val geminiApiKey by bookRepository.geminiApiKey.collectAsStateWithLifecycle()
            val quickThemes by bookRepository.quickThemes.collectAsStateWithLifecycle()
            val quickFonts by bookRepository.quickFonts.collectAsStateWithLifecycle()
            val orbActionOrder by bookRepository.orbActionOrder.collectAsStateWithLifecycle()
            val aiProvider by bookRepository.aiProvider.collectAsStateWithLifecycle()
            val aiBaseUrl by bookRepository.aiBaseUrl.collectAsStateWithLifecycle()
            val aiModel by bookRepository.aiModel.collectAsStateWithLifecycle()
            val wishlistBooks by bookRepository.wishlistBooks.collectAsStateWithLifecycle()
            val completedBookIds by bookRepository.completedBookIds.collectAsStateWithLifecycle()
            val preferredLanguage by bookRepository.preferredLanguage.collectAsStateWithLifecycle()

            var currentTab by rememberSaveable {
                mutableStateOf(
                    if (bookRepository.getLastTab() == ScreenTab.READER.name && books.isNotEmpty()) ScreenTab.READER else ScreenTab.LIBRARY
                )
            }
            var showBookmarksSheet by rememberSaveable { mutableStateOf(false) }
            var showAppearanceSheet by rememberSaveable { mutableStateOf(false) }
            var showAdvancedSettingsScreen by rememberSaveable { mutableStateOf(false) }
            var showAddBookSheet by rememberSaveable { mutableStateOf(false) }
            var showNavBarInReader by rememberSaveable { mutableStateOf(false) }
            var isFullscreen by rememberSaveable { mutableStateOf(false) }
            var activeWordDefinition by remember { mutableStateOf<WordDefinition?>(null) }

            LaunchedEffect(currentTab) {
                bookRepository.setLastTab(currentTab.name)
            }

            LaunchedEffect(isFullscreen) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                if (isFullscreen) {
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.statusBars())
                } else {
                    insetsController.show(WindowInsetsCompat.Type.statusBars())
                }
            }

            val coroutineScope = rememberCoroutineScope()

            // EPUB File Picker Launcher
            val epubPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    try {
                        val fileName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Imported.epub"
                        val cleanName = if (fileName.endsWith(".epub", ignoreCase = true)) fileName else "$fileName.epub"
                        val epubDir = File(applicationContext.filesDir, "epubs").apply { if (!exists()) mkdirs() }
                        val destFile = File(epubDir, "${System.currentTimeMillis()}_$cleanName")

                        contentResolver.openInputStream(uri)?.use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        destFile.inputStream().use { stream ->
                            val parsedBook = EpubParser.parseEpub(stream, cleanName, applicationContext)
                            val bookToSave = parsedBook.copy(
                                filePath = destFile.absolutePath,
                                fileSize = destFile.length(),
                                isDownloaded = false
                            )
                            bookRepository.addBook(bookToSave)
                            currentTab = ScreenTab.READER
                            Toast.makeText(this, "Added \"${parsedBook.title}\" to Library", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(this, "Failed to parse EPUB file", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            LuminaReaderTheme(themeFamily = themeFamily, themeVariant = themeVariant) {
                BackHandler(enabled = showAdvancedSettingsScreen) {
                    showAdvancedSettingsScreen = false
                }
                BackHandler(enabled = !showAdvancedSettingsScreen && currentTab != ScreenTab.LIBRARY) {
                    currentTab = ScreenTab.LIBRARY
                }

                val showBottomNav = !isFullscreen && (currentTab == ScreenTab.LIBRARY)

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                        when (currentTab) {
                            ScreenTab.LIBRARY -> {
                                LibraryScreen(
                                    books = books,
                                    activeBook = activeBook,
                                    repository = bookRepository,
                                    wishlistBooks = wishlistBooks,
                                    completedBookIds = completedBookIds,
                                    onToggleCompleted = { bookRepository.toggleBookCompleted(it) },
                                    onBookSelect = { bookId ->
                                        bookRepository.setActiveBook(bookId)
                                        currentTab = ScreenTab.READER
                                    },
                                    onNavigateToScene = { bookId, chapterIndex ->
                                        bookRepository.setActiveBook(bookId)
                                        bookRepository.updateReadingPosition(bookId, chapterIndex, 0, 0, 0)
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
                                if (activeBook != null) {
                                    // Stabilize book reference during active reader session to isolate scrolling
                                    // progress updates from triggering full ReaderScreen tree recompositions.
                                    val readerBook = remember(activeBook.id) { activeBook }
                                    ReaderScreen(
                                        book = readerBook,
                                        bookmarks = bookmarks,
                                        readingMode = readingMode,
                                        fontSize = fontSize,
                                        typeface = typeface,
                                        lineHeightMultiplier = lineHeight,
                                        isFullscreen = isFullscreen,
                                        onToggleFullscreen = { isFullscreen = !isFullscreen },
                                        showNavBarInReader = showNavBarInReader,
                                        onToggleNavBar = { showNavBarInReader = !showNavBarInReader },
                                        onControlsVisibilityChange = {},
                                        onModeChange = { bookRepository.setReadingMode(it) },
                                        onBackToLibrary = { currentTab = ScreenTab.LIBRARY },
                                        onPositionChange = { chap, page, scroll, pct ->
                                            bookRepository.updateReadingPosition(readerBook.id, chap, page, scroll, pct)
                                        },
                                        onAddBookmark = { quote, color, page ->
                                            bookRepository.addBookmark(quote, color, pageNumber = page)
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
                                        repository = bookRepository,
                                        onThemeFamilyChange = { bookRepository.setThemeFamily(it) },
                                        onThemeVariantChange = { bookRepository.setThemeVariant(it) },
                                        onOpenAdvancedSettings = { showAdvancedSettingsScreen = true }
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier.padding(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoStories,
                                                contentDescription = null,
                                                modifier = Modifier.size(64.dp),
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                text = "No Book Selected",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Open a book from your Library or import an EPUB to begin reading.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            Button(
                                                onClick = { currentTab = ScreenTab.LIBRARY },
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Go to Library")
                                            }
                                        }
                                    }
                                }
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
                                currentBookTitle = activeBook?.title,
                                onNavigate = { mark ->
                                    val book = books.find { it.title == mark.bookTitle }
                                    if (book != null) {
                                        val chapIdx = book.chapters.indexOfFirst { it.title.equals(mark.chapter, ignoreCase = true) }
                                        val targetPage = if (mark.pageNumber > 0) mark.pageNumber - 1 else 0
                                        if (chapIdx != -1) {
                                            bookRepository.updateReadingPosition(book.id, chapIdx, targetPage, 0, book.progress)
                                        } else if (mark.pageNumber > 0) {
                                            bookRepository.updateReadingPosition(book.id, book.currentChapter, targetPage, 0, book.progress)
                                        }
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
                                orbSize = orbSize,
                                onOrbSizeChange = { bookRepository.setOrbSize(it) },
                                orbMenuSize = orbMenuSize,
                                onOrbMenuSizeChange = { bookRepository.setOrbMenuSize(it) },
                                orbColor = orbColor,
                                onOrbColorChange = { bookRepository.setOrbColor(it) },
                                readingMode = readingMode,
                                onReadingModeChange = { bookRepository.setReadingMode(it) },
                                paragraphSpacing = paragraphSpacing,
                                onParagraphSpacingChange = { bookRepository.setParagraphSpacing(it) },
                                currentLanguage = preferredLanguage,
                                onLanguageChange = { bookRepository.setPreferredLanguage(it) },
                                onOpenAdvancedSettings = { showAdvancedSettingsScreen = true },
                                onDismiss = { showAppearanceSheet = false }
                            )
                        }

                        // Dedicated Full-Screen Advanced Settings Page
                        if (showAdvancedSettingsScreen) {
                            AdvancedSettingsScreen(
                                repository = bookRepository,
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
                                        val epubDir = File(applicationContext.filesDir, "epubs").apply { if (!exists()) mkdirs() }
                                        val cleanName = onlineBook.title.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".epub"
                                        val destFile = File(epubDir, "${System.currentTimeMillis()}_$cleanName")

                                        val stream = OnlineEpubService.downloadEpubStream(onlineBook.epubDownloadUrl)
                                        if (stream != null) {
                                            destFile.outputStream().use { output ->
                                                stream.copyTo(output)
                                            }
                                            destFile.inputStream().use { savedStream ->
                                                val parsed = EpubParser.parseEpub(savedStream, cleanName, applicationContext)
                                                val finalBook = (if (onlineBook.coverUrl.isNotBlank() && parsed.coverUrl.startsWith("http")) {
                                                    parsed.copy(coverUrl = onlineBook.coverUrl)
                                                } else parsed).copy(
                                                    filePath = destFile.absolutePath,
                                                    fileSize = destFile.length(),
                                                    isDownloaded = true,
                                                    downloadUrl = onlineBook.epubDownloadUrl
                                                )
                                                bookRepository.addBook(finalBook)
                                                showAddBookSheet = false
                                                currentTab = ScreenTab.READER
                                                Toast.makeText(this@MainActivity, "Opened \"${finalBook.title}\"", Toast.LENGTH_SHORT).show()
                                            }
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


