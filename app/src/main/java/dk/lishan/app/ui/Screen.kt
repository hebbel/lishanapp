package dk.lishan.app.ui

import androidx.compose.runtime.saveable.listSaver
import dk.lishan.app.model.Deck

/**
 * Appens skærme. `sealed` betyder, at der kun findes netop disse varianter,
 * så `when (screen)` kan tjekke, at alle skærme er håndteret.
 */
sealed interface Screen {
    /** Forsiden: mapper og decks, der ikke ligger i en mappe. */
    data object DeckList : Screen
    /** Indholdet af en mappe. */
    data class FolderDetail(val folderId: Long) : Screen
    /** [cardIndex]: hvilket kort decket åbner på. */
    data class DeckDetail(val deck: Deck, val cardIndex: Int = 0) : Screen
    /** [folderId]: mappen, det nye deck skal ligge i; `null` = forsiden. */
    data class NewDeck(val folderId: Long?) : Screen
    /** [labels]: deckets labels pr. position (plads 0 = side 1), til at udfylde formularen. */
    data class EditDeck(val deck: Deck, val labels: List<String>) : Screen
    data class NewCard(val deck: Deck, val labels: List<String>) : Screen
    /** [sides]: kortets nuværende tekst. [cardIndex] huskes, så man kommer tilbage til samme kort bagefter. */
    data class EditCard(
        val deck: Deck,
        val cardId: Long,
        val sides: List<String>,
        val cardIndex: Int,
        val labels: List<String>,
        val notes: String,
        val comment: String,
    ) : Screen
}

/** Listen, et deck i [folderId] ligger i: mappen, eller forsiden når [folderId] er `null`. */
internal fun listScreen(folderId: Long?): Screen =
    if (folderId == null) Screen.DeckList else Screen.FolderDetail(folderId)

/**
 * Fortæller `rememberSaveable`, hvordan en [Screen] gemmes, når telefonen drejes:
 * som en liste af tal og tekst (det kan Android gemme), med skærmens navn først og derefter
 * dens felter i fast rækkefølge. [restore] læser felterne tilbage i samme rækkefølge.
 */
internal val ScreenSaver = listSaver<Screen, Any?>(
    save = { screen ->
        fun deck(d: Deck) = listOf(d.id, d.name, d.folderId)
        when (screen) {
            Screen.DeckList -> listOf("DeckList")
            is Screen.FolderDetail -> listOf("FolderDetail", screen.folderId)
            is Screen.DeckDetail -> listOf("DeckDetail") + deck(screen.deck) + screen.cardIndex
            is Screen.NewDeck -> listOf("NewDeck", screen.folderId)
            is Screen.EditDeck -> listOf("EditDeck") + deck(screen.deck) + listOf(ArrayList(screen.labels))
            is Screen.NewCard -> listOf("NewCard") + deck(screen.deck) + listOf(ArrayList(screen.labels))
            is Screen.EditCard -> listOf("EditCard") + deck(screen.deck) + listOf(
                screen.cardId, ArrayList(screen.sides), screen.cardIndex, ArrayList(screen.labels),
                screen.notes, screen.comment,
            )
        }
    },
    restore = { saved ->
        // Læser felterne ét ad gangen, i den rækkefølge `save` skrev dem.
        val fields = saved.iterator()
        val kind = fields.next()
        fun long() = fields.next() as Long
        fun longOrNull() = fields.next() as Long?
        fun int() = fields.next() as Int
        fun string() = fields.next() as String
        @Suppress("UNCHECKED_CAST")
        fun strings() = fields.next() as List<String>
        fun deck() = Deck(id = long(), name = string(), folderId = longOrNull())

        when (kind) {
            "FolderDetail" -> Screen.FolderDetail(long())
            "DeckDetail" -> Screen.DeckDetail(deck(), cardIndex = int())
            "NewDeck" -> Screen.NewDeck(longOrNull())
            "EditDeck" -> Screen.EditDeck(deck(), labels = strings())
            "NewCard" -> Screen.NewCard(deck(), labels = strings())
            "EditCard" -> Screen.EditCard(
                deck(), cardId = long(), sides = strings(), cardIndex = int(), labels = strings(),
                notes = string(), comment = string(),
            )
            else -> Screen.DeckList
        }
    },
)
