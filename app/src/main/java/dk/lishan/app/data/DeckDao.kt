package dk.lishan.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dk.lishan.app.model.CardSide
import dk.lishan.app.model.Deck
import dk.lishan.app.model.DeckSideLabel
import dk.lishan.app.model.Flashcard
import dk.lishan.app.model.FlashcardWithSides
import dk.lishan.app.model.Folder
import kotlinx.coroutines.flow.Flow

/**
 * DAO = Data Access Object: de forespørgsler appen kan stille databasen.
 * Room genererer selve koden ud fra annotationerne.
 *
 * Læsning returnerer `Flow`, en strøm af værdier: den udsender listen med det samme
 * og igen, hver gang data i tabellen ændrer sig. Skrivning er `suspend`-funktioner,
 * så de kører i baggrunden uden at fryse skærmen.
 *
 * Det er en `abstract class` (ikke et interface), så [insertCardWithSides] kan have en krop.
 */
@Dao
abstract class DeckDao {
    @Query("SELECT * FROM decks ORDER BY name")
    abstract fun getDecks(): Flow<List<Deck>>

    /**
     * Decks i en bestemt mappe, eller direkte på forsiden, når [folderId] er `null`.
     * `IS` i stedet for `=`, fordi `folderId = NULL` aldrig er sandt i SQL; `IS` sammenligner også NULL.
     */
    // Lektioner fra et kursus i kursets rækkefølge (position), derefter egne decks efter navn.
    @Query("SELECT * FROM decks WHERE folderId IS :folderId ORDER BY position IS NULL, position, name")
    abstract fun getDecksIn(folderId: Long?): Flow<List<Deck>>

    @Query("SELECT * FROM decks WHERE folderId IS :folderId")
    abstract suspend fun getDecksInOnce(folderId: Long?): List<Deck>

    /** Ændrer kun navnet, så decket beholder sine øvrige felter (fx om synkronisering). */
    @Query("UPDATE decks SET name = :name WHERE id = :deckId")
    abstract suspend fun renameDeck(deckId: Long, name: String)

    /** Flytter et deck til en mappe, eller til forsiden, når [folderId] er `null`. */
    @Query("UPDATE decks SET folderId = :folderId WHERE id = :deckId")
    abstract suspend fun moveDeck(deckId: Long, folderId: Long?)

    // --- Mapper ---

    @Query("SELECT * FROM folders ORDER BY name")
    abstract fun getFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :folderId")
    abstract fun getFolder(folderId: Long): Flow<Folder?>

    @Insert
    abstract suspend fun insertFolder(folder: Folder): Long

    @Update
    abstract suspend fun updateFolder(folder: Folder)

    /** Sletter en mappe. Dens decks og deres kort slettes automatisk med (ForeignKey CASCADE). */
    @Delete
    abstract suspend fun deleteFolder(folder: Folder)

    // @Transaction: Room henter kort og sider i to forespørgsler; transaktionen sikrer, at de passer sammen.
    @Transaction
    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY id")
    abstract fun getCards(deckId: Long): Flow<List<FlashcardWithSides>>

    /** Gemmer et nyt deck og returnerer dets nye id. */
    @Insert
    abstract suspend fun insertDeck(deck: Deck): Long

    @Insert
    abstract suspend fun insertCard(card: Flashcard): Long

    @Insert
    abstract suspend fun insertSides(sides: List<CardSide>)

    /** Gemmer ændringer i et eksisterende deck (fx nyt navn). Rækken findes via `id`. */
    @Update
    abstract suspend fun updateDeck(deck: Deck)

    /** Sletter et deck. Dets kort og deres sider slettes automatisk med (ForeignKey CASCADE). */
    @Delete
    abstract suspend fun deleteDeck(deck: Deck)

    /** Sletter et kort. Dets sider slettes automatisk med (ForeignKey CASCADE). */
    @Query("DELETE FROM flashcards WHERE id = :cardId")
    abstract suspend fun deleteCard(cardId: Long)

    @Query("DELETE FROM card_sides WHERE cardId = :cardId")
    abstract suspend fun deleteSides(cardId: Long)

    /**
     * Gemmer et nyt kort med dets sider. [sides] er teksten pr. position (plads 0 = side 1).
     * Tomme sider gemmes ikke, men de øvrige beholder deres position, så de passer til
     * deckets labels. Højst [CardSide.MAX_SIDES] positioner bruges.
     * [notes] og [comment] gemmes på selve kortet.
     * `@Transaction`: enten gemmes både kort og sider, eller ingenting.
     */
    @Transaction
    open suspend fun insertCardWithSides(
        deckId: Long,
        sides: List<String>,
        notes: String = "",
        comment: String = "",
    ) {
        val cardId = insertCard(Flashcard(deckId = deckId, notes = notes.trim(), comment = comment.trim()))
        insertSides(toCardSides(cardId, sides))
    }

    @Query("UPDATE flashcards SET notes = :notes, comment = :comment WHERE id = :cardId")
    abstract suspend fun updateNotesAndComment(cardId: Long, notes: String, comment: String)

    /**
     * Gemmer ændringer i et eksisterende kort: erstatter alle sider og sætter noter og kommentar.
     * Samme regler for sider som [insertCardWithSides]. Det er enklere at slette de gamle sider
     * og indsætte de nye end at finde ud af, hvilke der er ændret, tilføjet eller fjernet.
     */
    @Transaction
    open suspend fun updateCard(cardId: Long, sides: List<String>, notes: String, comment: String) {
        val newSides = toCardSides(cardId, sides)
        deleteSides(cardId)
        insertSides(newSides)
        updateNotesAndComment(cardId, notes.trim(), comment.trim())
    }

