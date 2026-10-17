package de.ninefold

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportValidationTest {
    private val classic = SudokuFixtures.classic.givens

    @Test fun recognizesUniquePuzzleWithoutChangingClues() = runTest {
        val result = validateClassicImport(classic)
        assertEquals(ImportStatus.Unique, result.status)
        assertEquals(SudokuFixtures.classic.solution, result.solution)
        assertEquals(30, classic.count { it != '.' })
    }

    @Test fun retainedSolutionDetectsWrongDigitsThatDoNotBreakClassicRules() = runTest {
        val result = validateClassicImport(classic)
        val puzzle = SudokuFixtures.classic.copy(solution = result.solution)
        val cells = puzzle.startingCells().toMutableList().also { it[2] = CellState(value = 1) }
        assertTrue(checkSudokuRules(puzzle, cells).cells.isEmpty())
        assertEquals(setOf(2), checkAgainstSolution(puzzle, cells).cells)
    }

    @Test fun reportsAllDuplicateCellsInRowsColumnsAndBoxes() = runTest {
        for (pair in listOf(0 to 8, 0 to 72, 0 to 10)) {
            val grid = CharArray(81) { '.' }.also { it[pair.first] = '4'; it[pair.second] = '4' }.concatToString()
            val result = validateClassicImport(grid)
            assertEquals(ImportStatus.Conflicts, result.status)
            assertEquals(setOf(pair.first, pair.second), result.conflicts)
            assertNull(result.solution)
        }
    }

    @Test fun rejectsUnsolvableGridWithoutDuplicates() = runTest {
        // r1c3=1 is locally legal but contradicts the unique solution.
        val grid = classic.toCharArray().also { it[2] = '1' }.concatToString()
        val puzzle = SudokuFixtures.empty.copy(givens = grid)
        assertTrue(conflictsFor(puzzle, puzzle.startingCells()).isEmpty())
        val result = validateClassicImport(grid)
        assertEquals(ImportStatus.Unsolvable, result.status)
        assertNull(result.solution)
        assertEquals(ImportStatus.Unique, validateClassicImport(classic).status)
    }

    @Test fun detectsMultipleSolutionsAndAcceptsCompletedGrid() = runTest {
        val multiple = validateClassicImport(SudokuFixtures.empty.givens)
        assertEquals(ImportStatus.Multiple, multiple.status)
        assertNull(multiple.solution)
        val completed = validateClassicImport(requireNotNull(SudokuFixtures.classic.solution))
        assertEquals(ImportStatus.Unique, completed.status)
        assertEquals(SudokuFixtures.classic.solution, completed.solution)
    }
}
