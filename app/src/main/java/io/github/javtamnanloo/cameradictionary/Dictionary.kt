package io.github.javtamnanloo.cameradictionary

import org.json.JSONArray
import org.json.JSONObject

/** Everything we show for one word. All of this text has already been cleaned by [TextSafety]. */
data class DictionaryEntry(
    val word: String,
    val phonetic: String = "",   // pronunciation in IPA, e.g. "/həˈloʊ/"
    val note: String = "",       // e.g. "simple past of go" when we followed a word to its base form
    val baseWord: String = "",   // set when this entry only says "form of <baseWord>"
    val meanings: List<Meaning> = emptyList(),
    val license: String = "",    // license of the definition text, e.g. "CC BY-SA 4.0"
)

data class Meaning(
    val partOfSpeech: String,
    val definitions: List<Definition>,
    val synonyms: List<String> = emptyList(),
)

data class Definition(val text: String, val example: String = "")

/**
 * Reads dictionary answers. Answers from the internet are untrusted, so every value is
 * cleaned and size-limited, deeply nested JSON (a known way to crash parsers) is refused,
 * and anything unexpected simply means "no result" instead of a crash.
 */
object DictionaryParser {

    private const val MAX_NESTING = 20
    private const val MAX_ITEMS = 50 // read at most this many items from any JSON list
    private const val MAX_PARTS_OF_SPEECH = 8
    private const val MAX_DEFINITIONS_PER_PART = 6
    private const val MAX_SYNONYMS = 8
    private const val MAX_DEFINITION_LENGTH = 500
    private const val MAX_EXAMPLE_LENGTH = 300
    private const val MAX_HTML_LENGTH = 5_000
    private const val DEFAULT_LICENSE = "CC BY-SA 4.0"

    /**
     * Reads an answer from the Free Dictionary API (api.dictionaryapi.dev). The copies of
     * entries saved in the database use the same format, so this reads those too.
     */
    fun parseFreeDictionary(json: String, word: String): DictionaryEntry? = safely(json) {
        val meanings = MeaningsBuilder()
        var headword = ""
        var phonetic = ""
        var note = ""
        var license = ""
        JSONArray(json).objects().forEach { entry ->
            if (headword.isEmpty()) headword = clean(entry.text("word"), TextSafety.MAX_WORD_LENGTH)
            if (phonetic.isEmpty()) phonetic = pickPhonetic(entry)
            if (note.isEmpty()) note = clean(entry.text("note"), 120)
            if (license.isEmpty()) license = clean(entry.optJSONObject("license")?.text("name").orEmpty(), 40)
            entry.optJSONArray("meanings").objects().forEach { meaning ->
                val partOfSpeech = clean(meaning.text("partOfSpeech"), 30).lowercase()
                meaning.optJSONArray("definitions").objects().forEach { definition ->
                    meanings.add(
                        partOfSpeech,
                        Definition(
                            text = clean(definition.text("definition"), MAX_DEFINITION_LENGTH),
                            example = clean(definition.text("example"), MAX_EXAMPLE_LENGTH),
                        )
                    )
                    meanings.addSynonyms(partOfSpeech, definition.optJSONArray("synonyms").strings())
                }
                meanings.addSynonyms(partOfSpeech, meaning.optJSONArray("synonyms").strings())
            }
        }
        meanings.build().takeIf { it.isNotEmpty() }?.let {
            DictionaryEntry(
                word = headword.ifEmpty { word },
                phonetic = phonetic,
                note = note,
                meanings = it,
                license = license.ifEmpty { DEFAULT_LICENSE },
            )
        }
    }

    /**
     * Reads an answer from Wiktionary's definition API. Its definitions are small pieces of
     * HTML, which are turned into plain text (HTML is never displayed). When a word is only
     * listed as a form of another word ("went" → "simple past of go"), [DictionaryEntry.baseWord]
     * tells the caller which word to look up instead.
     */
    fun parseWiktionary(json: String, word: String): DictionaryEntry? = safely(json) {
        val meanings = MeaningsBuilder()
        var baseWord = ""
        var formOf = ""
        var realDefinitions = 0
        JSONObject(json).optJSONArray("en").objects().forEach { section ->
            val partOfSpeech = clean(section.text("partOfSpeech"), 30).lowercase()
            section.optJSONArray("definitions").objects().forEach { definition ->
                val html = definition.text("definition").take(MAX_HTML_LENGTH)
                val text = htmlToText(html, MAX_DEFINITION_LENGTH)
                if (text.isEmpty()) return@forEach
                if ("form-of-definition" in html) {
                    if (baseWord.isEmpty()) baseWord = findBaseWord(html)
                    if (formOf.isEmpty()) formOf = text
                } else {
                    realDefinitions++
                }
                val example = definition.optJSONArray("examples").strings().firstOrNull().orEmpty()
                meanings.add(partOfSpeech, Definition(text, htmlToText(example.take(MAX_HTML_LENGTH), MAX_EXAMPLE_LENGTH)))
            }
        }
        val onlyFormOf = realDefinitions == 0
        meanings.build().takeIf { it.isNotEmpty() }?.let {
            DictionaryEntry(
                word = word,
                note = if (onlyFormOf) formOf else "",
                baseWord = if (onlyFormOf) baseWord else "",
                meanings = it,
                license = DEFAULT_LICENSE,
            )
        }
    }

