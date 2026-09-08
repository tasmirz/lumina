package io.github.tasmirz.lumina.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.tasmirz.lumina.data.AiProvider
import io.github.tasmirz.lumina.data.AssistantAction
import io.github.tasmirz.lumina.data.AssistantService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantChatSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    bookTitle: String,
    activeChapterTitle: String,
    knownContext: String,
    apiKey: String,
    provider: AiProvider,
    baseUrl: String,
    modelName: String,
    spoilerShield: Boolean,
    disableStt: Boolean,
    disableAi: Boolean = false,
    autoStartVoice: Boolean = false,
    assistantService: AssistantService,
    onExecuteAction: (AssistantAction) -> Unit,
    onStartVoiceListening: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var inputPrompt by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var chatResponse by remember { mutableStateOf("") }
    var isQuerying by remember { mutableStateOf(false) }
    var lastExecutedAction by remember { mutableStateOf<AssistantAction?>(null) }

    fun sendQuery(prompt: String) {
        val q = prompt.trim()
        if (q.isBlank()) return
        inputPrompt = ""
        isQuerying = true
        chatResponse = ""
        lastExecutedAction = null

        // 1. Check local rule-based commands first (instant, zero network, zero embeddings)
        val localAction = assistantService.parseLocalCommand(q, 100)
        if (localAction != null) {
            onExecuteAction(localAction)
            lastExecutedAction = localAction
            chatResponse = when (localAction) {
                is AssistantAction.NextChapter -> "Moved to next chapter."
                is AssistantAction.PreviousChapter -> "Moved to previous chapter."
                is AssistantAction.NavigateChapter -> "Jumped to chapter ${localAction.targetIndex + 1}."
                is AssistantAction.ToggleTts -> if (localAction.play) "Started reading aloud." else "Paused reading."
                is AssistantAction.AddNote -> "Note saved: \"${localAction.noteContent}\""
                is AssistantAction.SwitchTheme -> "Theme switched to ${localAction.themeFamily ?: localAction.mode}."
                is AssistantAction.JumpToScene -> "Searching scene in book..."
                is AssistantAction.ControlTts -> "TTS command executed."
                is AssistantAction.ToggleAutoScroll -> if (localAction.enable) "Auto-scroll started." else "Auto-scroll paused."
                is AssistantAction.CreateTheme -> "Custom theme \"${localAction.name}\" created and applied."
                is AssistantAction.UpdateTheme -> "Custom theme \"${localAction.name}\" updated and applied."
                is AssistantAction.Answer -> localAction.text
            }
            isQuerying = false
            return
        }

        if (disableAi) {
            chatResponse = "Generative AI is disabled in Settings. You can type offline commands like 'next chapter', 'previous chapter', 'dark mode', 'warm theme', 'read aloud', 'jump to chapter [N]', or 'note: [text]'."
            isQuerying = false
            return
        }

        // 2. Query LLM endpoint (Gemini or OpenAI-compatible)
        coroutineScope.launch {
            try {
                val res = assistantService.queryAssistant(
                    provider = provider,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    modelName = modelName,
                    bookTitle = bookTitle,
                    activeChapterTitle = activeChapterTitle,
                    knownContext = knownContext,
                    userQuery = q,
                    spoilerShield = spoilerShield
                )
                chatResponse = res.answerText
                res.executedAction?.let {
                    lastExecutedAction = it
                    onExecuteAction(it)
                }
            } catch (e: Exception) {
                chatResponse = "Error connecting to ${provider.displayName}: ${e.message}"
            } finally {
                isQuerying = false
            }
        }
    }

    var startVoiceHandler: (() -> Unit)? = null

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceHandler?.invoke()
        } else {
            Toast.makeText(context, "Microphone permission required for voice", Toast.LENGTH_SHORT).show()
        }
    }

    fun startVoice() {
        if (disableStt || disableAi) return
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        isListening = true
        assistantService.startListening(
            onReady = { isListening = true },
            onPartialResult = { partial ->
                inputPrompt = partial
            },
            onResult = { text ->
                isListening = false
                inputPrompt = text
                if (text.isNotBlank()) {
                    sendQuery(text)
                }
            },
            onError = { err ->
                isListening = false
                Toast.makeText(context, "Voice: $err", Toast.LENGTH_SHORT).show()
            }
        )
    }

    startVoiceHandler = { startVoice() }

    LaunchedEffect(isOpen, autoStartVoice) {
        if (isOpen && autoStartVoice && !disableStt && !disableAi) {
            startVoice()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isListening) {
                assistantService.stopListening()
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (isListening) {
                assistantService.stopListening()
                isListening = false
            }
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (disableAi) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (disableAi) Icons.Default.Tune else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (disableAi) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (disableAi) "Lumina Command Palette" else "Lumina Story Assistant",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (disableAi) "100% Offline • Zero Network • Local Rules" else "Offline FTS5 Context • ${provider.displayName}",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Prompt Suggestion Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (disableAi) {
                        QuickPromptChip("Next Chapter", Icons.AutoMirrored.Filled.ArrowForward) { sendQuery("next chapter") }
                        QuickPromptChip("Prev Chapter", Icons.AutoMirrored.Filled.ArrowBack) { sendQuery("previous chapter") }
                        QuickPromptChip("Dark Mode", Icons.Outlined.DarkMode) { sendQuery("switch to dark theme") }
                        QuickPromptChip("Warm Theme", Icons.Outlined.LightMode) { sendQuery("switch to warm theme") }
                        QuickPromptChip("Read Aloud", Icons.Outlined.VolumeUp) { sendQuery("start reading aloud") }
                        QuickPromptChip("Auto-Scroll", Icons.Outlined.SwapVert) { sendQuery("start auto-scroll") }
                    } else {
                        QuickPromptChip("Summarize Chapter", Icons.Outlined.MenuBook) { sendQuery("Summarize $activeChapterTitle") }
                        QuickPromptChip("Explain Context", Icons.Outlined.HelpOutline) { sendQuery("Explain what is happening right now in $activeChapterTitle") }
                        QuickPromptChip("Key Characters", Icons.Outlined.Person) { sendQuery("Who are the main characters mentioned so far?") }
                        QuickPromptChip("Dark Mode", Icons.Outlined.DarkMode) { sendQuery("switch to dark theme") }
                        QuickPromptChip("Read Aloud", Icons.Outlined.VolumeUp) { sendQuery("start reading aloud") }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Response Box / Loading Indicator
                if (isQuerying || chatResponse.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 70.dp, max = 220.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (isQuerying) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "Analyzing story context...",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    text = chatResponse,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (lastExecutedAction != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Command executed automatically",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Live Listening Indicator Banner
                AnimatedVisibility(
                    visible = isListening,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Text(
                            text = "Listening... speak command or question",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Input Row: Text Field (with In-Box Mic Button) + Send / Enter Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputPrompt,
                        onValueChange = { inputPrompt = it },
                        placeholder = {
                            Text(
                                text = if (isListening) "Listening..." else if (disableAi) "Type offline command (e.g. dark mode)..." else "Ask anything or type command...",
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = {
                            if (!disableStt && !disableAi) {
                                IconButton(
                                    onClick = {
                                        if (isListening) {
                                            assistantService.stopListening()
                                            isListening = false
                                        } else {
                                            startVoice()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = if (isListening) "Stop listening" else "Speak voice command",
                                        tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        minLines = 2,
                        maxLines = 3,
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Send / Enter Button
                    FilledIconButton(
                        onClick = { sendQuery(inputPrompt) },
                        enabled = inputPrompt.isNotBlank() && !isQuerying,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send question",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPromptChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
