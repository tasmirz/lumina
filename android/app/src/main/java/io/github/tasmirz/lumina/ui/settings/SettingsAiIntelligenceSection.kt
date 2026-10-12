package io.github.tasmirz.lumina.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.data.BookRepository
import io.github.tasmirz.lumina.data.AiProvider

/**
 * Section 3: AI Assistant & Intelligence settings.
 */
@Composable
fun SettingsAiIntelligenceSection(
    repository: BookRepository?,
    disableAi: Boolean,
    aiProvider: AiProvider,
    onAiProviderChange: (AiProvider) -> Unit,
    geminiApiKey: String,
    onGeminiApiKeyChange: (String) -> Unit,
    aiBaseUrl: String,
    onAiBaseUrlChange: (String) -> Unit,
    aiModel: String,
    onAiModelChange: (String) -> Unit,
    disableVoiceInput: Boolean,
    autoStartMic: Boolean,
    spoilerShield: Boolean,
    preferredLanguage: String
) {
    val context = LocalContext.current
    var keyText by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

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
                        fontWeight = FontWeight.Normal,
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
                    fontWeight = FontWeight.Normal,
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
                                    fontWeight = FontWeight.Normal,
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
                    fontWeight = FontWeight.Normal,
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
                                fontWeight = FontWeight.Normal,
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
                        fontWeight = FontWeight.Normal,
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
                    fontWeight = FontWeight.Normal,
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
                                imageVector = if (disableVoiceInput) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                                contentDescription = null,
                                tint = if (disableVoiceInput) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Disable Voice Input (Mic)",
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (disableVoiceInput) "Microphone is completely disabled across the entire app." else "Speech-to-text input active for assistant and quick navigation.",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = disableVoiceInput,
                        onCheckedChange = { repository?.setDisableStt(it) }
                    )
                }

                // Auto-Start Mic Toggle (only when voice input is enabled)
                if (!disableVoiceInput) {
                    Spacer(modifier = Modifier.height(8.dp))
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
                                    tint = if (autoStartMic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Auto-Start Microphone",
                                    fontWeight = FontWeight.Normal,
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
                                fontWeight = FontWeight.Normal,
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
                    fontWeight = FontWeight.Normal,
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
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Normal) }
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
                fontWeight = FontWeight.Normal,
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
