package io.github.tasmirz.lumina.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import io.github.tasmirz.lumina.data.AiProvider
import io.github.tasmirz.lumina.data.AssistantAction
import io.github.tasmirz.lumina.data.AssistantService
import kotlinx.coroutines.launch

data class AssistantChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val executedAction: AssistantAction? = null,
    val timestamp: Long = System.currentTimeMillis()
)

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
    languageCode: String = "en",
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
    var isQuerying by remember { mutableStateOf(false) }
    var messageHistory by remember { mutableStateOf<List<AssistantChatMessage>>(emptyList()) }

    val listState = rememberLazyListState()

    fun addMessage(msg: AssistantChatMessage) {
        messageHistory = (messageHistory + msg).takeLast(6)
    }

    fun sendQuery(prompt: String) {
        val q = prompt.trim()
        if (q.isBlank()) return
        inputPrompt = ""
        isQuerying = true

        addMessage(AssistantChatMessage(isUser = true, text = q))

        // 1. Check local rule-based commands first
        val localAction = assistantService.parseLocalCommand(q, 100)
        if (localAction != null) {
            onExecuteAction(localAction)
            val responseText = when (localAction) {
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
            addMessage(AssistantChatMessage(isUser = false, text = responseText, executedAction = localAction))
            isQuerying = false
            return
        }

        if (disableAi) {
            val offlineMsg = "Generative AI is disabled. You can use offline commands: 'next chapter', 'dark mode', 'warm theme', 'read aloud', 'jump to chapter [N]'."
            addMessage(AssistantChatMessage(isUser = false, text = offlineMsg))
            isQuerying = false
            return
        }

        // 2. Query LLM endpoint
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
                    spoilerShield = spoilerShield,
                    language = languageCode
                )
                addMessage(AssistantChatMessage(isUser = false, text = res.answerText, executedAction = res.executedAction))
                res.executedAction?.let { onExecuteAction(it) }
            } catch (e: Exception) {
                addMessage(AssistantChatMessage(isUser = false, text = "Error: ${e.message}"))
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
            languageCode = languageCode,
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

    LaunchedEffect(messageHistory.size, isQuerying) {
        if (messageHistory.isNotEmpty()) {
            listState.animateScrollToItem(messageHistory.size - 1)
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
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Scrim overlay covering entire screen; tapping upper area dismisses
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            // Bottom translucent sheet container
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Do not dismiss when clicking inside overlay */ }
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.50f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Transparent Header Row without boxy card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (disableAi) Icons.Default.Tune else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (disableAi) "Command Palette" else "Lumina Assistant",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (disableAi) "Offline Rules" else "$activeChapterTitle • ${provider.displayName}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable Chat History with Extended Vanishing Alpha Gradient toward top
                if (messageHistory.isNotEmpty() || isQuerying) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp, max = 340.dp)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                // Extended vanishing point alpha gradient at the top
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        0.48f to Color.Black,
                                        1.0f to Color.Black
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(top = 18.dp, bottom = 6.dp)
                        ) {
                            items(messageHistory, key = { it.id }) { msg ->
                                if (msg.isUser) {
                                    // User question / voice query (right-aligned)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                            modifier = Modifier.widthIn(max = 280.dp)
                                        ) {
                                            Text(
                                                text = msg.text,
                                                fontSize = 12.5.sp,
                                                lineHeight = 17.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                } else {
                                    // Assistant response (left-aligned)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                            shadowElevation = 2.dp,
                                            modifier = Modifier.widthIn(max = 310.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = msg.text,
                                                    fontSize = 12.5.sp,
                                                    lineHeight = 18.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )

                                                if (msg.executedAction != null) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.secondary,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "Action executed",
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
                                }
                            }

                            if (isQuerying) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 1.8.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Thinking...",
                                                    fontSize = 11.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Command Chips directly above the typing box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (disableAi) {
                        QuickPromptChip("Next Chapter", Icons.AutoMirrored.Filled.ArrowForward) { sendQuery("next chapter") }
                        QuickPromptChip("Prev Chapter", Icons.AutoMirrored.Filled.ArrowBack) { sendQuery("previous chapter") }
                        QuickPromptChip("Dark Mode", Icons.Outlined.DarkMode) { sendQuery("switch to dark theme") }
                        QuickPromptChip("Warm Theme", Icons.Outlined.LightMode) { sendQuery("switch to warm theme") }
                        QuickPromptChip("Read Aloud", Icons.Default.PlayArrow) { sendQuery("start reading aloud") }
                        QuickPromptChip("Auto-Scroll", Icons.Outlined.SwapVert) { sendQuery("start auto-scroll") }
                    } else {
                        QuickPromptChip("Summarize", Icons.Filled.MenuBook) { sendQuery("Summarize $activeChapterTitle") }
                        QuickPromptChip("Context", Icons.Filled.HelpOutline) { sendQuery("Explain what is happening right now in $activeChapterTitle") }
                        QuickPromptChip("Characters", Icons.Outlined.Person) { sendQuery("Who are the main characters mentioned so far?") }
                        QuickPromptChip("Dark Mode", Icons.Outlined.DarkMode) { sendQuery("switch to dark theme") }
                        QuickPromptChip("Read Aloud", Icons.Default.PlayArrow) { sendQuery("start reading aloud") }
                    }
                }

                // Bottom Opaque Searchbox Pill
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(
                        if (isListening) 1.5.dp else 1.dp,
                        if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isListening) {
                            VoiceWaveformVisualizer(
                                isListening = true,
                                barColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        BasicTextField(
                            value = inputPrompt,
                            onValueChange = { inputPrompt = it },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.5.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { sendQuery(inputPrompt) }),
                            decorationBox = { innerTextField ->
                                if (inputPrompt.isEmpty()) {
                                    Text(
                                        text = if (isListening) "Listening to your voice..." else if (disableAi) "Type offline command..." else "Ask Lumina or type command...",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (!disableStt && !disableAi) {
                            val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
                            val micGlow by infiniteTransition.animateFloat(
                                initialValue = 0.15f,
                                targetValue = 0.45f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(650, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "mic_glow"
                            )
                            IconButton(
                                onClick = {
                                    if (isListening) {
                                        assistantService.stopListening()
                                        isListening = false
                                    } else {
                                        startVoice()
                                    }
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .then(
                                        if (isListening) Modifier.background(
                                            MaterialTheme.colorScheme.error.copy(alpha = micGlow),
                                            CircleShape
                                        ) else Modifier
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Voice input",
                                    tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { sendQuery(inputPrompt) },
                            enabled = inputPrompt.isNotBlank() && !isQuerying,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputPrompt.isNotBlank() && !isQuerying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceWaveformVisualizer(
    isListening: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    if (!isListening) return
    val infiniteTransition = rememberInfiniteTransition(label = "voice_wave")
    val anim1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val anim2 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(310, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val anim3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )
    val anim4 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar4"
    )
    val anim5 by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(440, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar5"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(anim1, anim2, anim3, anim4, anim5).forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((6.dp + (fraction * 14).dp))
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary
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
