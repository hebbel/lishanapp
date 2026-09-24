package dk.lishan.app.data.sync

import dk.lishan.app.data.remote.CourseSideDto
import dk.lishan.app.data.remote.LessonDto
import dk.lishan.app.model.CardSide

/**
 * Reglerne for, hvordan data fra serveren flettes med det, der ligger i appen.
 * Rene funktioner uden database og net, så de kan testes direkte (se SyncRulesTest).
 */
object SyncRules {

    /** Serverens tekst vinder, undtagen når den er tom: så beholdes appens tekst. */
    fun mergeText(local: String, server: String?): String =
        if (server.isNullOrBlank()) local else server.trim()

    /**
     * Fletter et korts sider position for position (plads 0 = side 1) med [mergeText].
     * Højst [CardSide.MAX_SIDES] positioner.
     */
    fun mergeSides(local: List<String>, server: List<String>): List<String> {
        val size = maxOf(local.size, server.size).coerceAtMost(CardSide.MAX_SIDES)
        return List(size) { i -> mergeText(local.getOrElse(i) { "" }, server.getOrNull(i)) }
    }

    /** Kursets sider som labels pr. position (plads 0 = side 1); manglende positioner er tomme. */
    fun labelsOf(sides: List<CourseSideDto>): List<String> {
        val size = sides.maxOfOrNull { it.position }?.coerceAtMost(CardSide.MAX_SIDES) ?: 0
        return List(size) { i -> sides.firstOrNull { it.position == i + 1 }?.label.orEmpty() }
    }

    /** Deckets titel for en lektion, fx "12.05 Familie". */
    fun deckTitle(lesson: LessonDto): String = "${lesson.id} ${lesson.title}".trim()
}
