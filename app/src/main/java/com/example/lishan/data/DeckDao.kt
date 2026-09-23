package com.example.lishan.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.example.lishan.model.Deck
import com.example.lishan.model.FlashcardWithSides
import kotlinx.coroutines.flow.Flow

/**
 * DAO = Data Access Object: de forespørgsler appen kan stille databasen.
 * Room genererer selve koden ud fra SQL'en i `@Query`.
 *
 * Funktionerne returnerer `Flow`, en strøm af værdier: den udsender listen med det samme
 * og igen, hver gang data i tabellen ændrer sig.
 */
@Dao
interface DeckDao {
    @Query("SELECT * FROM decks ORDER BY name")
    fun getDecks(): Flow<List<Deck>>

    // @Transaction: Room henter kort og sider i to forespørgsler; transaktionen sikrer, at de passer sammen.
    @Transaction
    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY id")
    fun getCards(deckId: Long): Flow<List<FlashcardWithSides>>
}
