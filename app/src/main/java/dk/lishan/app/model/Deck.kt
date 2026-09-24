package dk.lishan.app.model

import androidx.room.ColumnInfo
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
 *
 * Felterne om synkronisering bruges kun for decks, der er lektioner i et kursus fra serveren:
 * - [lessonId]: lektionens id på serveren, fx "12.05". `null` = brugeren har selv oprettet decket.
 * - [position]: lektionens plads i kursets rækkefølge.
 * - [serverHash]: lektionens fingeraftryk, som serveren sidst oplyste det.
 * - [downloadedHash]: fingeraftrykket, da kortene blev hentet. `null` = ikke hentet endnu.
 * - [removedOnServer]: lektionen findes ikke længere på serveren (decket beholdes i appen).
 * Tilstanden ([syncState]) beregnes ud fra dem og gemmes ikke for sig.
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
    // Unik: en lektion findes kun én gang i en kursusmappe. Egne decks (lessonId = NULL) er ikke omfattet.
    indices = [Index("folderId"), Index(value = ["folderId", "lessonId"], unique = true)],
)
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val folderId: Long? = null,
    val lessonId: String? = null,
    val position: Int? = null,
    val serverHash: String? = null,
    val downloadedHash: String? = null,
    @ColumnInfo(defaultValue = "0") val removedOnServer: Boolean = false,
) {
    /** Deckets tilstand i forhold til serveren. */
    val syncState: SyncState
        get() = when {
            lessonId == null -> SyncState.LOCAL
            downloadedHash == null -> SyncState.NOT_DOWNLOADED
            removedOnServer -> SyncState.REMOVED_ON_SERVER
            downloadedHash != serverHash -> SyncState.OUT_OF_SYNC
            else -> SyncState.SYNCED
        }
}

/** Et decks tilstand i forhold til serveren. */
enum class SyncState {
    /** Oprettet af brugeren; har intet med serveren at gøre. */
    LOCAL,
    /** En lektion fra serveren, hvis kort ikke er hentet endnu (vises gråt). */
    NOT_DOWNLOADED,
    /** Hentet og magen til serveren. */
    SYNCED,
    /** Hentet, men ændret på serveren siden. */
    OUT_OF_SYNC,
    /** Hentet, men lektionen findes ikke længere på serveren. */
    REMOVED_ON_SERVER,
}
