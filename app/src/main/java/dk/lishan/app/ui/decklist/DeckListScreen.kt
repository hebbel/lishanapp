package dk.lishan.app.ui.decklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dk.lishan.app.R
import dk.lishan.app.model.Deck
import dk.lishan.app.model.Folder
import dk.lishan.app.ui.ConfirmDeleteDialog
import dk.lishan.app.ui.NameDialog
import dk.lishan.app.ui.theme.LishanTheme

/**
 * En liste med mapper øverst og decks under dem. Bruges både til forsiden og til indholdet af
 * en mappe: er [title] sat, vises den øverst med en ←-pil ([onBack]) tilbage til forsiden.
 *
 * ⋮-menuen på en mappe kan omdøbe eller slette den; på et deck kan den redigere, flytte eller
 * slette det. Sletning skal først bekræftes i en dialog.
 *
 * Skærmen henter ikke selv data. Den får listerne udefra og siger besked, når der trykkes.
 * Så kan den vises i en preview uden database.
 *
 * @param folders mapperne, der vises i listen (tom inde i en mappe).
 * @param moveTargets alle mapper, et deck kan flyttes til.
 */
@Composable
fun DeckListScreen(
    folders: List<Folder>,
    decks: List<Deck>,
    moveTargets: List<Folder>,
    onFolderClick: (Folder) -> Unit,
    onRenameFolder: (Folder, newName: String) -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    onDeckClick: (Deck) -> Unit,
    onEditDeck: (Deck) -> Unit,
    onMoveDeck: (Deck, folderId: Long?) -> Unit,
    onDeleteDeck: (Deck) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: () -> Unit = {},
) {
    // Hvilken dialog der er åben, hvis nogen. Kun id'et gemmes, så det overlever, at telefonen drejes.
    var folderToRenameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var folderToDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deckToMoveId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deckToDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    // LazyColumn tegner kun de rækker, der er synlige, så listen kan blive lang.
    LazyColumn(modifier = modifier) {
        if (title != null) {
            item {
                // Box lægger pilen i venstre side og navnet i midten, oven på hinanden.
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Tilbage til forsiden",
                        )
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        items(folders, key = { "folder-${it.id}" }) { folder ->
            ListItem(
                headlineContent = { Text(folder.name) },
                leadingContent = { Icon(painterResource(R.drawable.ic_folder), contentDescription = "Mappe") },
                trailingContent = {
                    RowMenu(
                        contentDescription = "Flere valg for mappen ${folder.name}",
                        actions = listOf(
                            "Omdøb" to { folderToRenameId = folder.id },
                            "Slet" to { folderToDeleteId = folder.id },
                        ),
                    )
                },
                modifier = Modifier.clickable { onFolderClick(folder) },
            )
            HorizontalDivider()
        }

        items(decks, key = { "deck-${it.id}" }) { deck ->
            ListItem(
                headlineContent = { Text(deck.name) },
                trailingContent = {
                    RowMenu(
                        contentDescription = "Flere valg for ${deck.name}",
                        actions = listOf(
                            "Rediger" to { onEditDeck(deck) },
                            "Flyt til mappe" to { deckToMoveId = deck.id },
                            "Slet" to { deckToDeleteId = deck.id },
                        ),
                    )
                },
                modifier = Modifier.clickable { onDeckClick(deck) },
            )
            HorizontalDivider()
        }

        if (folders.isEmpty() && decks.isEmpty()) {
            item {
                Text(
                    if (title == null) "Ingen decks endnu. Tryk på + for at oprette et." else "Mappen er tom. Tryk på + for at oprette et deck.",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    folders.find { it.id == folderToRenameId }?.let { folder ->
        NameDialog(
            title = "Omdøb mappe",
            confirmLabel = "Gem",
            initialName = folder.name,
            onConfirm = { name ->
                folderToRenameId = null
                onRenameFolder(folder, name)
            },
            onDismiss = { folderToRenameId = null },
        )
    }

    folders.find { it.id == folderToDeleteId }?.let { folder ->
        ConfirmDeleteDialog(
            title = "Slet mappe?",
            text = "\"${folder.name}\" og alle decks i den slettes. Det kan ikke fortrydes.",
            onConfirm = {
                folderToDeleteId = null
                onDeleteFolder(folder)
            },
            onDismiss = { folderToDeleteId = null },
        )
    }

    decks.find { it.id == deckToMoveId }?.let { deck ->
        MoveDeckDialog(
            deck = deck,
            folders = moveTargets,
            onMove = { folderId ->
                deckToMoveId = null
                onMoveDeck(deck, folderId)
            },
            onDismiss = { deckToMoveId = null },
        )
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

/** ⋮-knappen og den lille menu, den åbner. [actions] er menupunkternes tekst og handling. */
@Composable
private fun RowMenu(contentDescription: String, actions: List<Pair<String, () -> Unit>>) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    // Box: menuen placeres ud fra det, den ligger i, så den dukker op ved knappen.
    Box {
        IconButton(onClick = { expanded = true }) {
            // Læses op af skærmlæsere (TalkBack), da ikonet ikke har synlig tekst.
            Icon(painter = painterResource(R.drawable.ic_more_vert), contentDescription = contentDescription)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((label, action) in actions) {
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
        }
    }
}

/** Dialog med de steder, et deck kan flyttes til: forsiden og alle mapper, undtagen hvor det ligger nu. */
@Composable
private fun MoveDeckDialog(
    deck: Deck,
    folders: List<Folder>,
    onMove: (folderId: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    // null = forsiden
    val targets: List<Pair<Long?, String>> =
        (listOf<Pair<Long?, String>>(null to "Forsiden") + folders.map { it.id to it.name })
            .filter { (id, _) -> id != deck.folderId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Flyt \"${deck.name}\" til") },
        text = {
            if (targets.isEmpty()) {
                Text("Der er ingen mapper at flytte til. Opret en med + på forsiden.")
            } else {
                LazyColumn {
                    items(targets, key = { it.first ?: -1L }) { (id, name) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            leadingContent = if (id != null) {
                                { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) }
                            } else null,
                            modifier = Modifier.clickable { onMove(id) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annullér") } },
    )
}

@Preview(showBackground = true)
@Composable
fun DeckListScreenPreview() {
    LishanTheme {
        DeckListScreen(
            folders = listOf(Folder(id = 1, name = "Arabisk 24-26")),
            decks = listOf(Deck(id = 1, name = "Dyr"), Deck(id = 2, name = "Farver")),
            moveTargets = listOf(Folder(id = 1, name = "Arabisk 24-26")),
            onFolderClick = {},
            onRenameFolder = { _, _ -> },
            onDeleteFolder = {},
            onDeckClick = {},
            onEditDeck = {},
            onMoveDeck = { _, _ -> },
            onDeleteDeck = {},
        )
    }
}
