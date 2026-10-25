package de.ninefold

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.encodeToString
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GamePersistenceTest {
    @Test fun keepsAllInProgressHistoryAndOnlyTheLastTwoCompletedHistories() {
        val storage = MemoryAppStorage()
        val repository = GameRepository(storage)
        val puzzles = (1..4).map { SudokuFixtures.classic.copy(sourceUrl = "https://sudokupad.app/synthetic-$it") }
        fun saved(puzzle: Puzzle, complete: Boolean) = SavedGame(puzzle,
            if (complete) puzzle.solution!!.map { CellState(value = it.digitToInt()) } else puzzle.startingCells(),
            true, listOf(puzzle.startingCells()), listOf(puzzle.startingCells()))
        puzzles.forEach { repository.saveGame(saved(it, false)) }
        puzzles.take(3).forEach { repository.saveGame(saved(it, true)) }
        repository.markPlayed(puzzles[0])
        val reopened = GameRepository(storage)
        val trimmed = reopened.game(puzzles[0])!!
        assertTrue(trimmed.completed)
        assertEquals(saved(puzzles[0], true).cells, trimmed.cells)
        assertTrue(trimmed.history.isEmpty())
        assertTrue(trimmed.redo.isEmpty())
        puzzles.drop(1).forEach { assertEquals(saved(it, it != puzzles.last()), reopened.game(it)) }

        // Undoing and completing again makes this the latest completion.
        reopened.saveGame(saved(puzzles[1], false))
        reopened.saveGame(saved(puzzles[1], true))
        reopened.saveGame(saved(puzzles[3], true))
        assertTrue(GameRepository(storage).game(puzzles[2])!!.history.isEmpty())
        assertTrue(reopened.game(puzzles[1])!!.history.isNotEmpty())
    }

    @Test fun existingHistoryIsPrunedOnStartupWithoutLosingProgressOrTimers() {
        val storage = MemoryAppStorage()
        val puzzles = (1..3).map { SudokuFixtures.classic.copy(sourceUrl = "https://sudokupad.app/legacy-$it") }
        val solved = SudokuFixtures.classic.solution!!.map { CellState(value = it.digitToInt()) }
        storage.write("ninefold.games.v1", kotlinx.serialization.json.Json.encodeToString(puzzles.map { it.storageId() }))
        storage.write("ninefold.recent-games.v1", kotlinx.serialization.json.Json.encodeToString(puzzles.map { it.storageId() }.reversed()))
        puzzles.forEach { puzzle ->
            val saved = SavedGame(puzzle, solved, true, listOf(puzzle.startingCells()), listOf(solved))
            storage.write("ninefold.game.${puzzle.storageId()}.v1", legacyGamePayload(saved))
        }
        val before = inspectStorage(storage).bytes
        val repository = GameRepository(storage)
        assertTrue(inspectStorage(storage).bytes < before)
        repository.saveElapsed(puzzles[0], 123)
        repository.markPlayed(puzzles[0])
        val reopened = GameRepository(storage)
        assertEquals(solved, reopened.game(puzzles[0])!!.cells)
        assertTrue(reopened.game(puzzles[0])!!.history.isEmpty())
        assertEquals(123, reopened.elapsed(puzzles[0]))
        puzzles.drop(1).forEach { assertTrue(reopened.game(it)!!.history.isNotEmpty()) }
    }

    @Test fun fadedOutsideCluesSurviveReopeningAndCanBeRestored() {
        val clue = PuzzleOverlay(GridPoint(1.5, -.5), width = 0.0, text = "1 6")
        val puzzle = SudokuFixtures.empty.copy(underlays = listOf(clue))
        val storage = MemoryAppStorage()
        val game = SavedGame(puzzle, puzzle.startingCells(), true, fadedOutsideClues = setOf(clue))
        GameRepository(storage).saveGame(game)
        val reopened = GameRepository(storage)
        assertEquals(setOf(clue), reopened.game(puzzle)?.fadedOutsideClues)
        reopened.saveGame(game.copy(fadedOutsideClues = emptySet()))
        assertEquals(emptySet(), GameRepository(storage).game(puzzle)?.fadedOutsideClues)
    }

    @Test fun eagerlyPreparesAllEntriesAndRetriesFailuresWithoutRefetchingReadyPuzzles() = runTest {
        var fail = true
        val requests = mutableListOf<String>()
        val repository = GameRepository(MemoryAppStorage()) { url ->
            requests += url
            if (fail && url.endsWith("3")) throw IllegalArgumentException("Conversion failed")
            SudokuFixtures.classic.copy(sourceUrl = url)
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

    @Test fun savedCatalogPreparationDoesNotFetchMissingPuzzles() = runTest {
        val storage = MemoryAppStorage()
        val entries = (1..2).map { SudokuIndexEntry("$it", "Saved puzzle", "Test", "2026-10-12", "https://sudokupad.app/synthetic-$it") }
        val puzzle = SudokuFixtures.empty.copy(sourceUrl = entries.first().puzzleUrl)
        GameRepository(storage).fetchPuzzle(entries.first()) { puzzle }
        val reopened = GameRepository(storage, fetchPuzzleData = { error("Offline startup must not fetch") })
        reopened.prepare(entries, this, downloadMissing = false)
        advanceUntilIdle()
        assertEquals(DownloadStatus.Ready, reopened.statuses["1"])
        assertEquals(DownloadStatus.Failed, reopened.statuses["2"])
        assertEquals(puzzle, reopened.fetchPuzzle(entries.first()))
    }

    @Test fun savedGameCanOpenFromCatalogWithoutSeparateDownloadRecord() = runTest {
        val storage = MemoryAppStorage()
        val entry = SudokuIndexEntry("saved", "Saved puzzle", "Test", "2026-10-12", "https://sudokupad.app/synthetic-saved")
        val puzzle = SudokuFixtures.empty.copy(sourceUrl = entry.puzzleUrl)
        GameRepository(storage).saveGame(SavedGame(puzzle, puzzle.startingCells(), true))
        val reopened = GameRepository(storage, fetchPuzzleData = { error("Saved game must open offline") })
        reopened.prepare(listOf(entry), this, downloadMissing = false)
        assertEquals(DownloadStatus.Ready, reopened.statuses[entry.id])
        assertEquals(puzzle, reopened.fetchPuzzle(entry))
    }

    @Test fun browseFiltersSurviveReopeningAndClearing() {
        val storage = MemoryAppStorage()
        val repository = GameRepository(storage)
        assertEquals("All", repository.settings.catalogProgress)
        assertEquals("All", repository.settings.catalogDifficulty)
        repository.saveSettings(repository.settings.copy(catalogProgress = "Unsolved", catalogDifficulty = "Hard"))

        val reopened = GameRepository(storage)
        assertEquals("Unsolved", reopened.settings.catalogProgress)
        assertEquals("Hard", reopened.settings.catalogDifficulty)
        reopened.saveSettings(reopened.settings.copy(letters = true))
        assertEquals("Hard", GameRepository(storage).settings.catalogDifficulty)
        reopened.saveSettings(reopened.settings.copy(catalogProgress = "All", catalogDifficulty = "All"))
        assertEquals("All", GameRepository(storage).settings.catalogProgress)
        assertEquals("All", GameRepository(storage).settings.catalogDifficulty)
        assertTrue(GameRepository(storage).settings.letters)
    }

    @Test fun existingSettingsDefaultToUnfilteredBrowse() {
        val storage = MemoryAppStorage()
        storage.write("ninefold.settings.v1", """{"letters":true}""")
        val settings = GameRepository(storage).settings
        assertTrue(settings.letters)
        assertEquals("All", settings.catalogProgress)
        assertEquals("All", settings.catalogDifficulty)
    }

    @Test fun movesNotesHistorySettingsAndTimerSurviveReopening() {
        val storage = MemoryAppStorage()
        val repository = GameRepository(storage)
        val puzzle = SudokuFixtures.classic.copy(fogLights = setOf(CellPosition(0, 0)))
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
        repository.saveGame(SavedGame(SudokuFixtures.classic, SudokuFixtures.classic.startingCells(), true))
        writes.clear()
        repository.saveElapsed(SudokuFixtures.classic, 1)
        repository.saveElapsed(SudokuFixtures.classic, 2)
        assertEquals(2, writes.size)
        assertTrue(writes.all { it.startsWith("ninefold.timer.") })
    }

    @Test fun solvedGamesReopenAndBecomeInProgressAfterUndo() {
        val storage = MemoryAppStorage()
        val repository = GameRepository(storage)
        val solved = SudokuFixtures.classic.solution!!.map { CellState(value = it.digitToInt()) }
        repository.saveGame(SavedGame(SudokuFixtures.classic, solved, true))
        assertTrue(GameRepository(storage).game(SudokuFixtures.classic)!!.completed)
        repository.saveGame(SavedGame(SudokuFixtures.classic, SudokuFixtures.classic.startingCells(), true))
        assertFalse(GameRepository(storage).game(SudokuFixtures.classic)!!.completed)
    }

    @Test fun convertedPuzzleReopensWithoutNetworkAndRetainsArtwork() = runTest {
        val storage = MemoryAppStorage()
        val entry = SudokuIndexEntry("one", "Test", "Test", "2026-10-12", "https://sudokupad.app/test", "https://example.com/video")
        val puzzle = SudokuFixtures.classic.copy(
            sourceUrl = entry.puzzleUrl,
            antiKnight = true,
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
        assertFailsWith<IllegalStateException> { unavailable.fetchPuzzle(entry) { SudokuFixtures.classic } }
        assertNull(unavailable.cachedPuzzle(entry))
        assertNotNull(unavailable.storageError)
    }

    @Test fun reimportReplacesCachedAndSavedPuzzleAndPreservesProgressAcrossRestart() = runTest {
        val storage = MemoryAppStorage()
        val repository = GameRepository(storage)
        val entry = SudokuIndexEntry("one", "Test", "Test", "2026-10-12", "https://sudokupad.app/test", "https://example.com/video")
        val original = repository.fetchPuzzle(entry) { SudokuFixtures.empty.copy(title = "Old", givens = "1" + ".".repeat(80), sourceUrl = entry.puzzleUrl) }
        val cells = original.startingCells().toMutableList().also {
            it[2] = CellState(value = 4, colours = setOf(3))
            it[3] = CellState(centreNotes = setOf(2, 5))
        }
        repository.saveGame(SavedGame(original, cells, true, listOf(cells), listOf(cells)))
        repository.saveElapsed(original, 123)
        var requests = 0
        val replacement = repository.reimportPuzzle(entry.puzzleUrl) { url ->
            assertEquals(entry.puzzleUrl, url)
            requests++
            original.copy(title = "New", givens = ".2" + ".".repeat(79), sourceUrl = "https://sudokupad.app/canonical", videoUrl = null)
        }
        assertEquals(1, requests)
        val reopened = GameRepository(storage)
        assertEquals(replacement, reopened.cachedPuzzle(entry))
        assertEquals(replacement, repository.cachedPuzzle(entry))
        assertEquals(entry.videoUrl, replacement.videoUrl)
        assertEquals(entry.puzzleUrl, replacement.sourceUrl)
        val saved = reopened.game(original)!!
        assertEquals(replacement, saved.puzzle)
        assertEquals(CellState(), saved.cells[0])
        assertEquals(CellState(value = 2, given = true), saved.cells[1])
        assertEquals(cells[2], saved.cells[2])
        assertEquals(cells[3], saved.cells[3])
        assertEquals(listOf(saved.cells), saved.history)
        assertEquals(listOf(saved.cells), saved.redo)
        assertTrue(saved.started)
        assertEquals(123, reopened.elapsed(replacement))
    }

    @Test fun failedReimportKeepsTheOldCacheAndGame() = runTest {
        val storage = MemoryAppStorage()
        val repository = GameRepository(storage)
        val entry = SudokuIndexEntry("one", "Test", "Test", "2026-10-12", "https://sudokupad.app/test")
        val original = repository.fetchPuzzle(entry) { SudokuFixtures.classic.copy(sourceUrl = entry.puzzleUrl) }
        val game = SavedGame(original, original.startingCells(), true)
        repository.saveGame(game)
        assertFailsWith<IllegalArgumentException> {
            repository.reimportPuzzle(entry.puzzleUrl) { error -> throw IllegalArgumentException("Conversion failed: $error") }
        }
        assertEquals(original, repository.cachedPuzzle(entry))
        assertEquals(game, repository.game(original))
        val reopened = GameRepository(storage)
        assertEquals(original, reopened.cachedPuzzle(entry))
        assertEquals(game, reopened.game(original))
    }

    @Test fun failedCacheWriteRollsBackReimportedGame() = runTest {
        val memory = MemoryAppStorage()
        var failCache = false
        val storage = object : AppStorage {
            override fun read(key: String) = memory.read(key)
            override fun write(key: String, payload: String) {
                if (failCache && key.startsWith("ninefold.puzzle.")) error("Storage full")
                memory.write(key, payload)
            }
        }
        val repository = GameRepository(storage)
        val entry = SudokuIndexEntry("one", "Test", "Test", "2026-10-12", "https://sudokupad.app/test")
        val original = repository.fetchPuzzle(entry) { SudokuFixtures.classic.copy(sourceUrl = entry.puzzleUrl) }
        val game = SavedGame(original, original.startingCells(), true)
        repository.saveGame(game)
        failCache = true
        assertFailsWith<IllegalStateException> {
            repository.reimportPuzzle(entry.puzzleUrl) { original.copy(title = "Replacement") }
        }
        assertEquals(original, repository.cachedPuzzle(entry))
        assertEquals(game, repository.game(original))
        val reopened = GameRepository(storage)
        assertEquals(original, reopened.cachedPuzzle(entry))
        assertEquals(game, reopened.game(original))
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
    @Test fun coloursAndTheirHistorySurviveRestartAndOlderCellsStillDecode() {
        val storage = MemoryAppStorage()
        val repository = GameRepository(storage)
        val puzzle = SudokuFixtures.empty
        val initial = puzzle.startingCells()
        val coloured = applyInput(initial, setOf(CellPosition(0, 0)), InputMode.Colour, 9)
        repository.saveSettings(repository.settings.copy(inputMode = "Colour"))
        repository.saveGame(SavedGame(puzzle, initial, started = true, history = listOf(initial), redo = listOf(coloured)))
        val restored = GameRepository(storage)
        assertEquals("Colour", restored.settings.inputMode)
        assertEquals(setOf(9), restored.game(puzzle)!!.redo.single()[0].colours)
        assertEquals(initial, restored.game(puzzle)!!.history.single())
        assertEquals(CellState(value = 5), kotlinx.serialization.json.Json.decodeFromString<CellState>("""{"value":5}"""))
    }

}
