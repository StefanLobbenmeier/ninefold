package de.ninefold

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Snapshots stay convenient for the player; disk history stores only changed cells. */
internal object SavedGameSerializer : KSerializer<SavedGame> {
    override val descriptor = StoredGame.serializer().descriptor

    override fun serialize(encoder: Encoder, value: SavedGame) {
        encoder.encodeSerializableValue(StoredGame.serializer(), StoredGame(
            puzzle = value.puzzle, cells = value.cells, started = value.started,
            automaticColours = value.automaticColours, fadedOutsideClues = value.fadedOutsideClues,
            historyEncoding = 2,
            undoChanges = changes(value.cells, value.history.asReversed()),
            redoChanges = changes(value.cells, value.redo),
        ))
    }

    override fun deserialize(decoder: Decoder): SavedGame {
        val stored = decoder.decodeSerializableValue(StoredGame.serializer())
        require(stored.historyEncoding in 1..2) { "Unsupported history encoding" }
        return SavedGame(stored.puzzle, stored.cells, stored.started,
            if (stored.historyEncoding == 1) stored.history else restore(stored.cells, stored.undoChanges).asReversed(),
            if (stored.historyEncoding == 1) stored.redo else restore(stored.cells, stored.redoChanges),
            stored.automaticColours, stored.fadedOutsideClues)
    }

    // Each patch transforms the preceding board into the next board along this stack.
    private fun changes(cells: List<CellState>, snapshots: List<List<CellState>>): List<Map<Int, CellState>> {
        var previous = cells
        return snapshots.map { snapshot ->
            require(snapshot.size == cells.size) { "History grid size differs from current grid" }
            snapshot.indices.filter { snapshot[it] != previous[it] }.associateWith { snapshot[it] }
                .also { previous = snapshot }
        }
    }

    private fun restore(cells: List<CellState>, changes: List<Map<Int, CellState>>): List<List<CellState>> {
        var previous = cells
        return changes.map { patch ->
            val next = previous.toMutableList()
            patch.forEach { (index, cell) ->
                require(index in next.indices) { "History cell is outside the grid" }
                next[index] = cell
            }
            next.toList().also { previous = it }
        }
    }
}

@Serializable
private data class StoredGame(
    val puzzle: Puzzle, val cells: List<CellState>, val started: Boolean,
    // Legacy fields are read but never written by the new encoder.
    val history: List<List<CellState>> = emptyList(), val redo: List<List<CellState>> = emptyList(),
    val automaticColours: Map<Int, AutomaticColourRule> = DefaultAutomaticColours,
    val fadedOutsideClues: Set<PuzzleOverlay> = emptySet(),
    val historyEncoding: Int = 1,
    val undoChanges: List<Map<Int, CellState>> = emptyList(),
    val redoChanges: List<Map<Int, CellState>> = emptyList(),
)
