package de.ninefold

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LetterInputTest {
    @Test fun lettersUseAllThreeInputTools() {
        val selected = setOf(CellPosition(0, 0))
        val empty = SudokuFixtures.empty.startingCells()
        for (mode in listOf(InputMode.Digit, InputMode.Corner, InputMode.Centre)) {
            val entered = applyInput(empty, selected, mode, 10)
            assertEquals(if (mode == InputMode.Digit) 10 else null, entered[0].value)
            assertEquals(if (mode == InputMode.Corner) setOf(10) else emptySet(), entered[0].cornerNotes)
            assertEquals(if (mode == InputMode.Centre) setOf(10) else emptySet(), entered[0].centreNotes)
            assertEquals(empty, applyInput(entered, selected, mode, null))
        }
        assertEquals(GameShortcut.Enter(10, InputMode.Centre), gameShortcut(Key.A, true, false, letters = true))
        assertEquals(GameShortcut.Enter(35, null), gameShortcut(Key.Z, false, false, alt = true, letters = true))
    }

    @Test fun lettersConflictWithPeersButIgnoreSumsAndSolution() {
        val puzzle = SudokuFixtures.classic.copy(givens = ".".repeat(81), cages = listOf(KillerCage(3, setOf(CellPosition(0, 0), CellPosition(0, 1)))))
        val cells = puzzle.startingCells().toMutableList()
        cells[0] = CellState(value = 10)
        cells[1] = CellState(value = 2)
        cells[9] = CellState(cornerNotes = setOf(10), centreNotes = setOf(11))
        assertTrue(checkSudokuRules(puzzle, cells).cells.isEmpty())
        assertEquals(setOf(10), checkSudokuRules(puzzle, cells).pencilMarks[9])
        assertTrue(checkAgainstSolution(puzzle, cells).pencilMarks.isEmpty())
        assertTrue(0 !in checkAgainstSolution(puzzle, cells).cells)
        cells[1] = CellState(value = 10)
        assertEquals(setOf(0, 1), checkSudokuRules(puzzle, cells).cells)
    }
}
