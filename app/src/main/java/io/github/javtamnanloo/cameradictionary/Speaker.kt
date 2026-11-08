package io.github.javtamnanloo.cameradictionary

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Says words out loud with the phone's built-in text-to-speech voice. It works offline and
 * never downloads audio files from the internet.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    @Volatile
    private var ready = false

    // Declared after `ready`, so the "voice is ready" signal (onInit) can never be overwritten.
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS && chooseEnglishVoice()
    }

    private fun chooseEnglishVoice(): Boolean {
        for (locale in listOf(Locale.US, Locale.UK, Locale.ENGLISH)) {
            if (tts.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) {
                tts.setSpeechRate(0.9f) // a little slower, for clarity
                return true
            }
        }
        return false
    }

    /** Speaks [word]; returns false if this phone has no English voice installed. */
    fun speak(word: String): Boolean {
        if (!ready) return false
        tts.speak(word.take(TextSafety.MAX_WORD_LENGTH), TextToSpeech.QUEUE_FLUSH, null, "word")
        return true
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