    /** Saves an entry in the Free Dictionary format, so [parseFreeDictionary] can read it back. */
    fun toJson(entry: DictionaryEntry): String {
        val meanings = JSONArray()
        for (meaning in entry.meanings) {
            val definitions = JSONArray()
            for (d in meaning.definitions) {
                definitions.put(JSONObject().put("definition", d.text).put("example", d.example))
            }
            meanings.put(
                JSONObject()
                    .put("partOfSpeech", meaning.partOfSpeech)
                    .put("definitions", definitions)
                    .put("synonyms", JSONArray(meaning.synonyms))
            )
        }
        val json = JSONObject()
            .put("word", entry.word)
            .put("phonetic", entry.phonetic)
            .put("note", entry.note)
            .put("license", JSONObject().put("name", entry.license))
            .put("meanings", meanings)
        return JSONArray().put(json).toString()
    }

    /** Reads an entry saved by [toJson]; a damaged copy shows just the word instead of crashing. */
    fun parseSaved(json: String, word: String): DictionaryEntry =
        parseFreeDictionary(json, word) ?: DictionaryEntry(word = word)

    // ---------------------------------------------------------------------------------------

    /** Runs [parse] only on sensibly shaped JSON; any surprise means "no result", never a crash. */
    private inline fun safely(json: String, parse: () -> DictionaryEntry?): DictionaryEntry? {
        if (!isNestingSafe(json)) return null
        return try {
            parse()
        } catch (e: Exception) {
            null
        }
    }

    /** False for JSON nested more than [MAX_NESTING] levels deep. Real answers use about 7. */
    private fun isNestingSafe(json: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in json) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '[', '{' -> if (++depth > MAX_NESTING) return false
                    ']', '}' -> depth--
                }
            }
        }
        return true
    }

    /** The first pronunciation, preferring the American one (it matches the voice that reads aloud). */
    private fun pickPhonetic(entry: JSONObject): String {
        val phonetics = entry.optJSONArray("phonetics").objects()
        val american = phonetics.firstOrNull { "-us." in it.text("audio") && it.text("text").isNotBlank() }
        val raw = american?.text("text")
            ?: entry.text("phonetic").ifBlank { phonetics.map { it.text("text") }.firstOrNull { it.isNotBlank() }.orEmpty() }
        return clean(raw, 60)
    }

    private val NESTED_LIST = Regex("<(ol|ul|dl)\\b.*?</\\1>", RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("<[^>]*>")
    private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});")
    private val BASE_WORD_LINK = Regex("form-of-definition-link.*?title=\"([^\"]{1,100})\"", RegexOption.DOT_MATCHES_ALL)
    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "ndash" to "–", "mdash" to "—", "hellip" to "…",
    )

    /** Plain text from a small piece of HTML: sub-lists removed (Wiktionary repeats them), tags removed, entities decoded. */
    private fun htmlToText(html: String, maxLength: Int): String {
        val withoutLists = NESTED_LIST.replace(html, " ")
        val withoutTags = TAG.replace(withoutLists, "")
        return clean(decodeEntities(withoutTags), maxLength)
    }

    private fun decodeEntities(text: String): String = ENTITY.replace(text) { match ->
        val name = match.groupValues[1]
        val decoded = when {
            name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let(::codePointText)
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePointText)
            else -> NAMED_ENTITIES[name]
        }
        decoded ?: match.value
    }

    private fun codePointText(codePoint: Int): String? =
        if (Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else null

    /** The base word linked from a "form of" definition, e.g. "go" in "simple past of go". */
    private fun findBaseWord(html: String): String {
        val title = BASE_WORD_LINK.find(html)?.groupValues?.get(1) ?: return ""
        return TextSafety.cleanWord(decodeEntities(title).substringBefore('#')).orEmpty()
    }

    private fun clean(text: String, maxLength: Int) = TextSafety.cleanDisplay(text, maxLength)

    /** A string field, or "" when it is missing or null. (org.json returns the text "null" for null!) */
    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)

    /** The objects in a JSON list (at most [MAX_ITEMS]); empty when the list is missing. */
    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until minOf(length(), MAX_ITEMS)).mapNotNull { optJSONObject(it) }

    /** The non-blank strings in a JSON list (at most [MAX_ITEMS]), not yet cleaned. */
    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList()
        else (0 until minOf(length(), MAX_ITEMS)).mapNotNull { i -> if (isNull(i)) null else optString(i).takeIf { it.isNotBlank() } }

    /** Collects definitions grouped by part of speech (merging repeats), within the limits above. */
    private class MeaningsBuilder {
        private val definitions = LinkedHashMap<String, MutableList<Definition>>()
        private val synonyms = HashMap<String, LinkedHashSet<String>>()

        fun add(partOfSpeech: String, definition: Definition) {
            if (definition.text.isEmpty()) return
            val list = definitions[partOfSpeech]
                ?: if (definitions.size < MAX_PARTS_OF_SPEECH) mutableListOf<Definition>().also { definitions[partOfSpeech] = it } else return
            if (list.size < MAX_DEFINITIONS_PER_PART && definition !in list) list += definition
        }

        fun addSynonyms(partOfSpeech: String, words: List<String>) {
            val set = synonyms.getOrPut(partOfSpeech) { LinkedHashSet() }
            for (word in words) {
                if (set.size >= MAX_SYNONYMS) break
                clean(word, 40).takeIf { it.isNotEmpty() }?.let(set::add)
            }
        }

        /** Real meanings first; "symbol" (e.g. "to" = Tongan language code) and similar go last. */
        fun build(): List<Meaning> = definitions.entries
            .sortedBy { it.key in MINOR_PARTS_OF_SPEECH } // a stable sort: the original order stays otherwise
            .map { (partOfSpeech, list) -> Meaning(partOfSpeech, list.toList(), synonyms[partOfSpeech].orEmpty().toList()) }
    }

    private val MINOR_PARTS_OF_SPEECH = setOf(
        "symbol", "letter", "character", "numeral", "abbreviation", "initialism", "acronym",
        "prefix", "suffix", "affix", "infix", "interfix",
    )
}
