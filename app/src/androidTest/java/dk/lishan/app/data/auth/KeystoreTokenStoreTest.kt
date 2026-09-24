package dk.lishan.app.data.auth

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tester, at tokens gemmes krypteret og kan læses igen – og at ødelagte data bare glemmes. */
@RunWith(AndroidJUnit4::class)
class KeystoreTokenStoreTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: KeystoreTokenStore
    private val tokens = Tokens("access-æøå", "refresh-123", expiresAtMillis = 1_234_567_890L)

    // Testen bruger appens rigtige fil; tidligere tokens gemmes og lægges tilbage bagefter.
    private val prefs = context.getSharedPreferences("lishan_auth", Context.MODE_PRIVATE)
    private var saved: String? = null

    @Before
    fun setUp() {
        saved = prefs.getString("tokens", null)
        prefs.edit().remove("tokens").commit()
        store = KeystoreTokenStore(context)
    }

    @After
    fun tearDown() {
        prefs.edit().putString("tokens", saved).commit()
    }

    @Test
    fun saveAndLoad_roundTrip_andIsNotStoredInPlainText() {
        store.save(tokens)

        assertEquals(tokens, KeystoreTokenStore(context).load())
        val raw = prefs.getString("tokens", null).orEmpty()
        assertFalse(raw.contains("refresh-123"))
    }

    @Test
    fun clear_forgetsTokens() {
        store.save(tokens)
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun unreadableData_isForgotten_insteadOfCrashing() {
        prefs.edit().putString("tokens", "ikke-krypteret-data").commit()
        assertNull(store.load())
        assertNull(prefs.getString("tokens", null))
    }
}
