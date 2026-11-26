package de.ninefold

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagonalTest {
    @Test fun fpuzzlesFlagsIndependentlyDrawTheMatchingDiagonals() {
        val rising = listOf(GridPoint(0.0, 4.0), GridPoint(4.0, 0.0))
        val falling = listOf(GridPoint(0.0, 0.0), GridPoint(4.0, 4.0))
        for ((positive, negative) in listOf(true to false, false to true, true to true, false to false)) {
            val puzzle = parseSudokuPadPayload(SudokuFixtures.fpuzzlesPayload(
                givens = ".".repeat(16),
                properties = """"diagonal+":$positive,"diagonal-":$negative""",
            ))
            val expected = listOfNotNull(rising.takeIf { positive }, falling.takeIf { negative })
            assertEquals(expected, puzzle.lines.map { it.points })
            assertTrue(puzzle.lines.all { it.rule == LineRule.Diagonal })
            assertTrue(puzzle.importOmissions.isEmpty())
        }
    }

    @Test fun sclReusesTheArtworkOnlyForAnEnabledDiagonal() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties =
            """
            "diagonal-":true,"lines":[
              {"wayPoints":[[9,9],[0,0]],"color":"#123456"},
              {"wayPoints":[[0,9],[9,0]],"color":"#123456"}
            ]
            """.trimIndent(),
        ))
        assertEquals(listOf(LineRule.Diagonal, LineRule.Unspecified), puzzle.lines.map { it.rule })
        assertEquals("#123456", puzzle.lines.first().color)
    }

    @Test fun diagonalChecksAndSwipeIncludeIntermediateCells() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.fpuzzlesPayload(properties = """"diagonal+":true"""))
        val source = CellPosition(2, 6)
        val through = CellPosition(3, 5)
        val target = CellPosition(4, 4)
        val outside = CellPosition(3, 4)
        val notes = CellState(cornerNotes = setOf(2, 4, 7), centreNotes = setOf(2, 4, 7))
        val cells = puzzle.startingCells().toMutableList().apply {
            this[source.index] = CellState(value = 4)
            this[target.index] = notes
            this[outside.index] = notes
        }

        assertEquals(PuzzleCheckResult(pencilMarks = mapOf(target.index to setOf(4))), checkSudokuRules(puzzle, cells))
        assertEquals(PuzzleCheckResult(), checkSudokuRules(puzzle, cells, includeSpecialRules = false))
        val path = listOf(source, through, target, outside)
        val cleared = clearDraggedPencilmarks(puzzle, cells, path, path.toSet())
        assertEquals(CellState(cornerNotes = setOf(2, 7), centreNotes = setOf(2, 7)), cleared[target.index])
        assertEquals(notes, cleared[outside.index])

        cells[target.index] = CellState(value = 4)
        assertEquals(setOf(source.index, target.index), checkSudokuRules(puzzle, cells).cells)
        assertTrue(checkSudokuRules(puzzle, cells, includeSpecialRules = false).cells.isEmpty())
    }
}
