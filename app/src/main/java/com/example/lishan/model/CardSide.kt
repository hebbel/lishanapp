package com.example.lishan.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Én side af et flashcard. Et kort kan have op til [MAX_SIDES] sider;
 * [position] (1–8) bestemmer rækkefølgen.
 */
@Entity(
    tableName = "card_sides",
    foreignKeys = [
        ForeignKey(
            entity = Flashcard::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["cardId", "position"], unique = true)],
)
data class CardSide(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long = 0,
    val position: Int,
    val text: String,
) {
    companion object {
        const val MAX_SIDES = 8
    }
}
