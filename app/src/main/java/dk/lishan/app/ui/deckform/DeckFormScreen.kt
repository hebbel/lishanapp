package dk.lishan.app.ui.deckform

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dk.lishan.app.model.CardSide
import dk.lishan.app.ui.StringListSaver
import dk.lishan.app.ui.theme.LishanTheme

/**
 * Formular til et decks navn og labels på dets sider. Bruges både til at oprette et nyt deck
 * og til at redigere et eksisterende; [initialName] og [initialLabels] (plads 0 = side 1)
 * er det, felterne starter med. Labels er valgfrie; tomme labels gemmes ikke.
 * Kalder [onSave] med navn og labels, når brugeren trykker på knappen.
 */
@Composable
fun DeckFormScreen(
    title: String,
    saveLabel: String,
    onSave: (name: String, labels: List<String>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
    initialLabels: List<String> = emptyList(),
) {
    // `rememberSaveable`: teksten bevares, hvis telefonen drejes midt i skrivningen.
    var name by rememberSaveable { mutableStateOf(initialName) }
    val labels = rememberSaveable(saver = StringListSaver) {
        mutableStateListOf(*initialLabels.toTypedArray()).apply { while (size < 2) add("") }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Navn") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Labels på siderne", style = MaterialTheme.typography.titleSmall)
        Text(
            "Fx \"Dansk\" og \"Engelsk\". Vises på kortene og i kortformularen. Tomme labels gemmes ikke.",
            style = MaterialTheme.typography.bodySmall,
        )
        labels.forEachIndexed { index, label ->
            OutlinedTextField(
                value = label,
                onValueChange = { labels[index] = it },
                label = { Text("Side ${index + 1}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (labels.size < CardSide.MAX_SIDES) {
            TextButton(onClick = { labels.add("") }) { Text("+ Tilføj side") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
        ) {
            TextButton(onClick = onCancel) { Text("Annullér") }
            Button(
                onClick = { onSave(name.trim(), labels.toList()) },
                enabled = name.isNotBlank(),
            ) { Text(saveLabel) }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DeckFormScreenPreview() {
    LishanTheme {
        DeckFormScreen(
            title = "Rediger deck",
            saveLabel = "Gem",
            initialName = "Dyr",
            initialLabels = listOf("Dansk", "Engelsk", "Spansk"),
            onSave = { _, _ -> },
            onCancel = {},
        )
    }
}
