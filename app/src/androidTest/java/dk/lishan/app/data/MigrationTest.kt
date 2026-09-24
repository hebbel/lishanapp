package dk.lishan.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tester migrations mod de skemaer, Room har gemt i `app/schemas/`.
 * Kører på en enhed/emulator: `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LishanDatabase::class.java,
    )

    @Test
    fun migrate1To2_movesFrontAndBackToSides() {
        // Opret en database i version 1 med noget data, som det så ud dengang.
        helper.createDatabase(testDb, 1).apply {
            execSQL("INSERT INTO decks (id, name) VALUES (1, 'Dyr')")
            execSQL("INSERT INTO flashcards (id, deckId, front, back) VALUES (1, 1, 'hund', 'dog')")
            execSQL("INSERT INTO flashcards (id, deckId, front, back) VALUES (2, 1, 'kat', '')")
            close()
        }

        // Kør migrationen. `validateDroppedTables = true` + Rooms tjek sikrer, at resultatet
        // svarer præcis til skemaet for version 2.
        val db = helper.runMigrationsAndValidate(testDb, 2, true, MIGRATION_1_2)

        val sides = mutableListOf<String>()
        db.query("SELECT cardId, position, text FROM card_sides ORDER BY cardId, position").use { c ->
            while (c.moveToNext()) sides += "${c.getLong(0)}:${c.getInt(1)}:${c.getString(2)}"
        }
        // Kort 2's tomme bagside er ikke blevet til en side.
        assertEquals(listOf("1:1:hund", "1:2:dog", "2:1:kat"), sides)

        db.query("SELECT COUNT(*) FROM flashcards WHERE deckId = 1").use { c ->
            c.moveToFirst()
            assertEquals(2, c.getInt(0))
        }
    }

    @Test
    fun migrate2To3_addsEmptyLabelTableAndKeepsData() {
        helper.createDatabase(testDb, 2).apply {
            execSQL("INSERT INTO decks (id, name) VALUES (1, 'Dyr')")
            execSQL("INSERT INTO flashcards (id, deckId) VALUES (1, 1)")
            execSQL("INSERT INTO card_sides (cardId, position, text) VALUES (1, 1, 'hund'), (1, 2, 'dog')")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 3, true, MIGRATION_2_3)

        db.query("SELECT COUNT(*) FROM card_sides").use { c ->
            c.moveToFirst()
            assertEquals(2, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM deck_side_labels").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        // Den nye tabel virker: et label kan indsættes på et eksisterende deck.
        db.execSQL("INSERT INTO deck_side_labels (deckId, position, label) VALUES (1, 1, 'Dansk')")
    }

    @Test
    fun migrate3To4_givesExistingCardsEmptyNotesAndComment() {
        helper.createDatabase(testDb, 3).apply {
            execSQL("INSERT INTO decks (id, name) VALUES (1, 'Dyr')")
            execSQL("INSERT INTO flashcards (id, deckId) VALUES (1, 1)")
            execSQL("INSERT INTO card_sides (cardId, position, text) VALUES (1, 1, 'hund'), (1, 2, 'dog')")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 4, true, MIGRATION_3_4)

        db.query("SELECT notes, comment FROM flashcards WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("", c.getString(0))
            assertEquals("", c.getString(1))
        }
        db.query("SELECT COUNT(*) FROM card_sides WHERE cardId = 1").use { c ->
            c.moveToFirst()
            assertEquals(2, c.getInt(0))
        }
    }

    @Test
    fun migrate4To5_putsExistingDecksOnFrontPageAndKeepsCards() {
        helper.createDatabase(testDb, 4).apply {
            execSQL("INSERT INTO decks (id, name) VALUES (1, 'Dyr')")
            execSQL("INSERT INTO flashcards (id, deckId) VALUES (1, 1)")
            execSQL("INSERT INTO card_sides (cardId, position, text) VALUES (1, 1, 'hund')")
            execSQL("INSERT INTO deck_side_labels (deckId, position, label) VALUES (1, 1, 'Dansk')")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 5, true, MIGRATION_4_5)

        db.query("SELECT folderId FROM decks WHERE id = 1").use { c ->
            c.moveToFirst()
            assertTrue(c.isNull(0))
        }
        for (table in listOf("flashcards", "card_sides", "deck_side_labels")) {
            db.query("SELECT COUNT(*) FROM $table").use { c ->
                c.moveToFirst()
                assertEquals(table, 1, c.getInt(0))
            }
        }
        // Fremmednøglen virker: sletter man en mappe, forsvinder dens decks.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("INSERT INTO folders (id, name) VALUES (1, 'Arabisk')")
        db.execSQL("UPDATE decks SET folderId = 1 WHERE id = 1")
        db.execSQL("DELETE FROM folders WHERE id = 1")
        db.query("SELECT COUNT(*) FROM decks").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun migrate5To6_keepsUserDataAsLocal() {
        helper.createDatabase(testDb, 5).apply {
            execSQL("INSERT INTO folders (id, name) VALUES (1, 'Mine')")
            execSQL("INSERT INTO decks (id, name, folderId) VALUES (1, 'Dyr', 1), (2, 'Farver', NULL)")
            execSQL("INSERT INTO flashcards (id, deckId, notes) VALUES (1, 1, 'min note')")
            execSQL("INSERT INTO card_sides (cardId, position, text) VALUES (1, 1, 'hund')")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 6, true, MIGRATION_5_6)

        db.query("SELECT lessonId, downloadedHash, removedOnServer FROM decks ORDER BY id").use { c ->
            while (c.moveToNext()) {
                assertTrue(c.isNull(0))
                assertTrue(c.isNull(1))
                assertEquals(0, c.getInt(2))
            }
        }
        db.query("SELECT wordId, category, notes FROM flashcards WHERE id = 1").use { c ->
            c.moveToFirst()
            assertTrue(c.isNull(0))
            assertEquals("", c.getString(1))
            assertEquals("min note", c.getString(2))
        }
        // Flere egne kort uden glose-id i samme deck er tilladt (NULL tæller ikke i unikke indekser).
        db.execSQL("INSERT INTO flashcards (deckId) VALUES (1), (1)")
    }

    @Test
    fun migrate6To7_decksAreNotLocallyModified() {
        helper.createDatabase(testDb, 6).apply {
            execSQL("INSERT INTO folders (id, name, courseId) VALUES (1, 'Arabisk', 71)")
            execSQL("INSERT INTO decks (id, name, folderId, lessonId, serverHash, downloadedHash) VALUES (1, '1.01 Hilsner', 1, '1.01', 'a', 'a')")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 7, true, MIGRATION_6_7)

        db.query("SELECT locallyModified, downloadedHash FROM decks WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
            assertEquals("a", c.getString(1))
        }
    }
}
