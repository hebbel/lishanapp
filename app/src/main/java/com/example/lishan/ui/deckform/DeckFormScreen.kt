package com.example.lishan.ui.deckform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lishan.ui.theme.LishanTheme

/**
 * Formular til et decks navn. Bruges både til at oprette et nyt deck og til at omdøbe et
 * eksisterende; [initialName] er det navn, feltet starter med.
 * Kalder [onSave] med navnet, når brugeren trykker på knappen.
 */
@Composable
fun DeckFormScreen(
    title: String,
    saveLabel: String,
    onSave: (name: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
) {
    var name by remember { mutableStateOf(initialName) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Navn") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
        ) {
            TextButton(onClick = onCancel) { Text("Annullér") }
            Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(saveLabel) }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DeckFormScreenPreview() {
    LishanTheme {
        DeckFormScreen(title = "Omdøb deck", saveLabel = "Gem", initialName = "Dyr", onSave = {}, onCancel = {})
    }
}
