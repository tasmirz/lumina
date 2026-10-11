package io.github.tasmirz.lumina.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fullscreen pinch-to-zoom Lightbox Dialog for inspecting reader images & illustrations.
 */
@Composable
fun LightboxDialog(
    previewBitmap: Bitmap?,
    imagePath: String? = null,
    onDismiss: () -> Unit
) {
    if (previewBitmap == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var fullResBitmap by remember { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(imagePath) {
            if (!imagePath.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val cleanPath = imagePath.removePrefix("file://")
                        val decodedPath = try { java.net.URLDecoder.decode(cleanPath, "UTF-8") } catch (_: Exception) { cleanPath }
                        val file = if (File(cleanPath).exists()) File(cleanPath) else File(decodedPath)
                        if (file.exists() && file.length() > 0) {
                            val full = BitmapFactory.decodeFile(file.absolutePath)
                            if (full != null) {
                                withContext(Dispatchers.Main) {
                                    fullResBitmap = full
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                fullResBitmap?.recycle()
            }
        }

        val displayBitmap = fullResBitmap ?: previewBitmap
        val bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.96f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.8f, 6.0f)
                        offset += pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                        onTap = {
                            onDismiss()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = displayBitmap.asImageBitmap(),
                contentDescription = "Full-Screen Illustration",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize(0.96f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }
    }
}
