package com.example.lishan.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lishan.model.Deck
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tester DAO'en mod en database, der kun ligger i hukommelsen og forsvinder efter testen.
 */
@RunWith(AndroidJUnit4::class)
class DeckDaoTest {

    private lateinit var db: LishanDatabase
    private lateinit var dao: DeckDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            LishanDatabase::class.java,
        ).build()
        dao = db.deckDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertCardWithSides_skipsBlankSidesAndNumbersTheRest() = runBlocking {
        val deckId = dao.insertDeck(Deck(name = "Farver"))

        dao.insertCardWithSides(deckId, listOf("rød", "", "  red ", "   ", "rojo"))

        val cards = dao.getCards(deckId).first()
        assertEquals(1, cards.size)
        assertEquals(listOf(1, 2, 3), cards[0].sides.sortedBy { it.position }.map { it.position })
        assertEquals(listOf("rød", "red", "rojo"), cards[0].visibleSides)
    }

    @Test
    fun updateCardSides_replacesAllSides() = runBlocking {
        val deckId = dao.insertDeck(Deck(name = "Farver"))
        dao.insertCardWithSides(deckId, listOf("rød", "red", "rojo"))
        val cardId = dao.getCards(deckId).first().single().card.id

        dao.updateCardSides(cardId, listOf("", "blå", "blue"))

        assertEquals(listOf("blå", "blue"), dao.getCards(deckId).first().single().visibleSides)
        assertEquals(2, count("card_sides"))
    }

    @Test
    fun updateDeck_changesName() = runBlocking {
        val id = dao.insertDeck(Deck(name = "Farvr"))

        dao.updateDeck(Deck(id = id, name = "Farver"))

        assertEquals(listOf("Farver"), dao.getDecks().first().map { it.name })
    }

    @Test
    fun deleteCard_alsoDeletesItsSides() = runBlocking {
        val deckId = dao.insertDeck(Deck(name = "Farver"))
        dao.insertCardWithSides(deckId, listOf("rød", "red"))
        dao.insertCardWithSides(deckId, listOf("blå", "blue"))
        val first = dao.getCards(deckId).first().first()

        dao.deleteCard(first.card.id)

        assertEquals(listOf(listOf("blå", "blue")), dao.getCards(deckId).first().map { it.visibleSides })
        assertEquals(2, count("card_sides"))
    }

    @Test
    fun deleteDeck_alsoDeletesItsCardsAndSides() = runBlocking {
        val keepId = dao.insertDeck(Deck(name = "Dyr"))
        dao.insertCardWithSides(keepId, listOf("hund", "dog"))
        val deleteId = dao.insertDeck(Deck(name = "Farver"))
        dao.insertCardWithSides(deleteId, listOf("rød", "red"))

        dao.deleteDeck(Deck(id = deleteId, name = "Farver"))

        assertEquals(listOf("Dyr"), dao.getDecks().first().map { it.name })
        assertEquals(1, count("flashcards"))
        assertEquals(2, count("card_sides"))
    }

    @Test
    fun unicodeText_isStoredAndReadBackUnchanged() = runBlocking {
        val name = "Æbler & øl — ÆØÅ"
        val sides = listOf("æøå ÆØÅ", "🐶🐱 👨‍👩‍👧", "狗", "כֶּלֶב", "كلب", "café")
        val deckId = dao.insertDeck(Deck(name = name))
        dao.insertCardWithSides(deckId, sides)

        assertEquals(listOf(name), dao.getDecks().first().map { it.name })
        assertEquals(sides, dao.getCards(deckId).first().single().visibleSides)
    }

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    @Test
    fun insertDeck_appearsInDeckList() = runBlocking {
        dao.insertDeck(Deck(name = "Farver"))
        dao.insertDeck(Deck(name = "Dyr"))

        assertEquals(listOf("Dyr", "Farver"), dao.getDecks().first().map { it.name })
    }
}
