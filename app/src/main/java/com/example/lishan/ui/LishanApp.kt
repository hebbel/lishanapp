package com.example.lishan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.lishan.data.DeckDao
import com.example.lishan.model.Deck
import com.example.lishan.ui.deck.DeckScreen
import com.example.lishan.ui.decklist.DeckListScreen

/**
 * Roden af appens UI. Bestemmer hvilken skærm der vises:
 * ingen deck valgt → listen over decks; deck valgt → decket kort.
 */
@Composable
fun LishanApp(dao: DeckDao) {
    var selectedDeck by remember { mutableStateOf<Deck?>(null) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val deck = selectedDeck
        if (deck == null) {
            // `remember` sørger for, at vi ikke starter en ny forespørgsel, hver gang skærmen tegnes.
            // `collectAsState` gør strømmen fra databasen til state, som Compose reagerer på.
            val decks by remember { dao.getDecks() }.collectAsState(initial = emptyList())
            DeckListScreen(
                decks = decks,
                onDeckClick = { selectedDeck = it },
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            // Androids tilbage-knap/-gestus går tilbage til listen i stedet for at lukke appen.
            BackHandler { selectedDeck = null }
            val cards by remember(deck.id) { dao.getCards(deck.id) }.collectAsState(initial = emptyList())
            DeckScreen(
                deckName = deck.name,
                cards = cards,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
