package de.sudokupad

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportValidationTest {
    private val classic = "530070000600195000098000060800060003400803001700020006060000280000419005000080079".replace('0', '.')

    @Test fun recognizesUniquePuzzleWithoutChangingClues() = runTest {
        assertEquals(ImportStatus.Unique, validateClassicImport(classic).status)
        assertEquals(30, classic.count { it != '.' })
    }

    @Test fun reportsAllDuplicateCellsInRowsColumnsAndBoxes() = runTest {
        for (pair in listOf(0 to 8, 0 to 72, 0 to 10)) {
            val grid = CharArray(81) { '.' }.also { it[pair.first] = '4'; it[pair.second] = '4' }.concatToString()
            val result = validateClassicImport(grid)
            assertEquals(ImportStatus.Conflicts, result.status)
            assertEquals(setOf(pair.first, pair.second), result.conflicts)
        }
    }

    @Test fun rejectsUnsolvableGridWithoutDuplicates() = runTest {
        // r1c3=1 is locally legal but contradicts the unique solution.
        val grid = classic.toCharArray().also { it[2] = '1' }.concatToString()
        assertTrue(conflictsFor(Puzzle("", "", grid), Puzzle("", "", grid).startingCells()).isEmpty())
        assertEquals(ImportStatus.Unsolvable, validateClassicImport(grid).status)
        assertEquals(ImportStatus.Unique, validateClassicImport(classic).status)
    }

    @Test fun detectsMultipleSolutionsAndAcceptsCompletedGrid() = runTest {
        assertEquals(ImportStatus.Multiple, validateClassicImport(".".repeat(81)).status)
        assertEquals(ImportStatus.Unique, validateClassicImport(requireNotNull(DemoPuzzle.solution)).status)
    }
}
