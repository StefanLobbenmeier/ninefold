package de.ninefold

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class CatalogProgress(val label: String) {
    All("Any progress"), Unsolved("Not solved"), Solved("Solved");

    fun matches(completed: Boolean): Boolean = when (this) {
        All -> true
        Unsolved -> !completed
        Solved -> completed
    }
}

internal enum class CatalogDifficulty(val label: String) {
    All("Any difficulty"), Easy("Easy · under 30 min"), Medium("Medium · 30–60 min"),
    Hard("Hard · 60+ min"), Unknown("Unknown · no video length");

    fun matches(seconds: Int?): Boolean = when (this) {
        All -> true
        Easy -> seconds != null && seconds in 1 until 1800
        Medium -> seconds != null && seconds in 1800 until 3600
        Hard -> seconds != null && seconds >= 3600
        Unknown -> seconds == null || seconds <= 0
    }
}

@Composable
internal fun CatalogFilters(
    count: Int, total: Int, progress: CatalogProgress, difficulty: CatalogDifficulty,
    onProgress: (CatalogProgress) -> Unit, onDifficulty: (CatalogDifficulty) -> Unit, onReset: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = (if (progress != CatalogProgress.All) 1 else 0) + (if (difficulty != CatalogDifficulty.All) 1 else 0)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (active == 0) "$total puzzles" else "$count of $total puzzles",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Box {
            TextButton({ expanded = true }) {
                AppIcon(Glyph.Settings)
                Spacer(Modifier.width(6.dp))
                Text(if (active == 0) "Filters" else "Filters · $active")
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.widthIn(max = 320.dp)) {
                Text("Progress", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                CatalogProgress.entries.forEach { option ->
                    FilterOption(option.label, progress == option) { onProgress(option) }
                }
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Estimated difficulty", style = MaterialTheme.typography.labelMedium)
                    Text("Based on video length, not your solve time.", style = MaterialTheme.typography.bodySmall)
                }
                CatalogDifficulty.entries.forEach { option ->
                    FilterOption(option.label, difficulty == option) { onDifficulty(option) }
                }
                if (active > 0) {
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Clear filters") }, onClick = onReset)
                }
            }
        }
    }
}

@Composable
private fun FilterOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) }, onClick = onClick,
        modifier = Modifier.semantics { selected = isSelected },
        trailingIcon = { if (isSelected) AppIcon(Glyph.Check) },
    )
}
