package dk.lishan.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dk.lishan.app.model.CardSide
import dk.lishan.app.model.Deck
import dk.lishan.app.model.DeckSideLabel
import dk.lishan.app.model.Flashcard
import dk.lishan.app.model.Folder

/**
 * Appens database. Den gemmes som filen "lishan.db" på enheden og overlever,
 * når appen lukkes.
 *
 * `version` skal tælles op, hver gang tabellerne ændres, og der skal skrives en migration
 * i Migrations.kt. Room gemmer skemaet for hver version i `app/schemas/`.
 *
 * Version 2: kortenes indhold flyttet fra front/back til tabellen card_sides.
 * Version 3: ny tabel deck_side_labels med labels på et decks sider.
 * Version 4: kolonnerne notes og comment på flashcards.
 * Version 5: ny tabel folders og kolonnen folderId på decks.
 * Version 6: synkronisering – lektions-id og fingeraftryk på decks, glose-id og kategori på kort.
 */
@Database(
    entities = [Folder::class, Deck::class, Flashcard::class, CardSide::class, DeckSideLabel::class],
    version = 6,
    exportSchema = true,
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
                    // Mangler der en migration, går appen ned i stedet for at slette data i stilhed.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
    }
}

/**
 * Startdata, der lægges ind, når databasen oprettes første gang (ny installation).
 * Eksisterende databaser får ikke startdata igen; de bliver migreret.
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
        db.execSQL(
            "INSERT INTO deck_side_labels (deckId, position, label) VALUES " +
                "(1, 1, 'Dansk'), (1, 2, 'Engelsk'), (1, 3, 'Spansk')"
        )
    }
}
