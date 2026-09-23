package com.example.lishan.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
}
