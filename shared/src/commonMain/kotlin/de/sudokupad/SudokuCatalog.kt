package de.sudokupad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

internal enum class DownloadStatus(val label: String) {
    Waiting("Waiting for download"), Downloading("Fetching and converting…"),
    Ready("Fetched and converted · Ready offline"), Failed("Download or conversion failed"),
}

@Composable
internal fun SudokuCatalog(repository: SudokuIndexRepository, games: GameRepository, downloadScope: CoroutineScope, onOpen: (Puzzle) -> Unit) {
    var index by remember(repository) { mutableStateOf(repository.cached()) }
    var refreshing by remember { mutableStateOf(true) }
    var cached by remember { mutableStateOf(index != null) }
    var refreshRequest by remember { mutableStateOf(0) }
    var indexError by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf<String?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }
    val statuses = games.statuses
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository, refreshRequest) {
        refreshing = true
        indexError = null
        try {
            index = repository.refresh()
            cached = false
        } catch (error: Throwable) {
            if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
            cached = index != null
            indexError = if (cached) "Could not refresh. Showing the saved sudoku index."
                else "Could not load the sudoku index. Connect to the internet and try again."
        } finally { refreshing = false }
    }

    LaunchedEffect(index, refreshRequest) {
        games.prepare(index?.entries.orEmpty(), downloadScope)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Surface(shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("NINEFOLD", fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                TextButton({ refreshRequest++ }, enabled = !refreshing) { Text("Refresh") }
                Button({ showImport = true }) { Text("Import") }
            }
        }
        LazyColumn(
            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().weight(1f).align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Continue sudokus", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            val inProgress = games.games.values.filter { it.started && !it.completed }.reversed()
            if (inProgress.isEmpty()) item { Text("Your puzzles in progress will appear here.") }
            items(inProgress, key = { "continue.${it.puzzle.storageId()}" }) { game ->
                SavedGameCard(game, games.elapsed(game.puzzle), "Continue") { onOpen(game.puzzle) }
            }
            item { Text("Solved sudokus", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            val solved = games.games.values.filter { it.completed }.reversed()
            if (solved.isEmpty()) item { Text("Your completed puzzles will appear here.") }
            items(solved, key = { "solved.${it.puzzle.storageId()}" }) { game ->
                SavedGameCard(game, games.elapsed(game.puzzle), "View") { onOpen(game.puzzle) }
            }
            item {
                Text("Browse sudokus", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Puzzles download and convert automatically for offline play.", modifier = Modifier.padding(top = 4.dp))
                if (refreshing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(if (index == null) "Loading puzzles…" else "Refreshing puzzles…")
                }
                indexError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                games.storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                index?.let { current ->
                    val ready = current.entries.count { statuses[it.id] == DownloadStatus.Ready }
                    Text("${current.entries.size} puzzles · $ready ready offline · ${if (cached) "Saved index" else "Updated"} ${current.generatedAt.take(10)}", style = MaterialTheme.typography.bodySmall)
                    if (current.entries.isEmpty()) Text("No puzzles have been published yet.")
                }
            }
            items(index?.entries.orEmpty(), key = { "catalog.${it.id}" }) { entry ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${entry.source} · ${entry.publishedAt.take(10)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                        val status = statuses[entry.id] ?: DownloadStatus.Waiting
                        Text(status.label, style = MaterialTheme.typography.bodySmall, color = if (status == DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        if (status == DownloadStatus.Failed) games.downloadErrors[entry.id]?.let { message ->
                            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(onClick = {
                            opening = entry.id
                            openError = null
                            scope.launch {
                                try {
                                    val puzzle = games.fetchPuzzle(entry)
                                    statuses[entry.id] = DownloadStatus.Ready
                                    onOpen(puzzle)
                                } catch (error: Throwable) {
                                    if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                                    statuses[entry.id] = DownloadStatus.Failed
                                    games.downloadErrors[entry.id] = error.message.orEmpty()
                                    openError = "Could not open ${entry.title}. ${error.message.orEmpty()}"
                                } finally { opening = null }
                            }
                        }, enabled = opening == null) { Text(if (opening == entry.id) "Opening…" else if (status == DownloadStatus.Failed) "Retry" else "Play") }
                    }
                }
            }
        }
    }
    openError?.let { message -> AlertDialog(onDismissRequest = { openError = null }, confirmButton = { TextButton({ openError = null }) { Text("OK") } }, title = { Text("Puzzle unavailable") }, text = { Text(message) }) }
    if (showImport) ImportDialog({ showImport = false }) { showImport = false; onOpen(it) }
}

@Composable
private fun SavedGameCard(game: SavedGame, elapsed: Long, action: String, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(game.puzzle.title, fontWeight = FontWeight.Bold)
                Text("by ${game.puzzle.author} · ${formatElapsed(elapsed)}", style = MaterialTheme.typography.bodySmall)
            }
            Button(onOpen) { Text(action) }
        }
    }
}
