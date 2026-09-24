package dk.lishan.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * En mappe på forsiden, der kan indeholde decks. Mapper kan ikke ligge i mapper.
 *
 * [courseId] er kursets id på Lishan-serveren, hvis mappen er et synkroniseret kursus;
 * `null` betyder, at brugeren selv har oprettet mappen.
 */
// Unik: et kursus bliver kun til én mappe. Egne mapper (courseId = NULL) er ikke omfattet.
@Entity(tableName = "folders", indices = [Index(value = ["courseId"], unique = true)])
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val courseId: Long? = null,
)
