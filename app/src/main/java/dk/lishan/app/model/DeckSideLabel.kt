package dk.lishan.app.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Et navn på én sideposition i et deck, fx "Engelsk" for side 2 i decket "Dyr".
 * Labels er metadata på decket: alle kort i decket bruger de samme labels.
 * Kun positioner med et label gemmes. Slettes decket, slettes dets labels også (CASCADE).
 */
@Entity(
    tableName = "deck_side_labels",
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["deckId", "position"], unique = true)],
)
data class DeckSideLabel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long = 0,
    val position: Int,
    val label: String,
)

/**
 * Labels som en liste, hvor plads 0 er side 1, plads 1 er side 2 osv.
 * Positioner uden label er tomme strenge; listen er så lang som den højeste position med et label.
 */
fun List<DeckSideLabel>.toPositional(): List<String> {
    val size = maxOfOrNull { it.position } ?: 0
    return List(size) { i -> firstOrNull { it.position == i + 1 }?.label.orEmpty() }
}
