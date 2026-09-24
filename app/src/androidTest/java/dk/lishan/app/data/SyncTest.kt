package dk.lishan.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dk.lishan.app.data.remote.FakeLishanApi
import dk.lishan.app.model.Deck
import dk.lishan.app.model.Folder
import dk.lishan.app.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tester synkroniseringen i [DeckRepository] mod en database i hukommelsen og [FakeLishanApi].
 */
@RunWith(AndroidJUnit4::class)
class SyncTest {

    private lateinit var db: LishanDatabase
    private lateinit var api: FakeLishanApi
    private lateinit var repository: DeckRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            LishanDatabase::class.java,
        ).build()
        api = FakeLishanApi(responseDelayMillis = 0)
        repository = DeckRepository(db, api)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun connect(): Folder = repository.connectCourse(repository.availableCourses().single())

    private suspend fun decksIn(folder: Folder): List<Deck> = repository.decksIn(folder.id).first()

    private suspend fun deck(folder: Folder, lessonId: String): Deck = decksIn(folder).single { it.lessonId == lessonId }

    @Test
    fun connectCourse_createsFolderWithLessonsNotDownloaded_inCourseOrder_withLabels() = runBlocking {
        val folder = connect()

        assertEquals(FakeLishanApi.DEMO_COURSE_ID, folder.courseId)
        val decks = decksIn(folder)
        assertEquals(listOf("1.01 Hilsner", "1.02 Familie", "2.01 Farver"), decks.map { it.name })
        assertTrue(decks.all { it.syncState == SyncState.NOT_DOWNLOADED })
        assertTrue(repository.cards(decks.first().id).first().isEmpty())
        assertEquals(listOf("Dansk", "Arabisk", "Translitteration", "Engelsk"), repository.labelsNow(decks.first().id))
    }

    @Test
    fun connectCourse_twice_doesNotDuplicate() = runBlocking {
        connect()
        val folder = connect()
        assertEquals(1, repository.folders.first().size)
        assertEquals(3, decksIn(folder).size)
    }

    @Test
    fun downloadLesson_createsCardsKeepingPositions_andMarksSynced() = runBlocking {
        val folder = connect()

        repository.downloadLesson(deck(folder, "1.02"))

        val family = deck(folder, "1.02")
        assertEquals(SyncState.SYNCED, family.syncState)
        val cards = repository.cards(family.id).first()
        assertEquals(3, cards.size)
        val brother = cards.single { it.card.wordId == 203L }
        // "bror" har ingen engelsk side; de øvrige beholder deres position.
        assertEquals(listOf("bror", "أخ", "akh"), brother.sidesByPosition())
        assertEquals("substantiv", brother.card.category)
        assertEquals("120", cards.single { it.card.wordId == 201L }.card.comment)
    }

    @Test
    fun serverChange_marksOutOfSync_andNewLessonAppearsNotDownloaded() = runBlocking {
        val folder = connect()
        repository.downloadLesson(deck(folder, "1.01"))

        api.simulateServerChange()
        repository.refreshCourse(folder)

        assertEquals(SyncState.OUT_OF_SYNC, deck(folder, "1.01").syncState)
        assertEquals(SyncState.NOT_DOWNLOADED, deck(folder, "2.02").syncState)
        assertEquals("2.02 Tal", decksIn(folder).last().name)
    }

    @Test
    fun resync_serverWins_exceptEmptyFields_notesUntouched_ownCardsKept() = runBlocking {
        val folder = connect()
        repository.downloadLesson(deck(folder, "1.01"))
        val hello = deck(folder, "1.01")
        val card = repository.cards(hello.id).first().single { it.card.wordId == 101L }

        // Brugeren retter kortet: skriver en note, retter translitterationen og en side, serveren ikke har.
        repository.updateCard(
            card.card.id, listOf("hej", "مرحبا", "min translitteration", "hello", "min ekstra side"),
            notes = "min note", comment = "",
        )
        // Brugeren laver sit eget kort i decket.
        repository.createCard(hello.id, listOf("godmorgen", "صباح الخير"), notes = "", comment = "")

        api.simulateServerChange() // ændrer den engelske side af "hej" på serveren
        repository.refreshCourse(folder)
        repository.downloadLesson(deck(folder, "1.01"))

        val cards = repository.cards(hello.id).first()
        val merged = cards.single { it.card.wordId == 101L }
        assertEquals(
            // Serveren vinder på side 3 og 4; side 5 er tom på serveren, så brugerens tekst bliver.
            listOf("hej", "مرحبا", "marhaban", "hello (1)", "min ekstra side"),
            merged.sidesByPosition(),
        )
        assertEquals("min note", merged.card.notes)
        assertTrue(cards.any { it.card.wordId == null && it.visibleSides.first() == "godmorgen" })
        assertEquals(SyncState.SYNCED, deck(folder, "1.01").syncState)
    }

    @Test
    fun offline_throws_andChangesNothing() = runBlocking {
        val folder = connect()
        api.offline = true

        try {
            repository.downloadLesson(deck(folder, "1.01"))
            fail("Burde have kastet en undtagelse")
        } catch (e: Exception) {
            // Forventet: ingen forbindelse.
        }

        assertEquals(SyncState.NOT_DOWNLOADED, deck(folder, "1.01").syncState)
        assertTrue(repository.cards(deck(folder, "1.01").id).first().isEmpty())
    }

    @Test
    fun editDeck_keepsSyncFields() = runBlocking {
        val folder = connect()
        repository.downloadLesson(deck(folder, "1.01"))
        val hello = deck(folder, "1.01")

        // Som efter en drejning: skærmen kender kun id, navn og mappe.
        repository.updateDeck(Deck(id = hello.id, name = "Hilsner (mine)", folderId = folder.id), listOf("Dansk"))

        val after = deck(folder, "1.01")
        assertEquals("Hilsner (mine)", after.name)
        assertEquals(SyncState.SYNCED, after.syncState)
    }
}
