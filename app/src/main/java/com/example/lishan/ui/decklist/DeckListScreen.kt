package com.example.lishan.ui.decklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.lishan.model.Deck
import com.example.lishan.ui.theme.LishanTheme

/**
 * Hovedskærmen: en liste over alle decks. Et tryk på et deck kalder [onDeckClick].
 *
 * Skærmen henter ikke selv data. Den får listen udefra og siger besked, når der trykkes.
 * Så kan den vises i en preview uden database.
 */
@Composable
fun DeckListScreen(
    decks: List<Deck>,
    onDeckClick: (Deck) -> Unit,
    modifier: Modifier = Modifier,
) {
    // LazyColumn tegner kun de rækker, der er synlige, så listen kan blive lang.
    LazyColumn(modifier = modifier) {
        items(decks, key = { it.id }) { deck ->
            ListItem(
                headlineContent = { Text(deck.name) },
                modifier = Modifier.clickable { onDeckClick(deck) },
            )
            HorizontalDivider()
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
        )
    }
}
