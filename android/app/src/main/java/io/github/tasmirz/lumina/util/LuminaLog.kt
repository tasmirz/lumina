package io.github.tasmirz.lumina.util

import android.content.Context
import android.util.Log
import io.github.tasmirz.lumina.data.LuminaStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-performance internal logging framework for Lumina.
 * Outputs to Android Logcat (tags Lumina and LuminaPerf) AND writes asynchronously to
 * a persistent file on disk (/sdcard/Lumina/logs/lumina.log) accessible via ADB.
 */
object LuminaLog {
    private const val TAG = "Lumina"
    private const val PERF_TAG = "LuminaPerf"
    private const val MAX_LOG_SIZE_BYTES = 2 * 1024 * 1024L // 2MB

    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logChannel = Channel<String>(capacity = 2000)
    @Volatile
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    init {
        logScope.launch {
            val pendingBuffer = ArrayList<String>()
            for (line in logChannel) {
                val file = logFile
                if (file == null) {
                    pendingBuffer.add(line)
                    if (pendingBuffer.size > 2000) pendingBuffer.removeAt(0)
                } else {
                    if (pendingBuffer.isNotEmpty()) {
                        for (p in pendingBuffer) {
                            writeLineToFile(p, file)
                        }
                        pendingBuffer.clear()
                    }
                    writeLineToFile(line, file)
                }
            }
        }
    }

    fun init(context: Context) {
        logScope.launch {
            try {
                val logDir = LuminaStorageManager.getPersistentLogsDirectory(context)
                val file = File(logDir, "lumina.log")
                logFile = file
                i("LuminaLog", "=== Lumina Session Started ===")
                i("LuminaLog", "Persistent log path: ${file.absolutePath}")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed initializing persistent log file: ${e.message}")
            }
        }
    }

    private fun writeLineToFile(line: String, targetFile: File? = logFile) {
        val file = targetFile ?: logFile ?: return
        try {
            if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
                val backup = File(file.parentFile, "lumina.log.old")
                if (backup.exists()) backup.delete()
                file.renameTo(backup)
            }
            file.parentFile?.mkdirs()
            FileWriter(file, true).use { writer ->
                writer.appendLine(line)
            }
        } catch (_: Throwable) {}
    }

    private fun formatLine(level: String, tag: String, msg: String): String {
        val time = synchronized(dateFormat) { dateFormat.format(Date()) }
        val thread = Thread.currentThread().name
        return "$time [$thread] [$level/$tag] $msg"
    }

    fun v(tag: String, msg: String) {
        Log.v(tag, msg)
        logChannel.trySend(formatLine("V", tag, msg))
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        logChannel.trySend(formatLine("D", tag, msg))
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        logChannel.trySend(formatLine("I", tag, msg))
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
        val fullMsg = if (tr != null) "$msg: ${tr.message}\n${tr.stackTraceToString()}" else msg
        logChannel.trySend(formatLine("W", tag, fullMsg))
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        val fullMsg = if (tr != null) "$msg: ${tr.message}\n${tr.stackTraceToString()}" else msg
        logChannel.trySend(formatLine("E", tag, fullMsg))
    }

    fun perf(operation: String, durationMs: Long, details: String = "") {
        val msg = "$operation took ${durationMs}ms${if (details.isNotBlank()) " ($details)" else ""}"
        Log.i(PERF_TAG, msg)
        logChannel.trySend(formatLine("PERF", PERF_TAG, msg))
    }

    inline fun <T> time(operation: String, details: String = "", block: () -> T): T {
        val start = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - start
        perf(operation, duration, details)
        return result
    }
}
