package io.github.javtamnanloo.cameradictionary

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One word in your Leitner box (one row of the database). */
@Entity(tableName = "saved_words")
data class SavedWord(
    @PrimaryKey val word: String, // the cleaned word, e.g. "serendipity"
    val entryJson: String,        // its definitions, saved so reviews also work offline
    val context: String,          // the line of text you found it in ("" if you typed it)
    val box: Int,                 // Leitner box, 1 to 6
    val dueDay: Long,             // next review date, as a day number (see Leitner.today)
    val addedAtMillis: Long,      // when you saved it
) {
    /** The saved definitions, ready to display. */
    fun entry(): DictionaryEntry = DictionaryParser.parseSaved(entryJson, word)
}

/**
 * Every database operation the app uses. Room turns these into SQL at build time and always
 * passes values (like :word) as parameters, so text can never change what a query does.
 */
@Dao
interface SavedWordDao {
    @Query("SELECT * FROM saved_words ORDER BY word")
    fun observeAll(): Flow<List<SavedWord>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_words WHERE word = :word)")
    suspend fun exists(word: String): Boolean

    @Query("SELECT COUNT(*) FROM saved_words WHERE dueDay <= :today")
    suspend fun countDue(today: Long): Int

    @Upsert
    suspend fun save(word: SavedWord)

    @Query("DELETE FROM saved_words WHERE word = :word")
    suspend fun delete(word: String)

    @Query("DELETE FROM saved_words")
    suspend fun deleteAll()
}

@Database(entities = [SavedWord::class], version = 1)
abstract class WordDatabase : RoomDatabase() {
    abstract fun savedWords(): SavedWordDao

    companion object {
        // IMPORTANT for future versions: if you ever change SavedWord, increase `version` above
        // and add a Migration. Never use fallbackToDestructiveMigration(): it would silently
        // delete every user's saved words when they update the app.
        fun create(context: Context): WordDatabase =
            Room.databaseBuilder(context, WordDatabase::class.java, "words.db").build()
    }
}
