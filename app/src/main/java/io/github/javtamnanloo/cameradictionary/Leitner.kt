package io.github.javtamnanloo.cameradictionary

import java.util.TimeZone

/**
 * The Leitner box: a simple, proven way to memorize words.
 *
 * Every saved word sits in a numbered box. When you know a word it moves up one box and
 * you see it less often. When you don't, it goes back to box 1 and you see it tomorrow.
 *
 *     box:              1    2    3    4    5    6
 *     next review in:   1    3    9   27   81  243 days   (each step × 3)
 *
 * A newly starred word starts in box 1, so its first review is tomorrow.
 * Tested in LeitnerTest.
 */
object Leitner {

    val INTERVAL_DAYS = listOf(1, 3, 9, 27, 81, 243)
    const val FIRST_BOX = 1
    val LAST_BOX = INTERVAL_DAYS.size

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** Days until the next review for a word in [box]. */
    fun intervalDays(box: Int): Int = INTERVAL_DAYS[box.coerceIn(FIRST_BOX, LAST_BOX) - 1]

    /** A word you just starred: box 1, first review tomorrow. */
    fun newWord(word: String, entry: DictionaryEntry, context: String, today: Long, nowMillis: Long) = SavedWord(
        word = word,
        entryJson = DictionaryParser.toJson(entry),
        context = context,
        box = FIRST_BOX,
        dueDay = today + intervalDays(FIRST_BOX),
        addedAtMillis = nowMillis,
    )

    /** Where a word goes after a review: up one box if you knew it, back to box 1 if you didn't. */
    fun afterReview(word: SavedWord, knewIt: Boolean, today: Long): SavedWord {
        val box = if (knewIt) (word.box + 1).coerceAtMost(LAST_BOX) else FIRST_BOX
        return word.copy(box = box, dueDay = today + intervalDays(box))
    }

    fun isDue(word: SavedWord, today: Long): Boolean = word.dueDay <= today

    /** Today as a day number (days since 1 January 1970) in the phone's time zone. */
    fun today(nowMillis: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Long =
        Math.floorDiv(nowMillis + zone.getOffset(nowMillis), DAY_MILLIS)
}
