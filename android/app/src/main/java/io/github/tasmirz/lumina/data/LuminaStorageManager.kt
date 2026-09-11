package io.github.tasmirz.lumina.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

object LuminaStorageManager {

    private const val TAG = "LuminaStorageManager"
    const val EPUB_DIR_NAME = "Lumina/epubs"

    private fun logW(tag: String, msg: String) {
        try {
            Log.w(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun logI(tag: String, msg: String) {
        try {
            Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    /**
     * Resolves the primary persistent EPUB storage directory.
     * On Android: /sdcard/Lumina/epubs (Environment.getExternalStorageDirectory() / Documents)
     * On Linux / Desktop / JVM: ~/Lumina/epubs
     */
    fun getPersistentEpubDirectory(context: Context?): File {
        // 1. Linux / Desktop / JVM / Termux environment
        val userHome = System.getProperty("user.home")
        if (!userHome.isNullOrBlank() && userHome != "/" && !userHome.startsWith("/data/user") && !userHome.startsWith("/data/data")) {
            try {
                val homeDir = File(userHome, EPUB_DIR_NAME)
                if (homeDir.exists() || homeDir.mkdirs()) {
                    return homeDir
                }
            } catch (e: Throwable) {
                logW(TAG, "Could not access user.home EPUB directory: ${e.message}")
            }
        }

        // 2. Android Shared Home Storage (/sdcard/Lumina/epubs)
        try {
            val extStorage = Environment.getExternalStorageDirectory()
            if (extStorage != null && (extStorage.canWrite() || Environment.MEDIA_MOUNTED == Environment.getExternalStorageState())) {
                val primaryDir = File(extStorage, EPUB_DIR_NAME)
                if (primaryDir.exists() || primaryDir.mkdirs()) {
                    return primaryDir
                }
            }
        } catch (e: Throwable) {
            logW(TAG, "Could not access external storage directory: ${e.message}")
        }

        // 3. Android Public Documents directory (/sdcard/Documents/Lumina/epubs)
        try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (docsDir != null) {
                val luminaDocsDir = File(docsDir, EPUB_DIR_NAME)
                if (luminaDocsDir.exists() || luminaDocsDir.mkdirs()) {
                    return luminaDocsDir
                }
            }
        } catch (e: Throwable) {
            logW(TAG, "Could not access public Documents directory: ${e.message}")
        }

        // 4. Android App-Specific External directory
        if (context != null) {
            try {
                val extFiles = context.getExternalFilesDir(null)
                if (extFiles != null) {
                    val appExtDir = File(extFiles, "epubs")
                    if (appExtDir.exists() || appExtDir.mkdirs()) {
                        return appExtDir
                    }
                }
            } catch (e: Throwable) {
                logW(TAG, "Could not access getExternalFilesDir: ${e.message}")
            }

            // 5. Internal private sandbox fallback
            try {
                val internalDir = File(context.filesDir, "epubs")
                if (!internalDir.exists()) {
                    internalDir.mkdirs()
                }
                return internalDir
            } catch (e: Throwable) {
                logW(TAG, "Could not access filesDir: ${e.message}")
            }
        }

        return File(".", "Lumina/epubs").apply { mkdirs() }
    }

    /**
     * Returns candidate directories where EPUBs might have been stored or imported.
     */
    fun getAllSearchDirectories(context: Context?): List<File> {
        val dirs = mutableListOf<File>()

        dirs.add(getPersistentEpubDirectory(context))

        try {
            val extStorage = Environment.getExternalStorageDirectory()
            if (extStorage != null) {
                dirs.add(File(extStorage, EPUB_DIR_NAME))
                dirs.add(File(extStorage, "Books"))
            }
        } catch (_: Throwable) {}

        try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (docsDir != null) {
                dirs.add(File(docsDir, EPUB_DIR_NAME))
                dirs.add(File(docsDir, "Books"))
            }
        } catch (_: Throwable) {}

        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null) {
                dirs.add(File(downloadsDir, EPUB_DIR_NAME))
            }
        } catch (_: Throwable) {}

        if (context != null) {
            try {
                val appExt = context.getExternalFilesDir(null)
                if (appExt != null) {
                    dirs.add(File(appExt, "epubs"))
                    dirs.add(File(appExt, EPUB_DIR_NAME))
                }
            } catch (_: Throwable) {}

            try {
                val internalDir = File(context.filesDir, "epubs")
                dirs.add(internalDir)
            } catch (_: Throwable) {}
        }

        val userHome = System.getProperty("user.home")
        if (!userHome.isNullOrBlank()) {
            dirs.add(File(userHome, EPUB_DIR_NAME))
        }

        return dirs.filter { it.exists() && it.isDirectory }.distinctBy { try { it.canonicalPath } catch (_: Throwable) { it.absolutePath } }
    }

    /**
     * Scans candidate directories for all valid .epub files.
     */
    fun scanEpubFiles(context: Context?): List<File> {
        val found = mutableListOf<File>()
        val seenNames = mutableSetOf<String>()

        for (dir in getAllSearchDirectories(context)) {
            val files = dir.listFiles { f ->
                f.isFile && f.name.endsWith(".epub", ignoreCase = true) && f.length() > 0L
            } ?: continue

            for (f in files) {
                // Deduplicate by name
                if (seenNames.add(f.name.lowercase())) {
                    found.add(f)
                }
            }
        }

        return found
    }

    /**
     * Migrates files from legacy internal storage (context.filesDir/epubs) to persistent storage.
     */
    fun migrateLegacyFiles(context: Context?) {
        if (context == null) return
        val internalDir = try { File(context.filesDir, "epubs") } catch (_: Throwable) { null } ?: return
        if (!internalDir.exists() || !internalDir.isDirectory) return

        val targetDir = getPersistentEpubDirectory(context)
        if (try { targetDir.canonicalPath == internalDir.canonicalPath } catch (_: Throwable) { false }) return

        val internalFiles = internalDir.listFiles { f ->
            f.isFile && f.name.endsWith(".epub", ignoreCase = true)
        } ?: return

        for (file in internalFiles) {
            try {
                val dest = File(targetDir, file.name)
                if (!dest.exists() || dest.length() != file.length()) {
                    file.copyTo(dest, overwrite = true)
                    logI(TAG, "Migrated legacy EPUB ${file.name} to ${dest.absolutePath}")
                }
            } catch (e: Throwable) {
                logW(TAG, "Failed migrating legacy file ${file.name}: ${e.message}")
            }
        }
    }
}
