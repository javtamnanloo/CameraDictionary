package io.github.javtamnanloo.cameradictionary

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.createBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Scan screen shows. */
data class ScanState(
    val photo: Bitmap? = null,          // the frozen camera picture; null while the live camera shows
    val isReading: Boolean = false,     // text recognition is running
    val words: List<OcrWord> = emptyList(),
    val selected: Int = -1,             // index in [words] of the highlighted word, or -1
    val lookup: Lookup? = null,         // what the definition panel shows
    val hint: String? = null,           // a message such as "No words found"
)

/** A word being looked up. [result] is null while the lookup is still running. */
data class Lookup(val spellings: List<String>, val context: String, val result: LookupResult?) {
    val word: String get() = spellings.first()
}

/**
 * The logic behind the Scan screen: reading text from a photo, choosing a word, looking it
 * up, and starring it. It survives screen rotation, so nothing is lost when the phone turns.
 */
class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val savedWordDao = app.database.savedWords()

    /** ML Kit's text recognizer. It runs entirely on the phone. */
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    var state by mutableStateOf(ScanState())
        private set

    /** The words in your Leitner box, so the star shows whether a word is already saved. */
    val savedWords: StateFlow<Set<String>> = savedWordDao.observeAll()
        .map { list -> list.mapTo(HashSet()) { it.word } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var lookupJob: Job? = null

    init {
        // The first recognition loads the model. Doing it now makes the first "Read" faster.
        recognizer.process(InputImage.fromBitmap(createBitmap(32, 32), 0))
    }

    /** "Read" was pressed: freeze this photo and find the words in it. */
    fun read(photo: Bitmap) {
        lookupJob?.cancel()
        state = ScanState(photo = photo, isReading = true, hint = "Reading the text…")
        recognizer.process(InputImage.fromBitmap(photo, 0))
            .addOnSuccessListener { text -> if (state.photo === photo) onTextRecognized(photo, text) }
            .addOnFailureListener {
                if (state.photo === photo) state = state.copy(isReading = false, hint = "Couldn't read the text. Please try again.")
            }
    }

    private fun onTextRecognized(photo: Bitmap, text: Text) {
        val words = OcrWords.fromParagraphs(text.toParagraphs())
        state = state.copy(
            isReading = false,
            words = words,
            hint = if (words.isEmpty()) "No words found. Hold the phone steady, move a little closer, and try again." else null,
        )
        // Start with the word under the + in the middle of the picture.
        val middle = OcrWords.nearest(words, photo.width / 2f, photo.height / 2f)
        if (middle >= 0) select(middle)
    }

    fun select(index: Int) {
        val word = state.words.getOrNull(index) ?: return
        state = state.copy(selected = index)
        lookUp(listOf(word.word) + word.alternatives, word.context)
    }

    /** A tap on the photo (in photo pixels). [tolerance] lets a near miss still pick a word. */
    fun selectAt(x: Float, y: Float, tolerance: Float) {
        val index = OcrWords.nearest(state.words, x, y, tolerance)
        if (index >= 0) select(index)
    }

    fun selectNext() = select(state.selected + 1)

    fun selectPrevious() = select(state.selected - 1)

    /** Back to the live camera. */
    fun backToCamera() {
        lookupJob?.cancel()
        state = ScanState()
    }

    /** Looks up a typed (or corrected) word. Returns false if it isn't a valid word. */
    fun lookUpTyped(input: String): Boolean {
        val word = TextSafety.cleanWord(input) ?: return false
        state = state.copy(selected = -1, hint = null)
        lookUp(listOf(word), context = "")
        return true
    }

    fun retry() {
        state.lookup?.let { lookUp(it.spellings, it.context) }
    }

    private fun lookUp(spellings: List<String>, context: String) {
        lookupJob?.cancel() // a newer tap always wins over an older, slower lookup
        val lookup = Lookup(spellings, context, result = null)
        state = state.copy(lookup = lookup)
        lookupJob = viewModelScope.launch {
            val result = app.dictionary.lookUp(spellings)
            if (state.lookup === lookup) state = state.copy(lookup = lookup.copy(result = result))
        }
    }

    /** Adds the shown word to the Leitner box, or removes it. Returns true if it was added. */
    suspend fun toggleStar(): Boolean? {
        val lookup = state.lookup ?: return null
        val found = lookup.result as? LookupResult.Found ?: return null
        return if (savedWordDao.exists(found.word)) {
            savedWordDao.delete(found.word)
            false
        } else {
            savedWordDao.save(Leitner.newWord(found.word, found.entry, lookup.context, Leitner.today(), System.currentTimeMillis()))
            true
        }
    }

    override fun onCleared() {
        recognizer.close()
    }
}

/** Copies ML Kit's result into plain data: paragraphs → lines → tokens with their boxes. */
private fun Text.toParagraphs(): List<List<OcrLine>> = textBlocks.map { block ->
    block.lines.map { line ->
        OcrLine(
            text = line.text,
            tokens = line.elements.mapNotNull { element ->
                element.boundingBox?.let { r ->
                    OcrToken(element.text, Box(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
                }
            },
        )
    }
}
