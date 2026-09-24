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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dk.lishan.app.ui.cardform.CardFormScreen
import dk.lishan.app.ui.courses.CoursesScreen
import dk.lishan.app.ui.deck.DeckScreen
import dk.lishan.app.ui.deckform.DeckFormScreen
import dk.lishan.app.ui.decklist.DeckListScreen

/**
 * Roden af appens UI. Viser den skærm, [viewModel] siger, og sender brugerens tryk videre til den.
 * Al logik – hvilken skærm der kommer bagefter, og hvad der gemmes – ligger i [LishanViewModel].
 */
@Composable
fun LishanApp(viewModel: LishanViewModel) {
    val screen = viewModel.screen
    // Om dialogen "Ny mappe" er åben, er ren skærmtilstand og hører til her.
    var showNewFolderDialog by rememberSaveable { mutableStateOf(false) }

    // Androids tilbage-knap/-gestus går ét skridt tilbage i stedet for at lukke appen.
    BackHandler(enabled = screen != Screen.DeckList) { viewModel.back() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            when (screen) {
                Screen.DeckList -> AddMenuButton(
                    onNewDeck = { viewModel.startNewDeck(folderId = null) },
                    onNewFolder = { showNewFolderDialog = true },
                    onConnectCourse = viewModel::openCourses,
                )
                // Mapper kan ikke ligge i mapper, så inde i en mappe opretter "+" bare et deck.
                is Screen.FolderDetail -> AddButton { viewModel.startNewDeck(screen.folderId) }
                is Screen.DeckDetail -> AddButton { viewModel.startNewCard(screen.deck) }
                else -> {}
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding() // Giv plads til tastaturet, når det er åbent.

        when (screen) {
            Screen.DeckList, is Screen.FolderDetail -> {
                val folderId = (screen as? Screen.FolderDetail)?.folderId
                // `collectAsState` gør strømmen fra databasen til state, som Compose reagerer på.
                // `remember` sørger for, at vi ikke starter en ny forespørgsel, hver gang skærmen tegnes.
                val allFolders by viewModel.folders.collectAsState()
                val decks by remember(folderId) { viewModel.decksIn(folderId) }.collectAsState(initial = emptyList())
                val folder = folderId?.let { id -> allFolders.find { it.id == id } }
                val isCourse = folder?.courseId != null
                DeckListScreen(
                    // På forsiden vises mapperne; inde i en mappe kun dens decks og mappens navn som titel.
                    folders = if (folderId == null) allFolders else emptyList(),
                    decks = decks,
                    moveTargets = allFolders,
                    title = folderId?.let { folder?.name.orEmpty() },
                    onBack = viewModel::back,
                    onFolderClick = viewModel::openFolder,
                    onRenameFolder = { folder, name -> viewModel.renameFolder(folder, name) },
                    onDeleteFolder = { viewModel.deleteFolder(it) },
                    onDeckClick = viewModel::openDeck,
                    onEditDeck = { viewModel.startEditDeck(it) },
                    onMoveDeck = { deck, targetFolderId -> viewModel.moveDeck(deck, targetFolderId) },
                    onDeleteDeck = { viewModel.deleteDeck(it) },
                    onDownloadDeck = { viewModel.downloadLesson(it) },
                    downloadingDeckIds = viewModel.downloadingDeckIds,
                    status = if (!isCourse) null else when (val status = viewModel.courseStatus) {
                        CourseStatus.Refreshing -> "Henter lektionslisten …"
                        is CourseStatus.Failed -> status.message
                        CourseStatus.Idle -> null
                    },
                    onSimulateServerChange = if (isCourse && viewModel.canSimulateServerChanges) {
                        { viewModel.simulateServerChange(folder) }
                    } else null,
                    modifier = contentModifier,
                )
            }

            Screen.Courses -> {
                val allFolders by viewModel.folders.collectAsState()
                CoursesScreen(
                    state = viewModel.coursesState,
                    connectedCourseIds = allFolders.mapNotNull { it.courseId }.toSet(),
                    connectingCourseId = viewModel.connectingCourseId,
                    message = viewModel.coursesMessage,
                    onConnect = { viewModel.connectCourse(it) },
                    onRetry = { viewModel.loadCourses() },
                    onBack = viewModel::back,
                    modifier = contentModifier,
                )
            }

            is Screen.DeckDetail -> {
                val deck = screen.deck
                val cards by remember(deck.id) { viewModel.cards(deck.id) }.collectAsState(initial = emptyList())
                val labels by remember(deck.id) { viewModel.labels(deck.id) }.collectAsState(initial = emptyList())
                DeckScreen(
                    deckName = deck.name,
                    cards = cards,
                    labels = labels,
                    initialIndex = screen.cardIndex,
                    onBack = viewModel::back,
                    onEditCard = { card, index -> viewModel.startEditCard(deck, card, index, labels) },
                    onDeleteCard = { viewModel.deleteCard(it) },
                    modifier = contentModifier,
                )
            }

            is Screen.NewDeck -> DeckFormScreen(
                title = "Nyt deck",
                saveLabel = "Opret",
                onSave = { name, labels -> viewModel.saveNewDeck(name, labels, screen.folderId) },
                onCancel = viewModel::back,
                modifier = contentModifier,
            )

            is Screen.EditDeck -> DeckFormScreen(
                title = "Rediger deck",
                saveLabel = "Gem",
                initialName = screen.deck.name,
                initialLabels = screen.labels,
                onSave = { name, labels -> viewModel.saveDeck(screen.deck, name, labels) },
                onCancel = viewModel::back,
                modifier = contentModifier,
            )

            is Screen.NewCard -> CardFormScreen(
                title = "Nyt kort i ${screen.deck.name}",
                labels = screen.labels,
                onSave = { sides, notes, comment -> viewModel.saveNewCard(screen.deck, sides, notes, comment) },
                onCancel = viewModel::back,
                modifier = contentModifier,
            )

            is Screen.EditCard -> CardFormScreen(
                title = "Ret kort",
                initialSides = screen.sides,
                labels = screen.labels,
                initialNotes = screen.notes,
                initialComment = screen.comment,
                onSave = { sides, notes, comment -> viewModel.saveCard(screen, sides, notes, comment) },
                onCancel = viewModel::back,
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
                viewModel.createFolder(name)
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

/** "+" på forsiden: åbner en lille menu med valget mellem et nyt deck, en ny mappe og at forbinde et kursus. */
@Composable
private fun AddMenuButton(onNewDeck: () -> Unit, onNewFolder: () -> Unit, onConnectCourse: () -> Unit) {
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
            DropdownMenuItem(
                text = { Text("Forbind kursus") },
                onClick = {
                    expanded = false
                    onConnectCourse()
                },
            )
        }
    }
}
