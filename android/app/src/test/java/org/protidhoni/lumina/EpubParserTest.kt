package org.protidhoni.lumina

import org.protidhoni.lumina.data.EpubParser
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
}
