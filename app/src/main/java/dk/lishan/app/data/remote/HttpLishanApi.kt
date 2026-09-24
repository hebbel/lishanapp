package dk.lishan.app.data.remote

import dk.lishan.app.data.auth.TokenStore
import dk.lishan.app.data.auth.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Det rigtige Lishan-API over HTTP (se docs/app-api.md).
 *
 * Hvert kald sendes med brugerens access token. Er det udløbet, eller svarer serveren `401`,
 * fornyes det med refresh token, og kaldet prøves én gang til. Afviser serveren fornyelsen,
 * glemmes login'et, og der kastes [NotLoggedInException]. Uden net kastes en [java.io.IOException],
 * og tokens beholdes.
 *
 * @param baseUrl fx `https://lishan.fak.dk`.
 * @param clientId appens OAuth-klient på serveren.
 * @param clock det aktuelle tidspunkt i millisekunder; kan sættes i tests.
 */
class HttpLishanApi(
    baseUrl: String,
    private val clientId: String,
    private val tokenStore: TokenStore,
    private val client: OkHttpClient = OkHttpClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) : LishanApi {

    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Kun én fornyelse ad gangen: serveren giver et nyt refresh token ved hver fornyelse og gør
     * det gamle ugyldigt, så to samtidige fornyelser ville ødelægge login'et.
     */
    private val refreshLock = Mutex()

    override suspend fun getCourses(): List<CourseDto> =
        get(listOf("api", "v1", "courses"), ListSerializer(CourseDto.serializer()))

    override suspend fun getLessons(courseId: Long): List<LessonDto> =
        get(
            listOf("api", "v1", "courses", courseId.toString(), "lessons"),
            ListSerializer(LessonDto.serializer()),
        )

    override suspend fun getLessonCards(courseId: Long, lessonId: String): LessonCardsDto =
        get(listOf("api", "v1", "courses", courseId.toString(), "lessons", lessonId, "cards"), LessonCardsDto.serializer())

    override suspend fun getCurrentUser(): UserDto = get(listOf("api", "v1", "me"), UserDto.serializer())

    override suspend fun logout() {
        try {
            authorized { token ->
                Request.Builder().url(url(listOf("api", "v1", "logout"))).bearer(token)
                    .post(ByteArray(0).toRequestBody()).build()
            }.close()
        } finally {
            // Login'et glemmes i appen, også hvis serveren ikke kunne nås.
            tokenStore.clear()
        }
    }

    // --- Hjælpefunktioner ---

    private suspend fun <T> get(segments: List<String>, deserializer: DeserializationStrategy<T>): T {
        val response = authorized { token -> Request.Builder().url(url(segments)).bearer(token).get().build() }
        return response.use { json.decodeFromString(deserializer, it.body.string()) }
    }

    /**
     * Sender et kald med brugerens access token, som [buildRequest] sætter på. Fornyer tokens og
     * prøver igen ved `401`. Giver svaret tilbage, hvis det lykkedes; kaster ellers.
     */
    private suspend fun authorized(buildRequest: (accessToken: String) -> Request): Response {
        var tokens = tokenStore.load() ?: throw NotLoggedInException()
        if (isExpired(tokens)) tokens = refresh(tokens)

        var response = execute(buildRequest(tokens.accessToken))
        if (response.code == 401) {
            response.close()
            tokens = refresh(tokens)
            response = execute(buildRequest(tokens.accessToken))
            if (response.code == 401) {
                response.close()
                tokenStore.clear()
                throw NotLoggedInException()
            }
        }
        if (!response.isSuccessful) throw response.use { apiError(it) }
        return response
    }

    /** Fornyer tokens med refresh token og gemmer de nye. */
    private suspend fun refresh(old: Tokens): Tokens = refreshLock.withLock {
        // Har et andet kald allerede fornyet dem, mens vi ventede, bruges de nye.
        val current = tokenStore.load() ?: throw NotLoggedInException()
        if (current.accessToken != old.accessToken && !isExpired(current)) return current

        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("refresh_token", current.refreshToken)
            .build()
        val request = Request.Builder().url(url(listOf("oauth", "token"))).post(body).build()
        execute(request).use { response ->
            when {
                response.isSuccessful -> {
                    val result = json.decodeFromString(TokenResponseDto.serializer(), response.body.string())
                    val fresh = Tokens(
                        accessToken = result.accessToken,
                        refreshToken = result.refreshToken,
                        expiresAtMillis = clock() + result.expiresIn * 1000,
                    )
                    tokenStore.save(fresh)
                    fresh
                }
                // Serveren afviser refresh token (udløbet eller tilbagekaldt): brugeren skal logge ind igen.
                response.code in 400..499 -> {
                    tokenStore.clear()
                    throw NotLoggedInException()
                }
                else -> throw apiError(response)
            }
        }
    }

    /** Et token regnes for udløbet lidt før tid, så det ikke udløber undervejs. */
    private fun isExpired(tokens: Tokens) = clock() >= tokens.expiresAtMillis - EXPIRY_MARGIN_MILLIS

    private suspend fun execute(request: Request): Response =
        withContext(Dispatchers.IO) { client.newCall(request).execute() }

    private fun url(segments: List<String>): HttpUrl =
        base.newBuilder().apply { segments.forEach { addPathSegment(it) } }.build()

    private fun Request.Builder.bearer(token: String) = header("Authorization", "Bearer $token")

    private fun apiError(response: Response): ApiException {
        val code = runCatching { json.decodeFromString(ErrorDto.serializer(), response.body.string()).error }
            .getOrDefault("")
        return ApiException(response.code, code)
    }

    private companion object {
        const val EXPIRY_MARGIN_MILLIS = 60_000L
    }
}
