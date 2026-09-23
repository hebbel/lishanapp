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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lishan.ui.theme.LishanTheme

/**
 * Viser ét flashcard. Et tryk går til næste side; efter den sidste starter kortet forfra.
 *
 * @param sides teksten på kortets sider i rækkefølge. Kun sider med indhold skal med.
 */
@Composable
fun FlashcardView(sides: List<String>, modifier: Modifier = Modifier) {
    // Husker hvilken side der vises. Når værdien ændres, tegner Compose kortet igen.
    var sideIndex by rememberSaveable { mutableIntStateOf(0) }

    Card(
        onClick = { if (sides.isNotEmpty()) sideIndex = (sideIndex + 1) % sides.size },
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
                text = sides.getOrElse(sideIndex) { "" },
                style = MaterialTheme.typography.headlineMedium
            )
            // Viser kun sidetælleren, når der er mere end én side at bladre i.
            if (sides.size > 1) {
                Text(
                    text = "${sideIndex + 1}/${sides.size}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FlashcardViewPreview() {
    LishanTheme {
        FlashcardView(
            sides = listOf("hund", "dog", "perro"),
            modifier = Modifier.padding(16.dp)
        )
    }
}
