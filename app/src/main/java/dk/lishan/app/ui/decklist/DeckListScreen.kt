package dk.lishan.app.ui.decklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dk.lishan.app.R
import dk.lishan.app.model.Deck
import dk.lishan.app.model.Folder
import dk.lishan.app.model.SyncState
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
 * Decks, der er lektioner fra et kursus, viser deres tilstand: gråt med ↓ (ikke hentet – et tryk
 * henter kortene), ✓ (hentet), ↻ (ændret på serveren – ↻ henter igen) eller en sky med streg over
 * (findes ikke længere på serveren) eller ✎ (ændret af dig – kan gendannes fra kurset via ⋮).
 * Symbolerne har tekst til skærmlæsere, så farven ikke er eneste tegn. Før brugerens rettelser
 * overskrives af en hentning, skal det bekræftes.
 *
 * @param folders mapperne, der vises i listen (tom inde i en mappe).
 * @param moveTargets alle mapper, et deck kan flyttes til.
 * @param downloadingDeckIds lektioner, hvis kort er ved at blive hentet (vises med en snurretop).
 * @param status en statuslinje under titlen, fx "Henter lektionslisten …".
 * @param onSimulateServerChange vises som en testknap, når den er sat (kun med det falske API).
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
    onDownloadDeck: (Deck) -> Unit = {},
    downloadingDeckIds: Set<Long> = emptySet(),
    status: String? = null,
    onSimulateServerChange: (() -> Unit)? = null,
) {
    // Hvilken dialog der er åben, hvis nogen. Kun id'et gemmes, så det overlever, at telefonen drejes.
    var folderToRenameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var folderToDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deckToMoveId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deckToDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deckToRestoreId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Hentning overskriver brugerens rettelser; det skal bekræftes først.
    val download: (Deck) -> Unit = { deck ->
        if (deck.locallyModified) deckToRestoreId = deck.id else onDownloadDeck(deck)
    }

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
        if (status != null || onSimulateServerChange != null) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    onSimulateServerChange?.let {
                        TextButton(onClick = it) { Text("Test: simulér en ændring på serveren") }
                    }
                }
            }
        }

        items(folders, key = { "folder-${it.id}" }) { folder ->
            ListItem(
                headlineContent = { Text(folder.name) },
                supportingContent = if (folder.courseId != null) {
                    { Text("Kursus") }
                } else null,
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
            val state = deck.syncState
            val notDownloaded = state == SyncState.NOT_DOWNLOADED
            ListItem(
                headlineContent = {
                    Text(
                        deck.name,
                        // Ikke hentede lektioner vises gråt.
                        color = if (notDownloaded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else Color.Unspecified,
                    )
                },
                supportingContent = syncHint(state)?.let { hint -> { Text(hint) } },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SyncStatus(
                            deck = deck,
                            downloading = deck.id in downloadingDeckIds,
                            onDownload = { download(deck) },
                        )
                        RowMenu(
                            contentDescription = "Flere valg for ${deck.name}",
                            actions = buildList {
                                add("Rediger" to { onEditDeck(deck) })
                                // En lektion hører til sit kursus; flyttet ville den dukke op igen ved næste opdatering.
                                if (state == SyncState.LOCAL) add("Flyt til mappe" to { deckToMoveId = deck.id })
                                if (deck.locallyModified && deck.downloadedHash != null) {
                                    add("Gendan fra kurset" to { deckToRestoreId = deck.id })
                                }
                                add("Slet" to { deckToDeleteId = deck.id })
                            },
                        )
                    }
                },
                // Et tryk på en ikke hentet lektion henter den; ellers åbnes decket.
                modifier = Modifier.clickable {
                    if (notDownloaded) onDownloadDeck(deck) else onDeckClick(deck)
                },
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

    decks.find { it.id == deckToRestoreId }?.let { deck ->
        ConfirmDeleteDialog(
            title = "Gendan fra kurset?",
            text = "Dine rettelser i \"${deck.name}\" erstattes af kursets version, og kort, du har slettet, " +
                "kommer tilbage. Dine noter og dine egne kort bevares.",
            confirmLabel = "Gendan",
            onConfirm = {
                deckToRestoreId = null
                onDownloadDeck(deck)
            },
            onDismiss = { deckToRestoreId = null },
        )
    }

    decks.find { it.id == deckToDeleteId }?.let { deck ->
        ConfirmDeleteDialog(
            title = "Slet deck?",
            text = if (deck.lessonId != null) {
                "\"${deck.name}\" og alle dets kort slettes fra telefonen, også dine noter. Lektionen kan hentes igen fra kurset."
            } else {
                "\"${deck.name}\" og alle dets kort slettes. Det kan ikke fortrydes."
            },
            onConfirm = {
                deckToDeleteId = null
                onDeleteDeck(deck)
            },
            onDismiss = { deckToDeleteId = null },
        )
    }
}

/** En kort forklaring under en lektions navn, eller `null` når der ikke er noget at sige. */
private fun syncHint(state: SyncState): String? = when (state) {
    SyncState.NOT_DOWNLOADED -> "Ikke hentet – tryk for at hente"
    SyncState.OUT_OF_SYNC -> "Ændret på serveren – tryk på ↻ for at hente igen"
    SyncState.REMOVED_ON_SERVER -> "Findes ikke længere på serveren"
    SyncState.LOCALLY_MODIFIED -> "Ændret af dig – kan gendannes fra kurset"
    SyncState.LOCAL, SyncState.SYNCED -> null
}

private val SyncedGreen = Color(0xFF2E7D32)
private val OutOfSyncOrange = Color(0xFFE65100)

/** Symbolet for en lektions tilstand: ↓, ✓, ↻, ✎ eller en sky med streg over. Intet for egne decks. */
@Composable
private fun SyncStatus(deck: Deck, downloading: Boolean, onDownload: () -> Unit) {
    val grey = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    when {
        downloading -> CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp))
        deck.syncState == SyncState.NOT_DOWNLOADED -> IconButton(onClick = onDownload) {
            Icon(painterResource(R.drawable.ic_download), contentDescription = "Hent ${deck.name}", tint = grey)
        }
        deck.syncState == SyncState.SYNCED -> Icon(
            painterResource(R.drawable.ic_check_circle), contentDescription = "Hentet",
            tint = SyncedGreen, modifier = Modifier.padding(12.dp),
        )
        deck.syncState == SyncState.OUT_OF_SYNC -> IconButton(onClick = onDownload) {
            Icon(painterResource(R.drawable.ic_sync), contentDescription = "Hent ${deck.name} igen", tint = OutOfSyncOrange)
        }
        deck.syncState == SyncState.LOCALLY_MODIFIED -> Icon(
            painterResource(R.drawable.ic_edit), contentDescription = "Ændret af dig",
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp),
        )
        deck.syncState == SyncState.REMOVED_ON_SERVER -> Icon(
            painterResource(R.drawable.ic_cloud_off), contentDescription = "Findes ikke længere på serveren",
            tint = grey, modifier = Modifier.padding(12.dp),
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
            decks = listOf(
                Deck(id = 1, name = "Dyr"),
                Deck(id = 2, name = "1.01 Hilsner", lessonId = "1.01", serverHash = "a", downloadedHash = "a"),
                Deck(id = 3, name = "1.02 Familie", lessonId = "1.02", serverHash = "b", downloadedHash = "a"),
                Deck(id = 4, name = "2.01 Farver", lessonId = "2.01", serverHash = "c"),
                Deck(id = 5, name = "2.02 Tal", lessonId = "2.02", serverHash = "d", downloadedHash = "d", locallyModified = true),
            ),
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
