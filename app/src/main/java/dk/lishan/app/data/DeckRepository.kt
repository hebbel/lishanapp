package dk.lishan.app.data

import android.content.Intent
import androidx.room.withTransaction
import dk.lishan.app.data.auth.LishanAuth
import dk.lishan.app.data.remote.CourseDto
import dk.lishan.app.data.remote.FakeLishanApi
import dk.lishan.app.data.remote.LishanApi
import dk.lishan.app.data.remote.UserDto
import dk.lishan.app.data.sync.SyncRules
import dk.lishan.app.model.CardSide
import dk.lishan.app.model.Deck
import dk.lishan.app.model.FlashcardWithSides
import dk.lishan.app.model.Folder
import dk.lishan.app.model.toPositional
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Appens eneste indgang til data. Skærmene spørger repository'et og ved ikke, hvor dataene
 * kommer fra.
 *
 * Skærmene læser altid fra den lokale database. Serveren ([api]) bruges kun til at fylde den:
 * repository'et henter fra serveren, fletter med det, der ligger lokalt (efter [SyncRules]), og
 * gemmer i databasen. Derfor virker alt offline, når en lektion først er hentet.
 *
 * [auth] er login hos serveren; `null` betyder, at [api] ikke kræver login (det falske API).
 */
class DeckRepository(
    private val database: LishanDatabase,
    private val api: LishanApi,
    private val auth: LishanAuth? = null,
) {

    private val dao = database.deckDao()

    // --- Mapper ---

    val folders: Flow<List<Folder>> = dao.getFolders()

    suspend fun createFolder(name: String) {
        dao.insertFolder(Folder(name = name))
    }

    suspend fun renameFolder(folder: Folder, name: String) = dao.updateFolder(folder.copy(name = name))

    /** Sletter mappen og alle decks i den. */
    suspend fun deleteFolder(folder: Folder) = dao.deleteFolder(folder)

    // --- Decks ---

    /** Decks i en mappe, eller på forsiden når [folderId] er `null`. */
    fun decksIn(folderId: Long?): Flow<List<Deck>> = dao.getDecksIn(folderId)

    /** Opretter et deck og returnerer det, så man kan gå direkte ind i det. */
    suspend fun createDeck(name: String, labels: List<String>, folderId: Long?): Deck {
        val id = dao.insertDeckWithLabels(name, labels, folderId)
        return Deck(id = id, name = name, folderId = folderId)
    }

    /** Gemmer et decks navn og labels. */
    suspend fun updateDeck(deck: Deck, labels: List<String>) = dao.updateDeckWithLabels(deck, labels)

    /** Flytter et deck til en mappe, eller til forsiden når [folderId] er `null`. */
    suspend fun moveDeck(deck: Deck, folderId: Long?) = dao.moveDeck(deck.id, folderId)

    /** Sletter decket og alle dets kort. */
    suspend fun deleteDeck(deck: Deck) = dao.deleteDeck(deck)

    /** Deckets labels pr. position (plads 0 = side 1), opdateret når de ændres. */
    fun labels(deckId: Long): Flow<List<String>> = dao.getLabels(deckId).map { it.toPositional() }

    /** Som [labels], men hentet én gang, fx til at udfylde en formular. */
    suspend fun labelsNow(deckId: Long): List<String> = dao.getLabelsOnce(deckId).toPositional()

    // --- Kort ---

    fun cards(deckId: Long): Flow<List<FlashcardWithSides>> = dao.getCards(deckId)

    suspend fun createCard(deckId: Long, sides: List<String>, notes: String, comment: String) =
        dao.insertCardWithSides(deckId, sides, notes, comment)

    /**
     * Gemmer et kort. Er det et kort fra et kursus, og er siderne eller kommentaren ændret, markeres
     * decket som "ændret af dig". Noterne tæller ikke med: de er brugerens egne.
     */
    suspend fun updateCard(cardId: Long, sides: List<String>, notes: String, comment: String) {
        database.withTransaction {
            val before = dao.getCardOnce(cardId)
            dao.updateCard(cardId, sides, notes, comment)
            if (before != null && before.card.wordId != null &&
                (!SyncRules.sameSides(before.sidesByPosition(), sides) || before.card.comment != comment.trim())
            ) {
                dao.markLocallyModified(before.card.deckId)
            }
        }
    }

    /** Sletter et kort. Er det et kort fra et kursus, markeres decket som "ændret af dig". */
    suspend fun deleteCard(cardId: Long) {
        database.withTransaction {
            val before = dao.getCardOnce(cardId)
            dao.deleteCard(cardId)
            if (before?.card?.wordId != null) dao.markLocallyModified(before.card.deckId)
        }
    }

    // --- Synkronisering med serveren ---
    // Funktionerne kaster en undtagelse, hvis serveren ikke kan nås. Så er intet ændret lokalt.

    /** De kurser, brugeren kan forbinde til. */
    suspend fun availableCourses(): List<CourseDto> = api.getCourses()

    /**
     * Forbinder til et kursus: opretter kursets mappe (eller finder den, hvis kurset allerede er
     * forbundet) og henter listen over lektioner. Kortene hentes ikke; det vælger brugeren selv.
     */
    suspend fun connectCourse(course: CourseDto): Folder {
        val folder = dao.getFolderByCourse(course.id)
            ?: Folder(name = course.name, courseId = course.id).let { it.copy(id = dao.insertFolder(it)) }
        refreshCourse(folder, course)
        return folder
    }

    /**
     * Henter kursets lektionsliste igen og opdaterer mappen:
     * - nye lektioner bliver til decks, der ikke er hentet (vises gråt),
     * - navn, rækkefølge og fingeraftryk opdateres; en hentet lektion med nyt fingeraftryk er "ude af sync",
     * - lektioner, der er forsvundet fra serveren, fjernes, hvis de ikke er hentet, og markeres ellers,
     * - deckenes labels sættes efter kursets sider.
     */
    suspend fun refreshCourse(folder: Folder, knownCourse: CourseDto? = null) {
        val courseId = requireNotNull(folder.courseId) { "Mappen ${folder.name} er ikke et kursus" }
        val course = knownCourse ?: api.getCourses().find { it.id == courseId }
        val lessons = api.getLessons(courseId)
        val labels = course?.let { SyncRules.labelsOf(it.sides) }

        database.withTransaction {
            if (course != null && course.name != folder.name) dao.updateFolder(folder.copy(name = course.name))

            val existing = dao.getDecksInOnce(folder.id).filter { it.lessonId != null }.associateBy { it.lessonId }
            lessons.forEachIndexed { position, lesson ->
                val title = SyncRules.deckTitle(lesson)
                val deckId = existing[lesson.id]
                    ?.also { dao.updateLessonDeck(it.id, title, position, lesson.hash) }?.id
                    ?: dao.insertDeck(
                        Deck(name = title, folderId = folder.id, lessonId = lesson.id, position = position, serverHash = lesson.hash)
                    )
                if (labels != null) dao.setLabels(deckId, labels)
            }

            val onServer = lessons.map { it.id }.toSet()
            val gone = existing.values.filter { it.lessonId !in onServer }
            gone.filter { it.downloadedHash == null }.forEach { dao.deleteDeck(it) }
            dao.markRemovedOnServer(gone.filter { it.downloadedHash != null }.map { it.id })
        }
    }

    /**
     * Henter en lektions kort og fletter dem ind i decket efter [SyncRules]: serverens tekst vinder,
     * undtagen når den er tom; noterne røres aldrig; kort, brugeren selv har oprettet, røres ikke.
     * Kort fra kurset, som brugeren har slettet, kommer tilbage. Bagefter er decket ikke længere
     * "ændret af dig". Bruges første gang, til "ude af sync" og til "Gendan fra kurset".
     */
    suspend fun downloadLesson(deck: Deck) {
        val lessonId = requireNotNull(deck.lessonId) { "Decket ${deck.name} er ikke en lektion" }
        val courseId = deck.folderId?.let { dao.getFolderOnce(it) }?.courseId
            ?: error("Decket ${deck.name} ligger ikke i en kursusmappe")
        val result = api.getLessonCards(courseId, lessonId)

        database.withTransaction {
            val existing = dao.getCardsOnce(deck.id).filter { it.card.wordId != null }.associateBy { it.card.wordId }
            for (card in result.cards.sortedBy { it.order }) {
                val sides = card.sides.take(CardSide.MAX_SIDES)
                val local = existing[card.wordId]
                if (local == null) {
                    dao.insertSyncedCard(
                        deck.id, card.wordId, sides.map { it.trim() }, card.category.orEmpty().trim(), card.comment.orEmpty().trim(),
                    )
                } else {
                    dao.updateSyncedCard(
                        local.card.id,
                        SyncRules.mergeSides(local.sidesByPosition(), sides),
                        SyncRules.mergeText(local.card.category, card.category),
                        SyncRules.mergeText(local.card.comment, card.comment),
                    )
                }
            }
            dao.markDownloaded(deck.id, result.lesson.hash)
        }
    }

    // --- Login ---

    /** Om serveren kræver, at brugeren logger ind (ikke med det falske API). */
    val requiresLogin: Boolean get() = auth != null

    fun isLoggedIn(): Boolean = auth == null || auth.tokenStore.load() != null

    /** Beskeden, der åbner Lishans login-side i browseren. */
    fun loginIntent(): Intent = requireNotNull(auth) { "Login er ikke nødvendigt" }.loginService.createLoginIntent()

    /** Afslutter login med svaret fra browseren og gemmer tokens. */
    suspend fun completeLogin(result: Intent?) {
        val auth = requireNotNull(auth) { "Login er ikke nødvendigt" }
        auth.tokenStore.save(auth.loginService.completeLogin(result))
    }

    suspend fun currentUser(): UserDto = api.getCurrentUser()

    /** Logger ud på serveren og glemmer login'et i appen. Hentede lektioner bliver liggende. */
    suspend fun logout() = api.logout()

    /** Om der kan simuleres ændringer "på serveren" (kun med det falske API, til afprøvning). */
    val canSimulateServerChanges: Boolean get() = api is FakeLishanApi

    fun simulateServerChange() {
        (api as? FakeLishanApi)?.simulateServerChange()
    }
}
