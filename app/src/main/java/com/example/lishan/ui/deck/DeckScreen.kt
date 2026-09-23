package com.example.lishan.ui.deck

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
 *
 * Knapperne til at omdøbe/slette decket og rette/slette det viste kort melder bare tilbage
 * via callbacks; sletning skal først bekræftes i en dialog.
 *
 * @param initialIndex det kort, skærmen starter på (fx når man kommer tilbage efter at have rettet et kort).
 */
@Composable
fun DeckScreen(
    deckName: String,
    cards: List<FlashcardWithSides>,
    onRenameDeck: () -> Unit,
    onDeleteDeck: () -> Unit,
    onEditCard: (card: FlashcardWithSides, index: Int) -> Unit,
    onDeleteCard: (card: FlashcardWithSides) -> Unit,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
) {
    var index by remember { mutableIntStateOf(initialIndex) }
    // Hvilken bekræftelsesdialog der er åben, hvis nogen.
    var confirmDeleteDeck by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<FlashcardWithSides?>(null) }

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
        Row {
            TextButton(onClick = onRenameDeck) { Text("Omdøb") }
            TextButton(onClick = { confirmDeleteDeck = true }) { Text("Slet deck") }
        }

        if (cards.isEmpty()) {
            Text("Dette deck har ingen kort endnu. Tryk på + for at tilføje et.")
        } else {
            val position = index % cards.size
            val card = cards[position]
            // `key` giver hvert kort sin egen FlashcardView, så et nyt kort altid starter på første side.
            key(card.card.id) {
                FlashcardView(sides = card.visibleSides)
            }
            Text("${position + 1} / ${cards.size}")
            Row {
                TextButton(onClick = { onEditCard(card, position) }) { Text("Ret kort") }
                TextButton(onClick = { cardToDelete = card }) { Text("Slet kort") }
            }
        }
    }

    if (confirmDeleteDeck) {
        ConfirmDeleteDialog(
            title = "Slet deck?",
            text = if (cards.isEmpty()) {
                "\"$deckName\" slettes. Det kan ikke fortrydes."
            } else {
                "\"$deckName\" og alle dets ${cards.size} kort slettes. Det kan ikke fortrydes."
            },
            onConfirm = {
                confirmDeleteDeck = false
                onDeleteDeck()
            },
            onDismiss = { confirmDeleteDeck = false },
        )
    }

    cardToDelete?.let { card ->
        ConfirmDeleteDialog(
            title = "Slet kort?",
            text = "\"${card.visibleSides.firstOrNull().orEmpty()}\" slettes. Det kan ikke fortrydes.",
            onConfirm = {
                cardToDelete = null
                onDeleteCard(card)
            },
            onDismiss = { cardToDelete = null },
        )
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Slet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annullér") } },
    )
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
            onRenameDeck = {},
            onDeleteDeck = {},
            onEditCard = { _, _ -> },
            onDeleteCard = {},
        )
    }
}
