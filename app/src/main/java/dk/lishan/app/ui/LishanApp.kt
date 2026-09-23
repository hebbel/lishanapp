package dk.lishan.app.ui

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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dk.lishan.app.data.DeckDao
import dk.lishan.app.model.Deck
import dk.lishan.app.model.toPositional
import dk.lishan.app.ui.cardform.CardFormScreen
import dk.lishan.app.ui.deck.DeckScreen
import dk.lishan.app.ui.deckform.DeckFormScreen
import dk.lishan.app.ui.decklist.DeckListScreen
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
    /** [labels]: deckets labels pr. position (plads 0 = side 1), til at udfylde formularen. */
    data class EditDeck(val deck: Deck, val labels: List<String>) : Screen
    data class NewCard(val deck: Deck, val labels: List<String>) : Screen
    /** [sides]: kortets nuværende tekst. [cardIndex] huskes, så man kommer tilbage til samme kort bagefter. */
    data class EditCard(
        val deck: Deck,
        val cardId: Long,
        val sides: List<String>,
        val cardIndex: Int,
        val labels: List<String>,
    ) : Screen
}

/**
 * Fortæller `rememberSaveable`, hvordan en [Screen] gemmes, når telefonen drejes:
 * som en liste af tal og tekst (det kan Android gemme), med skærmens navn først.
 * [restore] bygger skærmen op igen fra listen.
 */
internal val ScreenSaver = listSaver<Screen, Any>(
    save = { screen ->
        when (screen) {
            Screen.DeckList -> listOf("DeckList")
            is Screen.DeckDetail -> listOf("DeckDetail", screen.deck.id, screen.deck.name, screen.cardIndex)
            Screen.NewDeck -> listOf("NewDeck")
            is Screen.EditDeck -> listOf("EditDeck", screen.deck.id, screen.deck.name, ArrayList(screen.labels))
            is Screen.NewCard -> listOf("NewCard", screen.deck.id, screen.deck.name, ArrayList(screen.labels))
            is Screen.EditCard -> listOf(
                "EditCard", screen.deck.id, screen.deck.name, screen.cardId, screen.cardIndex, ArrayList(screen.sides),
                ArrayList(screen.labels),
            )
        }
    },
    restore = { saved ->
        fun deck() = Deck(id = saved[1] as Long, name = saved[2] as String)

        @Suppress("UNCHECKED_CAST")
        fun strings(index: Int) = saved[index] as List<String>
        when (saved[0]) {
            "DeckDetail" -> Screen.DeckDetail(deck(), saved[3] as Int)
            "NewDeck" -> Screen.NewDeck
            "EditDeck" -> Screen.EditDeck(deck(), labels = strings(3))
            "NewCard" -> Screen.NewCard(deck(), labels = strings(3))
            "EditCard" -> Screen.EditCard(
                deck(),
                cardId = saved[3] as Long,
                sides = strings(5),
                cardIndex = saved[4] as Int,
                labels = strings(6),
            )
            else -> Screen.DeckList
        }
    },
)

/**
 * Roden af appens UI. Holder styr på, hvilken skærm der vises, og forbinder skærmene
 * med databasen.
 */
@Composable
fun LishanApp(dao: DeckDao) {
    // `rememberSaveable` (i stedet for `remember`) overlever, at skærmen genskabes, fx når telefonen drejes.
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.DeckList) }
    // Databasekald er `suspend`-funktioner og skal startes fra en coroutine.
    val scope = rememberCoroutineScope()

    // Androids tilbage-knap/-gestus går ét skridt tilbage i stedet for at lukke appen.
    BackHandler(enabled = screen != Screen.DeckList) {
        screen = when (val current = screen) {
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
                is Screen.DeckDetail -> AddButton {
                    scope.launch {
                        screen = Screen.NewCard(current.deck, dao.getLabelsOnce(current.deck.id).toPositional())
                    }
                }
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
                    onEditDeck = { deck ->
                        scope.launch {
                            // Hent deckets labels én gang, så formularen kan starte med dem udfyldt.
                            screen = Screen.EditDeck(deck, dao.getLabelsOnce(deck.id).toPositional())
                        }
                    },
                    onDeleteDeck = { deck -> scope.launch { dao.deleteDeck(deck) } },
                    modifier = contentModifier,
                )
            }

            is Screen.DeckDetail -> {
                val deck = current.deck
                val cards by remember(deck.id) { dao.getCards(deck.id) }.collectAsState(initial = emptyList())
                val labels by remember(deck.id) { dao.getLabels(deck.id) }.collectAsState(initial = emptyList())
                val positionalLabels = labels.toPositional()
                DeckScreen(
                    deckName = deck.name,
                    cards = cards,
                    labels = positionalLabels,
                    initialIndex = current.cardIndex,
                    onBack = { screen = Screen.DeckList },
                    onEditCard = { card, index ->
                        screen = Screen.EditCard(deck, card.card.id, card.sidesByPosition(), index, positionalLabels)
                    },
                    onDeleteCard = { card -> scope.launch { dao.deleteCard(card.card.id) } },
                    modifier = contentModifier,
                )
            }

            Screen.NewDeck -> DeckFormScreen(
                title = "Nyt deck",
                saveLabel = "Opret",
                onSave = { name, labels ->
                    scope.launch {
                        val id = dao.insertDeckWithLabels(name, labels)
                        // Gå direkte ind i det nye deck, så man kan begynde at tilføje kort.
                        screen = Screen.DeckDetail(Deck(id = id, name = name))
                    }
                },
                onCancel = { screen = Screen.DeckList },
                modifier = contentModifier,
            )

            is Screen.EditDeck -> DeckFormScreen(
                title = "Rediger deck",
                saveLabel = "Gem",
                initialName = current.deck.name,
                initialLabels = current.labels,
                onSave = { name, labels ->
                    val renamed = current.deck.copy(name = name)
                    scope.launch {
                        dao.updateDeckWithLabels(renamed, labels)
                        screen = Screen.DeckList
                    }
                },
                onCancel = { screen = Screen.DeckList },
                modifier = contentModifier,
            )

            is Screen.NewCard -> CardFormScreen(
                title = "Nyt kort i ${current.deck.name}",
                labels = current.labels,
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
                initialSides = current.sides,
                labels = current.labels,
                onSave = { sides ->
                    scope.launch {
                        dao.updateCardSides(current.cardId, sides)
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
