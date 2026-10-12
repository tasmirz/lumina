package io.github.tasmirz.lumina.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.tasmirz.lumina.data.EpubParser
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
private val cacheSizeKb = (maxMemoryKb / 8).coerceIn(4096, 32768)

internal val bitmapCache = object : android.util.LruCache<String, Bitmap>(cacheSizeKb) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        return (value.byteCount / 1024).coerceAtLeast(1)
    }
}
private val failedSources = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
private val coverDownloadSemaphore = Semaphore(3)

internal val inlineAspectRatios = java.util.concurrent.ConcurrentHashMap<String, Float>()

fun decodeSvgBitmap(file: File, maxW: Int = 1440, maxH: Int = 1440): Bitmap? {
    return try {
        java.io.FileInputStream(file).use { fis ->
            val svg = com.caverock.androidsvg.SVG.getFromInputStream(fis)
            val docW = if (svg.documentWidth > 0) svg.documentWidth else svg.documentViewBox?.width() ?: 300f
            val docH = if (svg.documentHeight > 0) svg.documentHeight else svg.documentViewBox?.height() ?: 100f
            if (docW <= 0 || docH <= 0) return null

            val scale = minOf(maxW.toFloat() / docW, maxH.toFloat() / docH).coerceIn(0.1f, 4.0f)
            val targetW = (docW * scale).toInt().coerceIn(1, 2560)
            val targetH = (docH * scale).toInt().coerceIn(1, 2560)

            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            svg.renderToCanvas(canvas, android.graphics.RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()))
            bmp
        }
    } catch (_: Throwable) {
        null
    }
}

internal fun decodeSampledInlineBitmap(file: File, maxDimension: Int = 360): Bitmap? {
    if (file.name.endsWith(".svg", ignoreCase = true)) {
        return decodeSvgBitmap(file, 1440, 1440)
    }
    return try {
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opt)
        val origW = opt.outWidth
        val origH = opt.outHeight
        if (origW <= 0 || origH <= 0) {
            return decodeSvgBitmap(file, 1440, 1440)
        }

        var sample = 1
        while (origW / (sample * 2) >= maxDimension || origH / (sample * 2) >= maxDimension) {
            sample *= 2
        }
        val decodeOpt = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpt) ?: return null
        if (decoded.width > maxDimension * 1.5f || decoded.height > maxDimension * 1.5f) {
            val scale = minOf(maxDimension.toFloat() / decoded.width, maxDimension.toFloat() / decoded.height)
            val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            if (scaled != decoded) {
                decoded.recycle()
            }
            scaled
        } else {
            decoded
        }
    } catch (_: Throwable) {
        null
    }
}

