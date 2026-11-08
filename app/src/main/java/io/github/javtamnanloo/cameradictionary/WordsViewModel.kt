package io.github.javtamnanloo.cameradictionary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The logic behind the Review and My words screens: your saved words and what you do with them. */
class WordsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as App).database.savedWords()

    /** All saved words, alphabetically. Updates by itself whenever the database changes. */
    val words: StateFlow<List<SavedWord>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Records a review answer and returns the word with its new box and due date. */
    fun answer(word: SavedWord, knewIt: Boolean): SavedWord {
        val updated = Leitner.afterReview(word, knewIt, Leitner.today())
        viewModelScope.launch { dao.save(updated) }
        return updated
    }

    /** Puts a word back exactly as it was (used by "Undo"). */
    fun restore(word: SavedWord) {
        viewModelScope.launch { dao.save(word) }
    }

    fun delete(word: SavedWord) {
        viewModelScope.launch { dao.delete(word.word) }
    }

    fun deleteAll() {
        viewModelScope.launch { dao.deleteAll() }
    }
}
