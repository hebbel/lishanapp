package dk.lishan.app.data.remote

import kotlinx.coroutines.delay
import java.io.IOException
import java.security.MessageDigest

/**
 * Et falsk Lishan-API med et lille demokursus. Bruges, indtil serverens API er klar, og i tests.
 *
 * Det opfører sig som serveren skal: fingeraftrykket (hash) beregnes ud fra lektionens kort, så
 * det ændrer sig, når et kort ændres. Data ligger kun i hukommelsen og nulstilles, når appen genstartes.
 *
 * @param responseDelayMillis ventetid pr. kald, så appen opfører sig som over et rigtigt net.
 */
class FakeLishanApi(private val responseDelayMillis: Long = 600) : LishanApi {

    /** Sæt til `true` for at lade alle kald fejle, som hvis der ikke er net. */
    var offline = false

    private val course = CourseDto(
        id = DEMO_COURSE_ID,
        name = "Arabisk (demo)",
        language = LanguageDto(id = 1, code = "ARA"),
        sides = listOf(
            CourseSideDto(1, "Dansk"),
            CourseSideDto(2, "Arabisk", rtl = true),
            CourseSideDto(3, "Translitteration"),
            CourseSideDto(4, "Engelsk"),
        ),
    )

    private data class FakeLesson(val id: String, val title: String, val cards: MutableList<CardDto>)

    private val lessons = mutableListOf(
        FakeLesson(
            "1.01", "Hilsner",
            mutableListOf(
                card(101, 1, "hej", "مرحبا", "marhaban", "hello", category = "udtryk"),
                card(102, 2, "tak", "شكرا", "shukran", "thank you", category = "udtryk"),
                card(103, 3, "farvel", "مع السلامة", "ma'a as-salama", "goodbye", category = "udtryk"),
            ),
        ),
        FakeLesson(
            "1.02", "Familie",
            mutableListOf(
                card(201, 1, "far", "أب", "ab", "father", category = "substantiv", comment = "120"),
                card(202, 2, "mor", "أم", "umm", "mother", category = "substantiv", comment = "95"),
                // Ingen engelsk side: positionen bevares med en tom tekst.
                card(203, 3, "bror", "أخ", "akh", "", category = "substantiv"),
            ),
        ),
        FakeLesson(
            "2.01", "Farver",
            mutableListOf(
                card(301, 1, "rød", "أحمر", "ahmar", "red", category = "adjektiv"),
                card(302, 2, "blå", "أزرق", "azraq", "blue", category = "adjektiv"),
            ),
        ),
    )

    private var changeCount = 0

    override suspend fun getCourses(): List<CourseDto> = respond { listOf(course) }

    override suspend fun getLessons(courseId: Long): List<LessonDto> = respond {
        requireCourse(courseId)
        lessons.map { it.toDto() }
    }

    override suspend fun getLessonCards(courseId: Long, lessonId: String): LessonCardsDto = respond {
        requireCourse(courseId)
        val lesson = lessons.find { it.id == lessonId } ?: throw IOException("404: lektion $lessonId findes ikke")
        LessonCardsDto(lesson.toDto(), lesson.cards.sortedBy { it.order })
    }

    /**
     * Til test: ændrer noget "på serveren", så appen har noget at synkronisere.
     * Første gang tilføjes en ny lektion; hver gang ændres den engelske side af et kort i lektion 1.01.
     */
    fun simulateServerChange() {
        changeCount++
        if (lessons.none { it.id == "2.02" }) {
            lessons += FakeLesson(
                "2.02", "Tal",
                mutableListOf(
                    card(401, 1, "en", "واحد", "wahid", "one", category = "talord"),
                    card(402, 2, "to", "اثنان", "ithnan", "two", category = "talord"),
                ),
            )
        }
        val hello = lessons.first { it.id == "1.01" }
        val first = hello.cards.first()
        hello.cards[0] = first.copy(sides = first.sides.toMutableList().also { it[3] = "hello ($changeCount)" })
    }

    private suspend fun <T> respond(block: () -> T): T {
        delay(responseDelayMillis)
        if (offline) throw IOException("Ingen forbindelse til serveren")
        return block()
    }

    private fun requireCourse(courseId: Long) {
        if (courseId != course.id) throw IOException("404: kursus $courseId findes ikke")
    }

    private fun FakeLesson.toDto() = LessonDto(
        id = id,
        title = title,
        cardCount = cards.size,
        hash = hashOf(cards),
    )

    companion object {
        const val DEMO_COURSE_ID = 71L

        private fun card(
            wordId: Long, order: Int, vararg sides: String, category: String? = null, comment: String? = null,
        ) = CardDto(wordId = wordId, order = order, sides = sides.toList(), category = category, comment = comment)

        /** Fingeraftryk: SHA-256 af kortene i fast rækkefølge. Samme kort giver altid samme hash. */
        private fun hashOf(cards: List<CardDto>): String {
            val canonical = cards.sortedWith(compareBy({ it.order }, { it.wordId })).joinToString("\n") {
                listOf(it.wordId.toString(), it.order.toString(), it.sides.joinToString("\u001F"), it.category.orEmpty(), it.comment.orEmpty())
                    .joinToString("\u001E")
            }
            return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
