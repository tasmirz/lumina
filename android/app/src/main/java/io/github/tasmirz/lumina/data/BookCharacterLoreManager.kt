package io.github.tasmirz.lumina.data

import android.content.Context
import android.content.Intent
import io.github.tasmirz.lumina.model.BookCharacter
import io.github.tasmirz.lumina.model.BookLore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages character dossier, lore encyclopedia, and reading highlights/quotes sharing.
 */
object BookCharacterLoreManager {

    fun getCharactersForBook(repository: BookRepository, bookId: String): List<BookCharacter> {
        val cached = repository._characters.value[bookId]
        if (cached != null) return cached
        repository.dbHelper.deduplicateCharacters(bookId)
        val list = repository.dbHelper.getCharacters(bookId)
        repository._characters.value = repository._characters.value + (bookId to list)
        return list
    }

    fun loadCharacters(repository: BookRepository, bookId: String) {
        repository.repoScope.launch(Dispatchers.IO) {
            repository.dbHelper.deduplicateCharacters(bookId)
            val list = repository.dbHelper.getCharacters(bookId)
            repository._characters.value = repository._characters.value + (bookId to list)
        }
    }

    fun saveCharacter(repository: BookRepository, character: BookCharacter) {
        repository.repoScope.launch(Dispatchers.IO) {
            repository.dbHelper.insertCharacter(character)
            val list = repository.dbHelper.getCharacters(character.bookId)
            repository._characters.value = repository._characters.value + (character.bookId to list)
        }
    }

    fun saveCharacters(repository: BookRepository, bookId: String, newCharacters: List<BookCharacter>) {
        repository.repoScope.launch(Dispatchers.IO) {
            repository.dbHelper.saveCharacters(bookId, newCharacters)
            repository.dbHelper.deduplicateCharacters(bookId)
            val list = repository.dbHelper.getCharacters(bookId)
            repository._characters.value = repository._characters.value + (bookId to list)
        }
    }

    fun deleteCharacter(repository: BookRepository, id: Long, bookId: String) {
        repository.repoScope.launch(Dispatchers.IO) {
            repository.dbHelper.deleteCharacter(id)
            val list = repository.dbHelper.getCharacters(bookId)
            repository._characters.value = repository._characters.value + (bookId to list)
        }
    }

    fun clearCharacters(repository: BookRepository, bookId: String) {
        repository.repoScope.launch(Dispatchers.IO) {
            repository.dbHelper.clearCharacters(bookId)
            repository._characters.value = repository._characters.value + (bookId to emptyList())
        }
    }

    fun getLoreForBook(repository: BookRepository, bookId: String): List<BookLore> {
        val cached = repository._lore.value[bookId]
        if (cached != null) return cached
        repository.dbHelper.deduplicateLore(bookId)
        val list = repository.dbHelper.getLore(bookId)
        repository._lore.value = repository._lore.value + (bookId to list)
        return list
    }

    fun loadLore(repository: BookRepository, bookId: String) {
        repository.repoScope.launch(Dispatchers.IO) {
            repository.dbHelper.deduplicateLore(bookId)
            val list = repository.dbHelper.getLore(bookId)
            repository._lore.value = repository._lore.value + (bookId to list)
        }
    }

    fun saveLore(repository: BookRepository, loreItem: BookLore) {
        repository.repoScope.launch(Dispatchers.IO) {
            repository.dbHelper.insertLore(loreItem)
            val list = repository.dbHelper.getLore(loreItem.bookId)
            repository._lore.value = repository._lore.value + (loreItem.bookId to list)
        }
    }

    fun saveLoreList(repository: BookRepository, bookId: String, newLoreList: List<BookLore>) {
        repository.repoScope.launch(Dispatchers.IO) {
            repository.dbHelper.saveLoreList(bookId, newLoreList)
            repository.dbHelper.deduplicateLore(bookId)
            val list = repository.dbHelper.getLore(bookId)
            repository._lore.value = repository._lore.value + (bookId to list)
        }
    }

    fun deleteLore(repository: BookRepository, id: Long, bookId: String) {
        repository.repoScope.launch(Dispatchers.IO) {
            repository.dbHelper.deleteLore(id)
            val list = repository.dbHelper.getLore(bookId)
            repository._lore.value = repository._lore.value + (bookId to list)
        }
    }

    fun clearLore(repository: BookRepository, bookId: String) {
        repository.repoScope.launch {
            repository.dbHelper.clearLore(bookId)
            repository._lore.value = repository._lore.value + (bookId to emptyList())
        }
    }

    fun shareHighlights(repository: BookRepository, context: Context, bookTitle: String? = null) {
        val text = repository.dbHelper.formatHighlightsForShare(bookTitle)
        shareContent(context, text, "Reading Highlights - Lumina")
    }

    fun shareReadingList(repository: BookRepository, context: Context) {
        val text = repository.dbHelper.formatReadingListForShare()
        shareContent(context, text, "My Lumina Reading List")
    }

    fun shareContent(context: Context, text: String, title: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
    }
}
