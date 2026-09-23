package com.example.lishan.model

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Et kort sammen med dets sider. Er ikke en tabel i sig selv: Room fylder den ud
 * ved at hente kortet (`@Embedded`) og alle sider, hvis `cardId` matcher kortets `id` (`@Relation`).
 */
data class FlashcardWithSides(
    @Embedded val card: Flashcard,
    @Relation(parentColumn = "id", entityColumn = "cardId")
    val sides: List<CardSide>,
) {
    /** Teksten på de sider, der har indhold, i rækkefølge. Tomme sider springes over. */
    val visibleSides: List<String>
        get() = sides.sortedBy { it.position }.map { it.text }.filter { it.isNotBlank() }
}
