package dk.lishan.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Afprøver, at kotlinx.serialization virker med projektets opsætning (AGP's indbyggede Kotlin).
 * Virker compiler-plugin'et ikke, findes `serializer()` ikke, og filen kan ikke oversættes.
 *
 * Klasserne her er kun til testen; de rigtige klasser til serverens svar laves, når API'et findes.
 */
class JsonTest {

    @Serializable
    private data class TestCard(
        @SerialName("word_id") val wordId: Long,
        val sides: List<String>,
        val comment: String? = null,
    )

    // ignoreUnknownKeys: serveren må sende felter, som appen ikke kender (endnu).
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun roundTrip_keepsAllValues() {
        val card = TestCard(wordId = 42, sides = listOf("كلب", "hund", "dog"), comment = "Æbler & øl")

        val text = json.encodeToString(card)

        assertEquals(card, json.decodeFromString<TestCard>(text))
    }

    @Test
    fun decode_ignoresUnknownFieldsAndHandlesNull() {
        val text = """{"word_id": 7, "sides": ["kat", "cat"], "comment": null, "frekvensid": 120}"""

        val card = json.decodeFromString<TestCard>(text)

        assertEquals(7L, card.wordId)
        assertEquals(listOf("kat", "cat"), card.sides)
        assertNull(card.comment)
    }
}
