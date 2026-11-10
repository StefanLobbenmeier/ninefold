package de.ninefold

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputAutomationTest {
    private val puzzle = SudokuFixtures.empty
    private val first = setOf(CellPosition(0, 0))

    @Test fun promotionWaitsForDeselectAndPreservesHiddenNotes() {
        val original = CellState(cornerNotes = setOf(2, 8), centreNotes = setOf(3), colours = setOf(4))
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = original
            it[1] = CellState(centreNotes = setOf(5))
            it[2] = CellState(centreNotes = setOf(6, 7))
            it[3] = CellState(value = 8, centreNotes = setOf(9))
            it[4] = CellState(given = true, centreNotes = setOf(1))
        }
        assertEquals(cells, finalizeCellInput(puzzle, cells, emptySet(), first, CheckingSettings()))
        val leaving = (0..4).map { CellPosition(0, it) }.toSet() - CellPosition(0, 1)
        val promoted = finalizeCellInput(puzzle, cells, leaving, setOf(CellPosition(0, 1)), CheckingSettings())
        assertEquals(original.copy(value = 3), promoted[0])
        assertEquals(cells.drop(1), promoted.drop(1))
        assertEquals(original, applyInput(promoted, first, InputMode.Digit, null)[0])
    }

    @Test fun automationsCanBeDisabledIndependently() {
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(cornerNotes = setOf(2, 8), centreNotes = setOf(3))
            it[1] = CellState(cornerNotes = setOf(2, 8), centreNotes = setOf(3, 8))
        }
        val leaving = first + CellPosition(0, 1)
        for (promote in listOf(false, true)) for (cleanup in listOf(false, true)) {
            val settings = CheckingSettings(promoteSingleCentreDigit = promote, centreDigitsRemoveCornerDigits = cleanup)
            val result = finalizeCellInput(puzzle, cells, leaving, emptySet(), settings)
            assertEquals(if (promote) 3 else null, result[0].value)
            assertEquals(if (cleanup && !promote) emptySet() else setOf(2, 8), result[0].cornerNotes)
            assertEquals(if (cleanup) setOf(8) else setOf(2, 8), result[1].cornerNotes)
        }
    }

    @Test fun replacingAndErasingDigitsRestoresPencilMarks() {
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(cornerNotes = setOf(2, 8), centreNotes = setOf(3, 5))
        }
        val entered = applyInput(cells, first, InputMode.Digit, 7)
        assertEquals(cells[0].copy(value = 7), entered[0])
        val replaced = applyInput(entered, first, InputMode.Digit, 9)
        val storage = MemoryAppStorage()
        GameRepository(storage).saveGame(SavedGame(puzzle, replaced, started = true))
        val restored = GameRepository(storage).game(puzzle)!!.cells
        assertEquals(cells, applyInput(restored, first, InputMode.Digit, null))
    }

    @Test fun selectingMatchingNotesSkipsHiddenNotes() {
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(cornerNotes = setOf(2), centreNotes = setOf(3))
            it[1] = it[0].copy(value = 7)
            it[2] = it[0]
        }
        for (mode in listOf(InputMode.Corner, InputMode.Centre)) {
            assertEquals(first + CellPosition(0, 2), toolSensitiveSelection(cells, CellPosition(0, 0), mode))
        }
    }

    @Test fun hiddenMarksDoNotAffectChecksOrValidateOtherCornerMarks() {
        val puzzle = puzzle.copy(solution = SudokuFixtures.classic.solution)
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(value = 5, cornerNotes = setOf(5, 9), centreNotes = setOf(9))
            it[1] = CellState(value = 9)
            it[2] = CellState(cornerNotes = setOf(5))
        }
        val withoutHidden = cells.toMutableList().also { it[0] = CellState(value = 5) }
        assertEquals(checkSudokuRules(puzzle, withoutHidden), checkSudokuRules(puzzle, cells))
        assertEquals(checkAgainstSolution(puzzle, withoutHidden), checkAgainstSolution(puzzle, cells))
        assertEquals(setOf(5), checkAgainstSolution(puzzle, cells).pencilMarks[2])
    }

    @Test fun automationDefaultsAndPreferencesPersist() {
        for (legacy in listOf(false, true)) {
            val storage = MemoryAppStorage()
            if (legacy) storage.write("ninefold.settings.v1", """{"rules":false}""")
            val repository = GameRepository(storage)
            assertTrue(repository.settings.promoteSingleCentreDigit)
            assertTrue(repository.settings.centreDigitsRemoveCornerDigits)
            for (promote in listOf(false, true)) for (cleanup in listOf(false, true)) {
                val settings = repository.settings.copy(promoteSingleCentreDigit = promote, centreDigitsRemoveCornerDigits = cleanup)
                repository.saveSettings(settings)
                assertEquals(settings, GameRepository(storage).settings)
            }
        }
    }
}
