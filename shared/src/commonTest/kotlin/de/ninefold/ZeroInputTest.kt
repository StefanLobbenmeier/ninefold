package de.ninefold

import kotlin.test.Test
import kotlin.test.assertEquals

class ZeroInputTest {
    @Test fun zeroIsAValueAndAToggleableNote() {
        val cells = SudokuFixtures.empty.startingCells()
        val selected = setOf(CellPosition(0, 0))
        for (mode in listOf(InputMode.Digit, InputMode.Corner, InputMode.Centre)) {
            val entered = applyInput(cells, selected, mode, 0)
            assertEquals(if (mode == InputMode.Digit) 0 else null, entered[0].value)
            assertEquals(if (mode == InputMode.Corner) setOf(0) else emptySet(), entered[0].cornerNotes)
            assertEquals(if (mode == InputMode.Centre) setOf(0) else emptySet(), entered[0].centreNotes)
            assertEquals(cells, applyInput(entered, selected, mode, null))
            if (mode != InputMode.Digit) assertEquals(cells, applyInput(entered, selected, mode, 0))
        }
        assertEquals(cells, applyInput(cells, selected, InputMode.Colour, 0))
    }

    @Test fun zeroCountsAsNumericForCagesAndSolutions() {
        val puzzle = SudokuFixtures.empty.copy(
            solution = "0" + "1".repeat(80),
            cages = listOf(KillerCage(3, setOf(CellPosition(0, 0), CellPosition(0, 1)))),
        )
        val cells = puzzle.startingCells().toMutableList()
        cells[0] = CellState(value = 0)
        cells[1] = CellState(value = 2)
        assertEquals(setOf(0, 1), checkSudokuRules(puzzle, cells).cells)
        assertEquals(setOf(1), checkAgainstSolution(puzzle, cells).cells)
        cells[1] = CellState(value = 3)
        assertEquals(emptySet(), checkSudokuRules(puzzle, cells).cells)
        cells[1] = CellState(value = 0)
        assertEquals(setOf(0, 1), checkSudokuRules(puzzle, cells).cells)
        assertEquals(setOf(1), checkAgainstSolution(puzzle, cells).cells)
    }
}
