package de.sudokupad

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GamePersistenceTest {
    @Test fun eagerlyPreparesAllEntriesAndRetriesFailuresWithoutRefetchingReadyPuzzles() = runTest {
        var fail = true
        val requests = mutableListOf<String>()
        val repository = GameRepository(MemoryAppStorage()) { url ->
            requests += url
            if (fail && url.endsWith("3")) throw IllegalArgumentException("Conversion failed")
            DemoPuzzle.copy(sourceUrl = url)
        }
        val entries = (1..6).map { SudokuIndexEntry("$it", "Test", "Test", "2026-10-12", "https://sudokupad.app/$it") }
        repository.prepare(entries, this)
        advanceUntilIdle()
        assertEquals(6, requests.size)
        assertEquals(5, repository.statuses.values.count { it == DownloadStatus.Ready })
        assertEquals(DownloadStatus.Failed, repository.statuses["3"])
        fail = false
        repository.prepare(entries, this)
        advanceUntilIdle()
        assertEquals(7, requests.size)
        assertTrue(repository.statuses.values.all { it == DownloadStatus.Ready })
    }

    @Test fun movesNotesHistorySettingsAndTimerSurviveReopening() {
        val storage = MemoryAppStorage()
        val repository = GameRepository(storage)
        val puzzle = DemoPuzzle.copy(fogLights = setOf(CellPosition(0, 0)))
        val cells = puzzle.startingCells().toMutableList().also {
            it[2] = CellState(value = 4)
            it[3] = CellState(cornerNotes = setOf(1, 2), centreNotes = setOf(6, 8))
        }
        val game = SavedGame(puzzle, cells, true, listOf(puzzle.startingCells()), listOf(cells))
        repository.saveGame(game)
        repository.saveElapsed(puzzle, 123)
        val settings = CheckingSettings(inputMode = "Corner", multiSelect = true, rules = false, automaticRules = false, specialRules = false, solution = false, automaticSolution = false)
        repository.saveSettings(settings)
        val reopened = GameRepository(storage)
        assertEquals(game, reopened.game(puzzle))
        assertEquals(123, reopened.elapsed(puzzle))
        assertEquals(settings, reopened.settings)
        assertFalse(reopened.game(puzzle)!!.completed)
    }

    @Test fun timerTicksDoNotRewriteBoardOrGameList() {
        val writes = mutableListOf<String>()
        val storage = object : AppStorage {
            override fun read(key: String): String? = null
            override fun write(key: String, payload: String) { writes += key }
        }
        val repository = GameRepository(storage)
        repository.saveGame(SavedGame(DemoPuzzle, DemoPuzzle.startingCells(), true))
        writes.clear()
        repository.saveElapsed(DemoPuzzle, 1)
        repository.saveElapsed(DemoPuzzle, 2)
        assertEquals(2, writes.size)
        assertTrue(writes.all { it.startsWith("ninefold.timer.") })
    }

    @Test fun solvedGamesReopenAndBecomeInProgressAfterUndo() {
        val storage = MemoryAppStorage()
        val repository = GameRepository(storage)
        val solved = DemoPuzzle.solution!!.map { CellState(value = it.digitToInt()) }
        repository.saveGame(SavedGame(DemoPuzzle, solved, true))
        assertTrue(GameRepository(storage).game(DemoPuzzle)!!.completed)
        repository.saveGame(SavedGame(DemoPuzzle, DemoPuzzle.startingCells(), true))
        assertFalse(GameRepository(storage).game(DemoPuzzle)!!.completed)
    }

    @Test fun convertedPuzzleReopensWithoutNetworkAndRetainsArtwork() = runTest {
        val storage = MemoryAppStorage()
        val entry = SudokuIndexEntry("one", "Test", "Test", "2026-10-12", "https://sudokupad.app/test", "https://example.com/video")
        val puzzle = DemoPuzzle.copy(
            sourceUrl = entry.puzzleUrl,
            lines = listOf(PuzzleLine(listOf(GridPoint(.5, .5), GridPoint(1.5, .5)), rule = LineRule.Thermometer)),
            arrows = listOf(PuzzleArrow(listOf(GridPoint(.5, .5)), circle = CellPosition(0, 0))),
            overlays = listOf(PuzzleOverlay(GridPoint(.5, .5), text = "X")),
            outsideClues = listOf(OutsideClue(4, GridPoint(-.5, .5), listOf(CellPosition(0, 0)), OutsideClueType.Skyscraper)),
        )
        val downloaded = GameRepository(storage).fetchPuzzle(entry) { puzzle }
        val reopened = GameRepository(storage)
        assertEquals(puzzle.copy(videoUrl = entry.videoUrl), reopened.cachedPuzzle(entry))
        assertEquals(downloaded, reopened.fetchPuzzle(entry) { error("Must not fetch cached puzzle") })
    }

    @Test fun failedConversionAndFailedStorageNeverBecomeOfflineReady() = runTest {
        val entry = SudokuIndexEntry("one", "Test", "Test", "2026-10-12", "https://sudokupad.app/test")
        val repository = GameRepository(MemoryAppStorage())
        assertFailsWith<IllegalArgumentException> { repository.fetchPuzzle(entry) { throw IllegalArgumentException("Unsupported puzzle") } }
        assertNull(repository.cachedPuzzle(entry))
        val unavailable = GameRepository(object : AppStorage {
            override fun read(key: String): String? = null
            override fun write(key: String, payload: String) { error("Storage full") }
        })
        assertFailsWith<IllegalStateException> { unavailable.fetchPuzzle(entry) { DemoPuzzle } }
        assertNull(unavailable.cachedPuzzle(entry))
        assertNotNull(unavailable.storageError)
    }

    @Test fun restoredClockCountsOnlyTimeAfterResume() {
        var now = 0L
        val clock = GameClock { now }
        clock.restore(123)
        now = 10_000
        assertEquals(123, clock.elapsedSeconds())
        clock.start()
        now = 12_000
        assertEquals(125, clock.elapsedSeconds())
        clock.stop()
        now = 100_000
        assertEquals(125, clock.elapsedSeconds())
    }
}
