package dk.lishan.app.data.remote

import dk.lishan.app.data.auth.InMemoryTokenStore
import dk.lishan.app.data.auth.Tokens
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tester [HttpLishanApi] mod en falsk HTTP-server (MockWebServer), der svarer som beskrevet i
 * docs/app-api.md.
 */
class HttpLishanApiTest {

    private lateinit var server: MockWebServer
    private val now = 1_000_000_000L
    private val validTokens = Tokens("access-1", "refresh-1", expiresAtMillis = now + 3_600_000)
    private lateinit var tokenStore: InMemoryTokenStore
    private lateinit var api: HttpLishanApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokenStore = InMemoryTokenStore(validTokens)
        api = HttpLishanApi(
            baseUrl = server.url("/").toString(),
            clientId = "7",
            tokenStore = tokenStore,
            clock = { now },
        )
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private val courseJson = """[{"id": 60, "name": "arab17-19EA", "language": {"id": 3, "code": "EA"},
        "sides": [{"position": 1, "label": "dk", "rtl": false}, {"position": 2, "label": "eaar / eatr", "rtl": true}]}]"""

    private val tokenJson = """{"token_type": "Bearer", "expires_in": 3600, "access_token": "access-2", "refresh_token": "refresh-2"}"""

    @Test
    fun getCourses_sendsBearerToken_andReadsDocumentExample() = runBlocking {
        server.enqueue(json(courseJson))

        val course = api.getCourses().single()

        assertEquals("eaar / eatr", course.sides[1].label)
        assertTrue(course.sides[1].rtl)
        val request = server.takeRequest()
        assertEquals("/api/v1/courses", request.url.encodedPath)
        assertEquals("Bearer access-1", request.headers["Authorization"])
    }

    @Test
    fun getLessonCards_readsMultiLineAndArabicSides() = runBlocking {
        server.enqueue(
            json(
                """{"lesson": {"id": "1.50", "title": "(biṭā’a)", "part": "EA", "hash": "13d8"},
                "cards": [{"word_id": 473, "order": 1, "sides": ["farbror", "عَمٌّ\nʿamm", "", "", ""], "category": "nomen", "comment": "770"}]}"""
            )
        )

        val result = api.getLessonCards(60, "1.50")

        assertEquals("/api/v1/courses/60/lessons/1.50/cards", server.takeRequest().url.encodedPath)
        assertEquals(listOf("farbror", "عَمٌّ\nʿamm", "", "", ""), result.cards.single().sides)
        assertEquals("(biṭā’a)", result.lesson.title)
    }

    @Test
    fun on401_refreshesTokens_retries_andSavesNewRefreshToken() = runBlocking {
        server.enqueue(json("""{"error": "unauthenticated"}""", code = 401))
        server.enqueue(json(tokenJson))
        server.enqueue(json(courseJson))

        api.getCourses()

        server.takeRequest() // det første, afviste kald
        val refresh = server.takeRequest()
        assertEquals("/oauth/token", refresh.url.encodedPath)
        val form = refresh.body!!.utf8()
        assertTrue(form, form.contains("grant_type=refresh_token") && form.contains("refresh_token=refresh-1") && form.contains("client_id=7"))
        assertEquals("Bearer access-2", server.takeRequest().headers["Authorization"])
        assertEquals(Tokens("access-2", "refresh-2", now + 3_600_000), tokenStore.load())
    }

    @Test
    fun expiredToken_isRefreshedBeforeTheCall() = runBlocking {
        tokenStore.save(validTokens.copy(expiresAtMillis = now - 1))
        server.enqueue(json(tokenJson))
        server.enqueue(json(courseJson))

        api.getCourses()

        assertEquals("/oauth/token", server.takeRequest().url.encodedPath)
        assertEquals("Bearer access-2", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun rejectedRefresh_forgetsLogin_andThrowsNotLoggedIn() = runBlocking {
        server.enqueue(json("""{"error": "unauthenticated"}""", code = 401))
        server.enqueue(json("""{"error": "invalid_request"}""", code = 401))

        try {
            api.getCourses()
            fail("Burde have kastet NotLoggedInException")
        } catch (e: NotLoggedInException) {
            // Forventet.
        }
        assertNull(tokenStore.load())
    }

    @Test
    fun withoutTokens_throwsNotLoggedIn_withoutCallingServer() = runBlocking {
        tokenStore.clear()
        try {
            api.getCourses()
            fail("Burde have kastet NotLoggedInException")
        } catch (e: NotLoggedInException) {
            // Forventet.
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun serverError_becomesApiException_withErrorCode() = runBlocking {
        server.enqueue(json("""{"error": "not_found"}""", code = 404))
        try {
            api.getLessons(999)
            fail("Burde have kastet ApiException")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
            assertEquals("not_found", e.code)
        }
        assertEquals(validTokens, tokenStore.load())
    }

    @Test
    fun manyCallsGetting401_refreshOnlyOnce() = runBlocking {
        val refreshes = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath == "/oauth/token" -> {
                    refreshes.incrementAndGet()
                    json(tokenJson)
                }
                request.headers["Authorization"] == "Bearer access-2" -> json(courseJson)
                else -> json("""{"error": "unauthenticated"}""", code = 401)
            }
        }

        (1..5).map { async { api.getCourses() } }.awaitAll()

        assertEquals(1, refreshes.get())
    }

    @Test
    fun logout_callsServer_andForgetsLogin() = runBlocking {
        server.enqueue(json("""{"ok": true}"""))

        api.logout()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/logout", request.url.encodedPath)
        assertNull(tokenStore.load())
    }
}
