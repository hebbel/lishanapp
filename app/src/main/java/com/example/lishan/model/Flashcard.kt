package com.example.lishan.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ét flashcard: et spørgsmål på forsiden og svaret på bagsiden.
 * `deckId` peger på det deck, kortet hører til. Slettes decket, slettes dets kort også (CASCADE).
 */
@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("deckId")],
)
data class Flashcard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long = 0,
    val front: String,
    val back: String,
)
