package io.github.javtamnanloo.cameradictionary

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** The outcome of looking up a word. */
sealed interface LookupResult {
    /** [word] is the spelling that was found. It is always a [TextSafety.cleanWord]. */
    data class Found(val word: String, val entry: DictionaryEntry) : LookupResult
    data object NotFound : LookupResult
    data object Offline : LookupResult
    data object Unavailable : LookupResult
}

/**
 * Looks words up online, in two free dictionaries built from Wiktionary:
 *  1. the Free Dictionary API (api.dictionaryapi.dev): tidy data, with pronunciations;
 *  2. Wiktionary's own API: the reliable backup when the first one fails or doesn't know a word.
 *
 * Safety: requests go only to these two hosts, only over HTTPS, and only ever contain one
 * already-cleaned word. Answers are size-limited and parsed defensively ([DictionaryParser]).
 */
class DictionaryClient(cacheDir: File, private val userAgent: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followSslRedirects(false) // never follow a redirect from HTTPS down to plain HTTP
        .cache(Cache(File(cacheDir, "dictionary"), 5L * 1024 * 1024)) // repeat lookups load instantly
        .build()

    /** Recently found words, so going back and forth between words is instant. */
    private val recent = object : LinkedHashMap<String, LookupResult.Found>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LookupResult.Found>) = size > 200
    }

    /**
     * Looks up the first of [spellings] that a dictionary knows, also trying simple variants
     * such as "author's" → "author". Each spelling must already be a [TextSafety.cleanWord].
     */
    suspend fun lookUp(spellings: List<String>): LookupResult {
        val candidates = spellings.flatMap(TextSafety::spellingVariants).distinct().take(MAX_SPELLINGS)
        var result: LookupResult = LookupResult.NotFound
        for (word in candidates) {
            result = lookUpWord(word, followBaseWord = true)
            if (result != LookupResult.NotFound) break // found it, or the internet failed: stop here
        }
        return result
    }

    private suspend fun lookUpWord(word: String, followBaseWord: Boolean): LookupResult {
        if (TextSafety.cleanWord(word) != word) return LookupResult.NotFound // never send unchecked text
        synchronized(recent) { recent[word] }?.let { return it }

        val primary = fetch(freeDictionaryUrl(word)) { DictionaryParser.parseFreeDictionary(it, word) }
        val result = if (primary is Fetched.Ok) {
            LookupResult.Found(word, primary.entry)
        } else {
            val backup = fetch(wiktionaryUrl(word)) { DictionaryParser.parseWiktionary(it, word) }
            when {
                backup is Fetched.Ok && backup.entry.baseWord.isNotEmpty() && followBaseWord ->
                    withBaseWord(word, backup.entry)
                backup is Fetched.Ok -> LookupResult.Found(word, backup.entry)
                backup is Fetched.Missing -> LookupResult.NotFound
                primary is Fetched.Offline && backup is Fetched.Offline -> LookupResult.Offline
                else -> LookupResult.Unavailable
            }
        }
        if (result is LookupResult.Found) synchronized(recent) { recent[word] = result }
        return result
    }

    /** "went" only says "simple past of go", so we show the definitions of "go" under "went". */
    private suspend fun withBaseWord(word: String, form: DictionaryEntry): LookupResult {
        val base = lookUpWord(form.baseWord, followBaseWord = false)
        return if (base is LookupResult.Found) {
            LookupResult.Found(word, base.entry.copy(word = form.word, phonetic = form.phonetic, note = form.note, baseWord = ""))
        } else {
            LookupResult.Found(word, form) // still useful: shows "simple past of go"
        }
    }

    private sealed interface Fetched {
        data class Ok(val entry: DictionaryEntry) : Fetched
        data object Missing : Fetched // the dictionary doesn't have this word
        data object Offline : Fetched // no internet connection
        data object Failed : Fetched  // the service had a problem
    }

    private suspend fun fetch(url: HttpUrl, parse: (String) -> DictionaryEntry?): Fetched =
        try {
            runInterruptible(Dispatchers.IO) { download(url, parse) }
        } catch (e: CancellationException) {
            throw e // the user moved on to another word; stop quietly
        } catch (e: UnknownHostException) {
            Fetched.Offline
        } catch (e: ConnectException) {
            Fetched.Offline
        } catch (e: Exception) {
            Fetched.Failed
        }

    private fun download(url: HttpUrl, parse: (String) -> DictionaryEntry?): Fetched {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.request.url.host != url.host) return Fetched.Failed // redirected somewhere unexpected
            if (response.code == 404) return Fetched.Missing
            if (!response.isSuccessful) return Fetched.Failed
            val json = readAtMost(response, MAX_RESPONSE_BYTES) ?: return Fetched.Failed
            val entry = parse(json) ?: return Fetched.Missing
            return Fetched.Ok(entry)
        }
    }

    /** Reads the answer, refusing anything over [limit] bytes (a normal answer is 1–30 KB). */
    private fun readAtMost(response: Response, limit: Long): String? {
        val source = response.body.source()
        if (source.request(limit + 1)) return null
        return source.buffer.readUtf8()
    }

    private fun freeDictionaryUrl(word: String): HttpUrl = HttpUrl.Builder()
        .scheme("https").host("api.dictionaryapi.dev")
        .addPathSegments("api/v2/entries/en")
        .addPathSegment(word) // encoded as ONE path segment
        .build()

    private fun wiktionaryUrl(word: String): HttpUrl = HttpUrl.Builder()
        .scheme("https").host("en.wiktionary.org")
        .addPathSegments("api/rest_v1/page/definition")
        .addPathSegment(word)
        .build()

    private companion object {
        const val MAX_SPELLINGS = 3
        const val MAX_RESPONSE_BYTES = 2_000_000L
    }
}
