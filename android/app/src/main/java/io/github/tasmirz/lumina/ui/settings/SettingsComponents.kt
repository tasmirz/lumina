package io.github.tasmirz.lumina.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.data.OnlineEpubService
import io.github.tasmirz.lumina.model.CustomCatalogEndpoint
import io.github.tasmirz.lumina.model.GestureAction
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reusable UI components, cards, and modal dialogs for Lumina's Advanced Settings.
 */

@Composable
fun CollapsibleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isExpanded: Boolean = true,
    onToggle: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            content()
        }
    }
}

@Composable
fun AiToolCompactRow(
    name: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = desc,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ConfigurableGestureItem(
    gestureName: String,
    action: GestureAction,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gestureName,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = action.displayName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Change",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
fun GestureGuideItem(
    gesture: String,
    action: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = 1.dp)
        ) {
            Text(
                text = gesture,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(
            text = action,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ColorSwatchScrollRow(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    presets: List<Color>,
    onOpenPicker: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { color ->
            val isSelected = color == selectedColor
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 2.dp else 0.6.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        if (onOpenPicker != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable { onOpenPicker() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Pick Custom Color",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun LuminaColorPickerDialog(
    title: String,
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorConfirmed: (Color) -> Unit
) {
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        val r = (initialColor.red * 255f).roundToInt().coerceIn(0, 255)
        val g = (initialColor.green * 255f).roundToInt().coerceIn(0, 255)
        val b = (initialColor.blue * 255f).roundToInt().coerceIn(0, 255)
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1].coerceIn(0f, 1f)) }
    var valueBrightness by remember { mutableFloatStateOf(initialHsv[2].coerceIn(0f, 1f)) }

    fun hsvToRgbColor(h: Float, s: Float, v: Float): Color {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        return Color(rgb)
    }

    val currentColor = remember(hue, saturation, valueBrightness) {
        hsvToRgbColor(hue, saturation, valueBrightness)
    }

    var hexInput by remember(currentColor) {
        val r = (currentColor.red * 255f).roundToInt().coerceIn(0, 255)
        val g = (currentColor.green * 255f).roundToInt().coerceIn(0, 255)
        val b = (currentColor.blue * 255f).roundToInt().coerceIn(0, 255)
        mutableStateOf(String.format(Locale.US, "#%02X%02X%02X", r, g, b))
    }
    var hexError by remember { mutableStateOf(false) }

    fun updateFromHex(hex: String) {
        hexInput = hex
        val clean = hex.removePrefix("#").trim()
        if (clean.length == 6) {
            try {
                val parsed = java.lang.Long.parseLong(clean, 16).toInt()
                val r = (parsed shr 16) and 0xFF
                val g = (parsed shr 8) and 0xFF
                val b = parsed and 0xFF
                val hsv = FloatArray(3)
                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                hue = hsv[0]
                saturation = hsv[1]
                valueBrightness = hsv[2]
                hexError = false
            } catch (_: Exception) {
                hexError = true
            }
        } else {
            hexError = hex.isNotBlank() && clean.length != 6
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = currentColor,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = hexInput.uppercase(),
                            color = if (currentColor.luminance() > 0.5f) Color.Black else Color.White,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(190.dp)
                        .padding(4.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                fun handlePointer(offset: Offset) {
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    val radius = minOf(centerX, centerY)
                                    val dx = offset.x - centerX
                                    val dy = offset.y - centerY
                                    val dist = sqrt(dx * dx + dy * dy)
                                    val sat = (dist / radius).coerceIn(0f, 1f)
                                    val angleRad = atan2(dy, dx)
                                    val angleDeg = ((Math.toDegrees(angleRad.toDouble()).toFloat() + 360f) % 360f)
                                    hue = angleDeg
                                    saturation = sat
                                    hexError = false
                                }

                                detectTapGestures(
                                    onPress = { handlePointer(it) }
                                )
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    val radius = minOf(centerX, centerY)
                                    val dx = change.position.x - centerX
                                    val dy = change.position.y - centerY
                                    val dist = sqrt(dx * dx + dy * dy)
                                    val sat = (dist / radius).coerceIn(0f, 1f)
                                    val angleRad = atan2(dy, dx)
                                    val angleDeg = ((Math.toDegrees(angleRad.toDouble()).toFloat() + 360f) % 360f)
                                    hue = angleDeg
                                    saturation = sat
                                    hexError = false
                                }
                            }
                    ) {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val radius = minOf(centerX, centerY)

                        val sweepBrush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red
                            ),
                            center = Offset(centerX, centerY)
                        )
                        drawCircle(
                            brush = sweepBrush,
                            radius = radius,
                            center = Offset(centerX, centerY)
                        )

                        val radialBrush = Brush.radialGradient(
                            colors = listOf(Color.White, Color.Transparent),
                            center = Offset(centerX, centerY),
                            radius = radius
                        )
                        drawCircle(
                            brush = radialBrush,
                            radius = radius,
                            center = Offset(centerX, centerY)
                        )

                        if (valueBrightness < 1.0f) {
                            drawCircle(
                                color = Color.Black.copy(alpha = 1.0f - valueBrightness),
                                radius = radius,
                                center = Offset(centerX, centerY)
                            )
                        }

                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.35f),
                            radius = radius,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 1.5.dp.toPx())
                        )

                        val thumbAngleRad = Math.toRadians(hue.toDouble())
                        val thumbDist = saturation * radius
                        val thumbX = centerX + (thumbDist * cos(thumbAngleRad)).toFloat()
                        val thumbY = centerY + (thumbDist * sin(thumbAngleRad)).toFloat()

                        drawCircle(
                            color = Color.Black.copy(alpha = 0.6f),
                            radius = 11.dp.toPx(),
                            center = Offset(thumbX, thumbY),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 9.dp.toPx(),
                            center = Offset(thumbX, thumbY),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = currentColor,
                            radius = 7.dp.toPx(),
                            center = Offset(thumbX, thumbY)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Brightness", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${(valueBrightness * 100f).roundToInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Normal)
                    }
                    Slider(
                        value = valueBrightness,
                        onValueChange = {
                            valueBrightness = it
                            hexError = false
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { updateFromHex(it) },
                    label = { Text("Hex Code (#RRGGBB)", fontSize = 11.sp) },
                    singleLine = true,
                    isError = hexError,
                    supportingText = if (hexError) {
                        { Text("Invalid 6-digit hex code", color = MaterialTheme.colorScheme.error, fontSize = 10.sp) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Text("Reading Presets", fontSize = 11.sp, fontWeight = FontWeight.Normal, modifier = Modifier.align(Alignment.Start))
                val quickPicks = listOf(
                    Color(0xFFFAF6EE), Color(0xFFF4ECD8), Color(0xFFEFE8D8), Color(0xFFEFF4ED),
                    Color(0xFF1E1C1A), Color(0xFF181D24), Color(0xFF211B17), Color(0xFF131722),
                    Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFD55E00), Color(0xFF0072B2),
                    Color(0xFFC86446), Color(0xFFD4A017), Color(0xFF5B8A68), Color(0xFF5E81AC)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPicks.forEach { col ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(1.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
                            .clickable {
                                val hsv = FloatArray(3)
                                val r = (col.red * 255f).roundToInt().coerceIn(0, 255)
                                val g = (col.green * 255f).roundToInt().coerceIn(0, 255)
                                val b = (col.blue * 255f).roundToInt().coerceIn(0, 255)
                                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                valueBrightness = hsv[2]
                                hexError = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorConfirmed(currentColor) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Apply Color", fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 12.sp)
            }
        }
    )
}

fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) String.format("%.2f MB", mb) else String.format("%.1f KB", kb)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomEndpointEditorDialog(
    initialEndpoint: CustomCatalogEndpoint?,
    onDismiss: () -> Unit,
    onSave: (CustomCatalogEndpoint) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialEndpoint?.name ?: "") }
    var galleryUrl by remember { mutableStateOf(initialEndpoint?.galleryUrl ?: "") }
    var searchUrl by remember { mutableStateOf(initialEndpoint?.searchUrl ?: "") }
    var apiKey by remember { mutableStateOf(initialEndpoint?.apiKey ?: "") }
    var authHeader by remember { mutableStateOf(initialEndpoint?.authHeader ?: "Authorization") }
    var isEnabled by remember { mutableStateOf(initialEndpoint?.isEnabled ?: true) }
    var isKeyVisible by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var testStatusSuccess by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialEndpoint == null) "Add Custom OPDS / Book Endpoint" else "Edit Book Endpoint",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Catalog Name", fontSize = 12.sp) },
                    placeholder = { Text("e.g. My Calibre Server, Custom OPDS", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = searchUrl,
                    onValueChange = { searchUrl = it },
                    label = { Text("Search OPDS URL", fontSize = 12.sp) },
                    placeholder = { Text("https://my-server.com/opds/search?q=%s", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = galleryUrl,
                    onValueChange = { galleryUrl = it },
                    label = { Text("Gallery / Browse URL (Optional)", fontSize = 12.sp) },
                    placeholder = { Text("https://my-server.com/opds", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key / Token / Basic Auth (Optional)", fontSize = 12.sp) },
                    placeholder = { Text("Bearer token or user:pass", fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isKeyVisible) "Hide" else "Show",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = authHeader,
                    onValueChange = { authHeader = it },
                    label = { Text("HTTP Header Name", fontSize = 12.sp) },
                    placeholder = { Text("Authorization, X-API-Key, etc.", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val temp = CustomCatalogEndpoint(
                                id = initialEndpoint?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name.ifBlank { "Test Source" },
                                galleryUrl = galleryUrl.trim(),
                                searchUrl = searchUrl.trim(),
                                apiKey = apiKey.trim(),
                                authHeader = authHeader.trim().ifBlank { "Authorization" },
                                isEnabled = true
                            )
                            isTesting = true
                            testStatusMessage = null
                            testStatusSuccess = null
                            coroutineScope.launch {
                                val (ok, msg) = OnlineEpubService.testEndpointConnection(temp)
                                testStatusSuccess = ok
                                testStatusMessage = msg
                                isTesting = false
                            }
                        },
                        enabled = !isTesting && (searchUrl.isNotBlank() || galleryUrl.isNotBlank()),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing...", fontSize = 11.5.sp)
                        } else {
                            Icon(Icons.Outlined.NetworkCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Connection", fontSize = 11.5.sp)
                        }
                    }
                }

                if (testStatusMessage != null) {
                    val isOk = testStatusSuccess == true
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isOk) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = testStatusMessage.orEmpty(),
                            fontSize = 11.sp,
                            color = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val endpoint = CustomCatalogEndpoint(
                        id = initialEndpoint?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        galleryUrl = galleryUrl.trim(),
                        searchUrl = searchUrl.trim(),
                        apiKey = apiKey.trim(),
                        authHeader = authHeader.trim().ifBlank { "Authorization" },
                        isEnabled = isEnabled
                    )
                    onSave(endpoint)
                },
                enabled = name.isNotBlank() && (searchUrl.isNotBlank() || galleryUrl.isNotBlank()),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save", fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel", fontSize = 12.sp)
            }
        }
    )
}
