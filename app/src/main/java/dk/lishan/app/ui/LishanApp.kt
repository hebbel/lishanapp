package dk.lishan.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import dk.lishan.app.data.DeckRepository
import dk.lishan.app.model.Deck
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
    /** Forsiden: mapper og decks, der ikke ligger i en mappe. */
    data object DeckList : Screen
    /** Indholdet af en mappe. */
    data class FolderDetail(val folderId: Long) : Screen
    /** [cardIndex]: hvilket kort decket åbner på. */
    data class DeckDetail(val deck: Deck, val cardIndex: Int = 0) : Screen
    /** [folderId]: mappen, det nye deck skal ligge i; `null` = forsiden. */
    data class NewDeck(val folderId: Long?) : Screen
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
        val notes: String,
        val comment: String,
    ) : Screen
}

/** Listen, et deck i [folderId] ligger i: mappen, eller forsiden når [folderId] er `null`. */
private fun listScreen(folderId: Long?): Screen =
    if (folderId == null) Screen.DeckList else Screen.FolderDetail(folderId)

/**
 * Fortæller `rememberSaveable`, hvordan en [Screen] gemmes, når telefonen drejes:
 * som en liste af tal og tekst (det kan Android gemme), med skærmens navn først og derefter
 * dens felter i fast rækkefølge. [restore] læser felterne tilbage i samme rækkefølge.
 */
internal val ScreenSaver = listSaver<Screen, Any?>(
    save = { screen ->
        fun deck(d: Deck) = listOf(d.id, d.name, d.folderId)
        when (screen) {
            Screen.DeckList -> listOf("DeckList")
            is Screen.FolderDetail -> listOf("FolderDetail", screen.folderId)
            is Screen.DeckDetail -> listOf("DeckDetail") + deck(screen.deck) + screen.cardIndex
            is Screen.NewDeck -> listOf("NewDeck", screen.folderId)
            is Screen.EditDeck -> listOf("EditDeck") + deck(screen.deck) + listOf(ArrayList(screen.labels))
            is Screen.NewCard -> listOf("NewCard") + deck(screen.deck) + listOf(ArrayList(screen.labels))
            is Screen.EditCard -> listOf("EditCard") + deck(screen.deck) + listOf(
                screen.cardId, ArrayList(screen.sides), screen.cardIndex, ArrayList(screen.labels),
                screen.notes, screen.comment,
            )
        }
    },
    restore = { saved ->
        // Læser felterne ét ad gangen, i den rækkefølge `save` skrev dem.
        val fields = saved.iterator()
        val kind = fields.next()
        fun long() = fields.next() as Long
        fun longOrNull() = fields.next() as Long?
        fun int() = fields.next() as Int
        fun string() = fields.next() as String
        @Suppress("UNCHECKED_CAST")
        fun strings() = fields.next() as List<String>
        fun deck() = Deck(id = long(), name = string(), folderId = longOrNull())

        when (kind) {
            "FolderDetail" -> Screen.FolderDetail(long())
            "DeckDetail" -> Screen.DeckDetail(deck(), cardIndex = int())
            "NewDeck" -> Screen.NewDeck(longOrNull())
            "EditDeck" -> Screen.EditDeck(deck(), labels = strings())
            "NewCard" -> Screen.NewCard(deck(), labels = strings())
            "EditCard" -> Screen.EditCard(
                deck(), cardId = long(), sides = strings(), cardIndex = int(), labels = strings(),
                notes = string(), comment = string(),
            )
            else -> Screen.DeckList
        }
    },
)

/**
 * Roden af appens UI. Holder styr på, hvilken skærm der vises, og forbinder skærmene
 * med data via [repository].
 */
