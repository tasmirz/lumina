package io.github.tasmirz.lumina.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import io.github.tasmirz.lumina.model.BackgroundTexture
import io.github.tasmirz.lumina.model.CustomTextureData
import io.github.tasmirz.lumina.model.ThemeVariant
import java.io.File

/**
 * Surface texture overlays for reader canvas (Paper Grain, Parchment, Linen, Canvas, Kraft, Ruled lines, and Custom textures).
 */
@Composable
fun ReaderTextureOverlay(
    backgroundTexture: BackgroundTexture,
    currentThemeVariant: ThemeVariant,
    dynamicRollingTexture: Boolean,
    getTextureScrollOffsetX: () -> Int,
    getTextureScrollOffsetY: () -> Int,
    customTextures: List<CustomTextureData>,
    selectedCustomTextureId: String,
    modifier: Modifier = Modifier
) {
    when (backgroundTexture) {
        BackgroundTexture.GRAIN -> {
            val isDark = currentThemeVariant == ThemeVariant.DARK
            val grainShader = remember(isDark) {
                val tile = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                val canvas = AndroidCanvas(tile)
                val paint = AndroidPaint().apply {
                    isAntiAlias = false
                }
                val dotColor = if (isDark) AndroidColor.WHITE else AndroidColor.BLACK
                paint.color = dotColor
                val maxAlpha = if (isDark) 30 else 42
                for (x in 0 until 128 step 2) {
                    for (y in 0 until 128 step 2) {
                        val hash = ((x * 374761393 + y * 668265263) xor (x * 1274126177)) and 0x7FFFFFFF
                        if (hash % 5 <= 1) {
                            val alpha = (hash % maxAlpha) + 8
                            paint.alpha = alpha.coerceIn(0, 255)
                            val r = if (hash % 7 == 0) 1.25f else 0.75f
                            canvas.drawCircle(x.toFloat(), y.toFloat(), r, paint)
                        }
                    }
                }
                BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            Canvas(modifier = modifier.fillMaxSize()) {
                val rollX = if (dynamicRollingTexture) (((getTextureScrollOffsetX() % 128) + 128) % 128).toFloat() else 0f
                val rollY = if (dynamicRollingTexture) (((getTextureScrollOffsetY() % 128) + 128) % 128).toFloat() else 0f
                withTransform({
                    translate(left = -rollX, top = -rollY)
                }) {
                    drawIntoCanvas { canvas ->
                        val paint = androidx.compose.ui.graphics.Paint()
                        paint.asFrameworkPaint().shader = grainShader
                        canvas.drawRect(0f, 0f, size.width + 128f, size.height + 128f, paint)
                    }
                }
            }
        }
        BackgroundTexture.PARCHMENT -> {
            val isDark = currentThemeVariant == ThemeVariant.DARK
            val parchmentShader = remember(isDark) {
                val tile = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                val canvas = AndroidCanvas(tile)
                val paint = AndroidPaint().apply {
                    isAntiAlias = true
                }
                val fiberColor = if (isDark) AndroidColor.rgb(190, 160, 120) else AndroidColor.rgb(110, 75, 35)
                paint.color = fiberColor
                val baseAlpha = if (isDark) 35 else 55
                for (i in 0..45) {
                    val fx = ((i * 37 + 11) % 128).toFloat()
                    val fy = ((i * 53 + 23) % 128).toFloat()
                    val len = 8f + ((i * 13) % 22)
                    val angle = ((i * 41) % 60 - 30) * (Math.PI / 180f)
                    paint.alpha = (baseAlpha + (i * 7) % 35).coerceIn(0, 255)
                    paint.strokeWidth = if (i % 3 == 0) 1.2f else 0.7f
                    val endX = fx + (len * Math.cos(angle)).toFloat()
                    val endY = fy + (len * Math.sin(angle)).toFloat()
                    canvas.drawLine(fx, fy, endX, endY, paint)
                }
                BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                (if (isDark) Color(0xFF2E2419) else Color(0xFFE8D5B5)).copy(alpha = 0.20f),
                                Color.Transparent,
                                (if (isDark) Color(0xFF281E15) else Color(0xFFDEC39B)).copy(alpha = 0.25f)
                            )
                        )
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val rollX = if (dynamicRollingTexture) (((getTextureScrollOffsetX() % 128) + 128) % 128).toFloat() else 0f
                    val rollY = if (dynamicRollingTexture) (((getTextureScrollOffsetY() % 128) + 128) % 128).toFloat() else 0f
                    withTransform({
                        translate(left = -rollX, top = -rollY)
                    }) {
                        drawIntoCanvas { canvas ->
                            val paint = androidx.compose.ui.graphics.Paint()
                            paint.asFrameworkPaint().shader = parchmentShader
                            canvas.drawRect(0f, 0f, size.width + 128f, size.height + 128f, paint)
                        }
                    }
                }
            }
        }
        BackgroundTexture.LINEN -> {
            val isDark = currentThemeVariant == ThemeVariant.DARK
            val linenShader = remember(isDark) {
                val tile = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                val canvas = AndroidCanvas(tile)
                val paint = AndroidPaint().apply {
                    isAntiAlias = true
                }
                val threadColor = if (isDark) AndroidColor.WHITE else AndroidColor.BLACK
                paint.color = threadColor
                val alphaMain = if (isDark) 35 else 48
                val alphaSub = if (isDark) 20 else 28

                for (pos in 0 until 48 step 8) {
                    paint.alpha = alphaMain
                    paint.strokeWidth = 1.0f
                    canvas.drawLine(pos.toFloat(), 0f, pos.toFloat(), 48f, paint)
                    canvas.drawLine(0f, pos.toFloat(), 48f, pos.toFloat(), paint)

                    paint.alpha = alphaSub
                    paint.strokeWidth = 0.6f
                    canvas.drawLine((pos + 4).toFloat(), 0f, (pos + 4).toFloat(), 48f, paint)
                    canvas.drawLine(0f, (pos + 4).toFloat(), 48f, (pos + 4).toFloat(), paint)
                }
                BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            Canvas(modifier = modifier.fillMaxSize()) {
                val rollX = if (dynamicRollingTexture) (((getTextureScrollOffsetX() % 48) + 48) % 48).toFloat() else 0f
                val rollY = if (dynamicRollingTexture) (((getTextureScrollOffsetY() % 48) + 48) % 48).toFloat() else 0f
                withTransform({
                    translate(left = -rollX, top = -rollY)
                }) {
                    drawIntoCanvas { canvas ->
                        val paint = androidx.compose.ui.graphics.Paint()
                        paint.asFrameworkPaint().shader = linenShader
                        canvas.drawRect(0f, 0f, size.width + 48f, size.height + 48f, paint)
                    }
                }
            }
        }
        BackgroundTexture.CANVAS -> {
            val isDark = currentThemeVariant == ThemeVariant.DARK
            val canvasShader = remember(isDark) {
                val tile = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                val canvas = AndroidCanvas(tile)
                val paint = AndroidPaint().apply {
                    isAntiAlias = true
                }
                val threadColor = if (isDark) AndroidColor.WHITE else AndroidColor.BLACK
                paint.color = threadColor
                for (x in 0 until 32 step 4) {
                    for (y in 0 until 32 step 4) {
                        val isWeft = (x / 4 + y / 4) % 2 == 0
                        paint.alpha = if (isWeft) (if (isDark) 28 else 38) else (if (isDark) 14 else 20)
                        paint.strokeWidth = 1.2f
                        if (isWeft) {
                            canvas.drawLine(x.toFloat(), y.toFloat(), (x + 4).toFloat(), y.toFloat(), paint)
                        } else {
                            canvas.drawLine(x.toFloat(), y.toFloat(), x.toFloat(), (y + 4).toFloat(), paint)
                        }
                    }
                }
                BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            Canvas(modifier = modifier.fillMaxSize()) {
                val rollX = if (dynamicRollingTexture) (((getTextureScrollOffsetX() % 32) + 32) % 32).toFloat() else 0f
                val rollY = if (dynamicRollingTexture) (((getTextureScrollOffsetY() % 32) + 32) % 32).toFloat() else 0f
                withTransform({
                    translate(left = -rollX, top = -rollY)
                }) {
                    drawIntoCanvas { canvas ->
                        val paint = androidx.compose.ui.graphics.Paint()
                        paint.asFrameworkPaint().shader = canvasShader
                        canvas.drawRect(0f, 0f, size.width + 32f, size.height + 32f, paint)
                    }
                }
            }
        }
        BackgroundTexture.KRAFT -> {
            val isDark = currentThemeVariant == ThemeVariant.DARK
            val kraftShader = remember(isDark) {
                val tile = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                val canvas = AndroidCanvas(tile)
                val paint = AndroidPaint().apply {
                    isAntiAlias = true
                }
                val fiberColor = if (isDark) AndroidColor.rgb(180, 140, 95) else AndroidColor.rgb(120, 85, 45)
                paint.color = fiberColor
                val baseAlpha = if (isDark) 30 else 45
                for (i in 0..60) {
                    val fx = ((i * 43 + 7) % 96).toFloat()
                    val fy = ((i * 61 + 19) % 96).toFloat()
                    val len = 4f + ((i * 11) % 14)
                    val angle = ((i * 53) % 90 - 45) * (Math.PI / 180f)
                    paint.alpha = (baseAlpha + (i * 9) % 30).coerceIn(0, 255)
                    paint.strokeWidth = if (i % 4 == 0) 1.1f else 0.6f
                    val endX = fx + (len * Math.cos(angle)).toFloat()
                    val endY = fy + (len * Math.sin(angle)).toFloat()
                    canvas.drawLine(fx, fy, endX, endY, paint)
                }
                BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            Canvas(modifier = modifier.fillMaxSize()) {
                val rollX = if (dynamicRollingTexture) (((getTextureScrollOffsetX() % 96) + 96) % 96).toFloat() else 0f
                val rollY = if (dynamicRollingTexture) (((getTextureScrollOffsetY() % 96) + 96) % 96).toFloat() else 0f
                withTransform({
                    translate(left = -rollX, top = -rollY)
                }) {
                    drawIntoCanvas { canvas ->
                        val paint = androidx.compose.ui.graphics.Paint()
                        paint.asFrameworkPaint().shader = kraftShader
                        canvas.drawRect(0f, 0f, size.width + 96f, size.height + 96f, paint)
                    }
                }
            }
        }
        BackgroundTexture.RULED_FINE -> {
            val isDark = currentThemeVariant == ThemeVariant.DARK
            val lineColor = if (isDark) Color(0xFF64B5F6).copy(alpha = 0.12f) else Color(0xFF1E88E5).copy(alpha = 0.08f)
            Canvas(modifier = modifier.fillMaxSize()) {
                val lineSpacingPx = 28f
                val rollY = if (dynamicRollingTexture) (((getTextureScrollOffsetY() % lineSpacingPx.toInt()) + lineSpacingPx.toInt()) % lineSpacingPx.toInt()).toFloat() else 0f
                var y = -rollY
                while (y <= size.height) {
                    if (y >= 0f) {
                        drawLine(
                            color = lineColor,
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = 0.75f
                        )
                    }
                    y += lineSpacingPx
                }
            }
        }
        BackgroundTexture.RULED_WIDE -> {
            val isDark = currentThemeVariant == ThemeVariant.DARK
            val lineColor = if (isDark) Color(0xFF81C784).copy(alpha = 0.12f) else Color(0xFF2E7D32).copy(alpha = 0.08f)
            Canvas(modifier = modifier.fillMaxSize()) {
                val lineSpacingPx = 42f
                val rollY = if (dynamicRollingTexture) (((getTextureScrollOffsetY() % lineSpacingPx.toInt()) + lineSpacingPx.toInt()) % lineSpacingPx.toInt()).toFloat() else 0f
                var y = -rollY
                while (y <= size.height) {
                    if (y >= 0f) {
                        drawLine(
                            color = lineColor,
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = 0.85f
                        )
                    }
                    y += lineSpacingPx
                }
            }
        }
        BackgroundTexture.RULED_GRID -> {
            val isDark = currentThemeVariant == ThemeVariant.DARK
            val gridColor = if (isDark) Color(0xFF90A4AE).copy(alpha = 0.10f) else Color(0xFF37474F).copy(alpha = 0.06f)
            Canvas(modifier = modifier.fillMaxSize()) {
                val gridSizePx = 32f
                val rollX = if (dynamicRollingTexture) (((getTextureScrollOffsetX() % gridSizePx.toInt()) + gridSizePx.toInt()) % gridSizePx.toInt()).toFloat() else 0f
                val rollY = if (dynamicRollingTexture) (((getTextureScrollOffsetY() % gridSizePx.toInt()) + gridSizePx.toInt()) % gridSizePx.toInt()).toFloat() else 0f
                var x = -rollX
                while (x <= size.width) {
                    if (x >= 0f) {
                        drawLine(
                            color = gridColor,
                            start = androidx.compose.ui.geometry.Offset(x, 0f),
                            end = androidx.compose.ui.geometry.Offset(x, size.height),
                            strokeWidth = 0.65f
                        )
                    }
                    x += gridSizePx
                }
                var y = -rollY
                while (y <= size.height) {
                    if (y >= 0f) {
                        drawLine(
                            color = gridColor,
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = 0.65f
                        )
                    }
                    y += gridSizePx
                }
            }
        }
        BackgroundTexture.CUSTOM -> {
            val activeTexture = remember(customTextures, selectedCustomTextureId) {
                customTextures.find { it.id == selectedCustomTextureId } ?: customTextures.firstOrNull()
            }
            if (activeTexture != null) {
                val customBitmap = remember(activeTexture.id, activeTexture.imagePath) {
                    try {
                        val f = File(activeTexture.imagePath)
                        if (f.exists()) {
                            io.github.tasmirz.lumina.ui.components.bitmapCache.get(f.absolutePath)
                                ?: BitmapFactory.decodeFile(f.absolutePath)?.also {
                                    io.github.tasmirz.lumina.ui.components.bitmapCache.put(f.absolutePath, it)
                                }
                        } else null
                    } catch (_: Exception) { null }
                }
                if (customBitmap != null) {
                    if (activeTexture.isTiled) {
                        val shader = remember(customBitmap) {
                            BitmapShader(customBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                        }
                        Canvas(modifier = modifier.fillMaxSize()) {
                            val tileW = customBitmap.width.coerceAtLeast(1)
                            val tileH = customBitmap.height.coerceAtLeast(1)
                            val rollX = if (dynamicRollingTexture) (((getTextureScrollOffsetX() % tileW) + tileW) % tileW).toFloat() else 0f
                            val rollY = if (dynamicRollingTexture) (((getTextureScrollOffsetY() % tileH) + tileH) % tileH).toFloat() else 0f
                            withTransform({
                                translate(left = -rollX, top = -rollY)
                            }) {
                                drawIntoCanvas { canvas ->
                                    val paint = androidx.compose.ui.graphics.Paint()
                                    paint.alpha = activeTexture.opacity.coerceIn(0.05f, 1.0f)
                                    paint.asFrameworkPaint().shader = shader
                                    canvas.drawRect(0f, 0f, size.width + tileW.toFloat(), size.height + tileH.toFloat(), paint)
                                }
                            }
                        }
                    } else {
                        val imgBitmap = remember(customBitmap) {
                            customBitmap.asImageBitmap()
                        }
                        Canvas(modifier = modifier.fillMaxSize()) {
                            drawImage(
                                image = imgBitmap,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                alpha = activeTexture.opacity.coerceIn(0.05f, 1.0f)
                            )
                        }
                    }
                }
            }
        }
        BackgroundTexture.NONE -> {}
    }
}
