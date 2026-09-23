package com.example.lishan.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.lishan.model.CardSide
import com.example.lishan.model.Deck
import com.example.lishan.model.Flashcard
import com.example.lishan.model.FlashcardWithSides
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
     * Gemmer et nyt kort med dets sider. Tomme sider springes over, og de øvrige
     * nummereres 1, 2, 3 … i rækkefølge. Højst [CardSide.MAX_SIDES] sider gemmes.
     * `@Transaction`: enten gemmes både kort og sider, eller ingenting.
     */
    @Transaction
    open suspend fun insertCardWithSides(deckId: Long, sides: List<String>) {
        val cardId = insertCard(Flashcard(deckId = deckId))
        insertSides(toCardSides(cardId, sides))
    }

    /**
     * Erstatter alle sider på et eksisterende kort. Samme regler som [insertCardWithSides].
     * Det er enklere at slette de gamle sider og indsætte de nye end at finde ud af,
     * hvilke der er ændret, tilføjet eller fjernet.
     */
    @Transaction
    open suspend fun updateCardSides(cardId: Long, sides: List<String>) {
        val newSides = toCardSides(cardId, sides)
        deleteSides(cardId)
        insertSides(newSides)
    }

    private fun toCardSides(cardId: Long, sides: List<String>): List<CardSide> {
        val texts = sides.map { it.trim() }.filter { it.isNotEmpty() }.take(CardSide.MAX_SIDES)
        require(texts.isNotEmpty()) { "Et kort skal have mindst én side med indhold" }
        return texts.mapIndexed { i, text -> CardSide(cardId = cardId, position = i + 1, text = text) }
    }
}
