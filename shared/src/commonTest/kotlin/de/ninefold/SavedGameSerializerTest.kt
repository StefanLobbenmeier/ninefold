package de.ninefold

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

internal fun legacyGamePayload(game: SavedGame): String = JsonObject(
    Json.encodeToJsonElement(game).jsonObject.toMutableMap().apply {
        remove("historyEncoding")
        remove("undoChanges")
        remove("redoChanges")
        put("history", Json.encodeToJsonElement(game.history))
        put("redo", Json.encodeToJsonElement(game.redo))
    }
).toString()

class SavedGameSerializerTest {
    @Test fun roundTripsMultipleUndoAndRedoStepsIncludingGroupedEditsAndClears() {
        val puzzle = SudokuFixtures.classic
        val initial = puzzle.startingCells()
        val notes = initial.toMutableList().also {
            it[2] = CellState(cornerNotes = setOf(1, 2), centreNotes = setOf(3, 4), colours = setOf(2, 7))
            it[3] = CellState(value = 6)
        }
        val cleared = notes.toMutableList().also { it[2] = CellState(); it[3] = CellState() }
        val later = cleared.toMutableList().also { it[5] = CellState(value = 8) }
        // Duplicate snapshots are valid no-op steps and must keep their place.
        val game = SavedGame(puzzle, notes, true, listOf(initial, initial), listOf(cleared, later))
        val payload = Json.encodeToString(game)
        assertFalse(Json.parseToJsonElement(payload).jsonObject.containsKey("history"))
        assertEquals(game, Json.decodeFromString<SavedGame>(payload))
        val storage = MemoryAppStorage()
        GameRepository(storage).saveGame(game)
        val restored = GameRepository(storage).game(puzzle)!!
        assertEquals(game, restored)
        // Save after undo, then after redo, as the player moves the stacks around.
        val undone = restored.copy(cells = initial, history = listOf(initial), redo = listOf(notes, cleared, later))
        GameRepository(storage).saveGame(undone)
        assertEquals(undone, GameRepository(storage).game(puzzle))
        GameRepository(storage).saveGame(game)
        assertEquals(game, GameRepository(storage).game(puzzle))
    }

    @Test fun migratesLegacySnapshotsOnStartupAndSubstantiallyReducesPayload() {
        val puzzle = SudokuFixtures.classic
        val snapshots = (0..60).map { step -> puzzle.startingCells().toMutableList().also {
            it[2] = CellState(centreNotes = setOf(step % 9 + 1), colours = setOf(step % 4))
        } }
        val game = SavedGame(puzzle, snapshots.last(), true, snapshots.dropLast(1), listOf(snapshots.first()))
        val legacy = legacyGamePayload(game)
        assertEquals(game, Json.decodeFromString<SavedGame>(legacy))
        val storage = MemoryAppStorage()
        val id = puzzle.storageId()
        storage.write("ninefold.games.v1", Json.encodeToString(listOf(id)))
        storage.write("ninefold.game.$id.v1", legacy)
        assertEquals(game, GameRepository(storage).game(puzzle))
        val migrated = storage.read("ninefold.game.$id.v1")!!
        assertTrue(migrated.length < legacy.length / 3, "Expected delta encoding to remove repeated grids")
        assertEquals(game, GameRepository(storage).game(puzzle))
        assertEquals(2, Json.parseToJsonElement(migrated).jsonObject.getValue("historyEncoding").jsonPrimitive.int)
    }

    @Test fun worksWithSmallerGrids() {
        val puzzle = SudokuFixtures.empty.copy(givens = ".".repeat(36))
        val initial = puzzle.startingCells()
        val cells = initial.toMutableList().also { it[35] = CellState(value = 6) }
        val game = SavedGame(puzzle, cells, true, listOf(initial))
        assertEquals(game, Json.decodeFromString<SavedGame>(Json.encodeToString(game)))
    }

    @Test fun failedMigrationPreservesLegacyRecordAndRetriesOnNextStartup() {
        val puzzle = SudokuFixtures.classic
        val game = SavedGame(puzzle, puzzle.startingCells(), true, listOf(puzzle.startingCells()))
        val recordKey = "ninefold.game.${puzzle.storageId()}.v1"
        val legacy = legacyGamePayload(game)
        val memory = MemoryAppStorage()
        memory.write("ninefold.games.v1", Json.encodeToString(listOf(puzzle.storageId())))
        memory.write(recordKey, legacy)
        val failing = object : AppStorage {
            override fun read(key: String) = memory.read(key)
            override fun write(key: String, payload: String) {
                if (key == recordKey) error("Storage unavailable")
                memory.write(key, payload)
            }
        }
        val repository = GameRepository(failing)
        assertEquals(game, repository.game(puzzle))
        assertNotNull(repository.storageError)
        assertEquals(legacy, memory.read(recordKey))
        assertEquals(game, GameRepository(memory).game(puzzle))
        assertNotEquals(legacy, memory.read(recordKey))
    }
}
