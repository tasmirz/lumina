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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.model.Bookmark
import io.github.tasmirz.lumina.model.HighlightColor
import io.github.tasmirz.lumina.util.CitationHelper

@Composable
fun ReaderSelectionMenu(
    showSelectionMenu: Boolean,
    selectedText: String,
    selectedChapterTitle: String,
    activeChapterTitle: String,
    activePage: Int,
    author: String,
    bookTitle: String,
    activeBookmark: Bookmark?,
    bottomPadding: Dp,
    onAddBookmark: (String, HighlightColor, Int) -> Unit,
    onRemoveBookmark: (Long) -> Unit,
    onOpenNoteModal: (Bookmark) -> Unit,
    onReadFromHere: (String) -> Unit,
    onLookupWord: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = showSelectionMenu,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
            .padding(bottom = bottomPadding, start = 14.dp, end = 14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Highlight:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD4AF37))
                            .clickable {
                                onAddBookmark(selectedText, HighlightColor.GOLD, activePage)
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
                                onAddBookmark(selectedText, HighlightColor.ROSE, activePage)
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
                                onAddBookmark(selectedText, HighlightColor.SAGE, activePage)
                                onDismiss()
                                Toast.makeText(context, "Added Sage highlight", Toast.LENGTH_SHORT).show()
                            }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Read from here: starts TTS from the selected paragraph
                    IconButton(
                        onClick = {
                            onReadFromHere(selectedText)
                            onDismiss()
                            Toast.makeText(context, "Reading aloud from selection", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Read from here", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Note button: directly inspect or attach note
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
                                onAddBookmark(selectedText, HighlightColor.GOLD, activePage)
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
                        Icon(Icons.Default.FormatQuote, contentDescription = "Cite Quote")
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Menu")
                    }
                }
            }
        }
    }
}
