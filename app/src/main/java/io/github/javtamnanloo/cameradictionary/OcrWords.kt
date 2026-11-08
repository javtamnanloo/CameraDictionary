package io.github.javtamnanloo.cameradictionary

import kotlin.math.sqrt

/** A rectangle on the photo, in the photo's own pixel coordinates. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    /** Distance from a point to this box (0 when the point is inside it). */
    fun distanceTo(x: Float, y: Float): Float {
        val dx = maxOf(left - x, 0f, x - right)
        val dy = maxOf(top - y, 0f, y - bottom)
        return sqrt(dx * dx + dy * dy)
    }
}

/** One tappable word found in the photo. */
data class OcrWord(
    val word: String,               // cleaned and safe, e.g. "important"
    val alternatives: List<String>, // other spellings to try, e.g. "well-known" for "well-" + "known"
    val boxes: List<Box>,           // where it is on the photo (2 boxes when split across lines)
    val context: String,            // the line it came from; saved with the word for your reviews
) {
    fun distanceTo(x: Float, y: Float): Float = boxes.minOf { it.distanceTo(x, y) }
}

/** Raw text-recognition output, copied out of ML Kit so the logic below can be unit-tested. */
data class OcrToken(val text: String, val box: Box)
data class OcrLine(val text: String, val tokens: List<OcrToken>)

object OcrWords {

    /** A dense page can hold thousands of tokens; this many is plenty and keeps the app fast. */
    const val MAX_WORDS = 500
    private const val MAX_CONTEXT_LENGTH = 200

    /**
     * Turns recognized text into clean, tappable words. [paragraphs] is a list of
     * paragraphs, each a list of lines in reading order. Tokens that aren't words (numbers,
     * symbols, junk) are skipped, and a word hyphenated across two lines ("impor-" / "tant")
     * is joined back into one ("important").
     */
    fun fromParagraphs(paragraphs: List<List<OcrLine>>): List<OcrWord> {
        val words = mutableListOf<OcrWord>()
        for (lines in paragraphs) {
            var firstTokenAlreadyUsed = false // it was joined to the end of the previous line
            for ((lineIndex, line) in lines.withIndex()) {
                val tokens = if (firstTokenAlreadyUsed) line.tokens.drop(1) else line.tokens
                firstTokenAlreadyUsed = false
                val context = TextSafety.cleanDisplay(line.text, MAX_CONTEXT_LENGTH)
                for ((position, token) in tokens.withIndex()) {
                    if (words.size >= MAX_WORDS) return words
                    val nextLine = lines.getOrNull(lineIndex + 1)
                    val joined = if (position == tokens.lastIndex && nextLine != null) {
                        joinHyphenated(token, line, nextLine)
                    } else null
                    if (joined != null) {
                        words += joined
                        firstTokenAlreadyUsed = true
                        continue
                    }
                    val word = TextSafety.cleanWord(token.text) ?: continue
                    words += OcrWord(word, emptyList(), listOf(token.box), context)
                }
            }
        }
        return words
    }

    /** Index of the word closest to a point, or -1 if no word is within [maxDistance]. */
    fun nearest(words: List<OcrWord>, x: Float, y: Float, maxDistance: Float = Float.MAX_VALUE): Int {
        val index = words.indices.minByOrNull { words[it].distanceTo(x, y) } ?: return -1
        return if (words[index].distanceTo(x, y) <= maxDistance) index else -1
    }

    /** If [token] ends its line with a hyphen and the next line continues the word, joins them. */
    private fun joinHyphenated(token: OcrToken, line: OcrLine, nextLine: OcrLine): OcrWord? {
        val next = nextLine.tokens.firstOrNull() ?: return null
        val text = token.text.trimEnd()
        val endsWithHyphen = text.length >= 2 && text.last() in "-‐‑" && text[text.length - 2].isLetter()
        if (!endsWithHyphen) return null
        val firstPart = text.dropLast(1)
        val word = TextSafety.cleanWord(firstPart + next.text) ?: return null
        val hyphenated = TextSafety.cleanWord("$firstPart-${next.text}") // in case it really was "well-known"
        val context = TextSafety.cleanDisplay(line.text + " " + nextLine.text, MAX_CONTEXT_LENGTH)
        return OcrWord(word, listOfNotNull(hyphenated), listOf(token.box, next.box), context)
    }
}
