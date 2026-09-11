package io.github.tasmirz.lumina.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

object LuminaStorageManager {

    private const val TAG = "LuminaStorageManager"
    const val EPUB_DIR_NAME = "Lumina/epubs"

    @Volatile
    private var cachedPersistentDir: File? = null

    @Volatile
    private var legacyMigrationCompleted: Boolean = false

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

    private fun canWriteToDir(dir: File): Boolean {
        return try {
            if (!dir.exists() && !dir.mkdirs()) return false
            val probe = File(dir, ".probe_${System.currentTimeMillis()}")
            if (probe.createNewFile()) {
                probe.delete()
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Resolves the primary persistent EPUB storage directory.
     * On Android: /sdcard/Lumina/epubs (Environment.getExternalStorageDirectory() / Documents)
     * On Linux / Desktop / JVM: ~/Lumina/epubs
     */
    fun getPersistentEpubDirectory(context: Context?): File {
        cachedPersistentDir?.let { cached ->
            if (cached.exists() && cached.isDirectory && canWriteToDir(cached)) return cached
        }

        synchronized(this) {
            cachedPersistentDir?.let { cached ->
                if (cached.exists() && cached.isDirectory && canWriteToDir(cached)) return cached
            }

            // 1. Linux / Desktop / JVM / Termux environment
            val userHome = System.getProperty("user.home")
            if (!userHome.isNullOrBlank() && userHome != "/" && !userHome.startsWith("/data/user") && !userHome.startsWith("/data/data")) {
                try {
                    val homeDir = File(userHome, EPUB_DIR_NAME)
                    if (canWriteToDir(homeDir)) {
                        cachedPersistentDir = homeDir
                        return homeDir
                    }
                } catch (e: Throwable) {
                    logW(TAG, "Could not access user.home EPUB directory: ${e.message}")
                }
            }

            // 2. Android Shared Home Storage (/sdcard/Lumina/epubs)
            try {
                val extStorage = Environment.getExternalStorageDirectory()
                if (extStorage != null) {
                    val primaryDir = File(extStorage, EPUB_DIR_NAME)
                    if (canWriteToDir(primaryDir)) {
                        cachedPersistentDir = primaryDir
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
                    if (canWriteToDir(luminaDocsDir)) {
                        cachedPersistentDir = luminaDocsDir
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
                        if (canWriteToDir(appExtDir)) {
                            cachedPersistentDir = appExtDir
                            return appExtDir
                        }
                    }
                } catch (e: Throwable) {
                    logW(TAG, "Could not access getExternalFilesDir: ${e.message}")
                }

                // 5. Internal private sandbox fallback
                try {
                    val internalDir = File(context.filesDir, "epubs")
                    if (internalDir.exists() || internalDir.mkdirs()) {
                        cachedPersistentDir = internalDir
                        return internalDir
                    }
                } catch (e: Throwable) {
                    logW(TAG, "Could not access filesDir: ${e.message}")
                }
            }

            val fallback = File(".", "Lumina/epubs").apply { mkdirs() }
            cachedPersistentDir = fallback
            return fallback
        }
    }

    /**
     * Returns the root persistent Lumina directory (/sdcard/Lumina or ~/Lumina).
     */
    fun getPersistentLuminaDirectory(context: Context?): File {
        val epubDir = getPersistentEpubDirectory(context)
        return epubDir.parentFile ?: epubDir
    }

    /**
     * Returns persistent cache directory (/sdcard/Lumina/cache or ~/Lumina/cache).
     * Survives application reinstalls and APK updates.
     */
    fun getPersistentCacheDirectory(context: Context?): File {
        val base = getPersistentLuminaDirectory(context)
        val dir = File(base, "cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Returns persistent precomputed layout pages cache directory.
     */
    fun getPersistentPagesCacheDirectory(context: Context?): File {
        val dir = File(getPersistentCacheDirectory(context), "pages")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Returns persistent library and progress backup file.
     */
    fun getPersistentBackupFile(context: Context?): File {
        return File(getPersistentCacheDirectory(context), "backup.json")
    }

    /**
     * Returns persistent logging directory (/sdcard/Lumina/logs or ~/Lumina/logs).
     */
    fun getPersistentLogsDirectory(context: Context?): File {
        val base = getPersistentLuminaDirectory(context)
        val dir = File(base, "logs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Returns candidate directories where EPUBs might have been stored or imported.
     * Strictly restricted to Lumina-dedicated storage folders to avoid scanning unrelated system folders.
     */
    fun getAllSearchDirectories(context: Context?): List<File> {
        val dirs = mutableListOf<File>()

        dirs.add(getPersistentEpubDirectory(context))

        try {
            val extStorage = Environment.getExternalStorageDirectory()
            if (extStorage != null) {
                dirs.add(File(extStorage, EPUB_DIR_NAME))
            }
        } catch (_: Throwable) {}

        try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (docsDir != null) {
                dirs.add(File(docsDir, EPUB_DIR_NAME))
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
        if (context == null || legacyMigrationCompleted) return
        val internalDir = try { File(context.filesDir, "epubs") } catch (_: Throwable) { null } ?: return
        if (!internalDir.exists() || !internalDir.isDirectory) {
            legacyMigrationCompleted = true
            return
        }

        val targetDir = getPersistentEpubDirectory(context)
        if (try { targetDir.canonicalPath == internalDir.canonicalPath } catch (_: Throwable) { false }) {
            legacyMigrationCompleted = true
            return
        }

        val internalFiles = internalDir.listFiles { f ->
            f.isFile && f.name.endsWith(".epub", ignoreCase = true)
        } ?: run {
            legacyMigrationCompleted = true
            return
        }

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
        legacyMigrationCompleted = true
    }
}
