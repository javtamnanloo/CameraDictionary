package io.github.javtamnanloo.cameradictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryParserTest {

    // Shortened real answer from api.dictionaryapi.dev, with two entries for the same word.
    private val freeDictionaryAnswer = """
        [{"word":"hello","phonetics":[{"text":"/həˈləʊ/","audio":"https://x/hello-uk.mp3"},{"text":"/həˈloʊ/","audio":"https://x/hello-us.mp3"}],
          "meanings":[{"partOfSpeech":"noun","definitions":[{"definition":"\"Hello!\" or an equivalent greeting.","synonyms":[],"antonyms":[]}],"synonyms":["greeting"]},
                      {"partOfSpeech":"interjection","definitions":[{"definition":"A greeting.","example":"Hello, everyone.","synonyms":[]}]}],
          "license":{"name":"CC BY-SA 3.0","url":"https://creativecommons.org/licenses/by-sa/3.0"}},
         {"word":"hello","meanings":[{"partOfSpeech":"noun","definitions":[{"definition":"A second noun sense.","example":null}]}]}]
    """.trimIndent()

    @Test
    fun `reads a Free Dictionary answer and merges entries by part of speech`() {
        val entry = DictionaryParser.parseFreeDictionary(freeDictionaryAnswer, "hello")!!
        assertEquals("hello", entry.word)
        assertEquals("/həˈloʊ/", entry.phonetic) // the American pronunciation is preferred
        assertEquals(listOf("noun", "interjection"), entry.meanings.map { it.partOfSpeech })
        assertEquals(2, entry.meanings[0].definitions.size)
        assertEquals("", entry.meanings[0].definitions[1].example) // JSON null is not shown as "null"
        assertEquals(listOf("greeting"), entry.meanings[0].synonyms)
        assertEquals("Hello, everyone.", entry.meanings[1].definitions[0].example)
        assertEquals("CC BY-SA 3.0", entry.license)
    }

    @Test
    fun `reads a Wiktionary answer and turns its HTML into plain text`() {
        val answer = """{"en":[{"partOfSpeech":"Adjective","language":"English","definitions":[
            {"definition":"Moving or advancing at a <a href=\"/wiki/run\" title=\"run\">run</a>.\n<ol><li>Of a horse.</li></ol>",
             "examples":["<b>running</b> month"]},
            {"definition":"Tom &amp; Jerry &lt;3"}]}],
            "fr":[{"partOfSpeech":"Noun","language":"French","definitions":[{"definition":"pas anglais"}]}]}"""
        val entry = DictionaryParser.parseWiktionary(answer, "running")!!
        val definitions = entry.meanings.single().definitions
        assertEquals("adjective", entry.meanings.single().partOfSpeech) // only the English section is used
        assertEquals("Moving or advancing at a run.", definitions[0].text)
        assertEquals("running month", definitions[0].example)
        assertEquals("Tom & Jerry <3", definitions[1].text) // shown as plain text, never as HTML
        assertEquals("", entry.baseWord)
    }

    @Test
    fun `a Wiktionary word that is only a form of another word points to its base word`() {
        val answer = """{"en":[{"partOfSpeech":"Verb","definitions":[{"definition":
            "<span class=\"form-of-definition\">simple past of <span class=\"form-of-definition-link\"><i><a href=\"/wiki/go#English\" title=\"go\">go</a></i></span></span>"}]}]}"""
        val entry = DictionaryParser.parseWiktionary(answer, "went")!!
        assertEquals("go", entry.baseWord)
        assertEquals("simple past of go", entry.note)
    }

    @Test
    fun `broken, hostile, or empty answers give no result instead of crashing`() {
        val bad = listOf(
            "", "not json", "{}", "[]", "null", "[1,2,3]", "[{\"meanings\":\"oops\"}]",
            "[".repeat(100_000), // deeply nested JSON: a classic way to crash a parser
            "[{\"word\":\"x\",\"meanings\":[{\"definitions\":[{\"definition\":\"\"}]}]}]",
        )
        for (json in bad) {
            assertNull("Free Dictionary: $json".take(60), DictionaryParser.parseFreeDictionary(json, "x"))
            assertNull("Wiktionary: $json".take(60), DictionaryParser.parseWiktionary(json, "x"))
        }
    }

    @Test
    fun `huge or tricky values are cleaned and cut short`() {
        val long = "a".repeat(10_000)
        val answer = """[{"word":"x","meanings":[{"partOfSpeech":"noun","definitions":[
            {"definition":"$long"},{"definition":"line\u0000one‮two"}]}]}]"""
        val entry = DictionaryParser.parseFreeDictionary(answer, "x")!!
        val definitions = entry.meanings.single().definitions
        assertTrue(definitions[0].text.length <= 501)
        assertEquals("lineonetwo", definitions[1].text)
    }

    @Test
    fun `at most six definitions are kept per part of speech`() {
        val many = (1..20).joinToString(",") { """{"definition":"Sense $it."}""" }
        val entry = DictionaryParser.parseFreeDictionary("""[{"word":"set","meanings":[{"partOfSpeech":"verb","definitions":[$many]}]}]""", "set")!!
        assertEquals(6, entry.meanings.single().definitions.size)
    }

    @Test
    fun `symbols and abbreviations come after real meanings`() {
        val answer = """[{"word":"to","meanings":[
            {"partOfSpeech":"symbol","definitions":[{"definition":"ISO 639-1 language code for Tongan."}]},
            {"partOfSpeech":"particle","definitions":[{"definition":"Marks an infinitive."}]},
            {"partOfSpeech":"preposition","definitions":[{"definition":"Towards."}]}]}]"""
        val entry = DictionaryParser.parseFreeDictionary(answer, "to")!!
        assertEquals(listOf("particle", "preposition", "symbol"), entry.meanings.map { it.partOfSpeech })
    }

    @Test
    fun `an entry saved to the database reads back the same`() {
        val original = DictionaryParser.parseFreeDictionary(freeDictionaryAnswer, "hello")!!
        val restored = DictionaryParser.parseSaved(DictionaryParser.toJson(original), "hello")
        assertEquals(original, restored)
    }

    @Test
    fun `a damaged saved copy still shows the word`() {
        val restored = DictionaryParser.parseSaved("{corrupted", "hello")
        assertEquals("hello", restored.word)
        assertTrue(restored.meanings.isEmpty())
    }
}
