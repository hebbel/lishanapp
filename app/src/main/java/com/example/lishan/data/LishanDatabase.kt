package com.example.lishan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.lishan.model.Deck
import com.example.lishan.model.Flashcard

/**
 * Appens database. Den gemmes som filen "lishan.db" på enheden og overlever,
 * når appen lukkes.
 */
@Database(entities = [Deck::class, Flashcard::class], version = 1, exportSchema = false)
abstract class LishanDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao

    companion object {
        @Volatile
        private var instance: LishanDatabase? = null

        /** Hele appen deler én database-instans. */
        fun getInstance(context: Context): LishanDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LishanDatabase::class.java,
                    "lishan.db",
                )
                    .addCallback(SeedData)
                    .build()
                    .also { instance = it }
            }
    }
}

/** Startdata, der lægges ind én gang: første gang databasen oprettes. */
private object SeedData : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO decks (id, name) VALUES (1, 'Dyr')")
        db.execSQL(
            "INSERT INTO flashcards (deckId, front, back) VALUES (1, 'hund', 'dog'), (1, 'kat', 'cat')"
        )
    }
}
