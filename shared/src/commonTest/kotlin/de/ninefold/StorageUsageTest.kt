package de.ninefold

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class StorageUsageTest {
    @Test fun groupsPersistedRecordsByCategoryAndPuzzle() {
        val storage = MemoryAppStorage()
        val puzzle = SudokuFixtures.empty.copy(title = "Storage test", sourceUrl = "https://sudokupad.app/synthetic")
        val repository = GameRepository(storage)
        repository.saveGame(SavedGame(puzzle, puzzle.startingCells(), true))
        repository.saveElapsed(puzzle, 42)
        storage.write("ninefold.puzzle.${puzzle.sourceUrl}.v1", Json.encodeToString(puzzle))
        storage.write("ninefold.sudoku-index.v1", "{}")
        val usage = repository.storageUsage()
        val saved = usage.puzzles.single()
        assertEquals("Storage test", saved.title)
        assertEquals(setOf("Offline puzzles", "Progress and undo history", "Timers"), saved.categories.keys)
        assertEquals(storage.entries().entries.sumOf { (key, value) -> key.encodeToByteArray().size.toLong() + value.encodeToByteArray().size }, usage.bytes)
        assertEquals(usage.bytes, saved.bytes + usage.categories.getValue("Game lists") + usage.categories.getValue("Puzzle catalog"))
    }

    @Test fun browserCountsUtf16KeysAndValuesAndIncludesUnrelatedSiteData() {
        val storage = object : AppStorage {
            override val browserStorage = true
            override fun entries() = mapOf("other" to "😀")
            override fun read(key: String): String? = null
            override fun write(key: String, payload: String) = Unit
        }
        assertEquals(14L, inspectStorage(storage).categories.getValue("Other site data"))
    }

    @Test fun unreadableStorageReportsError() {
        val storage = object : AppStorage {
            override fun entries(): Map<String, String> = error("Blocked")
            override fun read(key: String): String? = null
            override fun write(key: String, payload: String) = Unit
        }
        assertContains(inspectStorage(storage).error.orEmpty(), "Blocked")
    }
}
