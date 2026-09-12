package io.github.tasmirz.lumina.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object EdgeTtsService {

    private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
    private const val CHROMIUM_MAJOR_VERSION = "143"
    private const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_FULL_VERSION"
    private const val WIN_EPOCH = 11644473600L

    val AVAILABLE_VOICES = listOf(
        TtsVoiceOption("en-US-JennyNeural", "Jenny (US Natural Female)", "Warm & natural, ideal for narrative fiction"),
        TtsVoiceOption("en-US-GuyNeural", "Guy (US Natural Male)", "Calm & conversational, great for long reading"),
        TtsVoiceOption("en-US-AriaNeural", "Aria (US Expressive Female)", "Engaging and clear phrasing"),
        TtsVoiceOption("en-GB-SoniaNeural", "Sonia (British Female)", "Refined British RP accent"),
        TtsVoiceOption("en-US-ChristopherNeural", "Christopher (US Deep Male)", "Authoritative, classical tone")
    )

    data class TtsVoiceOption(
        val id: String,
        val displayName: String,
        val description: String
    )

    private fun generateSecMsGec(): String {
        // Windows file time epoch (1601-01-01) with clock skew rounded to 5-minute ticks
        val nowSec = System.currentTimeMillis() / 1000L
        var ticks = nowSec + WIN_EPOCH
        ticks -= (ticks % 300L)
        // Convert to 100-nanosecond intervals: ticks * 10,000,000
        val hundredNanoTicks = ticks * 10_000_000L
        val strToHash = "$hundredNanoTicks$TRUSTED_CLIENT_TOKEN"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(strToHash.toByteArray(Charsets.US_ASCII))
        val sb = StringBuilder()
        for (b in digest) {
            sb.append(String.format(Locale.US, "%02X", b))
        }
        return sb.toString()
    }

    private fun makeSecWebSocketKey(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    private fun isoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun createMaskedFrame(payload: ByteArray, opcode: Int = 0x1): ByteArray {
        val maskKey = ByteArray(4)
        SecureRandom().nextBytes(maskKey)

        val masked = ByteArray(payload.size)
        for (i in payload.indices) {
            masked[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
        }

        val bos = ByteArrayOutputStream()
        val firstByte = 0x80 or (opcode and 0x0f)
        bos.write(firstByte)

        val len = payload.size
        if (len < 126) {
            bos.write(0x80 or len)
        } else if (len <= 65535) {
            bos.write(0x80 or 126)
            bos.write((len shr 8) and 0xff)
            bos.write(len and 0xff)
        } else {
            bos.write(0x80 or 127)
            val buf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            buf.putLong(len.toLong())
            bos.write(buf.array())
        }
        bos.write(maskKey)
        bos.write(masked)
        return bos.toByteArray()
    }

    private fun readExact(inputStream: InputStream, count: Int): ByteArray? {
        val buf = ByteArray(count)
        var readTotal = 0
        while (readTotal < count) {
            val r = inputStream.read(buf, readTotal, count - readTotal)
            if (r < 0) return null
            readTotal += r
        }
        return buf
    }

    suspend fun synthesizeToBytes(
        text: String,
        voice: String = "en-US-JennyNeural",
        speedMultiplier: Float = 1.0f,
        pitchMultiplier: Float = 1.0f
    ): ByteArray? = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@withContext null

        var socket: Socket? = null
        try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslSocket = factory.createSocket("speech.platform.bing.com", 443) as SSLSocket
            sslSocket.soTimeout = 12000
            sslSocket.startHandshake()
            socket = sslSocket

            val out = sslSocket.outputStream
            val inputStream = sslSocket.inputStream

            val connectionId = UUID.randomUUID().toString().replace("-", "")
            val secMsGec = generateSecMsGec()
            val secKey = makeSecWebSocketKey()
            val muid = UUID.randomUUID().toString().replace("-", "").uppercase(Locale.US)

            val path = "/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=$TRUSTED_CLIENT_TOKEN&ConnectionId=$connectionId&Sec-MS-GEC=$secMsGec&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

            val handshake = StringBuilder().apply {
                append("GET $path HTTP/1.1\r\n")
                append("Host: speech.platform.bing.com\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: $secKey\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                append("Origin: chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold\r\n")
                append("Pragma: no-cache\r\n")
                append("Cache-Control: no-cache\r\n")
                append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROMIUM_MAJOR_VERSION.0.0.0 Safari/537.36 Edg/$CHROMIUM_MAJOR_VERSION.0.0.0\r\n")
                append("Cookie: muid=$muid;\r\n")
                append("\r\n")
            }.toString()

            out.write(handshake.toByteArray(Charsets.UTF_8))
            out.flush()

            // Read HTTP 101 Handshake response headers
            val headerBytes = ByteArrayOutputStream()
            var prev1 = 0
            var prev2 = 0
            var prev3 = 0
            while (true) {
                val b = inputStream.read()
                if (b < 0) return@withContext null
                headerBytes.write(b)
                if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                    break
                }
                prev3 = prev2
                prev2 = prev1
                prev1 = b
            }

            val handshakeResp = headerBytes.toString("UTF-8")
            if (!handshakeResp.contains("101 Switching Protocols")) {
                return@withContext null
            }

            // 1. Send speech.config
            val configMsg = "X-Timestamp:${isoTimestamp()}\r\n" +
                    "Content-Type:application/json; charset=utf-8\r\n" +
                    "Path:speech.config\r\n\r\n" +
                    "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"
            out.write(createMaskedFrame(configMsg.toByteArray(Charsets.UTF_8), opcode = 0x1))
            out.flush()

            // Calculate rate and pitch in %
            val ratePct = ((speedMultiplier - 1.0f) * 100).toInt()
            val rateStr = if (ratePct >= 0) "+$ratePct%" else "$ratePct%"
            val pitchPct = ((pitchMultiplier - 1.0f) * 100).toInt()
            val pitchStr = if (pitchPct >= 0) "+$pitchPct%" else "$pitchPct%"

            // Escape XML entities for SSML
            val escapedText = cleanText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")

            // 2. Send SSML
            val requestId = UUID.randomUUID().toString().replace("-", "")
            val ssml = "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"en-US\">" +
                    "<voice name=\"$voice\"><prosody rate=\"$rateStr\" pitch=\"$pitchStr\">$escapedText</prosody></voice></speak>"
            val ssmlMsg = "X-RequestId:$requestId\r\n" +
                    "Content-Type:application/ssml+xml\r\n" +
                    "X-Timestamp:${isoTimestamp()}\r\n" +
                    "Path:ssml\r\n\r\n" +
                    ssml
            out.write(createMaskedFrame(ssmlMsg.toByteArray(Charsets.UTF_8), opcode = 0x1))
            out.flush()

            // 3. Receive binary audio data
            val audioAccumulator = ByteArrayOutputStream()
            while (true) {
                val hdr = readExact(inputStream, 2) ?: break
                val b1 = hdr[0].toInt() and 0xff
                val b2 = hdr[1].toInt() and 0xff
                val opcode = b1 and 0x0f
                val isMasked = (b2 and 0x80) != 0
                var payloadLen = (b2 and 0x7f).toLong()

                if (payloadLen == 126L) {
                    val ext = readExact(inputStream, 2) ?: break
                    payloadLen = ByteBuffer.wrap(ext).order(ByteOrder.BIG_ENDIAN).short.toLong() and 0xffffL
                } else if (payloadLen == 127L) {
                    val ext = readExact(inputStream, 8) ?: break
                    payloadLen = ByteBuffer.wrap(ext).order(ByteOrder.BIG_ENDIAN).long
                }

                val mask = if (isMasked) readExact(inputStream, 4) ?: break else null
                val payload = readExact(inputStream, payloadLen.toInt()) ?: break

                if (mask != null) {
                    for (i in payload.indices) {
                        payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                    }
                }

                if (opcode == 0x1) {
                    val textStr = String(payload, Charsets.UTF_8)
                    if (textStr.contains("Path:turn.end")) {
                        break
                    }
                } else if (opcode == 0x2) {
                    if (payload.size >= 2) {
                        val headerLen = ByteBuffer.wrap(payload, 0, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xffff
                        val audioOffset = 2 + headerLen
                        if (payload.size > audioOffset) {
                            audioAccumulator.write(payload, audioOffset, payload.size - audioOffset)
                        }
                    }
                }
            }

            val result = audioAccumulator.toByteArray()
            if (result.isNotEmpty()) result else null
        } catch (_: Exception) {
            null
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    private var previewMediaPlayer: android.media.MediaPlayer? = null
    private var currentPlayingVoiceId: String? = null

    fun getVoiceDemoText(voiceId: String): String {
        return when (voiceId) {
            "en-US-JennyNeural" -> "Hello! I am Jenny, a warm natural voice for Lumina."
            "en-US-GuyNeural" -> "Hello! I am Guy, a calm and natural voice for long reading sessions."
            "en-US-AriaNeural" -> "Hello! I am Aria, clear and engaging for narrative literature."
            "en-GB-SoniaNeural" -> "Hello! I am Sonia, featuring a refined British accent."
            "en-US-ChristopherNeural" -> "Hello! I am Christopher, with an authoritative tone."
            else -> "Hello! This is a preview of the Lumina neural reading voice."
        }
    }

    suspend fun playVoiceDemo(
        context: android.content.Context,
        voiceId: String,
        onPlayingChanged: ((Boolean) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        // If clicking the same playing voice, stop it (toggle behavior)
        if (currentPlayingVoiceId == voiceId && previewMediaPlayer?.isPlaying == true) {
            stopVoiceDemo()
            withContext(Dispatchers.Main) {
                onPlayingChanged?.invoke(false)
            }
            return@withContext
        }

        stopVoiceDemo()

        val cacheDir = File(context.cacheDir, "tts_previews").apply { mkdirs() }
        val audioFile = File(cacheDir, "${voiceId}_preview.mp3")

        val audioBytes: ByteArray? = if (audioFile.exists() && audioFile.length() > 0L) {
            audioFile.readBytes()
        } else {
            val demoText = getVoiceDemoText(voiceId)
            val bytes = synthesizeToBytes(demoText, voice = voiceId)
            if (bytes != null && bytes.isNotEmpty()) {
                try {
                    audioFile.writeBytes(bytes)
                } catch (_: Exception) {}
            }
            bytes
        }

        if (audioBytes == null || audioBytes.isEmpty()) {
            withContext(Dispatchers.Main) {
                onPlayingChanged?.invoke(false)
            }
            return@withContext
        }

        withContext(Dispatchers.Main) {
            try {
                val mp = android.media.MediaPlayer()
                val tempFile = File.createTempFile("tts_temp_", ".mp3", context.cacheDir)
                tempFile.deleteOnExit()
                tempFile.writeBytes(audioBytes)

                mp.setDataSource(tempFile.absolutePath)
                mp.prepare()
                mp.setOnCompletionListener { player ->
                    player.release()
                    if (previewMediaPlayer == player) {
                        previewMediaPlayer = null
                        currentPlayingVoiceId = null
                        onPlayingChanged?.invoke(false)
                    }
                    try { tempFile.delete() } catch (_: Exception) {}
                }
                mp.setOnErrorListener { player, _, _ ->
                    player.release()
                    if (previewMediaPlayer == player) {
                        previewMediaPlayer = null
                        currentPlayingVoiceId = null
                        onPlayingChanged?.invoke(false)
                    }
                    try { tempFile.delete() } catch (_: Exception) {}
                    true
                }
                previewMediaPlayer = mp
                currentPlayingVoiceId = voiceId
                onPlayingChanged?.invoke(true)
                mp.start()
            } catch (e: Exception) {
                currentPlayingVoiceId = null
                previewMediaPlayer = null
                onPlayingChanged?.invoke(false)
            }
        }
    }

    fun stopVoiceDemo() {
        try {
            previewMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {}
        previewMediaPlayer = null
        currentPlayingVoiceId = null
    }

    fun isVoiceDemoPlaying(voiceId: String): Boolean {
        return currentPlayingVoiceId == voiceId && previewMediaPlayer?.isPlaying == true
    }
}
