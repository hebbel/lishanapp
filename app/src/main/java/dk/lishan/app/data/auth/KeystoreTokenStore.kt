package dk.lishan.app.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Gemmer tokens krypteret i appens egne indstillinger (SharedPreferences, filen `lishan_auth.xml`).
 *
 * Krypteringsnøglen (AES-256) ligger i Android Keystore: den laves og bruges inde i telefonens
 * sikre del og kan ikke læses ud af appen – heller ikke på en "rootet" telefon. Filen holdes ude
 * af backup (se res/xml/backup_rules.xml), for nøglen følger ikke med til en anden telefon.
 *
 * Kan de gemte tokens ikke dekrypteres (fx fordi nøglen er væk), glemmes de, og brugeren må
 * logge ind igen.
 */
class KeystoreTokenStore(context: Context) : TokenStore {

    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    private val json = Json

    @Synchronized
    override fun load(): Tokens? {
        val stored = prefs.getString(KEY_TOKENS, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val encrypted = bytes.copyOfRange(IV_SIZE, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            json.decodeFromString<Tokens>(cipher.doFinal(encrypted).decodeToString())
        } catch (e: Exception) {
            Log.w(TAG, "De gemte tokens kunne ikke læses; brugeren må logge ind igen", e)
            clear()
            null
        }
    }

    @Synchronized
    override fun save(tokens: Tokens) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key()) // Keystore vælger selv en ny, tilfældig IV.
        val encrypted = cipher.doFinal(json.encodeToString(tokens).encodeToByteArray())
        val stored = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        prefs.edit().putString(KEY_TOKENS, stored).apply()
    }

    @Synchronized
    override fun clear() {
        prefs.edit().remove(KEY_TOKENS).apply()
    }

    /** Henter nøglen fra Keystore, eller laver den første gang. */
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "KeystoreTokenStore"
        const val PREFS_FILE = "lishan_auth"
        const val KEY_TOKENS = "tokens"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "lishan_tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
