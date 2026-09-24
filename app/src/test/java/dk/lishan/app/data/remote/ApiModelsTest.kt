package dk.lishan.app.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tjekker, at appen kan læse JSON i det format, der er foreslået i docs/server-api-prompt.md. */
class ApiModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesCourseList() {
        val text = """[{"id": 71, "name": "ARA24-26", "language": {"id": 1, "code": "ARA"},
            "sides": [{"position": 1, "label": "Dansk", "rtl": false}, {"position": 2, "label": "Msasp", "rtl": true}]}]"""

        val course = json.decodeFromString<List<CourseDto>>(text).single()

        assertEquals(71L, course.id)
        assertEquals("ARA", course.language.code)
        assertEquals(listOf(true to "Msasp"), course.sides.filter { it.rtl }.map { it.rtl to it.label })
    }

    @Test
    fun parsesLessonCards_withEmptySidesAndMissingOptionalFields() {
        val text = """{"lesson": {"id": "12.05", "title": "Familie", "part": "1", "card_count": 1, "hash": "abc"},
            "cards": [{"word_id": 1234, "order": 3, "sides": ["far", "أب\nab", ""], "category": "substantiv"}]}"""

        val result = json.decodeFromString<LessonCardsDto>(text)

        assertEquals("abc", result.lesson.hash)
        val card = result.cards.single()
        assertEquals(1234L, card.wordId)
        assertEquals(listOf("far", "أب\nab", ""), card.sides)
        assertEquals(null, card.comment)
    }
}
