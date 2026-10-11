package de.sudokupad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun parsesCommonClassicFormats() {
        assertEquals(DemoPuzzle.givens, parseClassicGrid(DemoPuzzle.givens.chunked(9).joinToString("\n")))
        assertNotNull(parseClassicGrid(DemoPuzzle.givens.replace('.', '0')))
    }

    @Test
    fun detectsRowAndSolutionConflicts() {
        val cells = DemoPuzzle.startingCells().toMutableList()
        cells[2] = CellState(value = 5)
        val conflicts = conflictsFor(DemoPuzzle, cells)
        assertTrue(0 in conflicts)
        assertTrue(2 in conflicts)
    }

    @Test
    fun detectsBrokenKillerSum() {
        val puzzle = Puzzle("Cage", "Test", ".".repeat(81), cages = listOf(KillerCage(3, setOf(CellPosition(0, 0), CellPosition(0, 1)))))
        val cells = puzzle.startingCells().toMutableList()
        cells[0] = CellState(value = 2)
        cells[1] = CellState(value = 2)
        assertTrue(conflictsFor(puzzle, cells).containsAll(listOf(0, 1)))
    }
}
