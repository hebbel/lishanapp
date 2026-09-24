package dk.lishan.app.data.auth

import kotlinx.serialization.Serializable

/**
 * Brugerens login hos Lishan-serveren.
 *
 * [accessToken] sendes med hvert kald og holder kort tid (1 time). [refreshToken] bruges til at få
 * et nyt access token uden at logge ind igen (holder 90 dage og skiftes hver gang). [expiresAtMillis]
 * er tidspunktet (millisekunder siden 1970), hvor access token udløber.
 */
@Serializable
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
)

/** Stedet, hvor tokens gemmes mellem appens opstarter. */
interface TokenStore {
    /** De gemte tokens, eller `null`, hvis brugeren ikke er logget ind. */
    fun load(): Tokens?

    fun save(tokens: Tokens)

    /** Glemmer brugerens login. */
    fun clear()
}

/** Gemmer kun tokens i hukommelsen. Til tests. */
class InMemoryTokenStore(private var tokens: Tokens? = null) : TokenStore {
    @Synchronized override fun load(): Tokens? = tokens
    @Synchronized override fun save(tokens: Tokens) { this.tokens = tokens }
    @Synchronized override fun clear() { tokens = null }
}
