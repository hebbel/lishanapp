package dk.lishan.app.model

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
    /** De sider, der har indhold, sorteret efter position. Tomme sider springes over. */
    val visibleCardSides: List<CardSide>
        get() = sides.sortedBy { it.position }.filter { it.text.isNotBlank() }

    /** Teksten på de sider, der har indhold, i rækkefølge. */
    val visibleSides: List<String>
        get() = visibleCardSides.map { it.text }

    /**
     * Teksten som en liste, hvor plads 0 er side 1, plads 1 er side 2 osv. Positioner uden
     * indhold er tomme strenge. Bruges til at udfylde formularen, når kortet rettes.
     */
    fun sidesByPosition(): List<String> {
        val size = sides.maxOfOrNull { it.position } ?: 0
        return List(size) { i -> sides.firstOrNull { it.position == i + 1 }?.text.orEmpty() }
    }
}
