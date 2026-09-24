package dk.lishan.app.data

import dk.lishan.app.model.Deck
import dk.lishan.app.model.FlashcardWithSides
import dk.lishan.app.model.Folder
import dk.lishan.app.model.toPositional
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Appens eneste indgang til data. Skærmene spørger repository'et og ved ikke, hvor dataene
 * kommer fra.
 *
 * I dag kommer alt fra den lokale database ([DeckDao]). Når kurser kan synkroniseres, kommer
 * serveren til her: repository'et henter fra serveren, fletter med det, der ligger lokalt, og
 * gemmer i databasen. Skærmene læser stadig kun fra databasen, så alt virker offline.
 */
class DeckRepository(private val dao: DeckDao) {

    // --- Mapper ---

    val folders: Flow<List<Folder>> = dao.getFolders()

    suspend fun createFolder(name: String) {
        dao.insertFolder(Folder(name = name))
    }

    suspend fun renameFolder(folder: Folder, name: String) = dao.updateFolder(folder.copy(name = name))

    /** Sletter mappen og alle decks i den. */
    suspend fun deleteFolder(folder: Folder) = dao.deleteFolder(folder)

    // --- Decks ---

    /** Decks i en mappe, eller på forsiden når [folderId] er `null`. */
    fun decksIn(folderId: Long?): Flow<List<Deck>> = dao.getDecksIn(folderId)

    /** Opretter et deck og returnerer det, så man kan gå direkte ind i det. */
    suspend fun createDeck(name: String, labels: List<String>, folderId: Long?): Deck {
        val id = dao.insertDeckWithLabels(name, labels, folderId)
        return Deck(id = id, name = name, folderId = folderId)
    }

    /** Gemmer et decks navn og labels. */
    suspend fun updateDeck(deck: Deck, labels: List<String>) = dao.updateDeckWithLabels(deck, labels)

    /** Flytter et deck til en mappe, eller til forsiden når [folderId] er `null`. */
    suspend fun moveDeck(deck: Deck, folderId: Long?) = dao.moveDeck(deck.id, folderId)

    /** Sletter decket og alle dets kort. */
    suspend fun deleteDeck(deck: Deck) = dao.deleteDeck(deck)

    /** Deckets labels pr. position (plads 0 = side 1), opdateret når de ændres. */
    fun labels(deckId: Long): Flow<List<String>> = dao.getLabels(deckId).map { it.toPositional() }

    /** Som [labels], men hentet én gang, fx til at udfylde en formular. */
    suspend fun labelsNow(deckId: Long): List<String> = dao.getLabelsOnce(deckId).toPositional()

    // --- Kort ---

    fun cards(deckId: Long): Flow<List<FlashcardWithSides>> = dao.getCards(deckId)

    suspend fun createCard(deckId: Long, sides: List<String>, notes: String, comment: String) =
        dao.insertCardWithSides(deckId, sides, notes, comment)

    suspend fun updateCard(cardId: Long, sides: List<String>, notes: String, comment: String) =
        dao.updateCard(cardId, sides, notes, comment)

    suspend fun deleteCard(cardId: Long) = dao.deleteCard(cardId)
}
