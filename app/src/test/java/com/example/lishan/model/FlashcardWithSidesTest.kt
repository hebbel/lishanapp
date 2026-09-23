package com.example.lishan.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FlashcardWithSidesTest {

    private fun cardWith(vararg sides: Pair<Int, String>) = FlashcardWithSides(
        card = Flashcard(id = 1),
        sides = sides.map { (position, text) -> CardSide(cardId = 1, position = position, text = text) },
    )

    @Test
    fun visibleSides_skipsEmptySides() {
        val card = cardWith(1 to "hund", 2 to "", 3 to "  ", 4 to "dog")
        assertEquals(listOf("hund", "dog"), card.visibleSides)
    }

    @Test
    fun sidesByPosition_fillsGapsWithEmptyText() {
        val card = cardWith(3 to "perro", 1 to "hund")
        assertEquals(listOf("hund", "", "perro"), card.sidesByPosition())
    }

    @Test
    fun labelsToPositional_fillsGapsWithEmptyText() {
        val labels = listOf(
            DeckSideLabel(deckId = 1, position = 3, label = "Spansk"),
            DeckSideLabel(deckId = 1, position = 1, label = "Dansk"),
        )
        assertEquals(listOf("Dansk", "", "Spansk"), labels.toPositional())
        assertEquals(emptyList<String>(), emptyList<DeckSideLabel>().toPositional())
    }

    @Test
    fun visibleSides_areSortedByPosition() {
        val card = cardWith(8 to "sidst", 1 to "først", 5 to "midt")
        assertEquals(listOf("først", "midt", "sidst"), card.visibleSides)
    }
}