    private fun toCardSides(cardId: Long, sides: List<String>): List<CardSide> {
        val result = sides.take(CardSide.MAX_SIDES)
            .mapIndexed { i, text -> CardSide(cardId = cardId, position = i + 1, text = text.trim()) }
            .filter { it.text.isNotEmpty() }
        require(result.isNotEmpty()) { "Et kort skal have mindst én side med indhold" }
        return result
    }

    // --- Labels på et decks sider ---

    @Query("SELECT * FROM deck_side_labels WHERE deckId = :deckId ORDER BY position")
    abstract fun getLabels(deckId: Long): Flow<List<DeckSideLabel>>

    /** Som [getLabels], men henter én gang i stedet for at følge med i ændringer. */
    @Query("SELECT * FROM deck_side_labels WHERE deckId = :deckId ORDER BY position")
    abstract suspend fun getLabelsOnce(deckId: Long): List<DeckSideLabel>

    @Query("DELETE FROM deck_side_labels WHERE deckId = :deckId")
    abstract suspend fun deleteLabels(deckId: Long)

    @Insert
    abstract suspend fun insertLabels(labels: List<DeckSideLabel>)

    /** Opretter et deck med labels (plads 0 = side 1) i en mappe (eller på forsiden) og returnerer dets id. */
    @Transaction
    open suspend fun insertDeckWithLabels(name: String, labels: List<String>, folderId: Long? = null): Long {
        val deckId = insertDeck(Deck(name = name, folderId = folderId))
        insertLabels(toLabels(deckId, labels))
        return deckId
    }

    /** Gemmer et decks navn og erstatter alle dets labels. */
    @Transaction
    open suspend fun updateDeckWithLabels(deck: Deck, labels: List<String>) {
        renameDeck(deck.id, deck.name)
        setLabels(deck.id, labels)
    }

    /** Erstatter alle et decks labels (plads 0 = side 1). */
    @Transaction
    open suspend fun setLabels(deckId: Long, labels: List<String>) {
        deleteLabels(deckId)
        insertLabels(toLabels(deckId, labels))
    }

    /** Tomme labels gemmes ikke; de øvrige beholder deres position. */
    private fun toLabels(deckId: Long, labels: List<String>): List<DeckSideLabel> =
        labels.take(CardSide.MAX_SIDES)
            .mapIndexed { i, label -> DeckSideLabel(deckId = deckId, position = i + 1, label = label.trim()) }
            .filter { it.label.isNotEmpty() }

    // --- Synkronisering med serveren ---

    @Query("SELECT * FROM folders WHERE courseId = :courseId")
    abstract suspend fun getFolderByCourse(courseId: Long): Folder?

    @Query("SELECT * FROM folders WHERE id = :folderId")
    abstract suspend fun getFolderOnce(folderId: Long): Folder?

    /** Opdaterer en lektions deck efter serverens lektionsliste. */
    @Query(
        "UPDATE decks SET name = :name, position = :position, serverHash = :serverHash, removedOnServer = 0 " +
            "WHERE id = :deckId"
    )
    abstract suspend fun updateLessonDeck(deckId: Long, name: String, position: Int, serverHash: String)

    @Query("UPDATE decks SET removedOnServer = 1 WHERE id IN (:deckIds)")
    abstract suspend fun markRemovedOnServer(deckIds: List<Long>)

    /** Kortene er hentet: decket er nu magen til serveren, og brugerens rettelser er erstattet. */
    @Query("UPDATE decks SET downloadedHash = :hash, serverHash = :hash, locallyModified = 0 WHERE id = :deckId")
    abstract suspend fun markDownloaded(deckId: Long, hash: String)

    /** Brugeren har rettet i en hentet lektion. Gælder kun lektioner fra et kursus. */
    @Query("UPDATE decks SET locallyModified = 1 WHERE id = :deckId AND lessonId IS NOT NULL")
    abstract suspend fun markLocallyModified(deckId: Long)

    @Transaction
    @Query("SELECT * FROM flashcards WHERE id = :cardId")
    abstract suspend fun getCardOnce(cardId: Long): FlashcardWithSides?

    @Transaction
    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY id")
    abstract suspend fun getCardsOnce(deckId: Long): List<FlashcardWithSides>

    @Query("UPDATE flashcards SET category = :category, comment = :comment WHERE id = :cardId")
    abstract suspend fun updateCategoryAndComment(cardId: Long, category: String, comment: String)

    /**
     * Gemmer et nyt kort fra serveren (plads 0 i [sides] = side 1). Et kort uden nogen tekst gemmes ikke.
     */
    @Transaction
    open suspend fun insertSyncedCard(deckId: Long, wordId: Long, sides: List<String>, category: String, comment: String) {
        if (sides.none { it.isNotBlank() }) return
        val cardId = insertCard(Flashcard(deckId = deckId, wordId = wordId, category = category, comment = comment))
        insertSides(toCardSides(cardId, sides))
    }

    /**
     * Opdaterer et hentet kort med flettede værdier fra serveren. Noterne røres ikke.
     */
    @Transaction
    open suspend fun updateSyncedCard(cardId: Long, sides: List<String>, category: String, comment: String) {
        if (sides.any { it.isNotBlank() }) {
            val newSides = toCardSides(cardId, sides)
            deleteSides(cardId)
            insertSides(newSides)
        }
        updateCategoryAndComment(cardId, category, comment)
    }
}
