package org.protidhoni.lumina.ui.components

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
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val bitmapCache = object : android.util.LruCache<String, Bitmap>(60) {}

@Composable
fun rememberBookImage(source: String): Bitmap? {
    var bitmap by remember(source) { mutableStateOf(bitmapCache.get(source)) }

    LaunchedEffect(source) {
        if (source.isBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        val cached = bitmapCache.get(source)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val decoded: Bitmap? = if (source.startsWith("http://") || source.startsWith("https://")) {
                    var currentUrl = source
                    var bytes: ByteArray? = null
                    for (step in 0 until 5) {
                        val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                            instanceFollowRedirects = true
                            connectTimeout = 12000
                            readTimeout = 15000
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
                        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opt)
                        var sample = 1
                        while (opt.outWidth / sample > 1200 || opt.outHeight / sample > 1600) {
                            sample *= 2
                        }
                        val decodeOpt = BitmapFactory.Options().apply { inSampleSize = sample }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpt)
                    } else null
                } else {
                    val cleanPath = source.removePrefix("file://")
                    val file = File(cleanPath)
                    if (file.exists() && file.length() > 0) {
                        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, opt)
                        var sample = 1
                        while (opt.outWidth / sample > 1200 || opt.outHeight / sample > 1600) {
                            sample *= 2
                        }
                        val decodeOpt = BitmapFactory.Options().apply { inSampleSize = sample }
                        BitmapFactory.decodeFile(file.absolutePath, decodeOpt)
                    } else null
                }
                if (decoded != null) {
                    bitmapCache.put(source, decoded)
                    withContext(Dispatchers.Main) {
                        bitmap = decoded
                    }
                }
            } catch (_: Throwable) {
                // Ignore load error safely (prevent OOM crashes)
            }
        }
    }
    return bitmap
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
