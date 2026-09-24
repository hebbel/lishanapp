package dk.lishan.app.ui

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
import dk.lishan.app.model.Deck
import dk.lishan.app.model.FlashcardWithSides
import dk.lishan.app.model.Folder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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

    fun decksIn(folderId: Long?): Flow<List<Deck>> = repository.decksIn(folderId)

    fun cards(deckId: Long): Flow<List<FlashcardWithSides>> = repository.cards(deckId)

    fun labels(deckId: Long): Flow<List<String>> = repository.labels(deckId)

    // --- Navigation ---

    fun openFolder(folder: Folder) { screen = Screen.FolderDetail(folder.id) }

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