@Composable
fun LishanApp(repository: DeckRepository) {
    // `rememberSaveable` (i stedet for `remember`) overlever, at skærmen genskabes, fx når telefonen drejes.
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.DeckList) }
    var showNewFolderDialog by rememberSaveable { mutableStateOf(false) }
    // Databasekald er `suspend`-funktioner og skal startes fra en coroutine.
    val scope = rememberCoroutineScope()

    // Androids tilbage-knap/-gestus går ét skridt tilbage i stedet for at lukke appen.
    BackHandler(enabled = screen != Screen.DeckList) {
        screen = when (val current = screen) {
            is Screen.DeckDetail -> listScreen(current.deck.folderId)
            is Screen.NewDeck -> listScreen(current.folderId)
            is Screen.EditDeck -> listScreen(current.deck.folderId)
            is Screen.NewCard -> Screen.DeckDetail(current.deck)
            is Screen.EditCard -> Screen.DeckDetail(current.deck, current.cardIndex)
            else -> Screen.DeckList
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            when (val current = screen) {
                Screen.DeckList -> AddMenuButton(
                    onNewDeck = { screen = Screen.NewDeck(folderId = null) },
                    onNewFolder = { showNewFolderDialog = true },
                )
                // Mapper kan ikke ligge i mapper, så inde i en mappe opretter "+" bare et deck.
                is Screen.FolderDetail -> AddButton { screen = Screen.NewDeck(current.folderId) }
                is Screen.DeckDetail -> AddButton {
                    scope.launch {
                        screen = Screen.NewCard(current.deck, repository.labelsNow(current.deck.id))
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
            Screen.DeckList, is Screen.FolderDetail -> {
                val folderId = (current as? Screen.FolderDetail)?.folderId
                // `remember` sørger for, at vi ikke starter en ny forespørgsel, hver gang skærmen tegnes.
                // `collectAsState` gør strømmen fra databasen til state, som Compose reagerer på.
                val allFolders by repository.folders.collectAsState(initial = emptyList())
                val decks by remember(folderId) { repository.decksIn(folderId) }.collectAsState(initial = emptyList())
                DeckListScreen(
                    // På forsiden vises mapperne; inde i en mappe kun dens decks og mappens navn som titel.
                    folders = if (folderId == null) allFolders else emptyList(),
                    decks = decks,
                    moveTargets = allFolders,
                    title = folderId?.let { id -> allFolders.find { it.id == id }?.name.orEmpty() },
                    onBack = { screen = Screen.DeckList },
                    onFolderClick = { screen = Screen.FolderDetail(it.id) },
                    onRenameFolder = { folder, name -> scope.launch { repository.renameFolder(folder, name) } },
                    onDeleteFolder = { folder -> scope.launch { repository.deleteFolder(folder) } },
                    onDeckClick = { screen = Screen.DeckDetail(it) },
                    onEditDeck = { deck ->
                        scope.launch {
                            // Hent deckets labels én gang, så formularen kan starte med dem udfyldt.
                            screen = Screen.EditDeck(deck, repository.labelsNow(deck.id))
                        }
                    },
                    onMoveDeck = { deck, targetFolderId -> scope.launch { repository.moveDeck(deck, targetFolderId) } },
                    onDeleteDeck = { deck -> scope.launch { repository.deleteDeck(deck) } },
                    modifier = contentModifier,
                )
            }

            is Screen.DeckDetail -> {
                val deck = current.deck
                val cards by remember(deck.id) { repository.cards(deck.id) }.collectAsState(initial = emptyList())
                val positionalLabels by remember(deck.id) { repository.labels(deck.id) }.collectAsState(initial = emptyList())
                DeckScreen(
                    deckName = deck.name,
                    cards = cards,
                    labels = positionalLabels,
                    initialIndex = current.cardIndex,
                    onBack = { screen = listScreen(deck.folderId) },
                    onEditCard = { card, index ->
                        screen = Screen.EditCard(
                            deck, card.card.id, card.sidesByPosition(), index, positionalLabels,
                            notes = card.card.notes, comment = card.card.comment,
                        )
                    },
                    onDeleteCard = { card -> scope.launch { repository.deleteCard(card.card.id) } },
                    modifier = contentModifier,
                )
            }

            is Screen.NewDeck -> DeckFormScreen(
                title = "Nyt deck",
                saveLabel = "Opret",
                onSave = { name, labels ->
                    scope.launch {
                        val deck = repository.createDeck(name, labels, current.folderId)
                        // Gå direkte ind i det nye deck, så man kan begynde at tilføje kort.
                        screen = Screen.DeckDetail(deck)
                    }
                },
                onCancel = { screen = listScreen(current.folderId) },
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
                        repository.updateDeck(renamed, labels)
                        screen = listScreen(renamed.folderId)
                    }
                },
                onCancel = { screen = listScreen(current.deck.folderId) },
                modifier = contentModifier,
            )

            is Screen.NewCard -> CardFormScreen(
                title = "Nyt kort i ${current.deck.name}",
                labels = current.labels,
                onSave = { sides, notes, comment ->
                    scope.launch {
                        repository.createCard(current.deck.id, sides, notes, comment)
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
                initialNotes = current.notes,
                initialComment = current.comment,
                onSave = { sides, notes, comment ->
                    scope.launch {
                        repository.updateCard(current.cardId, sides, notes, comment)
                        screen = Screen.DeckDetail(current.deck, current.cardIndex)
                    }
                },
                onCancel = { screen = Screen.DeckDetail(current.deck, current.cardIndex) },
                modifier = contentModifier,
            )
        }
    }

    if (showNewFolderDialog) {
        NameDialog(
            title = "Ny mappe",
            confirmLabel = "Opret",
            onConfirm = { name ->
                showNewFolderDialog = false
                scope.launch { repository.createFolder(name) }
            },
            onDismiss = { showNewFolderDialog = false },
        )
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) {
        Text("+", style = MaterialTheme.typography.headlineMedium)
    }
}

/** "+" på forsiden: åbner en lille menu med valget mellem et nyt deck og en ny mappe. */
@Composable
private fun AddMenuButton(onNewDeck: () -> Unit, onNewFolder: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Box: menuen placeres ud fra det, den ligger i, så den dukker op ved knappen.
    Box {
        AddButton { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Nyt deck") },
                onClick = {
                    expanded = false
                    onNewDeck()
                },
            )
            DropdownMenuItem(
                text = { Text("Ny mappe") },
                onClick = {
                    expanded = false
                    onNewFolder()
                },
            )
        }
    }
}
