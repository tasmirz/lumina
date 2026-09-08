package io.github.tasmirz.lumina

import io.github.tasmirz.lumina.util.CitationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitationHelperTest {

    @Test
    fun testFormatCitationIncludesAllMetadata() {
        val quote = "One morning, when Gregor Samsa woke from troubled dreams"
        val author = "Franz Kafka"
        val bookTitle = "The Metamorphosis"
        val chapter = "Chapter I"

        val citation = CitationHelper.formatCitation(quote, author, bookTitle, chapter)

        assertTrue(citation.contains("“$quote”"))
        assertTrue(citation.contains(author))
        assertTrue(citation.contains(bookTitle))
        assertTrue(citation.contains(chapter))
        assertEquals("“One morning, when Gregor Samsa woke from troubled dreams”\n\n— Franz Kafka, The Metamorphosis (Chapter I)", citation)
    }
}
