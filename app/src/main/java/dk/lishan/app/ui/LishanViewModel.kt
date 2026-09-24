package dk.lishan.app.ui

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dk.lishan.app.data.DeckRepository
import dk.lishan.app.data.remote.CourseDto
import dk.lishan.app.model.Deck
import dk.lishan.app.model.FlashcardWithSides
import dk.lishan.app.model.Folder
import dk.lishan.app.ui.courses.CoursesState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Holder styr på, hvilken skærm der vises, og udfører brugerens handlinger via [repository].
 *
 * En ViewModel overlever, at skærmen bygges op forfra (fx når telefonen drejes). Derfor kører
 * handlingerne i [viewModelScope]: en sletning eller (senere) en synkronisering bliver ikke
 * afbrudt, selvom telefonen drejes midt i den. Skærmen gemmes desuden i [SavedStateHandle],
 * så den også overlever, at Android lukker appen i baggrunden.
 *
 * `LishanApp` viser bare det, ViewModel'en siger, og sender brugerens tryk hertil.
 */
@OptIn(SavedStateHandleSaveableApi::class)
class LishanViewModel(
    private val repository: DeckRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Den skærm, der vises. Kan kun ændres herinde. */
    var screen: Screen by savedStateHandle.saveable(stateSaver = ScreenSaver) { mutableStateOf(Screen.DeckList) }
        private set

    /**
     * Alle mapper. `stateIn` holder den seneste liste klar, så den ikke skal hentes igen, når
     * skærmen bygges op forfra; forespørgslen stopper 5 sekunder efter, at ingen længere ser den.
     */
    val folders: StateFlow<List<Folder>> =
        repository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Synkronisering: tilstand, som skærmene viser ---

    /** Listen over kurser på serveren (skærmen "Forbind kursus"). */
    var coursesState: CoursesState by mutableStateOf(CoursesState.Loading)
        private set

    /** Kurset, der er ved at blive forbundet, hvis nogen. */
    var connectingCourseId: Long? by mutableStateOf(null)
        private set

    /** Besked på skærmen "Forbind kursus", fx hvis forbindelsen fejlede. */
    var coursesMessage: String? by mutableStateOf(null)
        private set

    /** Status for den kursusmappe, der er åben: henter lektionslisten, eller det fejlede. */
    var courseStatus: CourseStatus by mutableStateOf(CourseStatus.Idle)
        private set

    /** Lektioner (deck-id'er), hvis kort er ved at blive hentet. */
    var downloadingDeckIds: Set<Long> by mutableStateOf(emptySet())
        private set

    /** Om der kan simuleres ændringer på serveren (kun med det falske API). */
    val canSimulateServerChanges: Boolean = repository.canSimulateServerChanges

    init {
        // Er appen blevet genskabt på skærmen "Forbind kursus", skal listen hentes igen.
        if (screen == Screen.Courses) loadCourses()
    }

    fun decksIn(folderId: Long?): Flow<List<Deck>> = repository.decksIn(folderId)

    fun cards(deckId: Long): Flow<List<FlashcardWithSides>> = repository.cards(deckId)

    fun labels(deckId: Long): Flow<List<String>> = repository.labels(deckId)

    // --- Navigation ---

    /** Åbner en mappe. Er det et kursus, hentes lektionslisten igen fra serveren. */
    fun openFolder(folder: Folder) {
        screen = Screen.FolderDetail(folder.id)
        courseStatus = CourseStatus.Idle
        if (folder.courseId != null) refreshCourse(folder)
    }

    fun openDeck(deck: Deck) { screen = Screen.DeckDetail(deck) }

    fun startNewDeck(folderId: Long?) { screen = Screen.NewDeck(folderId) }

    /** Går ét skridt tilbage. Svarer til Androids tilbage-knap. */
    fun back() {
        screen = when (val current = screen) {
            is Screen.DeckDetail -> listScreen(current.deck.folderId)
            is Screen.NewDeck -> listScreen(current.folderId)
            is Screen.EditDeck -> listScreen(current.deck.folderId)
            is Screen.NewCard -> Screen.DeckDetail(current.deck)
            is Screen.EditCard -> Screen.DeckDetail(current.deck, current.cardIndex)
            else -> Screen.DeckList
        }
    }

    // --- Mapper ---

    fun createFolder(name: String) = viewModelScope.launch { repository.createFolder(name) }

    fun renameFolder(folder: Folder, name: String) = viewModelScope.launch { repository.renameFolder(folder, name) }

    fun deleteFolder(folder: Folder) = viewModelScope.launch { repository.deleteFolder(folder) }

    // --- Decks ---

    /** Hent deckets labels, så formularen kan starte med dem udfyldt, og åbn den. */
    fun startEditDeck(deck: Deck) = viewModelScope.launch {
        screen = Screen.EditDeck(deck, repository.labelsNow(deck.id))
    }

    fun saveNewDeck(name: String, labels: List<String>, folderId: Long?) = viewModelScope.launch {
        val deck = repository.createDeck(name, labels, folderId)
        // Gå direkte ind i det nye deck, så man kan begynde at tilføje kort.
        screen = Screen.DeckDetail(deck)
    }

    fun saveDeck(deck: Deck, name: String, labels: List<String>) = viewModelScope.launch {
        val renamed = deck.copy(name = name)
        repository.updateDeck(renamed, labels)
        screen = listScreen(renamed.folderId)
    }

    fun moveDeck(deck: Deck, folderId: Long?) = viewModelScope.launch { repository.moveDeck(deck, folderId) }

    fun deleteDeck(deck: Deck) = viewModelScope.launch { repository.deleteDeck(deck) }

    // --- Kort ---

    /** Hent deckets labels til feltnavnene og åbn formularen til et nyt kort. */
    fun startNewCard(deck: Deck) = viewModelScope.launch {
        screen = Screen.NewCard(deck, repository.labelsNow(deck.id))
    }

    fun startEditCard(deck: Deck, card: FlashcardWithSides, cardIndex: Int, labels: List<String>) {
        screen = Screen.EditCard(
            deck, card.card.id, card.sidesByPosition(), cardIndex, labels,
            notes = card.card.notes, comment = card.card.comment,
        )
    }

    fun saveNewCard(deck: Deck, sides: List<String>, notes: String, comment: String) = viewModelScope.launch {
        repository.createCard(deck.id, sides, notes, comment)
        screen = Screen.DeckDetail(deck)
    }

    fun saveCard(edit: Screen.EditCard, sides: List<String>, notes: String, comment: String) = viewModelScope.launch {
        repository.updateCard(edit.cardId, sides, notes, comment)
        screen = Screen.DeckDetail(edit.deck, edit.cardIndex)
    }

    fun deleteCard(card: FlashcardWithSides) = viewModelScope.launch { repository.deleteCard(card.card.id) }

    // --- Synkronisering ---

    fun openCourses() {
        screen = Screen.Courses
        coursesMessage = null
        loadCourses()
    }

    fun loadCourses() = viewModelScope.launch {
        coursesState = CoursesState.Loading
        coursesState = tryServer { CoursesState.Loaded(repository.availableCourses()) }
            ?: CoursesState.Failed("Kunne ikke hente kurserne. Tjek forbindelsen, og prøv igen.")
    }

    /** Forbinder til et kursus (eller åbner det, hvis det allerede er forbundet) og går ind i mappen. */
    fun connectCourse(course: CourseDto) = viewModelScope.launch {
        connectingCourseId = course.id
        coursesMessage = null
        val folder = tryServer { repository.connectCourse(course) }
        connectingCourseId = null
        if (folder != null) {
            courseStatus = CourseStatus.Idle
            screen = Screen.FolderDetail(folder.id)
        } else {
            coursesMessage = "Kunne ikke forbinde til ${course.name}. Prøv igen."
        }
    }

    /** Henter kursets lektionsliste igen. Uden net vises bare de data, appen allerede har. */
    fun refreshCourse(folder: Folder) = viewModelScope.launch {
        courseStatus = CourseStatus.Refreshing
        courseStatus = if (tryServer { repository.refreshCourse(folder) } != null) {
            CourseStatus.Idle
        } else {
            CourseStatus.Failed("Kunne ikke kontakte serveren. Viser de gemte data.")
        }
    }

    /** Henter en lektions kort (første gang eller igen, når den er "ude af sync"). */
    fun downloadLesson(deck: Deck) = viewModelScope.launch {
        if (deck.id in downloadingDeckIds) return@launch
        downloadingDeckIds = downloadingDeckIds + deck.id
        if (tryServer { repository.downloadLesson(deck) } == null) {
            courseStatus = CourseStatus.Failed("Kunne ikke hente \"${deck.name}\". Prøv igen, når der er forbindelse.")
        }
        downloadingDeckIds = downloadingDeckIds - deck.id
    }

    /** Til afprøvning med det falske API: ændrer noget "på serveren" og henter lektionslisten igen. */
    fun simulateServerChange(folder: Folder) {
        repository.simulateServerChange()
        refreshCourse(folder)
    }

    /**
     * Kører et kald til serveren og giver `null` tilbage, hvis det fejler (fx uden net).
     * En [CancellationException] sendes videre: den betyder, at opgaven skal stoppes, og må ikke
     * behandles som en almindelig fejl.
     */
    private inline fun <T> tryServer(block: () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("LishanViewModel", "Kald til serveren fejlede", e)
            null
        }

    companion object {
        /**
         * Fortæller Android, hvordan ViewModel'en oprettes: den skal have et repository, og
         * [createSavedStateHandle] giver den stedet, hvor skærmen gemmes.
         */
        fun factory(repository: DeckRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { LishanViewModel(repository, createSavedStateHandle()) }
        }
    }
}

/** Status for en kursusmappe, mens lektionslisten hentes. */
sealed interface CourseStatus {
    data object Idle : CourseStatus
    data object Refreshing : CourseStatus
    data class Failed(val message: String) : CourseStatus
}
