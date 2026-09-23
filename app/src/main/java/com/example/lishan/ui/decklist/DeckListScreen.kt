package com.example.lishan.ui.decklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.lishan.R
import com.example.lishan.model.Deck
import com.example.lishan.ui.ConfirmDeleteDialog
import com.example.lishan.ui.theme.LishanTheme

/**
 * Hovedskærmen: en liste over alle decks. Et tryk på et deck kalder [onDeckClick].
 * ⋮-menuen i højre side af hver række kan redigere (navn og labels) eller slette decket; sletning skal
 * først bekræftes i en dialog.
 *
 * Skærmen henter ikke selv data. Den får listen udefra og siger besked, når der trykkes.
 * Så kan den vises i en preview uden database.
 */
@Composable
fun DeckListScreen(
    decks: List<Deck>,
    onDeckClick: (Deck) -> Unit,
    onEditDeck: (Deck) -> Unit,
    onDeleteDeck: (Deck) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Det deck, hvis slet-dialog er åben, hvis nogen. Kun id'et gemmes.
    var deckToDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    // LazyColumn tegner kun de rækker, der er synlige, så listen kan blive lang.
    LazyColumn(modifier = modifier) {
        items(decks, key = { it.id }) { deck ->
            ListItem(
                headlineContent = { Text(deck.name) },
                trailingContent = {
                    DeckMenu(
                        deckName = deck.name,
                        onEdit = { onEditDeck(deck) },
                        onDelete = { deckToDeleteId = deck.id },
                    )
                },
                modifier = Modifier.clickable { onDeckClick(deck) },
            )
            HorizontalDivider()
        }
    }

    decks.find { it.id == deckToDeleteId }?.let { deck ->
        ConfirmDeleteDialog(
            title = "Slet deck?",
            text = "\"${deck.name}\" og alle dets kort slettes. Det kan ikke fortrydes.",
            onConfirm = {
                deckToDeleteId = null
                onDeleteDeck(deck)
            },
            onDismiss = { deckToDeleteId = null },
        )
    }
}

/** ⋮-knappen og den lille menu, den åbner. */
@Composable
private fun DeckMenu(
    deckName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    // Box: menuen placeres ud fra det, den ligger i, så den dukker op ved knappen.
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                // Læses op af skærmlæsere (TalkBack), da ikonet ikke har synlig tekst.
                contentDescription = "Flere valg for $deckName",
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Rediger") },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("Slet") },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DeckListScreenPreview() {
    LishanTheme {
        DeckListScreen(
            decks = listOf(Deck(id = 1, name = "Dyr"), Deck(id = 2, name = "Farver")),
            onDeckClick = {},
            onEditDeck = {},
            onDeleteDeck = {},
        )
    }
}
