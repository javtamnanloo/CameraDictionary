package io.github.javtamnanloo.cameradictionary

import java.text.Normalizer
import java.util.Locale

/**
 * The single gatekeeper for text that comes from OUTSIDE the app: the camera (OCR), the
 * keyboard, and the dictionary websites.
 *
 * Why it matters: anyone can print text designed to trick software — shell commands
 * (`rm -rf /`), database code (`'; DROP TABLE words;--`), web code (`<script>`), file paths
 * (`../../`), invisible or right-to-left control characters, look-alike letters, or a
 * "word" that is 50,000 letters long. A camera will happily read all of it.
 *
 * This app never RUNS text. Outside text is only ever:
 *  1. shown on screen as plain text (never as HTML, never as a clickable link),
 *  2. spoken by the phone's text-to-speech voice,
 *  3. saved through Room's parameterized queries (text is never glued into SQL),
 *  4. sent to a dictionary as ONE word that passed [cleanWord]: letters, with ' or - inside.
 * There is no WebView, no shell, no code evaluation, and no AI model that could obey
 * instructions hidden in the text. The functions below enforce the "letters only" rule and
 * tidy text before it is displayed. They are tested in TextSafetyTest.
 */
object TextSafety {

    /** The longest word accepted. (The longest word in major dictionaries has 45 letters.) */
    const val MAX_WORD_LENGTH = 45

    /** A token longer than this can't be a word, so it isn't even examined. */
    private const val MAX_TOKEN_LENGTH = 100

    /** Typographic apostrophes that count as a plain ' (don’t → don't). */
    private const val APOSTROPHES = "’‘ʼ′`´"

    /** Unicode hyphens that count as a plain - (well‐known → well-known). */
    private const val HYPHENS = "‐‑"

    /** At most this many accent marks may sit on one letter ("Zalgo" text can cover the screen). */
    private const val MAX_MARKS_PER_LETTER = 2

    private val FORMAT = Character.FORMAT.toInt()
    private val NON_SPACING_MARK = Character.NON_SPACING_MARK.toInt()
    private val ENCLOSING_MARK = Character.ENCLOSING_MARK.toInt()

    /** Invisible or non-printing characters that are never displayed. */
    private val HIDDEN_TYPES = setOf(
        Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE,
        Character.SURROGATE, Character.UNASSIGNED,
    ).map { it.toInt() }.toSet()

    /**
     * Turns raw text (one OCR token, or what the user typed) into a clean, lowercase
     * dictionary word — or returns null if it is not a word.
     *
     *     "(Hello!)" → "hello"     "don’t" → "don't"     "ﬁnal" → "final"
     *     "Café"     → "café"      "well-known" → "well-known"
     *     "3rd", "1984", "../etc", "<b>x</b>", "a;b", "rm -rf" → null
     */
    fun cleanWord(raw: String): String? {
        if (raw.length > MAX_TOKEN_LENGTH) return null
        val text = normalize(raw).lowercase(Locale.ROOT)
        if (text.any { it.isDigit() }) return null // "3rd", "mp3", "1984" aren't dictionary words
        val word = text.trim { !isWordLetter(it) } // drop surrounding punctuation: quotes, commas, brackets…
        return word.takeIf { it.length <= MAX_WORD_LENGTH && isValidWord(it) }
    }

    /**
     * Other spellings worth trying when a dictionary doesn't know [word]:
     * "author's" → "author", "café" → "cafe". Every result is itself a valid word.
     */
    fun spellingVariants(word: String): List<String> {
        val variants = mutableListOf(word)
        if (word.endsWith("'s") && word.length > 3) variants += word.dropLast(2)
        val withoutAccents = Normalizer.normalize(word, Normalizer.Form.NFD)
            .filter { Character.getType(it) != NON_SPACING_MARK }
        if (isValidWord(withoutAccents)) variants += withoutAccents
        return variants.distinct()
    }

    /**
     * Makes outside text safe and tidy to DISPLAY: removes control and invisible characters
     * (including right-to-left "override" tricks), limits stacked accent marks, squeezes
     * whitespace into single spaces, and cuts it to [maxLength] characters (adding "…").
     */
    fun cleanDisplay(raw: String, maxLength: Int): String {
        val text = Normalizer.normalize(raw.take(maxLength * 4), Normalizer.Form.NFC)
        val out = StringBuilder()
        var pendingSpace = false
        var marksOnThisLetter = 0
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            i += Character.charCount(codePoint)
            val type = Character.getType(codePoint)
            when {
                Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) ->
                    pendingSpace = out.isNotEmpty()

                type in HIDDEN_TYPES -> Unit // skip it entirely

                type == NON_SPACING_MARK || type == ENCLOSING_MARK ->
                    if (++marksOnThisLetter <= MAX_MARKS_PER_LETTER) out.appendCodePoint(codePoint)

                else -> {
                    if (out.length >= maxLength) return out.toString() + "…"
                    if (pendingSpace) out.append(' ')
                    pendingSpace = false
                    marksOnThisLetter = 0
                    out.appendCodePoint(codePoint)
                }
            }
        }
        return out.toString()
    }

    /**
     * Unifies apostrophes and hyphens, then applies NFKC normalization, which turns
     * look-alike forms into plain letters (ﬁ → fi, ｗｏｒｄ → word), and finally drops
     * invisible characters (zero-width spaces, direction overrides, soft hyphens).
     */
    private fun normalize(raw: String): String {
        val unified = buildString(raw.length) {
            for (c in raw) {
                append(
                    when (c) {
                        in APOSTROPHES -> '\''
                        in HYPHENS -> '-'
                        else -> c
                    }
                )
            }
        }
        return Normalizer.normalize(unified, Normalizer.Form.NFKC)
            .filter { Character.getType(it) != FORMAT }
    }

    /** English letters, plus the accented Latin letters used in loanwords (café, naïve, façade). */
    private fun isWordLetter(c: Char): Boolean =
        c in 'a'..'z' || (c in 'ß'..'ÿ' && c != '÷') || c in 'Ā'..'ſ'

    /** Letters, optionally joined by single apostrophes or hyphens: don't, rock'n'roll, well-known. */
    private fun isValidWord(word: String): Boolean {
        if (word.isEmpty() || !isWordLetter(word.first()) || !isWordLetter(word.last())) return false
        for (i in word.indices) {
            val c = word[i]
            if (isWordLetter(c)) continue
            // Safe to look at i-1 and i+1: the first and last characters are letters.
            val joinsTwoLetters = (c == '\'' || c == '-') && isWordLetter(word[i - 1]) && isWordLetter(word[i + 1])
            if (!joinsTwoLetters) return false
        }
        return true
    }
}
