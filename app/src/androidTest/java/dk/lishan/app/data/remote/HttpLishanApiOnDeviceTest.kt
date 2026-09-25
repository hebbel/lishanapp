package dk.lishan.app.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import dk.lishan.app.data.auth.InMemoryTokenStore
import dk.lishan.app.data.auth.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tester [HttpLishanApi] på en rigtig Android, hvor netværk på hovedtråden er forbudt.
 * Et stort svar, der kommer i bidder, skal kunne hentes, selvom kaldet startes fra hovedtråden
 * (som ViewModel'en gør).
 */
@RunWith(AndroidJUnit4::class)
class HttpLishanApiOnDeviceTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun largeChunkedResponse_canBeFetchedFromMainThread() = runBlocking {
        val lessons = (1..2000).joinToString(",", "[", "]") {
            """{"id": "${it / 100}.${"%02d".format(it % 100)}", "title": "Lektion $it – كلمات", "part": "EA", "card_count": 30, "hash": "${"a".repeat(64)}"}"""
        }
        server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "application/json").chunkedBody(lessons, 1024).build()
        )
        val api = HttpLishanApi(
            baseUrl = server.url("/").toString(),
            clientId = "7",
            tokenStore = InMemoryTokenStore(Tokens("a", "r", expiresAtMillis = Long.MAX_VALUE)),
        )

        val result = withContext(Dispatchers.Main) { api.getLessons(71) }

        assertEquals(2000, result.size)
    }
}
