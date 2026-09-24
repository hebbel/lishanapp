package dk.lishan.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Serverens dataformat, som det er foreslået i docs/server-api-prompt.md. Klasserne svarer
 * præcis til JSON'en fra serveren; `@SerialName` kobler serverens feltnavne til Kotlins.
 * Når det rigtige API er beskrevet (docs/app-api.md), rettes eventuelle forskelle her.
 */

/** Et kursus, brugeren har adgang til. */
@Serializable
data class CourseDto(
    val id: Long,
    val name: String,
    val language: LanguageDto,
    /** Kursets kortsider. Positionerne bevares, også hvis nogle mangler. */
    val sides: List<CourseSideDto>,
)

@Serializable
data class LanguageDto(val id: Long, val code: String)

/** Én kortside i et kursus, fx position 2 = "Arabisk", skrevet fra højre mod venstre. */
@Serializable
data class CourseSideDto(val position: Int, val label: String, val rtl: Boolean = false)

/** En lektion i kursets lektionsliste – uden kort. [hash] ændrer sig, når lektionens kort ændrer sig. */
@Serializable
data class LessonDto(
    val id: String,
    val title: String,
    val part: String? = null,
    @SerialName("card_count") val cardCount: Int = 0,
    val hash: String,
)

/** Svaret, når én lektions kort hentes. */
@Serializable
data class LessonCardsDto(val lesson: LessonDto, val cards: List<CardDto>)

/** Et kort fra serveren. `sides[i]` er side `i + 1`; tomme sider er `""`. */
@Serializable
data class CardDto(
    @SerialName("word_id") val wordId: Long,
    val order: Int = 0,
    val sides: List<String>,
    val category: String? = null,
    val comment: String? = null,
)
