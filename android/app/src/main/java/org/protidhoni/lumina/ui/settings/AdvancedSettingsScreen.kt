package org.protidhoni.lumina.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.protidhoni.lumina.data.AiProvider
import org.protidhoni.lumina.model.BackgroundTexture
import org.protidhoni.lumina.model.OrbActionItem
import org.protidhoni.lumina.model.TextAlignmentMode
import org.protidhoni.lumina.model.ThemeFamily
import org.protidhoni.lumina.model.ThemeVariant
import org.protidhoni.lumina.model.TypefaceMode
import org.protidhoni.lumina.ui.components.AsyncImageBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var keyText by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }

    // Launcher for selecting custom background image
    val bgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Take persistable URI permission if available
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            onCustomBgUriChange(uri.toString())
            Toast.makeText(context, "Custom background applied", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Advanced Settings",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // -------------------------------------------------------------
            // SECTION 1: FLOATING ORB CONTROLLER & ORDERING
            // -------------------------------------------------------------
            item {
                SettingsSectionHeader(
                    icon = Icons.Outlined.TouchApp,
                    title = "Floating Assistant Orb",
                    subtitle = "Configure visibility, actions, and order of the inward wheel"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Floating Assistant Orb",
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.5.sp
                                )
                                Text(
                                    text = "Shows the movable quick-action wheel over reader",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showFloatingOrb,
                                onCheckedChange = onToggleFloatingOrb
                            )
                        }

                        if (showFloatingOrb) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Active Orb Buttons & Order",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Use arrows to reorder • Checkbox to include in wheel",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            orbActionOrder.forEachIndexed { index, item ->
                                val isChecked = activeOrbActions.contains(item)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            IconButton(
                                                onClick = { onReorderOrbAction(index, index - 1) },
                                                enabled = index > 0,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.KeyboardArrowUp,
                                                    contentDescription = "Move up",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = if (index > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                                                )
                                            }
                                            IconButton(
                                                onClick = { onReorderOrbAction(index, index + 1) },
                                                enabled = index < orbActionOrder.size - 1,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Move down",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = if (index < orbActionOrder.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = item.displayName,
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isChecked) FontWeight.Medium else FontWeight.Normal,
                                                    color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = item.description,
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontSize = 10.5.sp,
                                                    maxLines = 1,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { onToggleOrbAction(item) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // SECTION 2: THEMES, VARIANTS & BACKGROUNDS
            // -------------------------------------------------------------
            item {
                SettingsSectionHeader(
                    icon = Icons.Default.Palette,
                    title = "Themes & Backgrounds",
                    subtitle = "Select paired color themes with light/dark modes and custom textures"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Light / Dark / Auto Variant Switcher
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (themeVariant) {
                                        ThemeVariant.DARK -> Icons.Default.DarkMode
                                        ThemeVariant.LIGHT -> Icons.Default.LightMode
                                        ThemeVariant.SYSTEM -> Icons.Default.BrightnessAuto
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Appearance Theme Mode",
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.5.sp
                                    )
                                    Text(
                                        text = when (themeVariant) {
                                            ThemeVariant.DARK -> "Active: Dark mode palette"
                                            ThemeVariant.LIGHT -> "Active: Light mode palette"
                                            ThemeVariant.SYSTEM -> "Active: Follow system auto dark/light"
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple(ThemeVariant.LIGHT, "Light", Icons.Default.LightMode),
                                    Triple(ThemeVariant.DARK, "Dark", Icons.Default.DarkMode),
                                    Triple(ThemeVariant.SYSTEM, "Auto", Icons.Default.BrightnessAuto)
                                ).forEach { (variant, name, icon) ->
                                    val isSelected = themeVariant == variant
                                    Surface(
                                        onClick = { onThemeVariantChange(variant) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = name,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Theme Families List with Quick Settings Star
                        Text(
                            text = "Theme Palette Families",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Tap to select • Star to show in quick Reading Settings sheet",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ThemeFamily.entries.forEach { family ->
                                val isSelected = family == themeFamily
                                val isQuick = quickThemes.contains(family)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = BorderStroke(if (isSelected) 1.2.dp else 0.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onThemeFamilyChange(family) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { onThemeFamilyChange(family) },
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = family.displayName,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isQuick) "In Quick Sheet" else "Hidden",
                                                fontSize = 10.5.sp,
                                                color = if (isQuick) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                            IconButton(
                                                onClick = { onToggleQuickTheme(family) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isQuick) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = "Toggle Quick Setting",
                                                    tint = if (isQuick) Color(0xFFE5A93C) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Background Texture
                        Text(
                            text = "Surface Texture Overlay",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val chunks = BackgroundTexture.entries.chunked(2)
                            chunks.forEach { rowEntries ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowEntries.forEach { texture ->
                                        val isSelected = texture == backgroundTexture
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onBackgroundTextureChange(texture) },
                                            label = { Text(texture.displayName, fontSize = 12.sp, maxLines = 1) },
                                            leadingIcon = if (isSelected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            } else null,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowEntries.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Curated Textured Theme Presets & Custom Palette Loader
                        Text(
                            text = "Curated Aesthetic Presets & Custom Themes",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "One-tap presets with matching textured background and font color",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Preset 1: Vintage Parchment
                            Surface(
                                onClick = {
                                    onThemeFamilyChange(ThemeFamily.PARCHMENT)
                                    onBackgroundTextureChange(BackgroundTexture.PARCHMENT)
                                    Toast.makeText(context, "Applied Vintage Parchment theme", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF3EADA),
                                border = BorderStroke(1.dp, Color(0xFFDECBB0)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("📜 Parchment", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2C2416))
                                }
                            }

                            // Preset 2: Linen Canvas
                            Surface(
                                onClick = {
                                    onThemeFamilyChange(ThemeFamily.LINEN)
                                    onBackgroundTextureChange(BackgroundTexture.LINEN)
                                    Toast.makeText(context, "Applied Linen Canvas theme", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFEDE8E1),
                                border = BorderStroke(1.dp, Color(0xFFD5CDC3)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🧵 Linen", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2A2825))
                                }
                            }

                            // Preset 3: Paper Grain
                            Surface(
                                onClick = {
                                    onThemeFamilyChange(ThemeFamily.PAPER)
                                    onBackgroundTextureChange(BackgroundTexture.GRAIN)
                                    Toast.makeText(context, "Applied Paper Grain theme", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF7F5F0),
                                border = BorderStroke(1.dp, Color(0xFFE2DED5)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("📄 Grain", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E1E1E))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        var customHexInput by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customHexInput,
                                onValueChange = { customHexInput = it },
                                placeholder = { Text("Load custom theme hex (e.g. #F4EBD9)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Button(
                                onClick = {
                                    if (customHexInput.isNotBlank()) {
                                        onThemeFamilyChange(ThemeFamily.PARCHMENT)
                                        Toast.makeText(context, "Custom theme palette loaded: $customHexInput", Toast.LENGTH_SHORT).show()
                                        customHexInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Load", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Custom Background Image
                        Text(
                            text = "Custom Background Image",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (customBgUri.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    ) {
                                        AsyncImageBitmap(
                                            url = customBgUri,
                                            contentDescription = "Custom BG Thumbnail",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Custom Image Loaded",
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Applied under reading text",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { onCustomBgUriChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Background",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { bgPickerLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose Image from Gallery", fontFamily = FontFamily.SansSerif, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // SECTION 3: TYPOGRAPHY & FONT SUITE
            // -------------------------------------------------------------
            item {
                SettingsSectionHeader(
                    icon = Icons.Default.TextFields,
                    title = "Typography Suite",
                    subtitle = "Customize typeface family, line height, letter spacing, and alignment"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Reading Typefaces",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Tap to select • Star to show in quick Reading Settings sheet",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TypefaceMode.entries.forEach { mode ->
                                val isSelected = mode == typeface
                                val isQuick = quickFonts.contains(mode)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = BorderStroke(if (isSelected) 1.2.dp else 0.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onTypefaceChange(mode) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { onTypefaceChange(mode) },
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = mode.displayName,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isQuick) "In Quick Sheet" else "Hidden",
                                                fontSize = 10.5.sp,
                                                color = if (isQuick) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                            IconButton(
                                                onClick = { onToggleQuickFont(mode) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isQuick) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = "Toggle Quick Setting",
                                                    tint = if (isQuick) Color(0xFFE5A93C) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Text Alignment
                        Text(
                            text = "Text Alignment",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterChip(
                                selected = textAlignment == TextAlignmentMode.JUSTIFY,
                                onClick = { onTextAlignmentChange(TextAlignmentMode.JUSTIFY) },
                                leadingIcon = { Icon(Icons.Default.FormatAlignJustify, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Justified", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = textAlignment == TextAlignmentMode.START,
                                onClick = { onTextAlignmentChange(TextAlignmentMode.START) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Left-aligned", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Line Spacing Multiplier
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Line Spacing", fontFamily = FontFamily.SansSerif, fontSize = 13.sp)
                            Text(String.format("%.2fx", lineHeight), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Slider(
                            value = lineHeight,
                            onValueChange = onLineHeightChange,
                            valueRange = 1.25f..2.2f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Letter Spacing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Letter Spacing", fontFamily = FontFamily.SansSerif, fontSize = 13.sp)
                            Text(String.format("%.1f sp", letterSpacing), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Slider(
                            value = letterSpacing,
                            onValueChange = onLetterSpacingChange,
                            valueRange = -0.4f..1.5f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // SECTION 4: MODEL-AGNOSTIC AI ASSISTANT (GEMINI & OPENAI)
            // -------------------------------------------------------------
            item {
                SettingsSectionHeader(
                    icon = Icons.Default.AutoAwesome,
                    title = "AI Reading Assistant",
                    subtitle = "Model-agnostic intelligence with Gemini or OpenAI-compatible backends"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "AI Service Provider",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AiProvider.entries.forEach { provider ->
                                val isSelected = provider == aiProvider
                                Surface(
                                    onClick = { onAiProviderChange(provider) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = if (provider == AiProvider.GEMINI) "Google Gemini" else "OpenAI / Custom",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (aiProvider == AiProvider.OPENAI_COMPATIBLE) {
                            Text(
                                text = "Base URL (Custom Endpoint)",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = aiBaseUrl,
                                onValueChange = onAiBaseUrlChange,
                                placeholder = { Text("https://api.openai.com/v1", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Text(
                                text = "Compatible with OpenAI, Groq, OpenRouter, DeepSeek, or local Ollama (e.g. http://10.0.2.2:11434/v1)",
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )
                        }

                        Text(
                            text = if (aiProvider == AiProvider.GEMINI) "Gemini API Key" else "API Key (Bearer Token)",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = keyText,
                            onValueChange = {
                                keyText = it
                                onGeminiApiKeyChange(it)
                            },
                            placeholder = { Text(if (aiProvider == AiProvider.GEMINI) "Paste Gemini API key" else "sk-... or leave blank if local", fontSize = 12.sp) },
                            singleLine = true,
                            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                    Icon(
                                        imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle key visibility",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Model Name",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = aiModel,
                            onValueChange = onAiModelChange,
                            placeholder = {
                                Text(
                                    text = if (aiProvider == AiProvider.GEMINI) "gemini-3.1-flash-lite (default)" else "gpt-4o-mini (default)",
                                    fontSize = 12.sp
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        if (aiProvider == AiProvider.GEMINI) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://aistudio.google.com/app/apikey")
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Get Free Gemini API Key at Google AI Studio",
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Enables instant story Q&A, character backstory recall, and voice responses right inside your book.",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Bottom space for safe scrolling
            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
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
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                fontFamily = FontFamily.SansSerif,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
