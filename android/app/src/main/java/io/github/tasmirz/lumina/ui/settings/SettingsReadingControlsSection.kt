package io.github.tasmirz.lumina.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.data.BookRepository
import io.github.tasmirz.lumina.data.EdgeTtsService
import io.github.tasmirz.lumina.model.GestureAction
import io.github.tasmirz.lumina.model.OrbActionItem
import io.github.tasmirz.lumina.model.OrbColor
import io.github.tasmirz.lumina.model.OrbMenuSize
import io.github.tasmirz.lumina.model.OrbSize
import io.github.tasmirz.lumina.model.OrbFanDirection
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Section 2: Reading Controls, Gestures, Orb, Auto-Save, and Text-to-Speech Audio.
 */
@Composable
fun SettingsReadingControlsSection(
    repository: BookRepository?,
    autoScrollSpeed: Float,
    disableTts: Boolean,
    ttsEngine: String,
    ttsEdgeVoice: String,
    ttsSpeed: Float,
    showFloatingOrb: Boolean,
    onToggleFloatingOrb: (Boolean) -> Unit,
    assistantOrbStyle: String,
    orbSize: OrbSize,
    orbMenuSize: OrbMenuSize,
    orbEdgeSnap: Boolean,
    orbSnapThreshold: Float = 0.05f,
    orbReversePaletteLandscape: Boolean = false,
    orbReversePalettePortrait: Boolean = false,
    orbFanDirection: OrbFanDirection = OrbFanDirection.AUTO,
    orbColor: OrbColor,
    customOrbColor: Long,
    orbOpacity: Float,
    gestureDoubleTap: GestureAction,
    gestureTripleTap: GestureAction,
    gestureSingleTap: GestureAction,
    gestureTtsTap: GestureAction,
    pagedSwipeThreshold: Float,
    autoSaveDwellSeconds: Int,
    smartConsecutiveAutoSave: Boolean,
    autoSaveConsecutivePages: Int,
    activeOrbActions: Set<OrbActionItem>,
    orbActionOrder: List<OrbActionItem>,
    onToggleOrbAction: (OrbActionItem) -> Unit,
    onReorderOrbAction: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var playingVoiceId by remember { mutableStateOf<String?>(null) }
    var isVoiceDemoLoading by remember { mutableStateOf<String?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }

    // Dialog state for gestures
    var editingGestureName by remember { mutableStateOf<String?>(null) }
    var currentEditingAction by remember { mutableStateOf<GestureAction?>(null) }
    var onSaveGestureAction by remember { mutableStateOf<((GestureAction) -> Unit)?>(null) }

    // Dialog state for Dwell input
    var showDwellInputDialog by remember { mutableStateOf(false) }
    var dwellInputText by remember { mutableStateOf("") }

    // Dialog state for Consecutive pages input
    var showConsecutiveInputDialog by remember { mutableStateOf(false) }
    var consecutiveInputText by remember { mutableStateOf("") }

    if (showColorPicker) {
        LuminaColorPickerDialog(
            title = "Orb Custom Color",
            initialColor = Color(customOrbColor),
            onDismiss = { showColorPicker = false },
            onColorConfirmed = { confirmed ->
                repository?.setCustomOrbColor(confirmed.value.toLong())
                repository?.setOrbColor(OrbColor.CUSTOM)
                showColorPicker = false
            }
        )
    }

    if (editingGestureName != null && currentEditingAction != null) {
        AlertDialog(
            onDismissRequest = { editingGestureName = null },
            title = {
                Text(
                    text = "Assign ${editingGestureName}",
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GestureAction.entries.forEach { action ->
                        val isSelected = currentEditingAction == action
                        Surface(
                            onClick = {
                                onSaveGestureAction?.invoke(action)
                                editingGestureName = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSaveGestureAction?.invoke(action)
                                        editingGestureName = null
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = action.displayName,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 12.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = gestureActionDescription(action),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { editingGestureName = null }) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    if (showDwellInputDialog) {
        AlertDialog(
            onDismissRequest = { showDwellInputDialog = false },
            title = { Text("Set Dwell Time", fontSize = 15.sp, fontWeight = FontWeight.Normal) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter dwell threshold in seconds (1 to 60s):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = dwellInputText,
                        onValueChange = { dwellInputText = it.filter { ch -> ch.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = dwellInputText.toIntOrNull()
                        if (parsed != null) {
                            repository?.setAutoSaveDwellSeconds(parsed.coerceIn(1, 60))
                        }
                        showDwellInputDialog = false
                    }
                ) {
                    Text("Save", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDwellInputDialog = false }) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    if (showConsecutiveInputDialog) {
        AlertDialog(
            onDismissRequest = { showConsecutiveInputDialog = false },
            title = { Text("Set Consecutive Pages", fontSize = 15.sp, fontWeight = FontWeight.Normal) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter threshold pages (2 to 10 pages):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = consecutiveInputText,
                        onValueChange = { consecutiveInputText = it.filter { ch -> ch.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = consecutiveInputText.toIntOrNull()
                        if (parsed != null) {
                            repository?.setAutoSaveConsecutivePages(parsed.coerceIn(2, 10))
                        }
                        showConsecutiveInputDialog = false
                    }
                ) {
                    Text("Save", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConsecutiveInputDialog = false }) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    CollapsibleCard(
        icon = Icons.Outlined.TouchApp,
        title = "Reading Controls, Gestures & Audio",
        subtitle = "Speed ${String.format(Locale.US, "%.1f", autoScrollSpeed)}x • ${if (disableTts) "TTS Engine OFF" else "TTS Engine ON"}"
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
                            imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Text-to-Speech (TTS) Voice Engine",
                            fontWeight = FontWeight.Normal,
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
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isEdge = ttsEngine == "EDGE_NEURAL"
                    Surface(
                        onClick = { repository?.setTtsEngine("EDGE_NEURAL") },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isEdge) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, if (isEdge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Edge Neural\n(Audiobook)", fontWeight = FontWeight.Normal, fontSize = 11.5.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Natural human voice synthesis", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val isSystem = ttsEngine == "SYSTEM"
                    Surface(
                        onClick = { repository?.setTtsEngine("SYSTEM") },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSystem) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, if (isSystem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "System TTS\n(On-device)", fontWeight = FontWeight.Normal, fontSize = 11.5.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Local speech engine synthesis", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (ttsEngine == "EDGE_NEURAL") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Neural Voice Persona",
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EdgeTtsService.AVAILABLE_VOICES.forEach { voice ->
                            val isSelected = ttsEdgeVoice == voice.id
                            val isPlaying = playingVoiceId == voice.id
                            val isLoading = isVoiceDemoLoading == voice.id

                            val toggleVoiceDemo = {
                                if (isPlaying) {
                                    EdgeTtsService.stopVoiceDemo()
                                    playingVoiceId = null
                                } else {
                                    isVoiceDemoLoading = voice.id
                                    coroutineScope.launch {
                                        EdgeTtsService.playVoiceDemo(context, voice.id) { playing ->
                                            if (playing) {
                                                playingVoiceId = voice.id
                                                isVoiceDemoLoading = null
                                            } else {
                                                if (playingVoiceId == voice.id) {
                                                    playingVoiceId = null
                                                }
                                                if (isVoiceDemoLoading == voice.id) {
                                                    isVoiceDemoLoading = null
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Surface(
                                onClick = {
                                    repository?.setTtsEdgeVoice(voice.id)
                                    toggleVoiceDemo()
                                },
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
                                        onClick = {
                                            repository?.setTtsEdgeVoice(voice.id)
                                            toggleVoiceDemo()
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = voice.displayName, fontWeight = FontWeight.Normal, fontSize = 11.5.sp)
                                        Text(text = voice.description, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(
                                        onClick = { toggleVoiceDemo() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else if (isPlaying) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = "Stop voice sample",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                                                contentDescription = "Play voice sample",
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Narration Speed: ${String.format(Locale.US, "%.2f", ttsSpeed)}x",
                    fontWeight = FontWeight.Normal,
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
                            fontWeight = FontWeight.Normal,
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
                    fontWeight = FontWeight.Normal,
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
                                fontWeight = FontWeight.Normal,
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
                                fontWeight = FontWeight.Normal,
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
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Control the size of the docked edge pill and floating orb (default: Medium)",
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
                                        fontWeight = FontWeight.Normal,
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
                        fontWeight = FontWeight.Normal,
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
                                        fontWeight = FontWeight.Normal,
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
                                fontWeight = FontWeight.Normal,
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

                    if (orbEdgeSnap) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Edge Snap Margin",
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Snap to edge within this distance, otherwise float",
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${(orbSnapThreshold * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = orbSnapThreshold,
                            onValueChange = { repository?.setOrbSnapThreshold(it) },
                            valueRange = 0.02f..0.20f,
                            steps = 17,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Reverse Palette Direction (Landscape)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = "Reverse Palette Direction (Landscape)",
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (orbReversePaletteLandscape) "Menu fans outward in reverse direction of the edge" else "Standard: menu fans inward toward screen center",
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = orbReversePaletteLandscape,
                            onCheckedChange = { repository?.setOrbReversePaletteLandscape(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Reverse Palette Direction (Portrait)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = "Reverse Palette Direction (Portrait)",
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (orbReversePalettePortrait) "Menu fans outward in reverse direction of the edge" else "Standard: menu fans inward toward screen center",
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = orbReversePalettePortrait,
                            onCheckedChange = { repository?.setOrbReversePalettePortrait(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Orb Fan Direction
                    Text(
                        text = "Orb Fan Direction",
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Radial menu fanning direction: Auto adapts to edge, or force a direction (90° / horizontal)",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(OrbFanDirection.AUTO, OrbFanDirection.RIGHT, OrbFanDirection.LEFT).forEach { dir ->
                            val isSelected = orbFanDirection == dir
                            Surface(
                                onClick = { repository?.setOrbFanDirection(dir) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dir.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(OrbFanDirection.DOWN, OrbFanDirection.UP).forEach { dir ->
                            val isSelected = orbFanDirection == dir
                            Surface(
                                onClick = { repository?.setOrbFanDirection(dir) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dir.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Orb Color Customization
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Orb Color",
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Personalize the tint of the floating assistant",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        TextButton(
                            onClick = { showColorPicker = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Custom Hex", fontSize = 10.5.sp, maxLines = 1, softWrap = false)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OrbColor.entries.forEach { colorOption ->
                            val isSelected = orbColor == colorOption
                            val previewColor = when (colorOption) {
                                OrbColor.THEME -> MaterialTheme.colorScheme.primary
                                OrbColor.CUSTOM -> Color(customOrbColor)
                                else -> Color(colorOption.colorValue)
                            }
                            Surface(
                                onClick = {
                                    if (colorOption == OrbColor.CUSTOM && isSelected) {
                                        showColorPicker = true
                                    } else {
                                        repository?.setOrbColor(colorOption)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) previewColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) previewColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.widthIn(min = 58.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
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
                                        fontWeight = FontWeight.Normal,
                                        color = if (isSelected) previewColor else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Orb Opacity
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Orb Opacity (Reading Subtlety)",
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${(orbOpacity * 100).toInt()}%",
                            fontWeight = FontWeight.Normal,
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
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.5.sp
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", autoScrollSpeed)}x",
                    fontWeight = FontWeight.Normal,
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
                    fontWeight = FontWeight.Normal,
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

            Spacer(modifier = Modifier.height(10.dp))

            // Page Turn Swipe Effort
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Page Turn Swipe Effort", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Distance required to turn page (lower = easier flick)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                val effortLabel = when {
                    pagedSwipeThreshold <= 0.14f -> "Minimal"
                    pagedSwipeThreshold <= 0.22f -> "Light"
                    pagedSwipeThreshold <= 0.34f -> "Medium"
                    else -> "Firm"
                }
                Text(
                    text = "${(pagedSwipeThreshold * 100).toInt()}% ($effortLabel)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = pagedSwipeThreshold,
                onValueChange = { repository?.setPagedSwipeThreshold(it) },
                valueRange = 0.03f..0.50f,
                steps = 8,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Auto-Save Dwell Time & Smart Reading Cadence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Page Auto-Save Dwell Time",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Reading time required per page before progress is committed (default: 5 seconds)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = {
                            val newSec = (autoSaveDwellSeconds - 1).coerceAtLeast(1)
                            repository?.setAutoSaveDwellSeconds(newSec)
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("-", fontSize = 14.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Surface(
                        onClick = {
                            dwellInputText = autoSaveDwellSeconds.toString()
                            showDwellInputDialog = true
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.widthIn(min = 44.dp).height(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text(
                                text = "${autoSaveDwellSeconds}s",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Surface(
                        onClick = {
                            val newSec = (autoSaveDwellSeconds + 1).coerceAtMost(60)
                            repository?.setAutoSaveDwellSeconds(newSec)
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("+", fontSize = 14.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Smart Front-Matter Cadence Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart Front-Matter Cadence",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Auto-detects active reading after skipping front-matter pages (cover, contents) if consecutive pages are read steadily",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = smartConsecutiveAutoSave,
                    onCheckedChange = { repository?.setSmartConsecutiveAutoSave(it) }
                )
            }

            if (smartConsecutiveAutoSave) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Consecutive Steady Pages Threshold",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Number of consecutive pages read at dwell pace to trigger auto-save (default: 3 pages)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = {
                                val newPages = (autoSaveConsecutivePages - 1).coerceAtLeast(2)
                                repository?.setAutoSaveConsecutivePages(newPages)
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("-", fontSize = 13.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Surface(
                            onClick = {
                                consecutiveInputText = autoSaveConsecutivePages.toString()
                                showConsecutiveInputDialog = true
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.widthIn(min = 58.dp).height(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                                Text(
                                    text = "$autoSaveConsecutivePages pages",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Surface(
                            onClick = {
                                val newPages = (autoSaveConsecutivePages + 1).coerceAtMost(10)
                                repository?.setAutoSaveConsecutivePages(newPages)
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("+", fontSize = 13.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
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
                    fontWeight = FontWeight.Normal,
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
                                fontWeight = FontWeight.Normal
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

private fun gestureActionDescription(action: GestureAction): String = when (action) {
    GestureAction.TOGGLE_AUTOSCROLL -> "Start or stop hands-free automatic scrolling"
    GestureAction.SUMMON_ORB -> "Open the floating AI study companion orb"
    GestureAction.TOGGLE_BARS -> "Show or hide top and bottom reader navigation bars"
    GestureAction.TTS_READ_ALOUD -> "Start text-to-speech playback from this point"
    GestureAction.IN_BOOK_SEARCH -> "Search text across all chapters of this book"
    GestureAction.PREVIOUS_CHAPTER -> "Jump back to the previous chapter"
    GestureAction.NEXT_CHAPTER -> "Advance forward to the next chapter"
    GestureAction.ADD_BOOKMARK -> "Bookmark the current reading position"
    GestureAction.NONE -> "Do nothing for this gesture"
}
