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

@Composable
internal fun SudokuCatalog(repository: SudokuIndexRepository, onOpen: (Puzzle) -> Unit) {
    var index by remember(repository) { mutableStateOf(repository.cached()) }
    var refreshing by remember { mutableStateOf(true) }
    var cached by remember { mutableStateOf(index != null) }
    var refreshRequest by remember { mutableStateOf(0) }
    var indexError by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf<String?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository, refreshRequest) {
        refreshing = true
        indexError = null
        try {
            index = repository.refresh()
            cached = false
        } catch (error: Throwable) {
            // Ktor's browser engine reports fetch failures as Kotlin Error.
            if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
            cached = index != null
            indexError = if (cached) "Could not refresh. Showing the saved sudoku index."
                else "Could not load the sudoku index. Connect to the internet and try again."
        } finally { refreshing = false }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("NINEFOLD", fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                TextButton({ refreshRequest++ }, enabled = !refreshing) { Text("Refresh") }
                Button({ showImport = true }) { Text("Import") }
            }
        }
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().weight(1f).align(Alignment.CenterHorizontally).padding(20.dp)) {
            Text("Sudokus", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Choose a puzzle from the sudoku index.", modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            if (refreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (index == null) "Loading puzzles…" else "Refreshing puzzles…", modifier = Modifier.padding(vertical = 8.dp))
            }
            indexError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            index?.let { current ->
                Text("${current.entries.size} puzzles · ${if (cached) "Saved index" else "Updated"} ${current.generatedAt.take(10)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
                if (current.entries.isEmpty()) Text("No puzzles have been published yet.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(current.entries, key = { it.id }) { entry ->
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${entry.source} · ${entry.publishedAt.take(10)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                                Button(onClick = {
                                    opening = entry.id
                                    openError = null
                                    scope.launch {
                                        try { onOpen(importSudokuPadPuzzle(entry.puzzleUrl).let { it.copy(videoUrl = entry.videoUrl ?: it.videoUrl) }) }
                                        catch (error: Throwable) {
                                            if (error is CancellationException) throw error
                                            openError = "Could not open ${entry.title}. ${error.message.orEmpty()}"
                                        } finally { opening = null }
                                    }
                                }, enabled = opening == null) { Text(if (opening == entry.id) "Opening…" else "Play") }
                            }
                        }
                    }
                }
            }
        }
    }
    openError?.let { message -> AlertDialog(onDismissRequest = { openError = null }, confirmButton = { TextButton({ openError = null }) { Text("OK") } }, title = { Text("Puzzle unavailable") }, text = { Text(message) }) }
    if (showImport) ImportDialog({ showImport = false }) { showImport = false; onOpen(it) }
}
