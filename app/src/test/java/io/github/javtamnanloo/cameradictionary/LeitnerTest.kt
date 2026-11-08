package io.github.javtamnanloo.cameradictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class LeitnerTest {

    private val today = 20_000L
    private val entry = DictionaryEntry("serendipity", meanings = listOf(Meaning("noun", listOf(Definition("A happy accident.")))))

    private fun newWord() = Leitner.newWord("serendipity", entry, "a line from a book", today, nowMillis = 0)

    @Test
    fun `a new word starts in box 1 and is due tomorrow`() {
        val word = newWord()
        assertEquals(1, word.box)
        assertEquals(today + 1, word.dueDay)
        assertFalse(Leitner.isDue(word, today))
        assertTrue(Leitner.isDue(word, today + 1))
    }

    @Test
    fun `each correct answer triples the wait - 3, 9, 27, 81, 243 days`() {
        var word = newWord()
        var day = today
        for (expected in listOf(3, 9, 27, 81, 243)) {
            day = word.dueDay
            word = Leitner.afterReview(word, knewIt = true, today = day)
            assertEquals(day + expected, word.dueDay)
        }
        assertEquals(6, word.box)
    }

    @Test
    fun `the last box stays the last box`() {
        val top = newWord().copy(box = 6)
        val after = Leitner.afterReview(top, knewIt = true, today = today)
        assertEquals(6, after.box)
        assertEquals(today + 243, after.dueDay)
    }

    @Test
    fun `a wrong answer sends the word back to box 1, due tomorrow`() {
        val word = newWord().copy(box = 4)
        val after = Leitner.afterReview(word, knewIt = false, today = today)
        assertEquals(1, after.box)
        assertEquals(today + 1, after.dueDay)
    }

    @Test
    fun `reviewing late counts the wait from the day you review`() {
        val word = newWord() // due on today + 1, but reviewed 10 days later
        val after = Leitner.afterReview(word, knewIt = true, today = today + 11)
        assertEquals(today + 11 + 3, after.dueDay)
    }

    @Test
    fun `today follows the phone's time zone`() {
        val utc = TimeZone.getTimeZone("UTC")
        val newYork = TimeZone.getTimeZone("America/New_York")
        val jan2At3amUtc = 86_400_000L + 3 * 3_600_000L // 2 Jan 1970, 03:00 UTC = 1 Jan, 22:00 in New York
        assertEquals(1L, Leitner.today(jan2At3amUtc, utc))
        assertEquals(0L, Leitner.today(jan2At3amUtc, newYork))
    }

    @Test
    fun `saved definitions survive the trip through the database format`() {
        val restored = newWord().entry()
        assertEquals("serendipity", restored.word)
        assertEquals("A happy accident.", restored.meanings.single().definitions.single().text)
    }
}
