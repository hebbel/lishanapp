package com.example.lishan.ui.deck

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lishan.model.CardSide
import com.example.lishan.model.Flashcard
import com.example.lishan.model.FlashcardWithSides
import com.example.lishan.ui.flashcard.FlashcardView
import com.example.lishan.ui.theme.LishanTheme

/**
 * Viser kortene i ét deck, ét ad gangen. Swipe til venstre går til næste kort;
 * efter det sidste kort starter decket forfra.
 */
@Composable
fun DeckScreen(
    deckName: String,
    cards: List<FlashcardWithSides>,
    modifier: Modifier = Modifier,
) {
    var index by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            // Registrerer vandrette swipes over hele skærmen.
            .pointerInput(cards.size) {
                val threshold = 80.dp.toPx()
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        // Negativ = fingeren er trukket mod venstre.
                        if (dragged < -threshold && cards.isNotEmpty()) {
                            index = (index + 1) % cards.size
                        }
                    },
                    onHorizontalDrag = { _, dragAmount -> dragged += dragAmount },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(deckName, style = MaterialTheme.typography.headlineSmall)

        if (cards.isEmpty()) {
            Text("Dette deck har ingen kort endnu. Tryk på + for at tilføje et.")
            return@Column
        }

        val card = cards[index % cards.size]
        // `key` giver hvert kort sin egen FlashcardView, så et nyt kort altid starter på første side.
        key(card.card.id) {
            FlashcardView(sides = card.visibleSides)
        }
        Text("${index % cards.size + 1} / ${cards.size}")
    }
}

@Preview(showBackground = true)
@Composable
fun DeckScreenPreview() {
    LishanTheme {
        DeckScreen(
            deckName = "Dyr",
            cards = listOf(
                FlashcardWithSides(
                    card = Flashcard(id = 1),
                    sides = listOf(
                        CardSide(cardId = 1, position = 1, text = "hund"),
                        CardSide(cardId = 1, position = 2, text = "dog"),
                    ),
                ),
                FlashcardWithSides(
                    card = Flashcard(id = 2),
                    sides = listOf(
                        CardSide(cardId = 2, position = 1, text = "kat"),
                        CardSide(cardId = 2, position = 2, text = "cat"),
                    ),
                ),
            ),
        )
    }
}
