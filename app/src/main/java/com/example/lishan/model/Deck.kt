package com.example.lishan.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Et deck: en navngiven samling af flashcards.
 * `@Entity` gør klassen til en tabel ("decks") i databasen.
 */
@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)
