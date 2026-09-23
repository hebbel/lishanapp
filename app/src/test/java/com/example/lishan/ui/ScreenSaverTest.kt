package com.example.lishan.ui

import androidx.compose.runtime.saveable.SaverScope
import com.example.lishan.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tjekker, at alle skærme kommer uændret tilbage efter at være gemt og genskabt. */
class ScreenSaverTest {

    private val deck = Deck(id = 7, name = "Dyr")

    private fun roundTrip(screen: Screen): Screen? {
        val saved = with(ScreenSaver) { SaverScope { true }.save(screen) }!!
        return ScreenSaver.restore(saved)
    }

    @Test
    fun everyScreen_survivesSaveAndRestore() {
        val screens = listOf(
            Screen.DeckList,
            Screen.DeckDetail(deck, cardIndex = 3),
            Screen.NewDeck,
            Screen.EditDeck(deck),
            Screen.NewCard(deck),
            Screen.EditCard(deck, cardId = 42, sides = listOf("hund", "dog", "perro"), cardIndex = 1),
        )
        for (screen in screens) {
            assertEquals(screen, roundTrip(screen))
        }
    }
}