fun prewarmInlineBitmaps(imagePaths: List<String>) {
    if (imagePaths.isEmpty()) return
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        imagePaths.forEach { rawPath ->
            try {
                val cleanPath = rawPath.removePrefix("file://")
                val decodedPath = try { java.net.URLDecoder.decode(cleanPath, "UTF-8") } catch (_: Exception) { cleanPath }
                val file = if (File(cleanPath).exists()) File(cleanPath) else File(decodedPath)
                if (file.exists() && file.length() > 0) {
                    val absPath = file.absolutePath
                    if (bitmapCache.get(absPath) == null && bitmapCache.get(rawPath) == null) {
                        val isSvg = file.name.endsWith(".svg", ignoreCase = true)
                        val maxDim = if (isSvg) 1440 else 360
                        val decoded = decodeSampledInlineBitmap(file, maxDimension = maxDim)
                        if (decoded != null) {
                            bitmapCache.put(absPath, decoded)
                            bitmapCache.put(rawPath, decoded)
                            if (decoded.height > 0) {
                                val ar = (decoded.width.toFloat() / decoded.height.toFloat()).coerceIn(0.4f, 15.0f)
                                inlineAspectRatios[absPath] = ar
                                inlineAspectRatios[rawPath] = ar
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }
}

private fun sha256Hex(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun decodeSampledBitmapFromFile(file: File, maxWidth: Int = 280, maxHeight: Int = 420): Bitmap? {
    if (file.name.endsWith(".svg", ignoreCase = true)) {
        return decodeSvgBitmap(file, maxOf(maxWidth, 1440), maxOf(maxHeight, 1440))
    }
    return try {
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opt)
        val origW = opt.outWidth
        val origH = opt.outHeight
        if (origW <= 0 || origH <= 0) {
            return decodeSvgBitmap(file, maxOf(maxWidth, 1440), maxOf(maxHeight, 1440))
        }

        var sample = 1
        while (origW / (sample * 2) >= maxWidth || origH / (sample * 2) >= maxHeight) {
            sample *= 2
        }
        val decodeOpt = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpt) ?: return null

        if (decoded.width > maxWidth * 1.35f || decoded.height > maxHeight * 1.35f) {
            val scale = minOf(maxWidth.toFloat() / decoded.width, maxHeight.toFloat() / decoded.height)
            val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            if (scaled != decoded) {
                decoded.recycle()
            }
            scaled
        } else {
            decoded
        }
    } catch (_: Throwable) {
        null
    }
}

private fun decodeSampledBitmapFromByteArray(bytes: ByteArray, maxWidth: Int = 280, maxHeight: Int = 420): Bitmap? {
    return try {
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opt)
        val origW = opt.outWidth
        val origH = opt.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sample = 1
        while (origW / (sample * 2) >= maxWidth && origH / (sample * 2) >= maxHeight) {
            sample *= 2
        }
        val decodeOpt = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = if (maxWidth > 500) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpt) ?: return null

        if (decoded.width > maxWidth * 1.35f || decoded.height > maxHeight * 1.35f) {
            val scale = minOf(maxWidth.toFloat() / decoded.width, maxHeight.toFloat() / decoded.height)
            val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            if (scaled != decoded) {
                decoded.recycle()
            }
            scaled
        } else {
            decoded
        }
    } catch (_: Throwable) {
        null
    }
}

fun prewarmBookCovers(sources: List<String>, context: android.content.Context) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        sources.take(24).forEach { src ->
            if (src.isNotBlank() && bitmapCache.get(src) == null && !failedSources.contains(src)) {
                try {
                    val decoded: Bitmap? = if (src.startsWith("http://") || src.startsWith("https://")) {
                        val coversDir = File(context.filesDir, "covers")
                        val diskCacheFile = File(coversDir, sha256Hex(src))
                        if (diskCacheFile.exists() && diskCacheFile.length() > 0) {
                            decodeSampledBitmapFromFile(diskCacheFile)
                        } else null
                    } else if (src.startsWith("content://")) {
                        try {
                            val uri = android.net.Uri.parse(src)
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                decodeSampledBitmapFromByteArray(stream.readBytes())
                            }
                        } catch (_: Exception) { null }
                    } else {
                        val file = File(src.removePrefix("file://"))
                        if (file.exists() && file.length() > 0) {
                            decodeSampledBitmapFromFile(file)
                        } else null
                    }
                    if (decoded != null) {
                        bitmapCache.put(src, decoded)
                    }
                } catch (_: Throwable) {}
            }
        }
    }
}

