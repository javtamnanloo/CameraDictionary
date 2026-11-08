# Camera Dictionary

Point your phone's camera at a word and get its **English** definition, pronunciation
and examples. Then keep the words you want to learn in a **Leitner box** that quizzes you at
growing intervals.

Made for people reading in English who want English-to-English definitions. Most camera
apps only translate into another language.

## Features

- **Read words with the camera.** Tap **Read** and the word under the **+** is looked up. Tap any
  other highlighted word, or step through them with **‹ ›**. Pinch to zoom, tap to focus, and
  turn on the light for dim pages.
- **Clean words.** Punctuation is stripped, and a word split across two lines with a hyphen
  ("impor- / tant") is joined again.
- **Definitions, pronunciation and examples** from Wiktionary, via the Free Dictionary API, with
  Wiktionary's own API as an automatic backup. Forms like "went" lead to the base word "go".
  Hear any word spoken by the phone's voice.
- **Leitner box.** Tap ☆ to save a word. You review it tomorrow; each time you know it, the
  wait triples (3, 9, 27, 81, 243 days). If you miss it, it starts again tomorrow.
- **Reminder** at the hour you choose, on days when words are due (optional).
- **Type a word**, or fix one the camera misread.
- **One-handed use**: the main buttons float just above the tabs, near your thumb.
- **Room to read**: scroll a long definition and the picture slides out of the way, while the
  word, its speaker, pencil and star stay at the top.
- Light and dark mode, landscape and tablet layouts. Runs on Android 7.0 and newer.

## Privacy and security

- **Photos never leave the phone.** Text recognition runs on the device (Google ML Kit).
- **Only the one word you choose** is sent, over HTTPS, to the dictionary.
- **Saved words stay on your phone.** There are no accounts, ads, analytics or tracking.
- **Text from the camera is treated strictly as data.** It is never executed, never shown as
  HTML or as links, and never inserted into database queries. Every piece of outside text goes
  through one gatekeeper, [`TextSafety.kt`](app/src/main/java/io/github/javtamnanloo/cameradictionary/TextSafety.kt),
  which only lets plain words through. The tests in
  [`TextSafetyTest.kt`](app/src/test/java/io/github/javtamnanloo/cameradictionary/TextSafetyTest.kt)
  try commands, SQL, scripts, file paths and invisible-character tricks against it.

Full privacy policy: [PRIVACY.md](PRIVACY.md)

## Building the app

1. Install [Android Studio](https://developer.android.com/studio).
2. **File › Open** this folder, then wait for "Gradle sync" to finish.
3. Connect an Android phone with USB debugging on, and press **Run ▶**.

From a terminal: `gradlew assembleDebug` builds the app, `gradlew test` runs the unit tests, and
`gradlew bundleRelease` builds the Google Play bundle (signing setup:
[docs/PUBLISHING.md](docs/PUBLISHING.md)).

## Where things are

All code is in `app/src/main/java/io/github/javtamnanloo/cameradictionary/`:

| File | What it does |
|---|---|
| `MainActivity.kt` | The four tabs: Scan, Review, My words, More |
| `ScanScreen.kt`, `ScanViewModel.kt` | Camera, frozen photo, choosing a word, looking it up |
| `ReviewScreen.kt` | The Leitner review and box overview |
| `WordsScreen.kt`, `WordsViewModel.kt` | The saved-word list: answer, delete, delete all, undo |
| `MoreScreen.kt` | Reminder time, contact and support, about the app |
| `Leitner.kt` | The review schedule (1, 3, 9, 27, 81, 243 days) |
| `WordDatabase.kt` | The saved-words database (Room) |
| `TextSafety.kt` | The gatekeeper for all text from outside the app |
| `OcrWords.kt` | Turns recognized text into clean, tappable words |
| `Dictionary.kt`, `DictionaryClient.kt` | Fetching and reading definitions |
| `ReviewReminder.kt` | The daily notification |
| `Speaker.kt` | Saying words out loud |
| `CommonUi.kt`, `SystemScreens.kt` | Shared screen pieces; permissions and opening Settings |

Unit tests are in `app/src/test/`.

## Contact and support

- Email: <jav.tamnanloo@gmail.com>
- Bugs and ideas: [GitHub issues](https://github.com/javtamnanloo/CameraDictionary/issues)
- If the app helps you: [Buy me a coffee](https://buymeacoffee.com/javtamnanloo)

## Credits

- Definitions: [Wiktionary](https://en.wiktionary.org/) contributors, under
  [CC BY-SA](https://creativecommons.org/licenses/by-sa/4.0/), via the
  [Free Dictionary API](https://dictionaryapi.dev/) and the Wikimedia REST API.
- Text recognition: [Google ML Kit](https://developers.google.com/ml-kit).
- Icons: [Material Symbols](https://fonts.google.com/icons) by Google (Apache License 2.0).

## License

The app's code is released under the [MIT License](LICENSE). Dictionary text is not part of
this repository: the app fetches it at runtime, and it remains under Wiktionary's CC BY-SA license.
