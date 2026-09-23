package com.example.lishan.ui.flashcard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lishan.model.Flashcard
import com.example.lishan.ui.theme.LishanTheme

/**
 * Viser ét flashcard. Et tryk vender kortet mellem forside og bagside.
 */
@Composable
fun FlashcardView(card: Flashcard, modifier: Modifier = Modifier) {
    // Husker om kortet er vendt. Når værdien ændres, tegner Compose kortet igen.
    var showBack by remember { mutableStateOf(false) }

    Card(
        onClick = { showBack = !showBack },
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (showBack) card.back else card.front,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FlashcardViewPreview() {
    LishanTheme {
        FlashcardView(
            card = Flashcard(front = "hund", back = "dog"),
            modifier = Modifier.padding(16.dp)
        )
    }
}
