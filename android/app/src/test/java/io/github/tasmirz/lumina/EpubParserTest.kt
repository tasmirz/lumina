package io.github.tasmirz.lumina

import io.github.tasmirz.lumina.data.EpubParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    @Test
    fun testParseEpubExtractsChaptersAndCleansHtml() {
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)

        // Mock an XHTML chapter entry
        val entry = ZipEntry("OEBPS/chapter1.xhtml")
        zos.putNextEntry(entry)
        val htmlContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 1</title></head>
            <body>
              <h1>Chapter I</h1>
              <p>One morning, when Gregor Samsa woke from troubled dreams, he found himself transformed in his bed into a horrible vermin.</p>
              <p>The bedding was hardly able to cover it and seemed ready to slide off any moment. His many legs, pitifully thin compared with the size of the rest of him, waved about helplessly as he looked.</p>
            </body>
            </html>
        """.trimIndent()
        zos.write(htmlContent.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
        zos.close()

        val bais = ByteArrayInputStream(baos.toByteArray())
        val book = EpubParser.parseEpub(bais, "The_Metamorphosis.epub")

        assertEquals("The Metamorphosis", book.title)
        assertTrue(book.chapters.isNotEmpty())
        val firstChapter = book.chapters.first()
        assertTrue(firstChapter.paragraphs.isNotEmpty())
        assertTrue(firstChapter.paragraphs.any { it.contains("Gregor Samsa") })
    }

    @Test
    fun testIsHeadingOnlyDetection() {
        assertTrue(EpubParser.isHeadingOnly("Chapter 1", "Chapter 1", "Nineteen Eighty-Four", "Part One"))
        assertTrue(EpubParser.isHeadingOnly("Chapter 1Chapter 1", "Chapter 1", "Nineteen Eighty-Four", "Part One"))
        assertTrue(EpubParser.isHeadingOnly("Part One", "Chapter 1", "Nineteen Eighty-Four", "Part One"))
        assertTrue(EpubParser.isHeadingOnly("Nineteen Eighty-Four", "Chapter 1", "Nineteen Eighty-Four", "Part One"))
        assertTrue(EpubParser.isHeadingOnly("Chapter 2", "Chapter 2", "Book Title", ""))
        assertTrue(EpubParser.isHeadingOnly("Chapter IV", "Chapter IV", "Book Title", ""))
        org.junit.Assert.assertFalse(EpubParser.isHeadingOnly(
            "It was a bright cold day in April, and the clocks were striking thirteen.",
            "Chapter 1",
            "Nineteen Eighty-Four",
            "Part One"
        ))
    }

    @Test
    fun testParseEpubLanguageExtraction() {
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)

        // Add container.xml
        zos.putNextEntry(ZipEntry("META-INF/container.xml"))
        val containerXml = """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()
        zos.write(containerXml.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // Add content.opf with dc:language
        zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
        val opfXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Le Petit Prince</dc:title>
                <dc:creator>Antoine de Saint-Exupéry</dc:creator>
                <dc:language>fr-FR</dc:language>
              </metadata>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="c1"/>
              </spine>
            </package>
        """.trimIndent()
        zos.write(opfXml.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // Add chapter1.xhtml
        zos.putNextEntry(ZipEntry("OEBPS/chapter1.xhtml"))
        val htmlContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <body><p>Bonjour le monde.</p></body>
            </html>
        """.trimIndent()
        zos.write(htmlContent.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
        zos.close()

        val book = EpubParser.parseEpub(ByteArrayInputStream(baos.toByteArray()), "test.epub")
        assertEquals("fr", book.language)
        assertEquals("Le Petit Prince", book.title)
    }

    @Test
    fun testParseFrankenstein() {
        val file = java.io.File("/tmp/epub_test/frankenstein.epub")
        if (!file.exists()) return
        val book = EpubParser.parseEpub(file.inputStream(), file.name)
        println("Frankenstein chapters count: ${book.chapters.size}")
        println("Frankenstein title: ${book.title}")
        assertTrue("Chapters should not be empty", book.chapters.isNotEmpty())
    }

    @Test
    fun testParsePride() {
        val file = java.io.File("/tmp/epub_test/pride.epub")
        if (!file.exists()) return
        val book = EpubParser.parseEpub(file.inputStream(), file.name)
        println("Pride chapters count: ${book.chapters.size}")
        println("Pride title: ${book.title}")
        assertTrue("Chapters should not be empty", book.chapters.isNotEmpty())
    }

    @Test
    fun testParseEpubFilenameResolution() {
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)
        zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
        zos.write("<html><body><p>Test paragraph content.</p></body></html>".toByteArray(Charsets.UTF_8))
        zos.closeEntry()
        zos.close()

        val book1 = EpubParser.parseEpub(ByteArrayInputStream(baos.toByteArray()), "My_Favorite_Book.epub")
        assertEquals("My Favorite Book", book1.title)

        val book2 = EpubParser.parseEpub(ByteArrayInputStream(baos.toByteArray()), "1694380000_sample_story.epub")
        assertEquals("Sample Story", book2.title)
    }

    @Test
    fun testParseBookMetadataFast() {
        val tempFile = java.io.File.createTempFile("lumina_meta_test", ".epub")
        try {
            val zos = ZipOutputStream(tempFile.outputStream())
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write("""<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("content.opf"))
            val opfContent = """
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Fast Metadata Book</dc:title>
                        <dc:creator>Test Author</dc:creator>
                        <dc:language>en</dc:language>
                    </metadata>
                    <manifest>
                        <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    </manifest>
                </package>
            """.trimIndent()
            zos.write(opfContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.close()

            val book = EpubParser.parseBookMetadata(tempFile)
            assertEquals("Fast Metadata Book", book.title)
            assertEquals("Test Author", book.author)
            assertEquals("en", book.language)
            assertEquals(tempFile.absolutePath, book.filePath)
            assertTrue(book.chapters.isEmpty())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testCompressAndSaveCoverSafeFallback() {
        val dest = java.io.File.createTempFile("cover_test", ".jpg")
        try {
            val dummyBytes = "fake_image_bytes".toByteArray()
            val success = EpubParser.compressAndSaveCover(dummyBytes, dest, maxDimension = 640)
            assertTrue(success)
            assertTrue(dest.exists())
            assertTrue(dest.length() > 0)
        } finally {
            dest.delete()
        }
    }
}
