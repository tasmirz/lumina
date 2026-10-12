package io.github.tasmirz.lumina.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.data.AiProvider
import io.github.tasmirz.lumina.data.BookRepository
import io.github.tasmirz.lumina.data.EdgeTtsService
import io.github.tasmirz.lumina.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SettingsSubScreen(val title: String, val subtitle: String, val icon: ImageVector) {
    MENU("Advanced Settings", "Personalization, Storage & Open Source Engine", Icons.Default.Settings),
    THEMES_TYPOGRAPHY("Themes, Typography & Textures", "Palettes, fonts, line spacing, margins", Icons.Outlined.Palette),
    CUSTOM_CSS("Custom Theme & CSS Engine", "Palette builder, custom CSS import/export", Icons.Outlined.Brush),
    READING_CONTROLS("Reading Controls & Audio", "Floating orb, actions, TTS, auto-scroll", Icons.Outlined.TouchApp),
    AI_INTELLIGENCE("AI Assistant & Intelligence", "Offline command palette, Gemini, custom endpoints", Icons.Outlined.AutoAwesome),
    STORAGE_BACKUP("Storage, Backup & License", "Cache management, index cleaner, export/import", Icons.Outlined.Storage)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    repository: BookRepository? = null,
    fontSize: Int = 18,
    onFontSizeChange: (Int) -> Unit = {},
    readingMode: ReadingMode = ReadingMode.SCROLL,
    onReadingModeChange: (ReadingMode) -> Unit = {},
    showFloatingOrb: Boolean,
    onToggleFloatingOrb: (Boolean) -> Unit,
    activeOrbActions: Set<OrbActionItem>,
    onToggleOrbAction: (OrbActionItem) -> Unit,
    orbActionOrder: List<OrbActionItem>,
    onReorderOrbAction: (fromIndex: Int, toIndex: Int) -> Unit,
    themeFamily: ThemeFamily,
    onThemeFamilyChange: (ThemeFamily) -> Unit,
    themeVariant: ThemeVariant,
    onThemeVariantChange: (ThemeVariant) -> Unit,
    quickThemes: Set<ThemeFamily>,
    onToggleQuickTheme: (ThemeFamily) -> Unit,
    backgroundTexture: BackgroundTexture,
    onBackgroundTextureChange: (BackgroundTexture) -> Unit,
    dynamicRollingTexture: Boolean = true,
    onDynamicRollingTextureChange: (Boolean) -> Unit = {},
    customBgUri: String,
    onCustomBgUriChange: (String) -> Unit,
    typeface: TypefaceMode,
    onTypefaceChange: (TypefaceMode) -> Unit,
    quickFonts: Set<TypefaceMode>,
    onToggleQuickFont: (TypefaceMode) -> Unit,
    lineHeight: Float,
    onLineHeightChange: (Float) -> Unit,
    letterSpacing: Float,
    onLetterSpacingChange: (Float) -> Unit,
    textAlignment: TextAlignmentMode,
    onTextAlignmentChange: (TextAlignmentMode) -> Unit,
    aiProvider: AiProvider,
    onAiProviderChange: (AiProvider) -> Unit,
    aiBaseUrl: String,
    onAiBaseUrlChange: (String) -> Unit,
    aiModel: String,
    onAiModelChange: (String) -> Unit,
    geminiApiKey: String,
    onGeminiApiKeyChange: (String) -> Unit,
    openLibraryApiKey: String = "",
    onOpenLibraryApiKeyChange: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Navigation state
    var currentSubScreen by rememberSaveable { mutableStateOf(SettingsSubScreen.MENU) }
    BackHandler(enabled = currentSubScreen != SettingsSubScreen.MENU) {
        currentSubScreen = SettingsSubScreen.MENU
    }

    DisposableEffect(Unit) {
        onDispose {
            EdgeTtsService.stopVoiceDemo()
        }
    }

    // Repository states
    val assistantOrbStyleState = repository?.assistantOrbStyle?.collectAsState(initial = "EDGE_DOT")
    val assistantOrbStyle = assistantOrbStyleState?.value ?: "EDGE_DOT"

    val orbSizeState = repository?.orbSize?.collectAsState(initial = OrbSize.NANO)
    val orbSize = orbSizeState?.value ?: OrbSize.NANO

    val orbMenuSizeState = repository?.orbMenuSize?.collectAsState(initial = OrbMenuSize.MEDIUM)
    val orbMenuSize = orbMenuSizeState?.value ?: OrbMenuSize.MEDIUM

    val orbEdgeSnapState = repository?.orbEdgeSnap?.collectAsState(initial = true)
    val orbEdgeSnap = orbEdgeSnapState?.value ?: true

    val orbColorState = repository?.orbColor?.collectAsState(initial = OrbColor.THEME)
    val orbColor = orbColorState?.value ?: OrbColor.THEME

    val customOrbColorState = repository?.customOrbColor?.collectAsState(initial = 0xFF4F46E5L)
    val customOrbColor = customOrbColorState?.value ?: 0xFF4F46E5L

    val orbOpacityState = repository?.orbOpacity?.collectAsState(initial = 0.85f)
    val orbOpacity = orbOpacityState?.value ?: 0.85f

    val spoilerShieldState = repository?.spoilerShield?.collectAsState(initial = true)
    val spoilerShield = spoilerShieldState?.value ?: true

    val autoScrollSpeedState = repository?.autoScrollSpeed?.collectAsState(initial = 1.0f)
    val autoScrollSpeed = autoScrollSpeedState?.value ?: 1.0f

    val localOnlyModeState = repository?.localOnlyMode?.collectAsState(initial = false)
    val localOnlyMode = localOnlyModeState?.value ?: false

    val disableAiState = repository?.disableAi?.collectAsState(initial = false)
    val disableAi = disableAiState?.value ?: false

    val paragraphSpacingState = repository?.paragraphSpacingMultiplier?.collectAsState(initial = 1.2f)
    val paragraphSpacing = paragraphSpacingState?.value ?: 1.2f

    val inlineImageWidthPercentState = repository?.inlineImageWidthPercent?.collectAsState(initial = 100)
    val inlineImageWidthPercent = inlineImageWidthPercentState?.value ?: 100

    val preferredLanguageState = repository?.preferredLanguage?.collectAsState(initial = "auto")
    val preferredLanguage = preferredLanguageState?.value ?: "auto"

    val disableTtsState = repository?.disableTts?.collectAsState(initial = false)
    val disableTts = disableTtsState?.value ?: false

    val ttsEngineState = repository?.ttsEngine?.collectAsState(initial = "EDGE_NEURAL")
    val ttsEngine = ttsEngineState?.value ?: "EDGE_NEURAL"

    val ttsEdgeVoiceState = repository?.ttsEdgeVoice?.collectAsState(initial = "en-US-JennyNeural")
    val ttsEdgeVoice = ttsEdgeVoiceState?.value ?: "en-US-JennyNeural"

    val ttsSpeedState = repository?.ttsSpeed?.collectAsState(initial = 1.0f)
    val ttsSpeed = ttsSpeedState?.value ?: 1.0f

    val disableSttState = repository?.disableStt?.collectAsState(initial = false)
    val disableVoiceInput = disableSttState?.value ?: false

    val autoStartMicState = repository?.autoStartMic?.collectAsState(initial = true)
    val autoStartMic = autoStartMicState?.value ?: true

    val enableFtsIndexingState = repository?.enableFtsIndexing?.collectAsState(initial = false)
    val enableFtsIndexing = enableFtsIndexingState?.value ?: false

    var ftsIndexCount by remember { mutableIntStateOf(0) }
    var imageCacheSizeBytes by remember { mutableLongStateOf(0L) }
    var dbFileSizeBytes by remember { mutableLongStateOf(0L) }

    val horizontalPaddingState = repository?.horizontalPadding?.collectAsState(initial = 20)
    val horizontalPadding = horizontalPaddingState?.value ?: 20

    val verticalPaddingState = repository?.verticalPadding?.collectAsState(initial = 16)
    val verticalPadding = verticalPaddingState?.value ?: 16

    val pagedBottomClearanceDpState = repository?.pagedBottomClearanceDp?.collectAsState(initial = 28)
    val pagedBottomClearanceDp = pagedBottomClearanceDpState?.value ?: 28

    val pagedSafeLinesToRemoveState = repository?.pagedSafeLinesToRemove?.collectAsState(initial = 0)
    val pagedSafeLinesToRemove = pagedSafeLinesToRemoveState?.value ?: 0

    val verticallyCenterPagesState = repository?.verticallyCenterPages?.collectAsState(initial = false)
    val verticallyCenterPages = verticallyCenterPagesState?.value ?: false

    val fillPageToBottomState = repository?.fillPageToBottom?.collectAsState(initial = true)
    val fillPageToBottom = fillPageToBottomState?.value ?: true

    val adaptiveVerticalSpacingState = repository?.adaptiveVerticalSpacing?.collectAsState(initial = false)
    val adaptiveVerticalSpacing = adaptiveVerticalSpacingState?.value ?: false

    val goldenRatioChapterOpenersState = repository?.goldenRatioChapterOpeners?.collectAsState(initial = false)
    val goldenRatioChapterOpeners = goldenRatioChapterOpenersState?.value ?: false

    val pagedSwipeThresholdState = repository?.pagedSwipeThreshold?.collectAsState(initial = 0.08f)
    val pagedSwipeThreshold = pagedSwipeThresholdState?.value ?: 0.08f

    val autoSaveDwellSecondsState = repository?.autoSaveDwellSeconds?.collectAsState(initial = 5)
    val autoSaveDwellSeconds = autoSaveDwellSecondsState?.value ?: 5

    val smartConsecutiveAutoSaveState = repository?.smartConsecutiveAutoSave?.collectAsState(initial = true)
    val smartConsecutiveAutoSave = smartConsecutiveAutoSaveState?.value ?: true

    val autoSaveConsecutivePagesState = repository?.autoSaveConsecutivePages?.collectAsState(initial = 3)
    val autoSaveConsecutivePages = autoSaveConsecutivePagesState?.value ?: 3

    val allowTableSplittingState = repository?.allowTableSplitting?.collectAsState(initial = true)
    val allowTableSplitting = allowTableSplittingState?.value ?: true

    val showStartupLoadingScreenState = repository?.showStartupLoadingScreen?.collectAsState(initial = false)
    val showStartupLoadingScreen = showStartupLoadingScreenState?.value ?: false

    val uncachedBookOpenModeState = repository?.uncachedBookOpenMode?.collectAsState(initial = UncachedBookOpenMode.INSTANT_ACTIVE_FIRST)
    val uncachedBookOpenMode = uncachedBookOpenModeState?.value ?: UncachedBookOpenMode.INSTANT_ACTIVE_FIRST

    val imageFilterModeState = repository?.imageFilterMode?.collectAsState(initial = ImageFilterMode.ORIGINAL)
    val imageFilterMode = imageFilterModeState?.value ?: ImageFilterMode.ORIGINAL

    val invertScreenState = repository?.invertScreen?.collectAsState(initial = false)
    val invertScreen = invertScreenState?.value ?: false

    val fontSizeState = repository?.fontSize?.collectAsState(initial = fontSize)
    val currentFontSize = fontSizeState?.value ?: fontSize

    val readingModeState = repository?.readingMode?.collectAsState(initial = readingMode)
    val currentReadingMode = readingModeState?.value ?: readingMode

    val customThemesState = repository?.customThemes?.collectAsState(initial = emptyList())
    val customThemes = customThemesState?.value ?: emptyList()

    val customEndpointsState = repository?.customEndpoints?.collectAsState(initial = emptyList())
    val customEndpoints = customEndpointsState?.value ?: emptyList()

    val customTexturesState = repository?.customTextures?.collectAsState(initial = emptyList())
    val customTextures = customTexturesState?.value ?: emptyList()

    val selectedCustomTextureIdState = repository?.selectedCustomTextureId?.collectAsState(initial = "")
    val selectedCustomTextureId = selectedCustomTextureIdState?.value ?: ""

    // Update storage stats when entering STORAGE_BACKUP
    LaunchedEffect(currentSubScreen) {
        if (currentSubScreen == SettingsSubScreen.STORAGE_BACKUP && repository != null) {
            withContext(Dispatchers.IO) {
                val fts = repository.getFtsIndexCount()
                val img = repository.getImageCacheSizeBytes()
                val db = repository.getDatabaseSizeBytes()
                withContext(Dispatchers.Main) {
                    ftsIndexCount = fts
                    imageCacheSizeBytes = img
                    dbFileSizeBytes = db
                }
            }
        }
    }

    // Custom Texture Dialog state
    var showTextureImportDialog by remember { mutableStateOf(false) }
    var pendingTextureUri by remember { mutableStateOf<Uri?>(null) }
    var textureImportName by remember { mutableStateOf("") }
    var textureImportIsTiled by remember { mutableStateOf(true) }
    var textureImportOpacity by remember { mutableStateOf(0.5f) }

    var textureToRename by remember { mutableStateOf<CustomTextureData?>(null) }
    var renameTextureInput by remember { mutableStateOf("") }
    var textureToDelete by remember { mutableStateOf<CustomTextureData?>(null) }

    var themeToRename by remember { mutableStateOf<CustomThemeData?>(null) }
    var renameThemeInput by remember { mutableStateOf("") }

    val texturePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingTextureUri = uri
            textureImportName = "Custom Texture ${customTextures.size + 1}"
            textureImportIsTiled = true
            textureImportOpacity = 0.5f
            showTextureImportDialog = true
        }
    }

    val gestureDoubleTap by (repository?.gestureDoubleTap?.collectAsState(initial = GestureAction.TOGGLE_AUTOSCROLL) ?: remember { mutableStateOf(GestureAction.TOGGLE_AUTOSCROLL) })
    val gestureTripleTap by (repository?.gestureTripleTap?.collectAsState(initial = GestureAction.SUMMON_ORB) ?: remember { mutableStateOf(GestureAction.SUMMON_ORB) })
    val gestureSingleTap by (repository?.gestureSingleTap?.collectAsState(initial = GestureAction.TOGGLE_BARS) ?: remember { mutableStateOf(GestureAction.TOGGLE_BARS) })
    val gestureTtsTap by (repository?.gestureTtsTap?.collectAsState(initial = GestureAction.TTS_READ_ALOUD) ?: remember { mutableStateOf(GestureAction.TTS_READ_ALOUD) })

    var exportedCssContent by remember { mutableStateOf("") }

    val bgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            onCustomBgUriChange(uri.toString())
            Toast.makeText(context, "Custom wallpaper applied", Toast.LENGTH_SHORT).show()
        }
    }

    val cssFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: ""
                if (text.isNotBlank()) {
                    val parsed = parseCssToTheme(text)
                    val name = parsed.name.ifBlank { "Imported Theme ${customThemes.size + 1}" }
                    val bg = parsed.bg ?: Color(0xFF1E1E1E)
                    val txt = parsed.text ?: Color(0xFFE0E0E0)
                    val acc = parsed.accent ?: Color(0xFF64FFDA)
                    repository?.saveCustomTheme(
                        name = name,
                        bg = bg.toArgb().toLong(),
                        text = txt.toArgb().toLong(),
                        accent = acc.toArgb().toLong()
                    )
                    Toast.makeText(context, "Theme '$name' loaded from CSS file!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load CSS: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cssExportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/css")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.writer().use { it.write(exportedCssContent) }
                }
                Toast.makeText(context, "Theme CSS saved to file!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save CSS: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentSubScreen.title,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Normal,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentSubScreen.subtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentSubScreen != SettingsSubScreen.MENU) {
                            currentSubScreen = SettingsSubScreen.MENU
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Navigation Menu
            if (currentSubScreen == SettingsSubScreen.MENU) {
                val menuItems = listOf(
                    SettingsSubScreen.THEMES_TYPOGRAPHY to "Palettes, fonts, line spacing, margins, visual filters & inverted screen",
                    SettingsSubScreen.CUSTOM_CSS to "Custom CSS palette builder, CSS code import/export, and wallpapers",
                    SettingsSubScreen.READING_CONTROLS to "Assistant orb, customizable actions, auto-save cadence, and Edge TTS audio",
                    SettingsSubScreen.AI_INTELLIGENCE to "Gemini, OpenAI-compatible local AI, offline command palette, voice STT",
                    SettingsSubScreen.STORAGE_BACKUP to "Reclaim disk space, manage caches, unified JSON backup & restore, AGPLv3 license"
                )

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = if (localOnlyMode) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (localOnlyMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (localOnlyMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (localOnlyMode) Icons.Default.CloudOff else Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = if (localOnlyMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Local-Only Mode",
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (localOnlyMode) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(
                                                text = "OFFLINE",
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Normal,
                                                letterSpacing = 0.5.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (localOnlyMode) "All online features disabled. Operating strictly offline." else "Turn off all online features, cloud AI, and network requests.",
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = localOnlyMode,
                                onCheckedChange = { repository?.setLocalOnlyMode(it) }
                            )
                        }
                    }
                }

                items(menuItems) { (screen, desc) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { currentSubScreen = screen },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = screen.title,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Sub-screens
            if (currentSubScreen == SettingsSubScreen.THEMES_TYPOGRAPHY) {
                item {
                    SettingsThemesTypographySection(
                        repository = repository,
                        currentReadingMode = currentReadingMode,
                        onReadingModeChange = onReadingModeChange,
                        themeVariant = themeVariant,
                        onThemeVariantChange = onThemeVariantChange,
                        themeFamily = themeFamily,
                        onThemeFamilyChange = onThemeFamilyChange,
                        customThemes = customThemes,
                        onNavigateToCustomCss = { currentSubScreen = SettingsSubScreen.CUSTOM_CSS },
                        onRenameTheme = { theme ->
                            themeToRename = theme
                            renameThemeInput = theme.name
                        },
                        backgroundTexture = backgroundTexture,
                        onBackgroundTextureChange = onBackgroundTextureChange,
                        customTextures = customTextures,
                        selectedCustomTextureId = selectedCustomTextureId,
                        onPickTexture = { texturePickerLauncher.launch("image/*") },
                        onRenameTexture = { tex ->
                            textureToRename = tex
                            renameTextureInput = tex.name
                        },
                        onDeleteTexture = { tex ->
                            textureToDelete = tex
                        },
                        dynamicRollingTexture = dynamicRollingTexture,
                        onDynamicRollingTextureChange = onDynamicRollingTextureChange,
                        horizontalPadding = horizontalPadding,
                        verticalPadding = verticalPadding,
                        pagedBottomClearanceDp = pagedBottomClearanceDp,
                        pagedSafeLinesToRemove = pagedSafeLinesToRemove,
                        fillPageToBottom = fillPageToBottom,
                        verticallyCenterPages = verticallyCenterPages,
                        adaptiveVerticalSpacing = adaptiveVerticalSpacing,
                        goldenRatioChapterOpeners = goldenRatioChapterOpeners,
                        allowTableSplitting = allowTableSplitting,
                        pagedSwipeThreshold = pagedSwipeThreshold,
                        typeface = typeface,
                        onTypefaceChange = onTypefaceChange,
                        currentFontSize = currentFontSize,
                        onFontSizeChange = onFontSizeChange,
                        lineHeight = lineHeight,
                        onLineHeightChange = onLineHeightChange,
                        letterSpacing = letterSpacing,
                        onLetterSpacingChange = onLetterSpacingChange,
                        paragraphSpacing = paragraphSpacing,
                        inlineImageWidthPercent = inlineImageWidthPercent,
                        textAlignment = textAlignment,
                        onTextAlignmentChange = onTextAlignmentChange,
                        imageFilterMode = imageFilterMode,
                        invertScreen = invertScreen
                    )
                }
            }

            if (currentSubScreen == SettingsSubScreen.CUSTOM_CSS) {
                item {
                    SettingsCustomCssSection(
                        repository = repository,
                        customThemes = customThemes,
                        onThemeFamilyChange = onThemeFamilyChange,
                        onImportCss = { cssFilePickerLauncher.launch("*/*") },
                        onExportCss = { css ->
                            exportedCssContent = css
                            cssExportFileLauncher.launch("theme.css")
                        },
                        onPickWallpaper = { bgPickerLauncher.launch("image/*") },
                        customBgUri = customBgUri,
                        onCustomBgUriChange = onCustomBgUriChange,
                        onPickTexture = { texturePickerLauncher.launch("image/*") },
                        backgroundTexture = backgroundTexture,
                        onBackgroundTextureChange = onBackgroundTextureChange,
                        customTextures = customTextures,
                        selectedCustomTextureId = selectedCustomTextureId,
                        onRenameTexture = { tex ->
                            textureToRename = tex
                            renameTextureInput = tex.name
                        },
                        onDeleteTexture = { tex ->
                            textureToDelete = tex
                        },
                        onRenameTheme = { theme ->
                            themeToRename = theme
                            renameThemeInput = theme.name
                        },
                        dynamicRollingTexture = dynamicRollingTexture,
                        onDynamicRollingTextureChange = onDynamicRollingTextureChange
                    )
                }
            }

            if (currentSubScreen == SettingsSubScreen.READING_CONTROLS) {
                item {
                    SettingsReadingControlsSection(
                        repository = repository,
                        autoScrollSpeed = autoScrollSpeed,
                        disableTts = disableTts,
                        ttsEngine = ttsEngine,
                        ttsEdgeVoice = ttsEdgeVoice,
                        ttsSpeed = ttsSpeed,
                        showFloatingOrb = showFloatingOrb,
                        onToggleFloatingOrb = onToggleFloatingOrb,
                        assistantOrbStyle = assistantOrbStyle,
                        orbSize = orbSize,
                        orbMenuSize = orbMenuSize,
                        orbEdgeSnap = orbEdgeSnap,
                        orbColor = orbColor,
                        customOrbColor = customOrbColor,
                        orbOpacity = orbOpacity,
                        gestureDoubleTap = gestureDoubleTap,
                        gestureTripleTap = gestureTripleTap,
                        gestureSingleTap = gestureSingleTap,
                        gestureTtsTap = gestureTtsTap,
                        pagedSwipeThreshold = pagedSwipeThreshold,
                        autoSaveDwellSeconds = autoSaveDwellSeconds,
                        smartConsecutiveAutoSave = smartConsecutiveAutoSave,
                        autoSaveConsecutivePages = autoSaveConsecutivePages,
                        activeOrbActions = activeOrbActions,
                        onToggleOrbAction = onToggleOrbAction,
                        orbActionOrder = orbActionOrder,
                        onReorderOrbAction = onReorderOrbAction
                    )
                }
            }

            if (currentSubScreen == SettingsSubScreen.AI_INTELLIGENCE) {
                item {
                    SettingsAiIntelligenceSection(
                        repository = repository,
                        disableAi = disableAi,
                        aiProvider = aiProvider,
                        onAiProviderChange = onAiProviderChange,
                        geminiApiKey = geminiApiKey,
                        onGeminiApiKeyChange = onGeminiApiKeyChange,
                        aiBaseUrl = aiBaseUrl,
                        onAiBaseUrlChange = onAiBaseUrlChange,
                        aiModel = aiModel,
                        onAiModelChange = onAiModelChange,
                        disableVoiceInput = disableVoiceInput,
                        autoStartMic = autoStartMic,
                        spoilerShield = spoilerShield,
                        preferredLanguage = preferredLanguage
                    )
                }
            }

            if (currentSubScreen == SettingsSubScreen.STORAGE_BACKUP) {
                item {
                    SettingsStorageBackupSection(
                        repository = repository,
                        ftsIndexCount = ftsIndexCount,
                        onFtsIndexCountChange = { ftsIndexCount = it },
                        imageCacheSizeBytes = imageCacheSizeBytes,
                        onClearImageCache = {
                            repository?.clearImageCache()
                            imageCacheSizeBytes = 0L
                            Toast.makeText(context, "Image cache cleared", Toast.LENGTH_SHORT).show()
                        },
                        dbFileSizeBytes = dbFileSizeBytes,
                        enableFtsIndexing = enableFtsIndexing,
                        showStartupLoadingScreen = showStartupLoadingScreen,
                        uncachedBookOpenMode = uncachedBookOpenMode,
                        openLibraryApiKey = openLibraryApiKey,
                        onOpenLibraryApiKeyChange = onOpenLibraryApiKeyChange,
                        customEndpoints = customEndpoints
                    )
                }
            }

            // Bottom space for safe scrolling
            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Modal Dialogs for Custom Textures & Custom Themes

    // Import & Name Custom Texture Dialog
    if (showTextureImportDialog && pendingTextureUri != null) {
        AlertDialog(
            onDismissRequest = {
                showTextureImportDialog = false
                pendingTextureUri = null
            },
            title = {
                Text(
                    text = "Name Custom Texture",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Give your imported surface texture a name:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = textureImportName,
                        onValueChange = { textureImportName = it },
                        label = { Text("Texture Name", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Vintage Japanese Washi", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Seamless Tiling", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                            Text(
                                if (textureImportIsTiled) "Repeats as seamless pattern" else "Stretches to cover page",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = textureImportIsTiled,
                            onCheckedChange = { textureImportIsTiled = it }
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Overlay Opacity", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                            Text("${(textureImportOpacity * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = textureImportOpacity,
                            onValueChange = { textureImportOpacity = it },
                            valueRange = 0.1f..1.0f,
                            steps = 17
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = textureImportName.trim().ifBlank { "Custom Texture ${customTextures.size + 1}" }
                        val uri = pendingTextureUri
                        if (uri != null) {
                            val created = repository?.importCustomTexture(name, uri, textureImportIsTiled, textureImportOpacity)
                            if (created != null) {
                                Toast.makeText(context, "Texture '$name' saved and applied", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to import texture", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showTextureImportDialog = false
                        pendingTextureUri = null
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save & Apply", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTextureImportDialog = false
                    pendingTextureUri = null
                }) {
                    Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
            }
        )
    }

    // Rename Custom Texture Dialog
    if (textureToRename != null) {
        AlertDialog(
            onDismissRequest = { textureToRename = null },
            title = {
                Text(
                    text = "Rename Surface Texture",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a new name for this texture:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = renameTextureInput,
                        onValueChange = { renameTextureInput = it },
                        label = { Text("Texture Name", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = renameTextureInput.trim()
                        val target = textureToRename
                        if (newName.isNotBlank() && target != null) {
                            repository?.renameCustomTexture(target.id, newName)
                            Toast.makeText(context, "Renamed to '$newName'", Toast.LENGTH_SHORT).show()
                        }
                        textureToRename = null
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
            },
            dismissButton = {
                TextButton(onClick = { textureToRename = null }) {
                    Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
            }
        )
    }

    // Delete Custom Texture Confirmation Dialog
    if (textureToDelete != null) {
        AlertDialog(
            onDismissRequest = { textureToDelete = null },
            title = {
                Text(
                    text = "Delete Custom Texture?",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${textureToDelete?.name}'? This cannot be undone.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = textureToDelete
                        if (target != null) {
                            repository?.deleteCustomTexture(target.id)
                            Toast.makeText(context, "Texture '${target.name}' deleted", Toast.LENGTH_SHORT).show()
                        }
                        textureToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
            },
            dismissButton = {
                TextButton(onClick = { textureToDelete = null }) {
                    Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
            }
        )
    }

    // Rename Custom Theme Dialog
    if (themeToRename != null) {
        AlertDialog(
            onDismissRequest = { themeToRename = null },
            title = {
                Text(
                    text = "Rename Custom Theme",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a new name for this theme:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = renameThemeInput,
                        onValueChange = { renameThemeInput = it },
                        label = { Text("Theme Name", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = renameThemeInput.trim()
                        val target = themeToRename
                        if (newName.isNotBlank() && target != null) {
                            repository?.renameCustomTheme(target.id, newName)
                            Toast.makeText(context, "Renamed to '$newName'", Toast.LENGTH_SHORT).show()
                        }
                        themeToRename = null
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
            },
            dismissButton = {
                TextButton(onClick = { themeToRename = null }) {
                    Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
            }
        )
    }
}
