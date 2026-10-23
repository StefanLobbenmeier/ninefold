package de.ninefold

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*

internal data class StoragePuzzle(val id: String, val title: String, val categories: Map<String, Long>) {
    val bytes get() = categories.values.sum()
}
internal data class StorageUsage(
    val categories: Map<String, Long> = emptyMap(),
    val puzzles: List<StoragePuzzle> = emptyList(),
    val browser: Boolean = false,
    val error: String? = null,
) { val bytes get() = categories.values.sum() }

internal fun inspectStorage(storage: AppStorage): StorageUsage = try {
    val entries = storage.entries()
    if (entries == null) StorageUsage(error = "Storage usage is unavailable on this platform.") else {
        val categories = linkedMapOf<String, Long>()
        val puzzles = linkedMapOf<String, MutableMap<String, Long>>()
        val titles = mutableMapOf<String, String>()
        entries.forEach { (key, value) ->
            val prefix = listOf("puzzle", "game", "timer").firstOrNull { key.startsWith("ninefold.$it.") && key.endsWith(".v1") }
            val category = when {
                prefix == "puzzle" -> "Offline puzzles"
                prefix == "game" -> "Progress and undo history"
                prefix == "timer" -> "Timers"
                key == "ninefold.sudoku-index.v1" -> "Puzzle catalog"
                key == "ninefold.settings.v1" -> "Settings"
                key == "ninefold.games.v1" || key == "ninefold.recent-games.v1" -> "Game lists"
                key.startsWith("ninefold.") -> "Other app data"
                else -> "Other site data"
            }
            // Web Storage uses strings; estimate UTF-16 key and value size, without implementation overhead.
            val bytes = if (storage.browserStorage) (key.length.toLong() + value.length) * 2
                else key.encodeToByteArray().size.toLong() + value.encodeToByteArray().size
            categories[category] = (categories[category] ?: 0L) + bytes
            if (prefix != null) {
                val id = key.removePrefix("ninefold.$prefix.").removeSuffix(".v1")
                val parts = puzzles.getOrPut(id) { linkedMapOf() }
                parts[category] = (parts[category] ?: 0L) + bytes
                runCatching {
                    val record = Json.parseToJsonElement(value).jsonObject
                    val puzzle = if (prefix == "game") record["puzzle"]?.jsonObject else record
                    puzzle?.get("title")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { titles[id] = it }
                }
            }
        }
        StorageUsage(categories, puzzles.map { (id, parts) -> StoragePuzzle(id, titles[id] ?: "Untitled puzzle", parts) }.sortedByDescending { it.bytes }, storage.browserStorage)
    }
} catch (error: Exception) { StorageUsage(error = "Could not read storage usage: ${error.message.orEmpty()}") }

internal fun storageSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${(bytes * 10 / 1024) / 10.0} KiB"
    else -> "${(bytes * 100 / (1024 * 1024)) / 100.0} MiB"
}

@Composable
internal fun StorageSettingsDialog(repository: GameRepository, onDismiss: () -> Unit) {
    var usage by remember { mutableStateOf(repository.storageUsage()) }
    var byPuzzle by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storage settings") },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
        dismissButton = { TextButton({ usage = repository.storageUsage() }) { Text("Refresh usage") } },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(if (usage.error == null) "${storageSize(usage.bytes)} used" else "Usage unavailable", style = MaterialTheme.typography.titleLarge)
                    Text("Budget: unavailable", style = MaterialTheme.typography.titleSmall)
                    Text(if (usage.browser) "The browser does not report its local storage limit. Usage estimates include keys and values; browser overhead may differ. This excludes the app download and browser caches." else "The platform does not report a storage budget. Sizes estimate saved keys and values.")
                    usage.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!byPuzzle, { byPuzzle = false }, label = { Text("By category") })
                        FilterChip(byPuzzle, { byPuzzle = true }, label = { Text("By sudoku") })
                    }
                }
                if (byPuzzle) {
                    if (usage.puzzles.isEmpty()) item { Text("No saved puzzles.") }
                    items(usage.puzzles, key = { it.id }) { puzzle ->
                        Column {
                            Text(puzzle.title, style = MaterialTheme.typography.titleSmall)
                            Text(storageSize(puzzle.bytes))
                            puzzle.categories.forEach { (category, bytes) -> Text("$category · ${storageSize(bytes)}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    item { Text("Shared data, such as the catalog and settings, appears under By category.") }
                } else {
                    items(usage.categories.entries.sortedByDescending { it.value }) { (category, bytes) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(category, Modifier.weight(1f))
                            Text(storageSize(bytes))
                        }
                    }
                }
            }
        },
    )
}
