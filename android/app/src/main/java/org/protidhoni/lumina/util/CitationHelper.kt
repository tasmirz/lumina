package org.protidhoni.lumina.util

object CitationHelper {
    /**
     * Formats selected text with complete citation attribution.
     */
    fun formatCitation(quote: String, author: String, bookTitle: String, chapterTitle: String): String {
        return "“${quote.trim()}”\n\n— $author, $bookTitle ($chapterTitle)"
    }
}
