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
    fun insertDeck_appearsInDeckList() = runBlocking {
        dao.insertDeck(Deck(name = "Farver"))
        dao.insertDeck(Deck(name = "Dyr"))

        assertEquals(listOf("Dyr", "Farver"), dao.getDecks().first().map { it.name })
    }
}
