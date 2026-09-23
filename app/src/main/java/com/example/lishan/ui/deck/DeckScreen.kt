package com.example.lishan.ui.deck

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lishan.R
import com.example.lishan.model.CardSide
import com.example.lishan.model.Flashcard
import com.example.lishan.model.FlashcardWithSides
import com.example.lishan.ui.ConfirmDeleteDialog
import com.example.lishan.ui.flashcard.FlashcardView
import com.example.lishan.ui.flashcard.SideContent
import com.example.lishan.ui.theme.LishanTheme

/**
 * Viser kortene i ét deck, ét ad gangen. Swipe til venstre går til næste kort;
 * efter det sidste kort starter decket forfra.
 *
 * ←-pilen øverst går tilbage til listen over decks ([onBack]).
 * Knapperne til at rette/slette det viste kort melder bare tilbage via callbacks;
 * sletning skal først bekræftes i en dialog. Decket selv omdøbes/slettes fra listen over decks.
 *
 * @param labels deckets labels pr. position (plads 0 = side 1); tomme strenge = intet label.
 * @param initialIndex det kort, skærmen starter på (fx når man kommer tilbage efter at have rettet et kort).
 */
@Composable
fun DeckScreen(
    deckName: String,
    cards: List<FlashcardWithSides>,
    labels: List<String>,
    onBack: () -> Unit,
    onEditCard: (card: FlashcardWithSides, index: Int) -> Unit,
    onDeleteCard: (card: FlashcardWithSides) -> Unit,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
) {
    // `rememberSaveable`: overlever at skærmen genskabes, fx når telefonen drejes.
    var index by rememberSaveable { mutableIntStateOf(initialIndex) }
    // Det kort, hvis slet-dialog er åben, hvis nogen. Kun id'et gemmes.
    var cardToDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Kan rulle lodret, hvis indholdet ikke kan være der (fx når telefonen ligger ned).
            .verticalScroll(rememberScrollState())
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
        // Box lægger pilen i venstre side og navnet i midten, oven på hinanden.
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Tilbage til decks",
                )
            }
            Text(
                deckName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (cards.isEmpty()) {
            Text("Dette deck har ingen kort endnu. Tryk på + for at tilføje et.")
        } else {
            val position = index % cards.size
            val card = cards[position]
            // `key` giver hvert kort sin egen FlashcardView, så et nyt kort altid starter på første side.
            key(card.card.id) {
                FlashcardView(
                    sides = card.visibleCardSides.map { side ->
                        SideContent(side.text, label = labels.getOrNull(side.position - 1)?.takeIf { it.isNotBlank() })
                    },
                )
            }
            Text("${position + 1} / ${cards.size}")
            Row {
                TextButton(onClick = { onEditCard(card, position) }) { Text("Ret kort") }
                TextButton(onClick = { cardToDeleteId = card.card.id }) { Text("Slet kort") }
            }
        }
        // Plads i bunden, så "+"-knappen ikke dækker knapperne, når man har rullet helt ned.
        Spacer(Modifier.height(72.dp))
    }

    cards.find { it.card.id == cardToDeleteId }?.let { card ->
        ConfirmDeleteDialog(
            title = "Slet kort?",
            text = "\"${card.visibleSides.firstOrNull().orEmpty()}\" slettes. Det kan ikke fortrydes.",
            onConfirm = {
                cardToDeleteId = null
                onDeleteCard(card)
            },
            onDismiss = { cardToDeleteId = null },
        )
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
            labels = listOf("Dansk", "Engelsk"),
            onBack = {},
            onEditCard = { _, _ -> },
            onDeleteCard = {},
        )
    }
}
