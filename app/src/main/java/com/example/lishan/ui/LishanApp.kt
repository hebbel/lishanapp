package com.example.lishan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.lishan.data.DeckDao
import com.example.lishan.model.Deck
import com.example.lishan.model.FlashcardWithSides
import com.example.lishan.ui.cardform.CardFormScreen
import com.example.lishan.ui.deck.DeckScreen
import com.example.lishan.ui.deckform.DeckFormScreen
import com.example.lishan.ui.decklist.DeckListScreen
import kotlinx.coroutines.launch

/**
 * Appens skærme. `sealed` betyder, at der kun findes netop disse varianter,
 * så `when (screen)` kan tjekke, at alle skærme er håndteret.
 */
sealed interface Screen {
    data object DeckList : Screen
    /** [cardIndex]: hvilket kort decket åbner på. */
    data class DeckDetail(val deck: Deck, val cardIndex: Int = 0) : Screen
    data object NewDeck : Screen
    data class EditDeck(val deck: Deck) : Screen
    data class NewCard(val deck: Deck) : Screen
    /** [cardIndex] huskes, så man kommer tilbage til samme kort bagefter. */
    data class EditCard(val deck: Deck, val card: FlashcardWithSides, val cardIndex: Int) : Screen
}

/**
 * Roden af appens UI. Holder styr på, hvilken skærm der vises, og forbinder skærmene
 * med databasen.
 */
@Composable
fun LishanApp(dao: DeckDao) {
    var screen by remember { mutableStateOf<Screen>(Screen.DeckList) }
    // Databasekald er `suspend`-funktioner og skal startes fra en coroutine.
    val scope = rememberCoroutineScope()

    // Androids tilbage-knap/-gestus går ét skridt tilbage i stedet for at lukke appen.
    BackHandler(enabled = screen != Screen.DeckList) {
        screen = when (val current = screen) {
            is Screen.EditDeck -> Screen.DeckDetail(current.deck)
            is Screen.NewCard -> Screen.DeckDetail(current.deck)
            is Screen.EditCard -> Screen.DeckDetail(current.deck, current.cardIndex)
            else -> Screen.DeckList
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            when (val current = screen) {
                Screen.DeckList -> AddButton { screen = Screen.NewDeck }
                is Screen.DeckDetail -> AddButton { screen = Screen.NewCard(current.deck) }
                else -> {}
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding() // Giv plads til tastaturet, når det er åbent.

        when (val current = screen) {
            Screen.DeckList -> {
                // `remember` sørger for, at vi ikke starter en ny forespørgsel, hver gang skærmen tegnes.
                // `collectAsState` gør strømmen fra databasen til state, som Compose reagerer på.
                val decks by remember { dao.getDecks() }.collectAsState(initial = emptyList())
                DeckListScreen(
                    decks = decks,
                    onDeckClick = { screen = Screen.DeckDetail(it) },
                    modifier = contentModifier,
                )
            }

            is Screen.DeckDetail -> {
                val deck = current.deck
                val cards by remember(deck.id) { dao.getCards(deck.id) }.collectAsState(initial = emptyList())
                DeckScreen(
                    deckName = deck.name,
                    cards = cards,
                    initialIndex = current.cardIndex,
                    onRenameDeck = { screen = Screen.EditDeck(deck) },
                    onDeleteDeck = {
                        screen = Screen.DeckList
                        scope.launch { dao.deleteDeck(deck) }
                    },
                    onEditCard = { card, index -> screen = Screen.EditCard(deck, card, index) },
                    onDeleteCard = { card -> scope.launch { dao.deleteCard(card.card.id) } },
                    modifier = contentModifier,
                )
            }

            Screen.NewDeck -> DeckFormScreen(
                title = "Nyt deck",
                saveLabel = "Opret",
                onSave = { name ->
                    scope.launch {
                        val id = dao.insertDeck(Deck(name = name))
                        // Gå direkte ind i det nye deck, så man kan begynde at tilføje kort.
                        screen = Screen.DeckDetail(Deck(id = id, name = name))
                    }
                },
                onCancel = { screen = Screen.DeckList },
                modifier = contentModifier,
            )

            is Screen.EditDeck -> DeckFormScreen(
                title = "Omdøb deck",
                saveLabel = "Gem",
                initialName = current.deck.name,
                onSave = { name ->
                    val renamed = current.deck.copy(name = name)
                    scope.launch {
                        dao.updateDeck(renamed)
                        screen = Screen.DeckDetail(renamed)
                    }
                },
                onCancel = { screen = Screen.DeckDetail(current.deck) },
                modifier = contentModifier,
            )

            is Screen.NewCard -> CardFormScreen(
                title = "Nyt kort i ${current.deck.name}",
                onSave = { sides ->
                    scope.launch {
                        dao.insertCardWithSides(current.deck.id, sides)
                        screen = Screen.DeckDetail(current.deck)
                    }
                },
                onCancel = { screen = Screen.DeckDetail(current.deck) },
                modifier = contentModifier,
            )

            is Screen.EditCard -> CardFormScreen(
                title = "Ret kort",
                initialSides = current.card.visibleSides,
                onSave = { sides ->
                    scope.launch {
                        dao.updateCardSides(current.card.card.id, sides)
                        screen = Screen.DeckDetail(current.deck, current.cardIndex)
                    }
                },
                onCancel = { screen = Screen.DeckDetail(current.deck, current.cardIndex) },
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) {
        Text("+", style = MaterialTheme.typography.headlineMedium)
    }
}
