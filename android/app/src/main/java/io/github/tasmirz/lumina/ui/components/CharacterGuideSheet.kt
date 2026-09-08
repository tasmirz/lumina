package io.github.tasmirz.lumina.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.data.AiProvider
import io.github.tasmirz.lumina.data.AssistantService
import io.github.tasmirz.lumina.data.BookRepository
import io.github.tasmirz.lumina.model.Book
import io.github.tasmirz.lumina.model.BookCharacter
import io.github.tasmirz.lumina.model.BookLore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterGuideSheet(
    book: Book,
    currentChapterIndex: Int,
    isSpoilerShield: Boolean,
    geminiApiKey: String,
    aiProvider: AiProvider = AiProvider.GEMINI,
    aiModel: String = "gemini-3.1-flash-lite",
    customEndpoint: String = "",
    repository: BookRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val charactersMap by repository.characters.collectAsState()
    val characters = charactersMap[book.id] ?: emptyList()
    val loreMap by repository.lore.collectAsState()
    val loreList = loreMap[book.id] ?: emptyList()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") } // "ALL", "CHARACTERS", "LORE"
    var isExtracting by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var expandedCharacterId by remember { mutableStateOf<Long?>(null) }
    var expandedLoreId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(book.id) {
        repository.loadCharacters(book.id)
        repository.loadLore(book.id)
    }

    val filteredCharacters = remember(characters, searchQuery) {
        if (searchQuery.isBlank()) characters
        else {
            val q = searchQuery.trim().lowercase()
            characters.filter {
                it.name.lowercase().contains(q) ||
                it.role.lowercase().contains(q) ||
                it.summary.lowercase().contains(q) ||
                it.firstAppearanceChapter.lowercase().contains(q) ||
                it.aliases.any { alias -> alias.lowercase().contains(q) }
            }
        }
    }

    val filteredLore = remember(loreList, searchQuery) {
        if (searchQuery.isBlank()) loreList
        else {
            val q = searchQuery.trim().lowercase()
            loreList.filter {
                it.title.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.firstAppearanceChapter.lowercase().contains(q) ||
                it.keyFacts.any { fact -> fact.lowercase().contains(q) }
            }
        }
    }

    val activeChapterName = book.chapters.getOrNull(currentChapterIndex)?.title ?: "Beginning"

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
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Characters & World Lore",
                        fontFamily = FontFamily.Serif,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = book.title,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Spoiler Shield Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSpoilerShield) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSpoilerShield) Icons.Default.Shield else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSpoilerShield) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSpoilerShield) "Spoiler Shield ON • Knowledge capped to \"$activeChapterName\""
                               else "Spoiler Shield OFF • Full story lore permitted",
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Single-Line Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                maxLines = 1,
                placeholder = {
                    Text(
                        text = "Search characters, factions, locations, terms...",
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${filteredCharacters.size + filteredLore.size})", fontSize = 11.5.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "CHARACTERS",
                    onClick = { selectedFilter = "CHARACTERS" },
                    label = { Text("Characters (${filteredCharacters.size})", fontSize = 11.5.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "LORE",
                    onClick = { selectedFilter = "LORE" },
                    label = { Text("Lore & World (${filteredLore.size})", fontSize = 11.5.sp) }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (book.characterCheckpointChapter > 0 || book.characterCheckpointPage > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Checkpoint: Analyzed through Chapter ${book.characterCheckpointChapter + 1}" +
                                (if (book.characterCheckpointPage > 0) ", Page ${book.characterCheckpointPage}" else ""),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (geminiApiKey.isBlank() && aiProvider == AiProvider.GEMINI) {
                            Toast.makeText(context, "Please set your Gemini API Key in Advanced Settings", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        isExtracting = true
                        coroutineScope.launch {
                            try {
                                val result = AssistantService.extractCharactersAndLore(
                                    book = book,
                                    currentChapterIndex = currentChapterIndex,
                                    isSpoilerShield = isSpoilerShield,
                                    apiKey = geminiApiKey,
                                    provider = aiProvider,
                                    modelName = aiModel,
                                    customEndpoint = customEndpoint,
                                    existingCharacters = characters,
                                    existingLore = loreList,
                                    lastCalculatedChapter = book.characterCheckpointChapter,
                                    lastCalculatedPage = book.characterCheckpointPage,
                                    currentPageIndex = book.currentPage
                                )
                                if (result.characters.isNotEmpty() || result.lore.isNotEmpty()) {
                                    if (result.characters.isNotEmpty()) {
                                        repository.saveCharacters(book.id, result.characters)
                                    }
                                    if (result.lore.isNotEmpty()) {
                                        repository.saveLoreList(book.id, result.lore)
                                    }
                                    repository.updateCharacterCheckpoint(book.id, currentChapterIndex, book.currentPage + 1)
                                    Toast.makeText(
                                        context,
                                        "Analyzed: ${result.characters.size} characters & ${result.lore.size} lore entries up to Chapter ${currentChapterIndex + 1}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(context, "No new characters or lore found for this reading position", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error scanning characters & lore: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isExtracting = false
                            }
                        }
                    },
                    enabled = !isExtracting,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isExtracting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extracting with AI...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Extract with AI", fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val showCharacters = selectedFilter == "ALL" || selectedFilter == "CHARACTERS"
            val showLore = selectedFilter == "ALL" || selectedFilter == "LORE"
            val hasContent = (showCharacters && filteredCharacters.isNotEmpty()) || (showLore && filteredLore.isNotEmpty())

            // Content List
            if (!hasContent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.PeopleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No entries matching \"$searchQuery\""
                                   else "No characters or lore tracked yet",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap 'Extract with AI' to analyze characters, factions, locations, and lore up to your current reading spot.",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Characters section
                    if (showCharacters && filteredCharacters.isNotEmpty()) {
                        if (selectedFilter == "ALL" && filteredLore.isNotEmpty()) {
                            item(key = "header_characters") {
                                Text(
                                    text = "CHARACTERS (${filteredCharacters.size})",
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                            }
                        }
                        items(filteredCharacters, key = { "char_${it.id}" }) { character ->
                            val isExpanded = expandedCharacterId == character.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCharacterId = if (isExpanded) null else character.id
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = character.name,
                                                fontFamily = FontFamily.Serif,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = character.role,
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { repository.deleteCharacter(character.id, book.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    if (character.aliases.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Also known as: ${character.aliases.joinToString(", ")}",
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 11.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        )
                                    }

                                    if (character.firstAppearanceChapter.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Introduced: ${character.firstAppearanceChapter}",
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = character.summary,
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                    )

                                    AnimatedVisibility(visible = isExpanded && character.keyEvents.isNotBlank()) {
                                        Column(modifier = Modifier.padding(top = 10.dp)) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                thickness = 0.5.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            )
                                            Text(
                                                text = "Key Events & Timeline:",
                                                fontFamily = FontFamily.SansSerif,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = character.keyEvents,
                                                fontFamily = FontFamily.SansSerif,
                                                fontSize = 12.5.sp,
                                                lineHeight = 17.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Lore section
                    if (showLore && filteredLore.isNotEmpty()) {
                        if (selectedFilter == "ALL" && filteredCharacters.isNotEmpty()) {
                            item(key = "header_lore") {
                                Text(
                                    text = "WORLD LORE & CONCEPTS (${filteredLore.size})",
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                        }
                        items(filteredLore, key = { "lore_${it.id}" }) { lore ->
                            val isExpanded = expandedLoreId == lore.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedLoreId = if (isExpanded) null else lore.id
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = lore.title,
                                                fontFamily = FontFamily.Serif,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = lore.category.uppercase(),
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { repository.deleteLore(lore.id, book.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    if (lore.firstAppearanceChapter.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Appears in: ${lore.firstAppearanceChapter}",
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = lore.description,
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                    )

                                    AnimatedVisibility(visible = isExpanded && lore.keyFacts.isNotEmpty()) {
                                        Column(modifier = Modifier.padding(top = 10.dp)) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                thickness = 0.5.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            )
                                            Text(
                                                text = "Key Facts:",
                                                fontFamily = FontFamily.SansSerif,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            lore.keyFacts.split("•", "\n", ",")
                                                .map { it.trim() }
                                                .filter { it.isNotBlank() }
                                                .forEach { fact ->
                                                    Row(
                                                        modifier = Modifier.padding(vertical = 2.dp),
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        Text("• ", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                                                        Text(
                                                            text = fact,
                                                            fontFamily = FontFamily.SansSerif,
                                                            fontSize = 12.sp,
                                                            lineHeight = 16.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        }
    }

    // Manual Add Dialog (Character or Lore)
    if (showAddDialog) {
        var addType by remember { mutableStateOf("CHARACTER") } // "CHARACTER" or "LORE"
        var titleOrName by remember { mutableStateOf("") }
        var roleOrCategory by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var keyFactsText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = if (addType == "CHARACTER") "Add Character" else "Add Lore Entry",
                    fontFamily = FontFamily.Serif
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Type selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = addType == "CHARACTER",
                            onClick = { addType = "CHARACTER" },
                            label = { Text("Character", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = addType == "LORE",
                            onClick = { addType = "LORE" },
                            label = { Text("Lore / World", fontSize = 12.sp) }
                        )
                    }

                    OutlinedTextField(
                        value = titleOrName,
                        onValueChange = { titleOrName = it },
                        label = { Text(if (addType == "CHARACTER") "Character Name" else "Lore Title / Name", maxLines = 1) },
                        singleLine = true,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = roleOrCategory,
                        onValueChange = { roleOrCategory = it },
                        label = { Text(if (addType == "CHARACTER") "Role (e.g. Rebel, Officer)" else "Category (e.g. Faction, Location, Term)", maxLines = 1) },
                        singleLine = true,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description & Nuanced Background (3-4+ sentences)") },
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (addType == "LORE") {
                        OutlinedTextField(
                            value = keyFactsText,
                            onValueChange = { keyFactsText = it },
                            label = { Text("Key Facts (comma separated)", maxLines = 1) },
                            singleLine = true,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (titleOrName.isNotBlank()) {
                            if (addType == "CHARACTER") {
                                repository.saveCharacter(
                                    BookCharacter(
                                        bookId = book.id,
                                        name = titleOrName.trim(),
                                        role = if (roleOrCategory.isNotBlank()) roleOrCategory.trim() else "Character",
                                        firstAppearanceChapter = activeChapterName,
                                        summary = description.trim()
                                    )
                                )
                            } else {
                                repository.saveLore(
                                    BookLore(
                                        bookId = book.id,
                                        title = titleOrName.trim(),
                                        category = if (roleOrCategory.isNotBlank()) roleOrCategory.trim() else "CONCEPT",
                                        firstAppearanceChapter = activeChapterName,
                                        description = description.trim(),
                                        keyFacts = keyFactsText.trim()
                                    )
                                )
                            }
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

