package dk.lishan.app.ui.cardform

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
 * Formular til et korts sider. Bruges både til at oprette et nyt kort og til at rette et
 * eksisterende; [initialSides] er den tekst, felterne starter med (plads 0 = side 1).
 * Felterne navngives med deckets [labels]; positioner uden label hedder "Side n".
 * Der vises mindst to felter og mindst ét pr. label, og flere kan tilføjes op til
 * [CardSide.MAX_SIDES]. Under siderne er felterne kommentar og noter, som ikke er kortsider.
 * Kalder [onSave] med teksten fra alle sidefelterne (i position), noter og kommentar.
 */
@Composable
fun CardFormScreen(
    title: String,
    onSave: (sides: List<String>, notes: String, comment: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialSides: List<String> = emptyList(),
    labels: List<String> = emptyList(),
    initialNotes: String = "",
    initialComment: String = "",
) {
    // En liste, som Compose holder øje med: tilføjes eller ændres et element, tegnes skærmen igen.
    // `rememberSaveable` kan ikke selv gemme en sådan liste, så [StringListSaver] fortæller hvordan.
    val sides = rememberSaveable(saver = StringListSaver) {
        val fieldCount = maxOf(2, initialSides.size, labels.size).coerceAtMost(CardSide.MAX_SIDES)
        mutableStateListOf(*initialSides.toTypedArray()).apply { while (size < fieldCount) add("") }
    }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    var comment by rememberSaveable { mutableStateOf(initialComment) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)

        sides.forEachIndexed { index, text ->
            OutlinedTextField(
                value = text,
                onValueChange = { sides[index] = it },
                label = { Text(labels.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Side ${index + 1}") },
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

        // Ikke kortsider: vises ikke, når man bladrer i kortet.
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            label = { Text("Kommentar") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Noter") },
            supportingText = { Text("Dine egne noter. Overskrives aldrig ved synkronisering.") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
        ) {
            TextButton(onClick = onCancel) { Text("Annullér") }
            Button(
                onClick = { onSave(sides.toList(), notes, comment) },
                enabled = sides.any { it.isNotBlank() },
            ) { Text("Gem") }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CardFormScreenPreview() {
    LishanTheme {
        CardFormScreen(
            title = "Ret kort",
            initialSides = listOf("hund", "dog", "perro"),
            labels = listOf("Dansk", "Engelsk"),
            initialNotes = "Husk: perro er hankøn.",
            onSave = { _, _, _ -> },
            onCancel = {},
        )
    }
}
