package de.ninefold

import kotlin.test.Test
import kotlin.test.assertEquals

class CheckingSettingsTest {
    @Test fun automaticChecksSelectDigitsAndPencilmarksIndependently() {
        val puzzle = SudokuFixtures.empty.copy(solution = SudokuFixtures.classic.solution)
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(value = 1)
            it[1] = CellState(value = 1)
            it[2] = CellState(centreNotes = setOf(1))
        }
        for (digits in listOf(false, true)) for (marks in listOf(false, true)) {
            val settings = CheckingSettings(automaticRules = digits, automaticRulePencilmarks = marks,
                automaticSolution = digits, automaticSolutionPencilmarks = marks)
            val expected = PuzzleCheckResult(if (digits) setOf(0, 1) else emptySet(),
                if (marks) mapOf(2 to setOf(1)) else emptyMap())
            assertEquals(expected, settings.rulesResult(puzzle, cells))
            assertEquals(expected, settings.solutionResult(puzzle, cells))
            val all = PuzzleCheckResult(setOf(0, 1), mapOf(2 to setOf(1)))
            assertEquals(all, settings.rulesResult(puzzle, cells, manual = true))
            assertEquals(all, settings.solutionResult(puzzle, cells, manual = true))
            val disabled = settings.copy(rules = false, solution = false)
            assertEquals(PuzzleCheckResult(), disabled.rulesResult(puzzle, cells, manual = true))
            assertEquals(PuzzleCheckResult(), disabled.solutionResult(puzzle, cells, manual = true))
        }
    }

    @Test fun specialRulesApplyToBothAutomaticControls() {
        val puzzle = SudokuFixtures.empty.copy(antiKing = true)
        val cells = puzzle.startingCells().toMutableList().also {
            it[20] = CellState(value = 5)
            it[30] = CellState(value = 5)
            it[21] = CellState(centreNotes = setOf(6))
            it[29] = CellState(value = 6)
        }
        val digitsOnly = CheckingSettings(automaticRulePencilmarks = false)
        val marksOnly = CheckingSettings(automaticRules = false, automaticRulePencilmarks = true)
        assertEquals(PuzzleCheckResult(cells = setOf(20, 30)), digitsOnly.rulesResult(puzzle, cells))
        assertEquals(PuzzleCheckResult(pencilMarks = mapOf(21 to setOf(6))), marksOnly.rulesResult(puzzle, cells))
        assertEquals(PuzzleCheckResult(), digitsOnly.copy(specialRules = false).rulesResult(puzzle, cells))
        assertEquals(PuzzleCheckResult(), marksOnly.copy(specialRules = false).rulesResult(puzzle, cells))
    }

    @Test fun legacyPreferencesCarryOverToPencilmarksAndNewPreferencesPersistIndependently() {
        for (automatic in listOf(false, true)) {
            val storage = MemoryAppStorage()
            storage.write("ninefold.settings.v1", """{"automaticRules":$automatic,"automaticSolution":$automatic}""")
            val repository = GameRepository(storage)
            assertEquals(automatic, repository.settings.automaticRulePencilmarks)
            assertEquals(automatic, repository.settings.automaticSolutionPencilmarks)
            val settings = repository.settings.copy(automaticRulePencilmarks = !automatic, automaticSolutionPencilmarks = !automatic)
            repository.saveSettings(settings)
            assertEquals(settings, GameRepository(storage).settings)
        }
    }
}
