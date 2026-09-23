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

    /** Opretter et deck med labels (plads 0 = side 1) og returnerer dets id. */
    @Transaction
    open suspend fun insertDeckWithLabels(name: String, labels: List<String>): Long {
        val deckId = insertDeck(Deck(name = name))
        insertLabels(toLabels(deckId, labels))
        return deckId
    }

    /** Gemmer et decks navn og erstatter alle dets labels. */
    @Transaction
    open suspend fun updateDeckWithLabels(deck: Deck, labels: List<String>) {
        updateDeck(deck)
        deleteLabels(deck.id)
        insertLabels(toLabels(deck.id, labels))
    }

    /** Tomme labels gemmes ikke; de øvrige beholder deres position. */
    private fun toLabels(deckId: Long, labels: List<String>): List<DeckSideLabel> =
        labels.take(CardSide.MAX_SIDES)
            .mapIndexed { i, label -> DeckSideLabel(deckId = deckId, position = i + 1, label = label.trim()) }
            .filter { it.label.isNotEmpty() }
}
