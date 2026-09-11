package io.github.tasmirz.lumina.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.model.Bookmark
import io.github.tasmirz.lumina.model.HighlightColor
import io.github.tasmirz.lumina.util.CitationHelper

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun ReaderSelectionMenu(
    showSelectionMenu: Boolean,
    selectedText: String,
    paragraphText: String = "",
    selectedChapterTitle: String,
    activeChapterTitle: String,
    activePage: Int,
    author: String,
    bookTitle: String,
    activeBookmark: Bookmark?,
    bottomPadding: Dp,
    onAddBookmark: (String, HighlightColor, Int, String) -> Unit,
    onRemoveBookmark: (Long) -> Unit,
    onOpenNoteModal: (Bookmark) -> Unit,
    onReadFromHere: (String) -> Unit,
    onLookupWord: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showNamingPrompt by rememberSaveable(selectedText) { mutableStateOf(false) }
    var bookmarkNameDraft by rememberSaveable(selectedText) { mutableStateOf("") }
    var selectedColor by rememberSaveable(selectedText) { mutableStateOf(HighlightColor.GOLD) }
    var bookmarkScope by rememberSaveable(selectedText) {
        mutableStateOf(if (selectedText.trim().split(Regex("\\s+")).size <= 3 && paragraphText.isNotBlank()) "PARAGRAPH" else "SELECTION")
    }

    AnimatedVisibility(
        visible = showSelectionMenu,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
            .padding(bottom = bottomPadding, start = 14.dp, end = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
        ) {
            // Inline Quick Naming Prompt Card
            AnimatedVisibility(
                visible = showNamingPrompt,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bookmark & Highlight",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    HighlightColor.GOLD to Color(0xFFD4AF37),
                                    HighlightColor.ROSE to Color(0xFFE5B7B7),
                                    HighlightColor.SAGE to Color(0xFFB2C2B2)
                                ).forEach { (colorEnum, cVal) ->
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(cVal)
                                            .then(
                                                if (selectedColor == colorEnum) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                else Modifier
                                            )
                                            .clickable { selectedColor = colorEnum }
                                    )
                                }
                            }
                        }

                        if (paragraphText.isNotBlank() && paragraphText.trim() != selectedText.trim()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = bookmarkScope == "PARAGRAPH",
                                    onClick = {
                                        bookmarkScope = "PARAGRAPH"
                                        if (bookmarkNameDraft.isBlank()) {
                                            val words = paragraphText.trim().split(Regex("\\s+")).take(5).joinToString(" ")
                                            bookmarkNameDraft = words
                                        }
                                    },
                                    label = { Text("Entire Paragraph", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = bookmarkScope == "SELECTION",
                                    onClick = {
                                        bookmarkScope = "SELECTION"
                                        if (bookmarkNameDraft.isBlank()) {
                                            val words = selectedText.trim().split(Regex("\\s+")).take(5).joinToString(" ")
                                            bookmarkNameDraft = words
                                        }
                                    },
                                    label = { Text("Selection Only", fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = bookmarkNameDraft,
                                onValueChange = { bookmarkNameDraft = it },
                                placeholder = { Text("Bookmark title / note...", fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                            FilledIconButton(
                                onClick = {
                                    val title = bookmarkNameDraft.trim()
                                    val targetQuote = if (bookmarkScope == "PARAGRAPH" && paragraphText.isNotBlank()) paragraphText else selectedText
                                    onAddBookmark(targetQuote, selectedColor, activePage, title)
                                    showNamingPrompt = false
                                    bookmarkNameDraft = ""
                                    onDismiss()
                                    Toast.makeText(context, if (title.isNotBlank()) "Saved bookmark: $title" else "Bookmark saved", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Save Bookmark", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            IconButton(
                                onClick = {
                                    showNamingPrompt = false
                                    bookmarkNameDraft = ""
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Main Selection Toolbar Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD4AF37))
                                .clickable {
                                    onAddBookmark(selectedText, HighlightColor.GOLD, activePage, "")
                                    onDismiss()
                                    Toast.makeText(context, "Added Gold highlight", Toast.LENGTH_SHORT).show()
                                }
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE5B7B7))
                                .clickable {
                                    onAddBookmark(selectedText, HighlightColor.ROSE, activePage, "")
                                    onDismiss()
                                    Toast.makeText(context, "Added Rose highlight", Toast.LENGTH_SHORT).show()
                                }
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB2C2B2))
                                .clickable {
                                    onAddBookmark(selectedText, HighlightColor.SAGE, activePage, "")
                                    onDismiss()
                                    Toast.makeText(context, "Added Sage highlight", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    // Bookmark & Name button: opens inline quick naming prompt
                    IconButton(
                        onClick = {
                            showNamingPrompt = !showNamingPrompt
                            if (showNamingPrompt && bookmarkNameDraft.isBlank()) {
                                val words = selectedText.trim().split(Regex("\\s+")).take(5).joinToString(" ")
                                bookmarkNameDraft = words
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (showNamingPrompt) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
                            contentDescription = "Bookmark / Name",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Read from here: starts TTS from the selected paragraph
                    IconButton(
                        onClick = {
                            onReadFromHere(selectedText)
                            onDismiss()
                            Toast.makeText(context, "Reading aloud from selection", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read from here", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Note button: directly inspect or attach full note
                    IconButton(
                        onClick = {
                            val mark = activeBookmark ?: Bookmark(
                                bookTitle = bookTitle,
                                chapter = selectedChapterTitle.ifBlank { activeChapterTitle },
                                quote = selectedText,
                                color = HighlightColor.GOLD,
                                timestamp = "Just now",
                                pageNumber = activePage
                            )
                            if (activeBookmark == null) {
                                onAddBookmark(selectedText, HighlightColor.GOLD, activePage, "")
                            }
                            onOpenNoteModal(mark)
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = "Add Note / Inspect", tint = MaterialTheme.colorScheme.secondary)
                    }

                    if (activeBookmark != null) {
                        IconButton(
                            onClick = {
                                onRemoveBookmark(activeBookmark.id)
                                onDismiss()
                                Toast.makeText(context, "Removed highlight", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Highlight", tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    IconButton(
                        onClick = {
                            val firstWord = selectedText.trim().split("\\s+".toRegex()).firstOrNull()?.replace("[^a-zA-Z]".toRegex(), "") ?: selectedText
                            onLookupWord(firstWord.ifBlank { selectedText.trim() })
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.Spellcheck, contentDescription = "Word Meaning", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Regular Plain Text Copy
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Text", selectedText))
                            onDismiss()
                            Toast.makeText(context, "Copied text", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Plain Text", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Copy with Citation
                    IconButton(
                        onClick = {
                            val formatted = CitationHelper.formatCitation(
                                quote = selectedText,
                                author = author,
                                bookTitle = bookTitle,
                                chapterTitle = selectedChapterTitle.ifBlank { activeChapterTitle }
                            )
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Citation", formatted))
                            onDismiss()
                            Toast.makeText(context, "Copied with citation reference!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.FormatQuote, contentDescription = "Cite Quote", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Close Menu
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Menu", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
