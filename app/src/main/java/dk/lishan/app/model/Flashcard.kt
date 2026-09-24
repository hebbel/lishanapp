package dk.lishan.app.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ét flashcard i et deck. Selve indholdet ligger i [CardSide]-tabellen,
 * så et kort kan have mellem 1 og 8 sider.
 * `deckId` peger på det deck, kortet hører til. Slettes decket, slettes dets kort også (CASCADE).
 *
 * [notes] er brugerens egne noter; synkronisering rører dem aldrig.
 * [comment] er en kommentar til kortet (fra serverens `flashcomment`, når kurser synkroniseres).
 * [category] er en kategori fra serveren (`flashcat`, fx ordklasse).
 * Ingen af dem er en kortside. `defaultValue` er værdien i databasen for kort, der fandtes,
 * før kolonnerne blev tilføjet.
 *
 * [wordId] er glosens id på serveren; sammen med kurset genkender det kortet ved synkronisering.
 * `null` = brugeren har selv oprettet kortet.
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
    // Unik: en glose findes kun én gang i et deck. Egne kort (wordId = NULL) er ikke omfattet.
    indices = [Index("deckId"), Index(value = ["deckId", "wordId"], unique = true)],
)
data class Flashcard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long = 0,
    @ColumnInfo(defaultValue = "") val notes: String = "",
    @ColumnInfo(defaultValue = "") val comment: String = "",
    val wordId: Long? = null,
    @ColumnInfo(defaultValue = "") val category: String = "",
)
