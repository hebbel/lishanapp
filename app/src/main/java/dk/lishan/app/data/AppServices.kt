package dk.lishan.app.data

import android.content.Context
import dk.lishan.app.BuildConfig
import dk.lishan.app.data.auth.AppAuthLoginService
import dk.lishan.app.data.auth.KeystoreTokenStore
import dk.lishan.app.data.auth.LishanAuth
import dk.lishan.app.data.remote.FakeLishanApi
import dk.lishan.app.data.remote.HttpLishanApi

/**
 * Opretter appens delte objekter én gang: database, API og login, samlet i [DeckRepository].
 *
 * Er `lishan.clientId` sat i gradle.properties, bruges Lishan-serveren (adresse og klient fra
 * BuildConfig); ellers det falske API med demokurset, som ikke kræver login.
 */
object AppServices {

    @Volatile
    private var repository: DeckRepository? = null

    fun repository(context: Context): DeckRepository =
        repository ?: synchronized(this) {
            repository ?: create(context.applicationContext).also { repository = it }
        }

    private fun create(context: Context): DeckRepository {
        val database = LishanDatabase.getInstance(context)
        val clientId = BuildConfig.LISHAN_CLIENT_ID
        if (clientId.isBlank()) return DeckRepository(database, FakeLishanApi())

        val baseUrl = BuildConfig.LISHAN_BASE_URL
        val tokenStore = KeystoreTokenStore(context)
        return DeckRepository(
            database,
            HttpLishanApi(baseUrl, clientId, tokenStore),
            LishanAuth(tokenStore, AppAuthLoginService(context, baseUrl, clientId)),
        )
    }
}
