package com.example.lishan.ui.newcard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lishan.model.CardSide
import com.example.lishan.ui.theme.LishanTheme

/**
 * Skærm til at oprette et nyt kort i et deck. Starter med to sider; flere kan tilføjes
 * op til [CardSide.MAX_SIDES]. Kalder [onSave] med teksten fra alle felterne.
 */
@Composable
fun NewCardScreen(
    deckName: String,
    onSave: (sides: List<String>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // En liste, som Compose holder øje med: tilføjes eller ændres et element, tegnes skærmen igen.
    val sides = remember { mutableStateListOf("", "") }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Nyt kort i $deckName", style = MaterialTheme.typography.headlineSmall)

        sides.forEachIndexed { index, text ->
            OutlinedTextField(
                value = text,
                onValueChange = { sides[index] = it },
                label = { Text("Side ${index + 1}") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (sides.size < CardSide.MAX_SIDES) {
            TextButton(onClick = { sides.add("") }) { Text("+ Tilføj side") }
        }

        Text(
            "Tomme sider gemmes ikke.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
        ) {
            TextButton(onClick = onCancel) { Text("Annullér") }
            Button(
                onClick = { onSave(sides.toList()) },
                enabled = sides.any { it.isNotBlank() },
            ) { Text("Gem") }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NewCardScreenPreview() {
    LishanTheme {
        NewCardScreen(deckName = "Dyr", onSave = {}, onCancel = {})
    }
}
