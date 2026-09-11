package io.github.tasmirz.lumina.ui.reader

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.model.ReadingMode
import io.github.tasmirz.lumina.ui.components.AssistantVoiceState

/**
 * Distraction-Free Header Bar displaying book title and current chapter title.
 * In Full Paged mode, remains persistently visible.
 */
@Composable
fun ReaderTopBar(
    isUiVisible: Boolean,
    readingMode: ReadingMode,
    voiceState: AssistantVoiceState,
    showInBookSearchDialog: Boolean,
    bookTitle: String,
    activeChapterTitle: String,
    assistantOrbStyle: String,
    disableAi: Boolean,
    onOpenToc: () -> Unit,
    onOpenAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isVisible = (if (isLandscape) isUiVisible else (isUiVisible || readingMode == ReadingMode.PAGED)) &&
            voiceState == AssistantVoiceState.IDLE &&
            !showInBookSearchDialog

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier
    ) {
        val topCutout = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        val topStatusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topSafeInset = maxOf(topCutout, topStatusBar, 14.dp)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topSafeInset)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenToc() },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = bookTitle,
                            fontFamily = FontFamily.Serif,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = activeChapterTitle,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }

                    if (assistantOrbStyle == "TOP_BAR_BUTTON") {
                        IconButton(
                            onClick = {
                            if (disableAi) {
                                Toast.makeText(context, "AI Assistant is disabled in Settings", Toast.LENGTH_SHORT).show()
                            } else {
                                onOpenAssistant()
                            }
                        },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Assistant",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Delicate hairline divider underneath the header bar
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            }
        }
    }
}
