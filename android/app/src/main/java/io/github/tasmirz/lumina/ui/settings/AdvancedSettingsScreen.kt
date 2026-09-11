package io.github.tasmirz.lumina.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.tasmirz.lumina.data.AiProvider
import io.github.tasmirz.lumina.data.BookRepository
import io.github.tasmirz.lumina.data.EdgeTtsService
import java.util.Locale
import io.github.tasmirz.lumina.model.BackgroundTexture
import io.github.tasmirz.lumina.model.CustomThemeData
import io.github.tasmirz.lumina.model.GestureAction
import io.github.tasmirz.lumina.model.OrbActionItem
import io.github.tasmirz.lumina.model.OrbSize
import io.github.tasmirz.lumina.model.OrbMenuSize
import io.github.tasmirz.lumina.model.OrbColor
import io.github.tasmirz.lumina.model.TextAlignmentMode
import io.github.tasmirz.lumina.model.ThemeFamily
import io.github.tasmirz.lumina.model.ThemeVariant
import io.github.tasmirz.lumina.model.TypefaceMode

enum class SettingsSubScreen(val title: String, val subtitle: String, val icon: ImageVector) {
    MENU("Advanced Settings", "Personalization, Storage & FOSS Engine", Icons.Default.Settings),
    THEMES_TYPOGRAPHY("Themes, Typography & Textures", "Palettes, fonts, line spacing, margins", Icons.Outlined.Palette),
    CUSTOM_CSS("Custom Theme & CSS Engine", "Palette builder, custom CSS import/export", Icons.Outlined.Brush),
    READING_CONTROLS("Reading Controls & Audio", "Floating orb, actions, TTS, auto-scroll", Icons.Outlined.TouchApp),
    AI_INTELLIGENCE("AI Assistant & Intelligence", "Offline command palette, Gemini, custom endpoints", Icons.Outlined.AutoAwesome),
    STORAGE_BACKUP("Storage, Backup & AGPLv3", "Cache management, index cleaner, export/import", Icons.Outlined.Storage)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    repository: BookRepository? = null,
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
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var keyText by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }

    var isOpenLibraryKeyVisible by remember { mutableStateOf(false) }
    val openLibraryKeyRepo = repository?.openLibraryApiKey?.collectAsState(initial = openLibraryApiKey)?.value ?: openLibraryApiKey
    var openLibraryKeyText by remember(openLibraryKeyRepo) { mutableStateOf(openLibraryKeyRepo) }

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

    val orbOpacityState = repository?.orbOpacity?.collectAsState(initial = 0.85f)
    val orbOpacity = orbOpacityState?.value ?: 0.85f

    val spoilerShieldState = repository?.spoilerShield?.collectAsState(initial = true)
    val spoilerShield = spoilerShieldState?.value ?: true

    val autoScrollSpeedState = repository?.autoScrollSpeed?.collectAsState(initial = 1.0f)
    val autoScrollSpeed = autoScrollSpeedState?.value ?: 1.0f

    val disableAiState = repository?.disableAi?.collectAsState(initial = false)
    val disableAi = disableAiState?.value ?: false

    val paragraphSpacingState = repository?.paragraphSpacingMultiplier?.collectAsState(initial = 1.2f)
    val paragraphSpacing = paragraphSpacingState?.value ?: 1.2f

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

    val ttsPitchState = repository?.ttsPitch?.collectAsState(initial = 1.0f)
    val ttsPitch = ttsPitchState?.value ?: 1.0f

    val disableSttState = repository?.disableStt?.collectAsState(initial = false)
    val disableStt = disableSttState?.value ?: false

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

    val customThemesState = repository?.customThemes?.collectAsState(initial = emptyList())
    val customThemes = customThemesState?.value ?: emptyList()

    // Sub-screen navigation state
    var currentSubScreen by rememberSaveable { mutableStateOf(SettingsSubScreen.MENU) }
    BackHandler(enabled = currentSubScreen != SettingsSubScreen.MENU) {
        currentSubScreen = SettingsSubScreen.MENU
    }

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

    // Custom Theme Builder state
    var customThemeName by remember { mutableStateOf("") }
    var customBgColor by remember { mutableStateOf(Color(0xFF1E1E1E)) }
    var customTextColor by remember { mutableStateOf(Color(0xFFE0E0E0)) }
    var customAccentColor by remember { mutableStateOf(Color(0xFF64FFDA)) }

    // Dialog states
    var showCssImportDialog by remember { mutableStateOf(false) }
    var showCssExportDialog by remember { mutableStateOf(false) }
    var exportedCssContent by remember { mutableStateOf("") }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var backupJsonInput by remember { mutableStateOf("") }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var themeToRename by remember { mutableStateOf<CustomThemeData?>(null) }
    var renameThemeInput by remember { mutableStateOf("") }

    // Launcher for selecting custom background image
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

    // Launcher for importing backup JSON file
    val backupFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: ""
                if (jsonString.isNotBlank()) {
                    val success = repository?.importUnifiedBackupJson(jsonString) == true
                    if (success) {
                        Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to parse backup file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Import error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Gesture shortcut mappings from repository
    val gestureDoubleTap by (repository?.gestureDoubleTap?.collectAsState(initial = GestureAction.TOGGLE_AUTOSCROLL) ?: remember { mutableStateOf(GestureAction.TOGGLE_AUTOSCROLL) })
    val gestureTripleTap by (repository?.gestureTripleTap?.collectAsState(initial = GestureAction.SUMMON_ORB) ?: remember { mutableStateOf(GestureAction.SUMMON_ORB) })
    val gestureSingleTap by (repository?.gestureSingleTap?.collectAsState(initial = GestureAction.TOGGLE_BARS) ?: remember { mutableStateOf(GestureAction.TOGGLE_BARS) })
    val gestureTtsTap by (repository?.gestureTtsTap?.collectAsState(initial = GestureAction.TTS_READ_ALOUD) ?: remember { mutableStateOf(GestureAction.TTS_READ_ALOUD) })

    var editingGestureName by remember { mutableStateOf<String?>(null) }
    var currentEditingAction by remember { mutableStateOf<GestureAction?>(null) }
    var onSaveGestureAction by remember { mutableStateOf<((GestureAction) -> Unit)?>(null) }

    // Launcher for importing CSS from file
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

    // Launcher for exporting CSS to file
    val cssExportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/css")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.writer().use { it.write(exportedCssContent) }
                }
                Toast.makeText(context, "Theme CSS saved to file!", Toast.LENGTH_SHORT).show()
                showCssExportDialog = false
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save CSS: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = currentSubScreen != SettingsSubScreen.MENU) {
        currentSubScreen = SettingsSubScreen.MENU
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentSubScreen.title,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = currentSubScreen.subtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentSubScreen == SettingsSubScreen.MENU) {
                            onBack()
                        } else {
                            currentSubScreen = SettingsSubScreen.MENU
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (currentSubScreen == SettingsSubScreen.MENU) {
                val menuItems = listOf(
                    SettingsSubScreen.THEMES_TYPOGRAPHY to "Select curated themes, customize typography, line height, letter spacing, background textures, and reading margins.",
                    SettingsSubScreen.CUSTOM_CSS to "Build custom themes with color pickers, import/export CSS style sheets, and manage wallpaper backgrounds.",
                    SettingsSubScreen.READING_CONTROLS to "Configure the 2-layer floating assistant orb, active quick actions, auto-scroll speed, and TTS audio toggles.",
                    SettingsSubScreen.AI_INTELLIGENCE to "Toggle generative AI on/off for 100% offline privacy, configure Gemini API keys, or connect to custom OpenAI-compatible endpoints.",
                    SettingsSubScreen.STORAGE_BACKUP to "Manage live FTS5 search index and image caches, export/import complete unified JSON backups, and view AGPLv3 terms."
                )

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
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = screen.title,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.SemiBold,
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

            // =============================================================
            // SECTION 0: THEMES, TYPOGRAPHY & MARGINS
            // =============================================================
            if (currentSubScreen == SettingsSubScreen.THEMES_TYPOGRAPHY) {
                item {
                    CollapsibleCard(
                        icon = Icons.Outlined.Palette,
                        title = "Themes, Typography & Margins",
                        subtitle = "${typeface.displayName} • Side Margin: ${horizontalPadding}dp"
                    ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        // Theme Variant Chips
                        Text(
                            text = "Color Mode",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ThemeVariant.entries.forEach { variant ->
                                val isSel = variant == themeVariant
                                FilterChip(
                                    selected = isSel,
                                    onClick = { onThemeVariantChange(variant) },
                                    label = { Text(variant.displayName, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Themes Palette
                        Text(
                            text = "Preset Palettes",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ThemeFamily.entries.forEach { family ->
                                val isSel = family == themeFamily
                                FilterChip(
                                    selected = isSel,
                                    onClick = { onThemeFamilyChange(family) },
                                    label = { Text(family.displayName, fontSize = 11.sp) }
                                )
                            }
                        }

                        if (customThemes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Custom Named Themes",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { currentSubScreen = SettingsSubScreen.CUSTOM_CSS },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("Manage", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                customThemes.forEach { th ->
                                    val isSel = themeFamily == ThemeFamily.CUSTOM &&
                                        repository != null &&
                                        th.bgColor == repository.customBgColor.value &&
                                        th.textColor == repository.customTextColor.value
                                    FilterChip(
                                        selected = isSel,
                                        onClick = {
                                            repository?.applyCustomTheme(th)
                                            onThemeFamilyChange(ThemeFamily.CUSTOM)
                                            Toast.makeText(context, "Applied '${th.name}'", Toast.LENGTH_SHORT).show()
                                        },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(th.bgColor.toInt()))
                                            )
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    themeToRename = th
                                                    renameThemeInput = th.name
                                                },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Rename",
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        },
                                        label = { Text(th.name, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Page Margins & Spacing
                        Text(
                            text = "Page Margins & Padding",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Horizontal Padding
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Side Margin (Horizontal)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${horizontalPadding} dp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = horizontalPadding.toFloat(),
                            onValueChange = { repository?.setHorizontalPadding(it.toInt()) },
                            valueRange = 8f..48f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Vertical Padding
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Top / Bottom Margin (Vertical)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${verticalPadding} dp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = verticalPadding.toFloat(),
                            onValueChange = { repository?.setVerticalPadding(it.toInt()) },
                            valueRange = 0f..48f,
                            steps = 11,
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Typography Engine Controls
                        Text(
                            text = "Typography Engine",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Typeface Selector
                        Text("Reading Typeface", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TypefaceMode.entries.forEach { mode ->
                                val isSel = mode == typeface
                                FilterChip(
                                    selected = isSel,
                                    onClick = { onTypefaceChange(mode) },
                                    label = { Text(mode.displayName, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Line Height
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Line Spacing", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format("%.2f", lineHeight)}x", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = lineHeight,
                            onValueChange = onLineHeightChange,
                            valueRange = 1.0f..2.2f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Letter Spacing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Letter Spacing", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format("%.2f", letterSpacing)}sp", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = letterSpacing,
                            onValueChange = onLetterSpacingChange,
                            valueRange = 0.0f..2.5f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Paragraph Spacing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Paragraph Spacing", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format("%.2f", paragraphSpacing)}x", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = paragraphSpacing,
                            onValueChange = { repository?.setParagraphSpacing(it) },
                            valueRange = 0.6f..2.4f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Text Alignment
                        Text("Text Alignment", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TextAlignmentMode.entries.forEach { align ->
                                val isSel = align == textAlignment
                                FilterChip(
                                    selected = isSel,
                                    onClick = { onTextAlignmentChange(align) },
                                    label = { Text(align.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }
            }

            // =============================================================
            // SECTION 1: CUSTOM CSS THEMES & WALLPAPER (DEDICATED SECTION)
            // =============================================================
            if (currentSubScreen == SettingsSubScreen.CUSTOM_CSS) {
                item {
                    CollapsibleCard(
                        icon = Icons.Outlined.ColorLens,
                        title = "Custom CSS Themes & Wallpaper",
                        subtitle = "${customThemes.size} custom themes • CSS Import/Export"
                    ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Custom Palette Builder",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Design color schemes with background, text, and accent styling, or load/export CSS files.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showCssImportDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Import CSS", fontSize = 11.sp, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val themeName = customThemeName.ifBlank { "Custom Theme" }
                                        exportedCssContent = generateCssSnippet(
                                            name = themeName,
                                            bg = customBgColor,
                                            text = customTextColor,
                                            accent = customAccentColor,
                                            wallpaper = customBgUri.ifBlank { "none" }
                                        )
                                        showCssExportDialog = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Export CSS", fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = customThemeName,
                            onValueChange = { customThemeName = it },
                            placeholder = { Text("Theme Name (e.g. Cyberpunk / Nord)", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Background Color Row
                        Text("Background Color", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        ColorSwatchScrollRow(
                            selectedColor = customBgColor,
                            onColorSelected = { customBgColor = it },
                            presets = listOf(
                                Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFF0F172A),
                                Color(0xFFFBF0D9), Color(0xFFF4ECD8), Color(0xFF0A192F),
                                Color(0xFF263238), Color(0xFF1A1A2E), Color(0xFF2E3440)
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Text Color Row
                        Text("Text Color", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        ColorSwatchScrollRow(
                            selectedColor = customTextColor,
                            onColorSelected = { customTextColor = it },
                            presets = listOf(
                                Color(0xFFE0E0E0), Color(0xFFFFFFFF), Color(0xFF3C2F2F),
                                Color(0xFF64FFDA), Color(0xFFE2E8F0), Color(0xFFFFD54F),
                                Color(0xFFB0BEC5), Color(0xFF81C784), Color(0xFFECEFF4)
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Accent Color Row
                        Text("Accent Color", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        ColorSwatchScrollRow(
                            selectedColor = customAccentColor,
                            onColorSelected = { customAccentColor = it },
                            presets = listOf(
                                Color(0xFF64FFDA), Color(0xFFFF9800), Color(0xFF00B0FF),
                                Color(0xFFE91E63), Color(0xFF4CAF50), Color(0xFF9C27B0),
                                Color(0xFF88C0D0), Color(0xFFFF7043)
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Live Preview Box
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = customBgColor,
                            border = BorderStroke(1.dp, customAccentColor.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = customThemeName.ifBlank { "Chapter I. The Celestial Journey" },
                                    color = customAccentColor,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "The quiet hum of the night gave way to golden dawn across the endless pages.",
                                    color = customTextColor,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontFamily = FontFamily.Serif
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val name = customThemeName.trim().ifBlank { "Custom Theme ${customThemes.size + 1}" }
                                repository?.saveCustomTheme(
                                    name = name,
                                    bg = customBgColor.toArgb().toLong(),
                                    text = customTextColor.toArgb().toLong(),
                                    accent = customAccentColor.toArgb().toLong()
                                )
                                Toast.makeText(context, "Theme '$name' saved to SQLite", Toast.LENGTH_SHORT).show()
                                customThemeName = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Theme to Library", fontSize = 12.sp)
                        }

                        // Saved Themes List
                        if (customThemes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Saved Themes (${customThemes.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                customThemes.forEach { th ->
                                    val bg = Color(th.bgColor.toInt())
                                    val txt = Color(th.textColor.toInt())
                                    val acc = Color(th.accentColor.toInt())
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(bg))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(txt))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(acc))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = th.name,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        themeToRename = th
                                                        renameThemeInput = th.name
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Rename Theme", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(15.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        exportedCssContent = generateCssSnippet(
                                                            name = th.name,
                                                            bg = bg,
                                                            text = txt,
                                                            accent = acc,
                                                            wallpaper = customBgUri.ifBlank { "none" }
                                                        )
                                                        showCssExportDialog = true
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = "Export CSS", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(15.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        repository?.applyCustomTheme(th)
                                                        onThemeFamilyChange(ThemeFamily.CUSTOM)
                                                        Toast.makeText(context, "Applied '${th.name}'", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = "Apply", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        repository?.deleteCustomTheme(th.id)
                                                        Toast.makeText(context, "Deleted '${th.name}'", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Background Textures
                        Text(
                            text = "Background Textures",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BackgroundTexture.entries.forEach { tex ->
                                val isSel = tex == backgroundTexture
                                FilterChip(
                                    selected = isSel,
                                    onClick = { onBackgroundTextureChange(tex) },
                                    label = { Text(tex.displayName, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Custom Wallpaper", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                if (customBgUri.isNotBlank()) {
                                    Text("Image active", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Row {
                                if (customBgUri.isNotBlank()) {
                                    TextButton(onClick = { onCustomBgUriChange("") }) {
                                        Text("Clear", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                Button(
                                    onClick = { bgPickerLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(if (customBgUri.isNotBlank()) "Change" else "Choose Image", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
            }

            // =============================================================
            // SECTION 2: READING CONTROLS, GESTURES & AUDIO
            // =============================================================
            if (currentSubScreen == SettingsSubScreen.READING_CONTROLS) {
                item {
                    CollapsibleCard(
                        icon = Icons.Outlined.TouchApp,
                        title = "Reading Controls, Gestures & Audio",
                        subtitle = "Speed ${String.format("%.1f", autoScrollSpeed)}x • ${if (disableTts) "TTS Engine OFF" else "TTS Engine ON"}"
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            // Master Toggle for TTS Audio
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.VolumeUp,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Text-to-Speech (TTS) Voice Engine",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (disableTts) "TTS engine is disabled. Speech synthesis and background audio controls are hidden." else "TTS engine is active. Lumina can read books aloud using Android's local speech engine.",
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = !disableTts,
                                    onCheckedChange = { repository?.setDisableTts(!it) }
                                )
                            }

                            if (!disableTts) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Audio Engine Mode",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isEdge = ttsEngine == "EDGE_NEURAL"
                                    Surface(
                                        onClick = { repository?.setTtsEngine("EDGE_NEURAL") },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isEdge) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, if (isEdge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(text = "Edge Neural (Audiobook)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                            Text(text = "Natural human voice synthesis", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    val isSystem = ttsEngine == "SYSTEM"
                                    Surface(
                                        onClick = { repository?.setTtsEngine("SYSTEM") },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSystem) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, if (isSystem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(text = "System TTS", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                            Text(text = "On-device speech engine", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                if (ttsEngine == "EDGE_NEURAL") {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Neural Voice Persona",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        EdgeTtsService.AVAILABLE_VOICES.forEach { voice ->
                                            val isSelected = ttsEdgeVoice == voice.id
                                            Surface(
                                                onClick = { repository?.setTtsEdgeVoice(voice.id) },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = { repository?.setTtsEdgeVoice(voice.id) },
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(text = voice.displayName, fontWeight = FontWeight.Medium, fontSize = 11.5.sp)
                                                        Text(text = voice.description, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Narration Speed: ${String.format(Locale.US, "%.2f", ttsSpeed)}x",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                                Slider(
                                    value = ttsSpeed,
                                    onValueChange = { repository?.setTtsSpeed(it) },
                                    valueRange = 0.5f..2.0f,
                                    steps = 5,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )

                            // Master Toggle for Floating Assistant Orb
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Floating Assistant Orb",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (showFloatingOrb) "Assistant orb is active with inward action wheel." else "Assistant orb is hidden from reader.",
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = showFloatingOrb,
                                    onCheckedChange = onToggleFloatingOrb
                                )
                            }

                            if (showFloatingOrb) {
                                Spacer(modifier = Modifier.height(12.dp))

                                // Assistant Controller Placement Style
                                Text(
                                    text = "Trigger Placement Layout",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isDot = assistantOrbStyle == "EDGE_DOT"
                                    Surface(
                                        onClick = { repository?.setAssistantOrbStyle("EDGE_DOT") },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isDot) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isDot) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 60.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "Floating Orb",
                                                fontWeight = if (isDot) FontWeight.SemiBold else FontWeight.Medium,
                                                fontSize = 11.5.sp,
                                                color = if (isDot) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "On-page overlay",
                                                fontSize = 9.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    val isTopBar = assistantOrbStyle == "TOP_BAR_BUTTON"
                                    Surface(
                                        onClick = { repository?.setAssistantOrbStyle("TOP_BAR_BUTTON") },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isTopBar) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isTopBar) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 60.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "Top-Bar Button",
                                                fontWeight = if (isTopBar) FontWeight.SemiBold else FontWeight.Medium,
                                                fontSize = 11.5.sp,
                                                color = if (isTopBar) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Header toolbar",
                                                fontSize = 9.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (assistantOrbStyle != "TOP_BAR_BUTTON") {
                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Orb Size Customization
                                    Text(
                                        text = "Orb Edge Dock Size",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Control the size of the docked edge pill and floating orb (default: Nano)",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OrbSize.entries.forEach { size ->
                                            val isSelected = orbSize == size
                                            Surface(
                                                onClick = { repository?.setOrbSize(size) },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = size.displayName,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "${size.dockedWidth}×${size.dockedHeight}dp",
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                     Spacer(modifier = Modifier.height(14.dp))

                                     // Orb Menu Palette Size
                                     Text(
                                         text = "Orb Menu Palette Size",
                                         fontWeight = FontWeight.SemiBold,
                                         fontSize = 12.sp
                                     )
                                     Spacer(modifier = Modifier.height(2.dp))
                                     Text(
                                         text = "Control the size of the radial action wheel and icons around the orb (default: Medium)",
                                         fontSize = 10.5.sp,
                                         color = MaterialTheme.colorScheme.onSurfaceVariant
                                     )
                                     Spacer(modifier = Modifier.height(6.dp))
                                     Row(
                                         modifier = Modifier.fillMaxWidth(),
                                         horizontalArrangement = Arrangement.spacedBy(6.dp)
                                     ) {
                                         OrbMenuSize.entries.forEach { menuSize ->
                                             val isSelected = orbMenuSize == menuSize
                                             Surface(
                                                 onClick = { repository?.setOrbMenuSize(menuSize) },
                                                 shape = RoundedCornerShape(8.dp),
                                                 color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                 border = BorderStroke(
                                                     1.dp,
                                                     if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                 ),
                                                 modifier = Modifier.weight(1f)
                                             ) {
                                                 Column(
                                                     modifier = Modifier
                                                         .fillMaxWidth()
                                                         .padding(vertical = 8.dp),
                                                     horizontalAlignment = Alignment.CenterHorizontally
                                                 ) {
                                                     Text(
                                                         text = menuSize.displayName,
                                                         fontSize = 11.sp,
                                                         fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                         color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                     )
                                                     Text(
                                                         text = "${menuSize.itemSizeDp}dp icons",
                                                         fontSize = 9.sp,
                                                         color = MaterialTheme.colorScheme.onSurfaceVariant
                                                     )
                                                 }
                                             }
                                         }
                                     }

                                     Spacer(modifier = Modifier.height(14.dp))

                                     // Edge Snap Mode
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Edge Snap",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (orbEdgeSnap) "Auto-snaps to screen edges as a docked indicator" else "Free-floating: rests anywhere on the screen",
                                                fontSize = 10.5.sp,
                                                lineHeight = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = orbEdgeSnap,
                                            onCheckedChange = { repository?.setOrbEdgeSnap(it) }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Orb Color Customization
                                    Text(
                                        text = "Orb Color",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Personalize the tint of the floating assistant",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OrbColor.entries.forEach { colorOption ->
                                            val isSelected = orbColor == colorOption
                                            val previewColor = if (colorOption == OrbColor.THEME) MaterialTheme.colorScheme.primary else Color(colorOption.colorValue)
                                            Surface(
                                                onClick = { repository?.setOrbColor(colorOption) },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) previewColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) previewColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .clip(CircleShape)
                                                            .background(previewColor)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = colorOption.displayName,
                                                        fontSize = 9.5.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) previewColor else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Orb Opacity (distraction-free reading)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Orb Opacity (Reading Subtlety)",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "${(orbOpacity * 100).toInt()}%",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Lower opacity makes the orb subtle and non-distracting while reading",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Slider(
                                        value = orbOpacity,
                                        onValueChange = { repository?.setOrbOpacity(it) },
                                        valueRange = 0.25f..1.0f,
                                        steps = 14,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )

                            // Auto-scroll Speed Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auto-Scroll Speed",
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp
                            )
                            Text(
                                text = "${String.format("%.1f", autoScrollSpeed)}x",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = autoScrollSpeed,
                            onValueChange = { repository?.setAutoScrollSpeed(it) },
                            valueRange = 0.5f..3.0f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Gesture Shortcuts Customization
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Gesture Shortcuts",
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp
                            )
                            Text(
                                text = "Tap to customize",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ConfigurableGestureItem(
                                gestureName = "Double-Tap",
                                action = gestureDoubleTap,
                                onClick = {
                                    editingGestureName = "Double-Tap"
                                    currentEditingAction = gestureDoubleTap
                                    onSaveGestureAction = { repository?.setGestureDoubleTap(it) }
                                }
                            )
                            ConfigurableGestureItem(
                                gestureName = "Triple-Tap",
                                action = gestureTripleTap,
                                onClick = {
                                    editingGestureName = "Triple-Tap"
                                    currentEditingAction = gestureTripleTap
                                    onSaveGestureAction = { repository?.setGestureTripleTap(it) }
                                }
                            )
                            ConfigurableGestureItem(
                                gestureName = "Single-Tap",
                                action = gestureSingleTap,
                                onClick = {
                                    editingGestureName = "Single-Tap"
                                    currentEditingAction = gestureSingleTap
                                    onSaveGestureAction = { repository?.setGestureSingleTap(it) }
                                }
                            )
                            ConfigurableGestureItem(
                                gestureName = "Tap in TTS",
                                action = gestureTtsTap,
                                onClick = {
                                    editingGestureName = "Tap in TTS"
                                    currentEditingAction = gestureTtsTap
                                    onSaveGestureAction = { repository?.setGestureTtsTap(it) }
                                }
                            )
                            GestureGuideItem("Drag Down", "Drop dot into bottom bin to dismiss")
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Orb Quick Actions Selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Radial Orb Quick Actions",
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp
                            )
                            Text(
                                text = "${activeOrbActions.size}/${OrbActionItem.entries.size} active",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Toggle actions and use arrows to customize their clockwise position in the radial wheel.",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val displayActions = if (orbActionOrder.isNotEmpty()) orbActionOrder else OrbActionItem.entries
                        displayActions.forEachIndexed { index, item ->
                            val isActive = activeOrbActions.contains(item)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isActive) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else Color.Transparent)
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    onReorderOrbAction(index, index - 1)
                                                }
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Move Up",
                                                modifier = Modifier.size(16.dp),
                                                tint = if (index > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (index < displayActions.size - 1) {
                                                    onReorderOrbAction(index, index + 1)
                                                }
                                            },
                                            enabled = index < displayActions.size - 1,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Move Down",
                                                modifier = Modifier.size(16.dp),
                                                tint = if (index < displayActions.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onToggleOrbAction(item) }
                                    ) {
                                        Text(
                                            text = item.displayName,
                                            fontSize = 12.sp,
                                            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                                        )
                                        Text(
                                            text = item.description,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = isActive,
                                    onCheckedChange = { onToggleOrbAction(item) },
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

            // =============================================================
            // SECTION 3: AI ASSISTANT & INTELLIGENCE (MOVED DOWN)
            // =============================================================
            if (currentSubScreen == SettingsSubScreen.AI_INTELLIGENCE) {
                item {
                    CollapsibleCard(
                        icon = Icons.Default.AutoAwesome,
                        title = "AI Assistant & Intelligence",
                        subtitle = if (disableAi) "AI Disabled (100% Offline Mode)" else if (aiProvider == AiProvider.GEMINI) "Google Gemini • ${if (spoilerShield) "Shield ON" else "Shield OFF"}" else "OpenAI / Custom • ${if (spoilerShield) "Shield ON" else "Shield OFF"}"
                    ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        // Master Switch for AI
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Disable AI Assistant",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                )
                                Text(
                                    text = "Turn off AI features to run 100% locally and offline without external calls or Google Play support.",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = disableAi,
                                onCheckedChange = { repository?.setDisableAi(it) }
                            )
                        }

                        if (!disableAi) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )

                            Text(
                                text = "AI Service Provider",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AiProvider.entries.forEach { provider ->
                                    val isSelected = provider == aiProvider
                                    Surface(
                                        onClick = { onAiProviderChange(provider) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = provider.displayName,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                fontSize = 12.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // API Key Field
                            Text(
                                text = if (aiProvider == AiProvider.GEMINI) "Google Gemini API Key" else "API Key",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = keyText,
                                onValueChange = {
                                    keyText = it
                                    onGeminiApiKeyChange(it)
                                },
                                placeholder = { Text(if (aiProvider == AiProvider.GEMINI) "AIzaSy..." else "sk-...", fontSize = 12.sp) },
                                singleLine = true,
                                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                        Icon(
                                            imageVector = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (isApiKeyVisible) "Hide API key" else "Show API key"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )

                            if (aiProvider == AiProvider.GEMINI) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Get Free Gemini API Key (AI Studio)",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            if (aiProvider == AiProvider.OPENAI_COMPATIBLE) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Base URL (Ollama / Local / Custom)",
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = aiBaseUrl,
                                    onValueChange = onAiBaseUrlChange,
                                    placeholder = { Text("https://api.openai.com/v1 or http://localhost:11434/v1", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Model Name
                            Text(
                                text = "Model Selection",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = aiModel,
                                onValueChange = onAiModelChange,
                                placeholder = { Text("gemini-3.1-flash-lite / gpt-4o-mini", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )

                            // Disable Voice Input (STT) Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.MicOff,
                                            contentDescription = null,
                                            tint = if (disableStt) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Disable Voice Input / STT",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Turn off microphone usage and speech recognition. Type queries in Assistant chat instead.",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = disableStt,
                                    onCheckedChange = { repository?.setDisableStt(it) }
                                )
                            }

                            if (!disableStt) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )

                                // Auto-start Microphone in Assistant Mode
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Outlined.Mic,
                                                contentDescription = null,
                                                tint = if (autoStartMic) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Auto-Start Microphone",
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.5.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Automatically listen when opening the Assistant or Voice action. When off, tap the mic button to speak.",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = autoStartMic,
                                        onCheckedChange = { repository?.setAutoStartMic(it) }
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )

                            // Anti-Spoiler Shield Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.Shield,
                                            contentDescription = null,
                                            tint = if (spoilerShield) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Anti-Spoiler Context Shield",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Only provides past chapters & current page as AI context to prevent story spoilers.",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = spoilerShield,
                                    onCheckedChange = { repository?.setSpoilerShield(it) }
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )

                            // Assistant & Voice Language Selector
                            Text(
                                text = "Assistant & Voice Language",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Select default language for AI summaries, speech-to-text, and voice responses.",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val langs = listOf(
                                "auto" to "Auto (Doc)",
                                "en" to "English",
                                "es" to "Spanish",
                                "fr" to "French",
                                "de" to "German",
                                "bn" to "Bengali",
                                "hi" to "Hindi",
                                "zh" to "Chinese",
                                "ja" to "Japanese"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                langs.forEach { (code, label) ->
                                    val isSel = preferredLanguage == code
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { repository?.setPreferredLanguage(code) },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Local Reader Services & Offline Tools Catalog
                        Text(
                            text = "Local Reader Services & Built-in Tools",
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp
                        )
                        Text(
                            text = "Available 100% locally and privately without network calls, even when AI is disabled.",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AiToolCompactRow("switch_theme", "Change theme mode (Light, Dark, Sepia, Custom)")
                                AiToolCompactRow("jump_to_page", "Navigate to specific chapter or relative location")
                                AiToolCompactRow("search_scene", "Find story scenes using embedded SQLite FTS5 index")
                                AiToolCompactRow("control_tts", "Start or pause continuous voice reading")
                                AiToolCompactRow("toggle_autoscroll", "Enable or disable hands-free auto-scrolling")
                            }
                        }
                    }
                }
            }

            // =============================================================
            // SECTION 4: BACKUP, STORAGE & AGPLv3 LICENSE
            // =============================================================
            if (currentSubScreen == SettingsSubScreen.STORAGE_BACKUP) {
                item {
                    CollapsibleCard(
                        icon = Icons.Outlined.Storage,
                        title = "Storage, Backup & License",
                        subtitle = "FTS5 Index • Image Cache • Unified Backup • AGPLv3"
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            // Granular Storage Dashboard Card
                            Text(
                                text = "Device Storage & Caches",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Manage local index files, cover caches, and database footprint to reclaim internal phone storage.",
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Storage Stat 1: FTS5 Content Index
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Full-Text Deep Search Index",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "$ftsIndexCount chapter scenes indexed",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val isIndexing by (repository?.isIndexingActive?.collectAsState() ?: remember { mutableStateOf(false) })
                                    Button(
                                        onClick = {
                                            val active = repository?.getActiveBook()
                                            if (active != null) {
                                                repository.indexEntireBookNow(active) { count ->
                                                    ftsIndexCount = repository.getFtsIndexCount()
                                                    Toast.makeText(context, "Indexed $count paragraphs", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "No active book to index", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = !isIndexing,
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(if (isIndexing) "Indexing..." else if (ftsIndexCount == 0) "Index Now" else "Re-Index", fontSize = 10.5.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            repository?.clearFtsIndex()
                                            ftsIndexCount = 0
                                            Toast.makeText(context, "Search index cleared", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Clear Index", fontSize = 10.5.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Storage Stat 2: Image & Cover Cache
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Cover & Image Cache",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = formatBytes(imageCacheSizeBytes),
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        repository?.clearImageCache()
                                        imageCacheSizeBytes = 0L
                                        Toast.makeText(context, "Image cache cleared", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Clear Cache", fontSize = 10.5.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Storage Stat 3: SQLite Database & Reading Progress
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SQLite Database Footprint",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = formatBytes(dbFileSizeBytes),
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        repository?.resetReadingProgress()
                                        Toast.makeText(context, "Reading progress reset", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Reset Progress", fontSize = 10.5.sp)
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )

                            // SQLite FTS5 Search Engine Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Enable Full-Text Indexing",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (enableFtsIndexing) "Active. Allows deep scene searches across all imported books." else "Disabled to conserve disk space and battery.",
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = enableFtsIndexing,
                                    onCheckedChange = { repository?.setEnableFtsIndexing(it) }
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )

                            // Unified Backup & Restore Card
                            Text(
                                text = "Unified Data Backup & Restore",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Export and import your entire library progress, custom CSS themes, settings, bookmarks, highlights, and notes in a single portable JSON file.",
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val backupJson = repository?.exportUnifiedBackupJson() ?: ""
                                        if (backupJson.isNotBlank()) {
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, backupJson)
                                                putExtra(Intent.EXTRA_TITLE, "lumina_backup_${System.currentTimeMillis()}.json")
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Export Lumina Backup"))
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export Backup", fontSize = 11.5.sp)
                                }

                                OutlinedButton(
                                    onClick = { showBackupRestoreDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import Backup", fontSize = 11.5.sp)
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )

                            // Open Library & Internet Archive API Key
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Public,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Open Library & Archive.org Key",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Required to borrow or download restricted and lending public domain EPUB books from Open Library & Internet Archive.",
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = openLibraryKeyText,
                                onValueChange = {
                                    openLibraryKeyText = it
                                    onOpenLibraryApiKeyChange(it)
                                },
                                placeholder = { Text("access_key:secret_key or LOW key", fontSize = 12.sp) },
                                singleLine = true,
                                visualTransformation = if (isOpenLibraryKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (openLibraryKeyText.isNotBlank()) {
                                            IconButton(onClick = {
                                                openLibraryKeyText = ""
                                                onOpenLibraryApiKeyChange("")
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear key",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        IconButton(onClick = { isOpenLibraryKeyVisible = !isOpenLibraryKeyVisible }) {
                                            Icon(
                                                imageVector = if (isOpenLibraryKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = if (isOpenLibraryKeyVisible) "Hide key" else "Show key",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        try {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                            if (!clip.isNullOrBlank()) {
                                                openLibraryKeyText = clip.trim()
                                                onOpenLibraryApiKeyChange(clip.trim())
                                                Toast.makeText(context, "API key pasted from clipboard", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not paste from clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Paste Key",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://archive.org/account/s3.php"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Key,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Get Free S3 Keys",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )

                            // AGPLv3 Open Source License
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Gavel,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "AGPLv3 Open Source License",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.5.sp
                                        )
                                        Text(
                                            text = "GNU Affero General Public License v3.0 • F-Droid FOSS",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showLicenseDialog = true },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Terms", fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Lumina is 100% free software. You have complete freedom to run, inspect, modify, and redistribute Lumina without proprietary trackers or paywalls.",
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Bottom space for safe scrolling
            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // =============================================================
    // MODAL DIALOGS
    // =============================================================

    // 1. CSS Import Dialog
    if (showCssImportDialog) {
        var cssInputText by remember { mutableStateOf("") }
        var parsedThemeName by remember { mutableStateOf("") }
        var parsedBg by remember { mutableStateOf<Color?>(null) }
        var parsedText by remember { mutableStateOf<Color?>(null) }
        var parsedAccent by remember { mutableStateOf<Color?>(null) }

        AlertDialog(
            onDismissRequest = { showCssImportDialog = false },
            title = {
                Text(
                    text = "Import CSS Theme",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Paste standard CSS variables or rules. Example:\n:root {\n  --bg: #1e1e1e;\n  --text: #e0e0e0;\n  --accent: #64ffda;\n}",
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // File Picker Button for CSS files
                    OutlinedButton(
                        onClick = {
                            showCssImportDialog = false
                            cssFilePickerLauncher.launch("*/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Load from .css File", fontSize = 12.sp)
                    }

                    OutlinedTextField(
                        value = cssInputText,
                        onValueChange = {
                            cssInputText = it
                            val parsed = parseCssToTheme(it)
                            parsedThemeName = parsed.name
                            parsedBg = parsed.bg
                            parsedText = parsed.text
                            parsedAccent = parsed.accent
                        },
                        placeholder = { Text("Paste CSS here...", fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Live Parsed Preview Box
                    val bg = parsedBg ?: Color(0xFF1E1E1E)
                    val txt = parsedText ?: Color(0xFFE0E0E0)
                    val acc = parsedAccent ?: Color(0xFF64FFDA)

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = bg,
                        border = BorderStroke(1.dp, acc.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (parsedThemeName.isNotBlank()) parsedThemeName else "Theme Preview",
                                color = acc,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Sample text rendered with imported CSS styling.",
                                color = txt,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Serif
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = parsedThemeName.ifBlank { "Imported Theme ${customThemes.size + 1}" }
                        val bg = parsedBg ?: Color(0xFF1E1E1E)
                        val txt = parsedText ?: Color(0xFFE0E0E0)
                        val acc = parsedAccent ?: Color(0xFF64FFDA)

                        repository?.saveCustomTheme(
                            name = name,
                            bg = bg.toArgb().toLong(),
                            text = txt.toArgb().toLong(),
                            accent = acc.toArgb().toLong()
                        )
                        Toast.makeText(context, "Theme '$name' imported & saved!", Toast.LENGTH_SHORT).show()
                        showCssImportDialog = false
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Theme", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCssImportDialog = false }) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    // 2. CSS Export Dialog
    if (showCssExportDialog) {
        AlertDialog(
            onDismissRequest = { showCssExportDialog = false },
            title = {
                Text(
                    text = "Export Theme CSS",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Standard CSS variables for this theme:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = exportedCssContent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            val fileName = "${customThemeName.ifBlank { "theme" }.lowercase().replace(' ', '_')}.css"
                            cssExportFileLauncher.launch(fileName)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save to File", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Lumina Theme CSS", exportedCssContent)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "CSS copied to clipboard", Toast.LENGTH_SHORT).show()
                            showCssExportDialog = false
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Copy CSS", fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, exportedCssContent)
                            type = "text/css"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Theme CSS"))
                    }
                ) {
                    Text("Share", fontSize = 12.sp)
                }
            }
        )
    }

    // Modal to customize Gesture Shortcut action
    if (editingGestureName != null && currentEditingAction != null) {
        AlertDialog(
            onDismissRequest = { editingGestureName = null },
            title = {
                Text(
                    text = "Customize $editingGestureName",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(GestureAction.entries) { action ->
                        val isSelected = currentEditingAction == action
                        Surface(
                            onClick = {
                                onSaveGestureAction?.invoke(action)
                                editingGestureName = null
                                Toast.makeText(context, "$editingGestureName set to ${action.displayName}", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                if (isSelected) 1.dp else 0.5.dp,
                                if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = action.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingGestureName = null }) {
                    Text("Close")
                }
            }
        )
    }

    // 3. Unified Backup Restore Dialog
    if (showBackupRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showBackupRestoreDialog = false },
            title = {
                Text(
                    text = "Restore Complete Backup",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Select a backup file from your storage, or paste JSON content below to restore all library books, settings, custom themes, and notes.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            showBackupRestoreDialog = false
                            backupFilePickerLauncher.launch("*/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick .json Backup File", fontSize = 12.sp)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    OutlinedTextField(
                        value = backupJsonInput,
                        onValueChange = { backupJsonInput = it },
                        placeholder = { Text("Or paste backup JSON content here...", fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                if (backupJsonInput.isNotBlank()) {
                    Button(
                        onClick = {
                            val success = repository?.importUnifiedBackupJson(backupJsonInput) == true
                            if (success) {
                                Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_SHORT).show()
                                showBackupRestoreDialog = false
                            } else {
                                Toast.makeText(context, "Invalid backup JSON format", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Restore Pasted JSON", fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupRestoreDialog = false }) {
                    Text("Close", fontSize = 12.sp)
                }
            }
        )
    }

    // 4. AGPLv3 License Dialog
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = {
                Text(
                    text = "GNU Affero General Public License",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    Text(
                        text = "Version 3, 19 November 2007\nCopyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>\n\n" +
                                "Everyone is permitted to copy and distribute verbatim copies of this license document, but changing it is not allowed.\n\n" +
                                "Preamble:\n" +
                                "The GNU Affero General Public License is a free, copyleft license for software and other kinds of works, specifically designed to ensure cooperation with the community in the case of network server software.\n\n" +
                                "The licenses for most software and other practical works are designed to take away your freedom to share and change the works. By contrast, our General Public Licenses are intended to guarantee your freedom to share and change all versions of a program--to make sure it remains free software for all its users.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // 5. Rename Custom Theme Dialog
    if (themeToRename != null) {
        AlertDialog(
            onDismissRequest = { themeToRename = null },
            title = {
                Text(
                    text = "Rename Custom Theme",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
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
                    Text("Save", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { themeToRename = null }) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }
}

// =============================================================
// HELPER COMPOSABLES & UTILITIES
// =============================================================

@Composable
private fun CollapsibleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isExpanded: Boolean = true,
    onToggle: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            content()
        }
    }
}

@Composable
private fun AiToolCompactRow(
    name: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = desc,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ConfigurableGestureItem(
    gestureName: String,
    action: GestureAction,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gestureName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = action.displayName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Change",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun GestureGuideItem(
    gesture: String,
    action: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = 1.dp)
        ) {
            Text(
                text = gesture,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(
            text = action,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ColorSwatchScrollRow(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    presets: List<Color>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { color ->
            val isSelected = color == selectedColor
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 2.dp else 0.6.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

private data class ParsedThemeResult(
    val name: String,
    val bg: Color?,
    val text: Color?,
    val accent: Color?
)

private fun parseCssToTheme(css: String): ParsedThemeResult {
    var name = ""
    var bg: Color? = null
    var text: Color? = null
    var accent: Color? = null

    // Match theme name
    val nameRegex = Regex("""(?:--name|--theme-name|--lumina-theme-name):\s*["']?([^"';\n]+)["']?""", RegexOption.IGNORE_CASE)
    nameRegex.find(css)?.let { name = it.groupValues[1].trim() }

    // Match hex colors helper
    fun extractHexColor(pattern: Regex): Color? {
        pattern.find(css)?.let { match ->
            val hex = match.groupValues[1]
            return parseHex(hex)
        }
        return null
    }

    // BG patterns: --bg, --reader-bg, background, background-color
    bg = extractHexColor(Regex("""(?:--bg|--reader-bg|background-color|background):\s*(#[0-9a-fA-F]{3,8})""", RegexOption.IGNORE_CASE))

    // Text patterns: --text, --reader-text, color
    text = extractHexColor(Regex("""(?:--text|--reader-text|--foreground|color):\s*(#[0-9a-fA-F]{3,8})""", RegexOption.IGNORE_CASE))

    // Accent patterns: --accent, --reader-accent, --primary, accent-color
    accent = extractHexColor(Regex("""(?:--accent|--reader-accent|--primary|accent-color):\s*(#[0-9a-fA-F]{3,8})""", RegexOption.IGNORE_CASE))

    return ParsedThemeResult(name, bg, text, accent)
}

private fun parseHex(hex: String): Color? {
    return try {
        val clean = hex.removePrefix("#")
        when (clean.length) {
            3 -> {
                val r = clean.substring(0, 1).repeat(2).toInt(16)
                val g = clean.substring(1, 2).repeat(2).toInt(16)
                val b = clean.substring(2, 3).repeat(2).toInt(16)
                Color(r, g, b)
            }
            6 -> {
                val r = clean.substring(0, 2).toInt(16)
                val g = clean.substring(2, 4).toInt(16)
                val b = clean.substring(4, 6).toInt(16)
                Color(r, g, b)
            }
            8 -> {
                val a = clean.substring(0, 2).toInt(16)
                val r = clean.substring(2, 4).toInt(16)
                val g = clean.substring(4, 6).toInt(16)
                val b = clean.substring(6, 8).toInt(16)
                Color(r, g, b, a)
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun generateCssSnippet(
    name: String,
    bg: Color,
    text: Color,
    accent: Color,
    wallpaper: String
): String {
    fun toHex(c: Color): String = String.format("#%02X%02X%02X", (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
    return """
/* Lumina EPUB Reader Theme: $name */
:root {
  --lumina-theme-name: "$name";
  --reader-bg: ${toHex(bg)};
  --reader-text: ${toHex(text)};
  --reader-accent: ${toHex(accent)};
  --reader-wallpaper: "$wallpaper";
}
""".trimIndent()
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) String.format("%.2f MB", mb) else String.format("%.1f KB", kb)
}