@Composable
fun rememberBookImage(source: String, isInlineImage: Boolean = false): Bitmap? {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val cached = if (source.isNotBlank()) bitmapCache.get(source) else null
    var bitmap by remember(source) { mutableStateOf(cached) }

    if (source.isBlank() || failedSources.contains(source)) {
        return null
    }

    // Compress gallery covers to thumbnails. Sample inline images to screen width for zero-jank scrolling; full resolution is loaded on demand when lightbox zooming.
    val isGalleryCover = !isInlineImage && (source.contains("/covers/") || source.contains("_cover."))
    val screenWidthPx = context.resources.displayMetrics.widthPixels.coerceIn(720, 1440)
    val maxW = if (isGalleryCover) 280 else if (isInlineImage) screenWidthPx else 2048
    val maxH = if (isGalleryCover) 420 else if (isInlineImage) (screenWidthPx * 1.6f).toInt() else 2048

    if (bitmap == null) {
        LaunchedEffect(source) {
            val liveCached = bitmapCache.get(source)
            if (liveCached != null) {
                bitmap = liveCached
                return@LaunchedEffect
            }
            if (source.isBlank() || failedSources.contains(source)) {
                return@LaunchedEffect
            }
        withContext(Dispatchers.IO) {
            try {
                val decoded: Bitmap? = if (source.startsWith("http://") || source.startsWith("https://")) {
                    val coversDir = File(context.filesDir, "covers").apply { if (!exists()) mkdirs() }
                    val diskCacheFile = File(coversDir, sha256Hex(source))

                    if (diskCacheFile.exists() && diskCacheFile.length() > 0) {
                        decodeSampledBitmapFromFile(diskCacheFile, maxWidth = maxW, maxHeight = maxH)
                    } else {
                        // Throttled network download with max 3 concurrent requests and 5s timeout
                        coverDownloadSemaphore.withPermit {
                            if (diskCacheFile.exists() && diskCacheFile.length() > 0) {
                                decodeSampledBitmapFromFile(diskCacheFile, maxWidth = maxW, maxHeight = maxH)
                            } else {
                                var currentUrl = source
                                var bytes: ByteArray? = null
                                for (step in 0 until 5) {
                                    val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                                        instanceFollowRedirects = true
                                        connectTimeout = 5000
                                        readTimeout = 5000
                                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                                        setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                                    }
                                    val code = conn.responseCode
                                    if (code in 300..399) {
                                        val loc = conn.getHeaderField("Location")
                                        if (!loc.isNullOrBlank()) {
                                            currentUrl = if (loc.startsWith("http")) loc else URL(URL(currentUrl), loc).toString()
                                            continue
                                        }
                                    }
                                    if (code == 200) {
                                        val buffer = ByteArrayOutputStream()
                                        conn.inputStream.use { input ->
                                            val tmp = ByteArray(8192)
                                            var len: Int
                                            while (input.read(tmp).also { len = it } != -1) {
                                                buffer.write(tmp, 0, len)
                                            }
                                        }
                                        bytes = buffer.toByteArray()
                                    }
                                    break
                                }
                                if (bytes != null && bytes.isNotEmpty()) {
                                    try {
                                        val tempFile = File(coversDir, "${diskCacheFile.name}.tmp")
                                        EpubParser.compressAndSaveCover(bytes, tempFile, maxDimension = 540, quality = 82)
                                        tempFile.renameTo(diskCacheFile)
                                    } catch (_: Exception) {}
                                    decodeSampledBitmapFromByteArray(bytes, maxWidth = maxW, maxHeight = maxH)
                                } else null
                            }
                        }
                    }
                } else if (source.startsWith("res://") || source.startsWith("android.resource://")) {
                    try {
                        val resName = source.substringAfterLast("/")
                        val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                        if (resId != 0) {
                            val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeResource(context.resources, resId, opt)
                            var sample = 1
                            while (opt.outWidth / sample > maxW || opt.outHeight / sample > maxH) {
                                sample *= 2
                            }
                            val decodeOpt = BitmapFactory.Options().apply { inSampleSize = sample }
                            BitmapFactory.decodeResource(context.resources, resId, decodeOpt)
                        } else null
                    } catch (_: Exception) { null }
                } else if (source.startsWith("content://")) {
                    try {
                        val uri = android.net.Uri.parse(source)
                        val stream = context.contentResolver.openInputStream(uri)
                        stream?.use {
                            val bytes = it.readBytes()
                            decodeSampledBitmapFromByteArray(bytes, maxWidth = maxW, maxHeight = maxH)
                        }
                    } catch (_: Exception) { null }
                } else {
                    val cleanPath = source.removePrefix("file://")
                    val decodedPath = try { java.net.URLDecoder.decode(cleanPath, "UTF-8") } catch (_: Exception) { cleanPath }
                    val file = if (File(cleanPath).exists()) File(cleanPath) else File(decodedPath)
                    if (file.exists() && file.length() > 0) {
                        decodeSampledBitmapFromFile(file, maxWidth = maxW, maxHeight = maxH)
                    } else {
                        // Directory fallback: if the exact file path is missing or renamed during EPUB import,
                        // check the parent directory for best-matching image file
                        val parent = file.parentFile
                        val targetBase = file.nameWithoutExtension.lowercase()
                        val fallbackFile = if (parent != null && parent.exists() && parent.isDirectory) {
                            val candidates = parent.listFiles { f ->
                                val ext = f.extension.lowercase()
                                (ext == "png" || ext == "jpg" || ext == "jpeg" || ext == "webp" || ext == "svg") && f.length() > 0
                            } ?: emptyArray()

                            val keywords = targetBase.split('_', '-').filter { it.length >= 3 && !it.startsWith("custom") }
                            candidates.maxByOrNull { cand ->
                                val candName = cand.nameWithoutExtension.lowercase()
                                var score = 0
                                if (candName == targetBase) score += 100
                                if (candName.contains(targetBase) || targetBase.contains(candName)) score += 50
                                for (kw in keywords) {
                                    if (candName.contains(kw)) score += 10
                                }
                                score
                            }?.takeIf { cand ->
                                val candName = cand.nameWithoutExtension.lowercase()
                                candName.contains(targetBase) || targetBase.contains(candName) ||
                                    keywords.any { candName.contains(it) }
                            }
                        } else null

                        if (fallbackFile != null && fallbackFile.exists() && fallbackFile.length() > 0) {
                            decodeSampledBitmapFromFile(fallbackFile, maxWidth = maxW, maxHeight = maxH)
                        } else null
                    }
                }
                if (decoded != null) {
                    bitmapCache.put(source, decoded)
                    withContext(Dispatchers.Main) {
                        bitmap = decoded
                    }
                } else if (source.startsWith("http://") || source.startsWith("https://")) {
                    failedSources.add(source)
                }
            } catch (_: Throwable) {
                if (source.startsWith("http://") || source.startsWith("https://")) {
                    failedSources.add(source)
                }
            }
        }
    }
    }
    return bitmap
}

@Composable
fun AsyncImageBitmap(
    url: String,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberBookImage(url)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

@Composable
fun BookCoverImage(
    source: String,
    titleFallback: String,
    authorFallback: String,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberBookImage(source)

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = titleFallback,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = titleFallback.take(2).uppercase(),
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
