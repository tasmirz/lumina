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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private val bitmapCache = object : android.util.LruCache<String, Bitmap>(30) {}
private val failedSources = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
private val coverDownloadSemaphore = Semaphore(3)

private fun sha256Hex(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun decodeSampledBitmapFromFile(file: File, maxWidth: Int = 300, maxHeight: Int = 420): Bitmap? {
    return try {
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opt)
        var sample = 1
        while (opt.outWidth / sample > maxWidth || opt.outHeight / sample > maxHeight) {
            sample *= 2
        }
        val decodeOpt = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(file.absolutePath, decodeOpt)
    } catch (_: Throwable) {
        null
    }
}

private fun decodeSampledBitmapFromByteArray(bytes: ByteArray, maxWidth: Int = 300, maxHeight: Int = 420): Bitmap? {
    return try {
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opt)
        var sample = 1
        while (opt.outWidth / sample > maxWidth || opt.outHeight / sample > maxHeight) {
            sample *= 2
        }
        val decodeOpt = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpt)
    } catch (_: Throwable) {
        null
    }
}

@Composable
fun rememberBookImage(source: String): Bitmap? {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var bitmap by remember(source) { mutableStateOf(if (source.isNotBlank()) bitmapCache.get(source) else null) }

    if (source.isBlank() || failedSources.contains(source)) {
        return null
    }

    LaunchedEffect(source) {
        val cached = bitmapCache.get(source)
        if (cached != null) {
            bitmap = cached
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
                        decodeSampledBitmapFromFile(diskCacheFile)
                    } else {
                        // Throttled network download with max 3 concurrent requests and 5s timeout
                        coverDownloadSemaphore.withPermit {
                            if (diskCacheFile.exists() && diskCacheFile.length() > 0) {
                                decodeSampledBitmapFromFile(diskCacheFile)
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
                                        tempFile.writeBytes(bytes)
                                        tempFile.renameTo(diskCacheFile)
                                    } catch (_: Exception) {}
                                    decodeSampledBitmapFromByteArray(bytes)
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
                            while (opt.outWidth / sample > 1200 || opt.outHeight / sample > 1600) {
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
                            decodeSampledBitmapFromByteArray(bytes)
                        }
                    } catch (_: Exception) { null }
                } else {
                    val cleanPath = source.removePrefix("file://")
                    val file = File(cleanPath)
                    if (file.exists() && file.length() > 0) {
                        decodeSampledBitmapFromFile(file)
                    } else null
                }
                if (decoded != null) {
                    bitmapCache.put(source, decoded)
                    withContext(Dispatchers.Main) {
                        bitmap = decoded
                    }
                } else {
                    failedSources.add(source)
                }
            } catch (_: Throwable) {
                failedSources.add(source)
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
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
