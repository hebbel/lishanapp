package dk.lishan.app.data.sync

import dk.lishan.app.data.remote.CourseSideDto
import dk.lishan.app.data.remote.LessonDto
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncRulesTest {

    @Test
    fun mergeText_serverWins_unlessServerIsEmpty() {
        assertEquals("ny", SyncRules.mergeText("gammel", "ny"))
        assertEquals("gammel", SyncRules.mergeText("gammel", ""))
        assertEquals("gammel", SyncRules.mergeText("gammel", "   "))
        assertEquals("gammel", SyncRules.mergeText("gammel", null))
        assertEquals("ny", SyncRules.mergeText("", " ny "))
    }

    @Test
    fun mergeSides_keepsLocalTextWhereServerIsEmpty_andKeepsPositions() {
        val local = listOf("hund", "min rettelse", "", "dog")
        val server = listOf("hund", "", "kalb", "")
        assertEquals(listOf("hund", "min rettelse", "kalb", "dog"), SyncRules.mergeSides(local, server))
    }

    @Test
    fun mergeSides_handlesDifferentLengths() {
        assertEquals(listOf("a", "b", "c"), SyncRules.mergeSides(listOf("a"), listOf("", "b", "c")))
        assertEquals(listOf("a", "b"), SyncRules.mergeSides(listOf("a", "b"), listOf("a")))
    }

    @Test
    fun labelsOf_keepsPositionsWhenSidesAreMissing() {
        val sides = listOf(CourseSideDto(3, "Engelsk"), CourseSideDto(1, "Dansk"))
        assertEquals(listOf("Dansk", "", "Engelsk"), SyncRules.labelsOf(sides))
        assertEquals(emptyList<String>(), SyncRules.labelsOf(emptyList()))
    }

    @Test
    fun deckTitle_combinesNumberAndName() {
        assertEquals("12.05 Familie", SyncRules.deckTitle(LessonDto(id = "12.05", title = "Familie", hash = "x")))
    }
}
