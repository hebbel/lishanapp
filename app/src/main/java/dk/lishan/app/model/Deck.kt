package dk.lishan.app.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Et deck: en navngiven samling af flashcards.
 * `@Entity` gør klassen til en tabel ("decks") i databasen.
 *
 * [folderId] er den mappe, decket ligger i; `null` betyder, at det ligger direkte på forsiden.
 * Slettes mappen, slettes dens decks også (CASCADE).
 */
@Entity(
    tableName = "decks",
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("folderId")],
)
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val folderId: Long? = null,
)
