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
}
