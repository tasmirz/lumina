package io.github.tasmirz.lumina.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.data.BookRepository
import io.github.tasmirz.lumina.data.OnlineEpubService
import io.github.tasmirz.lumina.model.CustomCatalogEndpoint
import io.github.tasmirz.lumina.model.UncachedBookOpenMode
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Section 4: Storage, Caches, Unified Backup & Restore, Custom Catalog Endpoints, and Open Source License.
 */
@Composable
fun SettingsStorageBackupSection(
    repository: BookRepository?,
    ftsIndexCount: Int,
    onFtsIndexCountChange: (Int) -> Unit,
    imageCacheSizeBytes: Long,
    onClearImageCache: () -> Unit,
    dbFileSizeBytes: Long,
    enableFtsIndexing: Boolean,
    showStartupLoadingScreen: Boolean,
    uncachedBookOpenMode: UncachedBookOpenMode,
    openLibraryApiKey: String,
    onOpenLibraryApiKeyChange: (String) -> Unit,
    customEndpoints: List<CustomCatalogEndpoint>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var backupJsonInput by remember { mutableStateOf("") }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showEndpointDialog by remember { mutableStateOf(false) }
    var editingEndpoint by remember { mutableStateOf<CustomCatalogEndpoint?>(null) }
    var openLibraryKeyText by remember(openLibraryApiKey) { mutableStateOf(openLibraryApiKey) }
    var isOpenLibraryKeyVisible by remember { mutableStateOf(false) }

    val backupFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val json = reader.readText()
                    reader.close()
                    val success = repository?.importUnifiedBackupJson(json) == true
                    if (success) {
                        Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to restore: invalid backup format", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading backup file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    CollapsibleCard(
        icon = Icons.Outlined.Storage,
        title = "Storage, Backup & License",
        subtitle = "Offline Search • Image Cache • Vault Backup • Open Source"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Granular Storage Dashboard Card
            Text(
                text = "Device Storage & Caches",
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Manage local index files, cover caches, and vault footprint to reclaim internal phone storage.",
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Storage Stat 1: Offline Search Index
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
                        text = "Offline Search Index",
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.5.sp
                    )
                    Text(
                        text = "$ftsIndexCount scenes indexed",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isIndexing by (repository?.isIndexingActive?.collectAsState() ?: remember { mutableStateOf(false) })
                    Button(
                        onClick = {
                            val active = repository?.getActiveBook()
                            if (active != null) {
                                repository.indexEntireBookNow(active) { count ->
                                    val newCount = repository.getFtsIndexCount()
                                    onFtsIndexCountChange(newCount)
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
                        Text(if (isIndexing) "Indexing…" else if (ftsIndexCount == 0) "Index" else "Re-Index", fontSize = 10.5.sp, fontWeight = FontWeight.Normal)
                    }
                    if (ftsIndexCount > 0) {
                        OutlinedButton(
                            onClick = {
                                repository?.clearFtsIndex()
                                onFtsIndexCountChange(0)
                                Toast.makeText(context, "Search index cleared", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Clear", fontSize = 10.5.sp, fontWeight = FontWeight.Normal)
                        }
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
                        text = "Image & Cover Cache",
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.5.sp
                    )
                    Text(
                        text = formatBytes(imageCacheSizeBytes),
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onClearImageCache,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Clear Cache", fontSize = 10.5.sp, fontWeight = FontWeight.Normal)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Storage Stat 3: Library Database & Reading Progress
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
                        text = "Library & Vault Data",
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.5.sp
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
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Reset Progress", fontSize = 10.5.sp, fontWeight = FontWeight.Normal)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Offline Search Engine Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Offline Deep Search",
                        fontWeight = FontWeight.Normal,
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

            // Startup Loading Screen Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Startup Initialization Screen",
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (showStartupLoadingScreen) "Active. Displays a serene visual emblem while library and database warm-ups finish." else "Disabled. Launches directly into library or reader.",
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showStartupLoadingScreen,
                    onCheckedChange = { repository?.setShowStartupLoadingScreen(it) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Uncached Book Loading Mode
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Uncached Book Opening Mode",
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (uncachedBookOpenMode) {
                        UncachedBookOpenMode.INSTANT_ACTIVE_FIRST -> "Instant: renders first chapter immediately while remaining layout computes silently."
                        UncachedBookOpenMode.SERENE_EMBLEM -> "Serene: displays minimalist breathing emblem until entire book layout is ready."
                        UncachedBookOpenMode.ADAPTIVE -> "Adaptive: displays breathing emblem only if pagination computation exceeds 300ms."
                    },
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        UncachedBookOpenMode.INSTANT_ACTIVE_FIRST to "Instant",
                        UncachedBookOpenMode.SERENE_EMBLEM to "Serene",
                        UncachedBookOpenMode.ADAPTIVE to "Adaptive"
                    )
                    modes.forEach { (mode, label) ->
                        val isSelected = uncachedBookOpenMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { repository?.setUncachedBookOpenMode(mode) },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Unified Backup & Restore Card
            Text(
                text = "Unified Data Backup & Restore",
                fontWeight = FontWeight.Normal,
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
                    Text("Export Backup", fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
                }

                OutlinedButton(
                    onClick = { showBackupRestoreDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Backup", fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
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
                        fontWeight = FontWeight.Normal,
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
                        fontWeight = FontWeight.Normal,
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
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Custom Catalogs & Private Download Sources
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Custom Catalogs & OPDS",
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        editingEndpoint = null
                        showEndpointDialog = true
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Source", fontSize = 11.sp, fontWeight = FontWeight.Normal)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Connect Calibre OPDS web servers, private book feeds, or direct JSON/Atom APIs.",
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (customEndpoints.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No custom catalog endpoints configured yet.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    customEndpoints.forEach { endpoint ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = endpoint.name,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 12.5.sp
                                        )
                                        Text(
                                            text = endpoint.galleryUrl.ifBlank { endpoint.searchUrl },
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Switch(
                                        checked = endpoint.isEnabled,
                                        onCheckedChange = { isChecked ->
                                            repository?.updateCustomEndpoint(endpoint.copy(isEnabled = isChecked))
                                        }
                                    )
                                }

                                if (endpoint.apiKey.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    ) {
                                        Text(
                                            text = "🔑 Auth Configured (${endpoint.authHeader})",
                                            fontSize = 9.5.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    var isTestingThis by remember { mutableStateOf(false) }
                                    TextButton(
                                        onClick = {
                                            isTestingThis = true
                                            coroutineScope.launch {
                                                val (ok, msg) = OnlineEpubService.testEndpointConnection(endpoint)
                                                Toast.makeText(context, if (ok) "✓ $msg" else "✗ $msg", Toast.LENGTH_LONG).show()
                                                isTestingThis = false
                                            }
                                        },
                                        enabled = !isTestingThis,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(if (isTestingThis) "Testing..." else "Test", fontSize = 11.sp, fontWeight = FontWeight.Normal)
                                    }

                                    TextButton(
                                        onClick = {
                                            editingEndpoint = endpoint
                                            showEndpointDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Edit", fontSize = 11.sp, fontWeight = FontWeight.Normal)
                                    }

                                    TextButton(
                                        onClick = {
                                            repository?.deleteCustomEndpoint(endpoint.id)
                                            Toast.makeText(context, "Deleted ${endpoint.name}", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Delete", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Open Source License
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
                            text = "Open Source License",
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.5.sp
                        )
                        Text(
                            text = "Affero GPL v3.0 • Free & Open Source",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showLicenseDialog = true },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Terms", fontSize = 11.sp, fontWeight = FontWeight.Normal)
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

    // Backup & Restore Dialog
    if (showBackupRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showBackupRestoreDialog = false },
            title = {
                Text(
                    text = "Import & Restore Backup",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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
                        Text("Pick .json Backup File", fontSize = 12.sp, fontWeight = FontWeight.Normal)
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
                        Text("Restore Pasted JSON", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupRestoreDialog = false }) {
                    Text("Close", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
            }
        )
    }

    // AGPLv3 License Dialog
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = {
                Text(
                    text = "GNU Affero General Public License",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
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
                    Text("Close", fontWeight = FontWeight.Normal)
                }
            }
        )
    }

    // Custom Catalog / OPDS Endpoint Editor Dialog
    if (showEndpointDialog) {
        CustomEndpointEditorDialog(
            initialEndpoint = editingEndpoint,
            onDismiss = {
                showEndpointDialog = false
                editingEndpoint = null
            },
            onSave = { endpoint ->
                if (editingEndpoint != null) {
                    repository?.updateCustomEndpoint(endpoint)
                    Toast.makeText(context, "Updated ${endpoint.name}", Toast.LENGTH_SHORT).show()
                } else {
                    repository?.addCustomEndpoint(endpoint)
                    Toast.makeText(context, "Added ${endpoint.name}", Toast.LENGTH_SHORT).show()
                }
                showEndpointDialog = false
                editingEndpoint = null
            }
        )
    }
}
