package io.github.javtamnanloo.cameradictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSafetyTest {

    @Test
    fun `plain words are lowercased and surrounding punctuation is removed`() {
        assertEquals("hello", TextSafety.cleanWord("Hello,"))
        assertEquals("world", TextSafety.cleanWord("(world)."))
        assertEquals("quote", TextSafety.cleanWord("“Quote!”"))
        assertEquals("a", TextSafety.cleanWord("A"))
    }

    @Test
    fun `apostrophes and hyphens inside words are kept`() {
        assertEquals("don't", TextSafety.cleanWord("don’t")) // curly apostrophe from a printed book
        assertEquals("rock'n'roll", TextSafety.cleanWord("Rock'n'roll"))
        assertEquals("well-known", TextSafety.cleanWord("well‐known")) // Unicode hyphen
        assertNull(TextSafety.cleanWord("x''y"))
        assertNull(TextSafety.cleanWord("x--y"))
    }

    @Test
    fun `look-alike and accented letters become normal letters`() {
        assertEquals("final", TextSafety.cleanWord("ﬁnal")) // "fi" ligature
        assertEquals("word", TextSafety.cleanWord("ＷＯＲＤ")) // full-width letters
        assertEquals("café", TextSafety.cleanWord("Café"))
        assertEquals("naïve", TextSafety.cleanWord("naïve"))
    }

    @Test
    fun `invisible characters are removed`() {
        assertEquals("zerowidth", TextSafety.cleanWord("zero​width"))
        assertEquals("evil", TextSafety.cleanWord("‮evil")) // right-to-left override trick
    }

    @Test
    fun `dangerous or non-word text is rejected`() {
        val attacks = listOf(
            "rm -rf /", "'; DROP TABLE saved_words;--", "<script>alert(1)</script>", "../../etc/passwd",
            "a;b", "3rd", "1984", "mp3", "user@example.com", "https://evil.example", "%2e%2e%2f",
            "word\u0000null", "привет", "Ζεύς", "", "   ", "-", "'",
        )
        for (attack in attacks) assertNull("should reject: $attack", TextSafety.cleanWord(attack))
    }

    @Test
    fun `symbols around an ordinary word are stripped, leaving only that harmless word`() {
        // What remains is a plain English word, which is only ever looked up, never run.
        assertEquals("script", TextSafety.cleanWord("<script>"))
        assertEquals("reboot", TextSafety.cleanWord("\$(reboot)"))
        assertEquals("id", TextSafety.cleanWord("`id`"))
    }

    @Test
    fun `over-long input is rejected without being processed`() {
        assertNull(TextSafety.cleanWord("a".repeat(46)))
        assertNull(TextSafety.cleanWord("a".repeat(100_000)))
        assertEquals(45, TextSafety.cleanWord("a".repeat(45))!!.length)
    }

    @Test
    fun `spelling variants try the possessive and the unaccented forms`() {
        assertEquals(listOf("author's", "author"), TextSafety.spellingVariants("author's"))
        assertEquals(listOf("café", "cafe"), TextSafety.spellingVariants("café"))
        assertEquals(listOf("hello"), TextSafety.spellingVariants("hello"))
        TextSafety.spellingVariants("naïve's").forEach { assertEquals(it, TextSafety.cleanWord(it)) }
    }

    @Test
    fun `display text loses control characters and is length-limited`() {
        assertEquals("a b c", TextSafety.cleanDisplay("  a\n\tb \u0000\u001B c  ", 100))
        assertEquals("abc…", TextSafety.cleanDisplay("abcdef", 3))
        assertEquals("safe", TextSafety.cleanDisplay("‮safe⁦", 100))
        assertFalse(TextSafety.cleanDisplay("xy", 100).contains('')) // private-use character
    }

    @Test
    fun `stacked accent marks are limited`() {
        val zalgo = "q" + "́".repeat(50) + "a"
        val cleaned = TextSafety.cleanDisplay(zalgo, 100)
        assertEquals(4, cleaned.length) // q + at most 2 marks + a
        assertTrue(cleaned.startsWith("q") && cleaned.endsWith("a"))
    }
}
