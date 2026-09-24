package dk.lishan.app.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Login med Lishans eget login (OAuth 2 Authorization Code + PKCE, se docs/app-api.md).
 *
 * Login foregår i telefonens browser: appen ser aldrig brugerens adgangskode. [createLoginIntent]
 * giver den besked, der åbner login-siden; når browseren sender brugeren tilbage til appen, bytter
 * [completeLogin] den modtagne kode til tokens.
 */
interface LoginService {
    fun createLoginIntent(): Intent

    /** Afslutter login med svaret fra browseren. Kaster [LoginFailedException], hvis det mislykkedes. */
    suspend fun completeLogin(result: Intent?): Tokens
}

class LoginFailedException(message: String) : Exception(message)

/**
 * [LoginService] med biblioteket AppAuth, som står for PKCE, browseren (Custom Tabs) og redirect'en
 * til `dk.lishan.app:/oauth2redirect`.
 */
class AppAuthLoginService(
    context: Context,
    baseUrl: String,
    private val clientId: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : LoginService {

    private val service = AuthorizationService(context.applicationContext)
    private val configuration = AuthorizationServiceConfiguration(
        Uri.parse("$baseUrl/oauth/authorize"),
        Uri.parse("$baseUrl/oauth/token"),
    )

    override fun createLoginIntent(): Intent {
        // AppAuth laver selv PKCE (code_verifier/code_challenge med S256) og state.
        val request = AuthorizationRequest.Builder(configuration, clientId, ResponseTypeValues.CODE, Uri.parse(REDIRECT_URI))
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    override suspend fun completeLogin(result: Intent?): Tokens {
        val response = result?.let { AuthorizationResponse.fromIntent(it) }
            ?: throw LoginFailedException(
                result?.let { AuthorizationException.fromIntent(it) }?.errorDescription ?: "Login blev afbrudt"
            )
        return suspendCancellableCoroutine { continuation ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
                val access = tokenResponse?.accessToken
                val refresh = tokenResponse?.refreshToken
                if (access != null && refresh != null) {
                    val expiresAt = tokenResponse.accessTokenExpirationTime ?: (clock() + 3_600_000)
                    continuation.resume(Tokens(access, refresh, expiresAt))
                } else {
                    continuation.resumeWithException(
                        LoginFailedException(exception?.errorDescription ?: "Serveren gav ingen tokens")
                    )
                }
            }
        }
    }

    companion object {
        const val REDIRECT_URI = "dk.lishan.app:/oauth2redirect"
    }
}

/** Det, repository'et skal bruge for at kunne logge brugeren ind og ud. */
class LishanAuth(val tokenStore: TokenStore, val loginService: LoginService)
