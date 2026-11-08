package io.github.javtamnanloo.cameradictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrWordsTest {

    /** Builds a line of tokens laid out left to right at height [y]. */
    private fun line(y: Float, vararg tokens: String): OcrLine {
        var x = 0f
        return OcrLine(tokens.joinToString(" "), tokens.map { text ->
            OcrToken(text, Box(x, y, x + 10f * text.length, y + 20f)).also { x += 10f * text.length + 10f }
        })
    }

    @Test
    fun `numbers and symbols are skipped, punctuation is stripped`() {
        val words = OcrWords.fromParagraphs(listOf(listOf(line(0f, "Chapter", "12:", "“Hello,", "—", "world!”"))))
        assertEquals(listOf("chapter", "hello", "world"), words.map { it.word })
        assertEquals("Chapter 12: “Hello, — world!”", words[0].context)
    }

    @Test
    fun `a word hyphenated across two lines is joined`() {
        val paragraph = listOf(line(0f, "It", "was", "impor-"), line(30f, "tant", "news."))
        val words = OcrWords.fromParagraphs(listOf(paragraph))
        assertEquals(listOf("it", "was", "important", "news"), words.map { it.word })
        assertEquals(2, words[2].boxes.size) // both halves highlight together
        assertEquals(listOf("impor-tant"), words[2].alternatives) // in case it really was hyphenated
    }

    @Test
    fun `words are never joined across paragraphs`() {
        val words = OcrWords.fromParagraphs(listOf(listOf(line(0f, "end-")), listOf(line(30f, "start"))))
        assertEquals(listOf("end", "start"), words.map { it.word })
    }

    @Test
    fun `a lone dash at the end of a line does not swallow the next word`() {
        val words = OcrWords.fromParagraphs(listOf(listOf(line(0f, "wait", "-"), line(30f, "what"))))
        assertEquals(listOf("wait", "what"), words.map { it.word })
    }

    @Test
    fun `the number of words is capped`() {
        val huge = List(2_000) { line(it * 30f, "word") }
        assertEquals(OcrWords.MAX_WORDS, OcrWords.fromParagraphs(listOf(huge)).size)
    }

    @Test
    fun `nearest finds the word under or close to a point`() {
        val words = OcrWords.fromParagraphs(listOf(listOf(line(0f, "alpha", "beta"))))
        assertEquals(0, OcrWords.nearest(words, 5f, 5f))  // inside "alpha"
        assertEquals(1, OcrWords.nearest(words, 70f, 10f)) // inside "beta"
        assertEquals(-1, OcrWords.nearest(words, 500f, 500f, maxDistance = 30f))
    }
}
