package io.github.tasmirz.lumina

import io.github.tasmirz.lumina.data.LuminaStorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LuminaStorageManagerTest {

    @Test
    fun testPersistentEpubDirectoryResolvesAndCreates() {
        val dir = LuminaStorageManager.getPersistentEpubDirectory(null)
        assertNotNull("Persistent EPUB directory should not be null", dir)
        assertTrue("Directory path should end with Lumina/epubs", dir.path.endsWith("Lumina/epubs") || dir.name == "epubs")
        assertTrue("Directory must exist or be creatable", dir.exists() || dir.mkdirs())
    }

    @Test
    fun testGetAllSearchDirectoriesIncludesPersistentDirectory() {
        val dirs = LuminaStorageManager.getAllSearchDirectories(null)
        assertTrue("Search directories must not be empty", dirs.isNotEmpty())
        val persistentDir = LuminaStorageManager.getPersistentEpubDirectory(null)
        assertTrue("Candidate search directories must include persistent directory",
            dirs.any { it.absolutePath == persistentDir.absolutePath }
        )
    }

    @Test
    fun testScanEpubFilesFindsCreatedEpub() {
        val targetDir = LuminaStorageManager.getPersistentEpubDirectory(null)
        targetDir.mkdirs()
        val testEpub = File(targetDir, "test_discovery_book_${System.currentTimeMillis()}.epub")
        testEpub.writeText("PK\u0003\u0004mock content")

        try {
            val scanned = LuminaStorageManager.scanEpubFiles(null)
            assertTrue("Scanned files should not be empty", scanned.isNotEmpty())
            assertTrue("Scanned files must contain test epub", scanned.any { it.name == testEpub.name })
        } finally {
            testEpub.delete()
        }
    }
}
