package com.example.lishan.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.lishan.ui.cardform.CardFormScreen
import com.example.lishan.ui.deckform.DeckFormScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tjekker, at formularerne tager imod al slags tekst uændret: dansk, emoji, andre skriftsystemer.
 * Teksten skrives direkte ind i felterne, som et tastatur ville gøre det.
 */
@RunWith(AndroidJUnit4::class)
class UnicodeInputTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cardForm_keepsAllCharacters() {
        var saved: List<String>? = null
        compose.setContent {
            CardFormScreen(title = "Nyt kort", onSave = { saved = it }, onCancel = {})
        }

        compose.onNodeWithText("Side 1").performTextInput(UNICODE_1)
        compose.onNodeWithText("Side 2").performTextInput(UNICODE_2)
        compose.onNodeWithText("Gem").performClick()

        assertEquals(listOf(UNICODE_1, UNICODE_2), saved)
    }

    @Test
    fun deckForm_keepsAllCharacters() {
        var savedName: String? = null
        var savedLabels: List<String>? = null
        compose.setContent {
            DeckFormScreen(
                title = "Nyt deck",
                saveLabel = "Opret",
                onSave = { name, labels ->
                    savedName = name
                    savedLabels = labels
                },
                onCancel = {},
            )
        }

        compose.onNodeWithText("Navn").performTextInput(UNICODE_1)
        compose.onNodeWithText("Side 1").performTextInput(UNICODE_2)
        compose.onNodeWithText("Opret").performClick()

        assertEquals(UNICODE_1, savedName)
        assertEquals(listOf(UNICODE_2, ""), savedLabels)
    }

    companion object {
        const val UNICODE_1 = "Æble, øl og å — ÆØÅ æøå"
        // Emoji (også sammensat familie-emoji), kinesisk, hebraisk, arabisk, é skrevet som e + accent.
        const val UNICODE_2 = "🐶🐱 👨‍👩‍👧 狗 כֶּלֶב كلب café"
    }
}
