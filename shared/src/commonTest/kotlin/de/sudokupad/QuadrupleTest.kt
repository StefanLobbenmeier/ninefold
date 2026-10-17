package de.sudokupad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuadrupleTest {
    @Test fun importsSplitNumericLabelsAsOneReadableClue() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties =
            """"metadata":{"rules":"Quadruples: circled digits appear in the surrounding cells."},"overlays":[
                {"center":[1,1],"width":0.62,"height":0.62,"rounded":true,"text":"","backgroundColor":"#FFFFFF"},
                {"center":[0.88,0.915],"width":0.1,"height":0.1,"text":1},
                {"center":[0.88,1.085],"width":0.1,"height":0.1,"text":2},
                {"center":[1.12,1],"width":0.1,"height":0.1,"text":3}]"""))
        assertEquals("1 2\n3", puzzle.overlays.single().text)
        assertEquals(listOf(1, 2, 3), puzzle.quadruples.single().digits)
        assertEquals(listOf(0, 1, 9, 10), puzzle.quadruples.single().cells.map { it.index })
        assertEquals(puzzle, puzzleFromShareLink(puzzleShareLink(puzzle)))
    }

    @Test fun checksMissingDigitsAndPencilMarksOnlyWhenSpecialRulesEnabled() {
        val clue = QuadrupleClue(listOf(1, 2, 3), listOf(CellPosition(0, 0), CellPosition(0, 1), CellPosition(1, 0), CellPosition(1, 1)))
        val puzzle = SudokuFixtures.empty.copy(quadruples = listOf(clue))
        val cells = puzzle.startingCells().toMutableList()
        cells[0] = CellState(value = 1)
        cells[1] = CellState(value = 4)
        cells[9] = CellState(value = 2)
        cells[10] = CellState(centreNotes = setOf(3, 5))
        assertEquals(setOf(5), checkSudokuRules(puzzle, cells).pencilMarks[10])
        cells[10] = CellState(value = 5)
        assertEquals(setOf(0, 1, 9, 10), checkSudokuRules(puzzle, cells).cells)
        assertTrue(checkSudokuRules(puzzle, cells, includeSpecialRules = false).cells.isEmpty())
        cells[10] = CellState(value = 3)
        assertTrue(checkSudokuRules(puzzle, cells).cells.isEmpty())
    }

    @Test fun repeatedClueDigitsRequireMultipleOccurrences() {
        val puzzle = SudokuFixtures.empty.copy(quadruples = listOf(QuadrupleClue(listOf(1, 1),
            listOf(CellPosition(2, 2), CellPosition(2, 3), CellPosition(3, 2), CellPosition(3, 3)))))
        val cells = puzzle.startingCells().toMutableList()
        cells[20] = CellState(value = 1)
        cells[21] = CellState(value = 2)
        cells[29] = CellState(value = 3)
        cells[30] = CellState(value = 4)
        assertEquals(setOf(20, 21, 29, 30), checkSudokuRules(puzzle, cells).cells)
        cells[30] = CellState(value = 1)
        assertTrue(checkSudokuRules(puzzle, cells).cells.isEmpty())
    }
}
