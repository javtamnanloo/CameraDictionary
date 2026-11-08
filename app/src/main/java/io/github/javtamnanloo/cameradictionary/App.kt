package io.github.javtamnanloo.cameradictionary

import android.app.Application

/** Starts once when the app launches and holds the things the whole app shares. */
class App : Application() {

    /** Your saved words (the Leitner box). */
    val database: WordDatabase by lazy { WordDatabase.create(this) }

    /** Online dictionary lookups. */
    val dictionary: DictionaryClient by lazy {
        DictionaryClient(
            cacheDir = cacheDir,
            // Wikimedia asks apps to identify themselves; this names the app and its home page.
            userAgent = "CameraDictionary/${BuildConfig.VERSION_NAME} (https://github.com/javtamnanloo/CameraDictionary)",
        )
    }

    override fun onCreate() {
        super.onCreate()
        ReviewReminder.setUp(this)
    }
}
