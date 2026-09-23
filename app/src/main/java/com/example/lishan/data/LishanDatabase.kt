package com.example.lishan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.lishan.model.CardSide
import com.example.lishan.model.Deck
import com.example.lishan.model.Flashcard

/**
 * Appens database. Den gemmes som filen "lishan.db" på enheden og overlever,
 * når appen lukkes.
 *
 * `version` skal tælles op, hver gang tabellerne ændres.
 * Version 2: kortenes indhold flyttet fra front/back til tabellen card_sides.
 */
@Database(
    entities = [Deck::class, Flashcard::class, CardSide::class],
    version = 2,
    exportSchema = false,
)
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
                    // MIDLERTIDIGT: ved en ny version slettes hele databasen og bygges forfra.
                    // Det er fint, så længe der kun er startdata. Før appen får rigtige brugerdata,
                    // skal dette erstattes af migrations, der flytter data over.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}

/**
 * Startdata, der lægges ind, når databasen oprettes første gang.
 * NB: `onCreate` kaldes ikke efter en destruktiv migration. Efter en ny `version` er
 * databasen derfor tom, indtil appens data ryddes (`adb shell pm clear com.example.lishan`).
 */
private object SeedData : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO decks (id, name) VALUES (1, 'Dyr')")
        db.execSQL("INSERT INTO flashcards (id, deckId) VALUES (1, 1), (2, 1)")
        db.execSQL(
            """
            INSERT INTO card_sides (cardId, position, text) VALUES
                (1, 1, 'hund'), (1, 2, 'dog'), (1, 3, 'perro'),
                (2, 1, 'kat'),  (2, 2, 'cat')
            """
        )
    }
}
