package de.ninefold

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThermometerTest {
    private val line = PuzzleLine(listOf(GridPoint(.5, .5), GridPoint(4.5, 4.5), GridPoint(8.5, 8.5)), rule = LineRule.Thermometer)
    private val puzzle = SudokuFixtures.empty.copy(lines = listOf(line))

    @Test fun importsMultipleThermometerBranchesInBulbToTipOrder() {
        val payload = SudokuFixtures.fpuzzlesPayload(properties = """"thermometer":[{"lines":[["R1C1","R2C2","R3C2"],["R1C1","R2C1"]]}]""")
        val imported = parseSudokuPadPayload(payload)
        assertEquals(listOf(LineRule.Thermometer, LineRule.Thermometer), imported.lines.map { it.rule })
        assertEquals(listOf(GridPoint(.5, .5), GridPoint(1.5, 1.5), GridPoint(2.5, 1.5)), imported.lines[0].points)
        assertEquals(listOf(GridPoint(.5, .5), GridPoint(1.5, .5)), imported.lines[1].points)
        assertTrue(imported.lines.all { it.color == "#B8B8B8" && it.thickness == 12.0 })
    }

    @Test fun checksIncreasingDigitsAcrossBlanksWithEnoughRoomForMissingDigits() {
        for ((start, end) in listOf(4 to 3, 4 to 4, 4 to 5, 4 to 6, 1 to 9)) {
            val cells = puzzle.startingCells().toMutableList().also {
                it[0] = CellState(value = start)
                it[80] = CellState(value = end)
            }
            val expected = if (end - start < 2) setOf(0, 80) else emptySet()
            assertEquals(expected, checkSudokuRules(puzzle, cells).cells, "$start, blank, $end")
            assertTrue(checkSudokuRules(puzzle, cells, includeSpecialRules = false).cells.isEmpty())
        }
    }

    @Test fun checksLengthBoundsEvenWhenOnlyOneDigitIsEntered() {
        for ((index, value) in listOf(0 to 8, 40 to 1, 40 to 9, 80 to 2)) {
            val cells = puzzle.startingCells().toMutableList().also { it[index] = CellState(value = value) }
            assertEquals(setOf(index), checkSudokuRules(puzzle, cells).cells)
        }
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(value = 1)
            it[40] = CellState(value = 5)
            it[80] = CellState(value = 9)
        }
        assertTrue(checkSudokuRules(puzzle, cells).cells.isEmpty())
    }

    @Test fun checksBothPencilMarkKindsAgainstLengthAndEnteredDigits() {
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(cornerNotes = setOf(1, 4, 5, 8))
            it[40] = CellState(value = 4)
            it[80] = CellState(centreNotes = setOf(1, 3, 4, 5, 9))
        }
        assertEquals(mapOf(0 to setOf(4, 5, 8), 80 to setOf(1, 3, 4)), checkSudokuRules(puzzle, cells).pencilMarks)
        assertTrue(checkSudokuRules(puzzle, cells, includeSpecialRules = false).pencilMarks.isEmpty())
        cells[40] = CellState()
        assertEquals(mapOf(0 to setOf(8), 80 to setOf(1)), checkSudokuRules(puzzle, cells).pencilMarks)
    }

    @Test fun pencilMarksRespectDistanceAcrossBlankCells() {
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(value = 4)
            it[80] = CellState(cornerNotes = setOf(3, 5, 6, 9))
        }
        assertEquals(mapOf(80 to setOf(3, 5)), checkSudokuRules(puzzle, cells).pencilMarks)
    }

    @Test fun displayOnlyLinesDoNotApplyThermometerRules() {
        val displayOnly = puzzle.copy(lines = listOf(line.copy(rule = LineRule.Unspecified)))
        val cells = displayOnly.startingCells().toMutableList().also {
            it[0] = CellState(value = 9)
            it[80] = CellState(value = 1)
        }
        assertTrue(checkSudokuRules(displayOnly, cells).cells.isEmpty())
    }
}
