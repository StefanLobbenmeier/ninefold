package de.ninefold

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArrowImportTest {
    @Test fun sparseDiagonalArrowChecksTheIntermediateDigit() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties = """
            "overlays":[{"center":[2.5,2.5],"width":0.8,"height":0.8,"rounded":true}],
            "arrows":[{"wayPoints":[[2.2,2.2],[0.5,0.5]]}]
        """))
        assertEquals(CellPosition(2, 2), puzzle.arrows.single().circle)
        assertEquals(listOf(CellPosition(2, 2), CellPosition(1, 1), CellPosition(0, 0)), puzzle.arrows.single().pathCells)
        val cells = puzzle.startingCells().toMutableList().apply {
            this[20] = CellState(value = 9)
            this[10] = CellState(value = 8)
            this[0] = CellState(value = 1)
        }
        assertTrue(checkSudokuRules(puzzle, cells).cells.isEmpty())
        cells[10] = CellState()
        assertTrue(checkSudokuRules(puzzle, cells).cells.isEmpty())
        cells[10] = CellState(value = 7)
        assertEquals(setOf(0, 10, 20), checkSudokuRules(puzzle, cells).cells)
    }

    @Test fun tracesHorizontalVerticalAndBentShaftsInOrder() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties = """
            "arrows":[{"wayPoints":[[0.5,0.5],[0.5,2.5],[2.5,2.5],[0.5,0.5]]}]
        """))
        assertEquals(listOf(CellPosition(0, 0), CellPosition(0, 1), CellPosition(0, 2),
            CellPosition(1, 2), CellPosition(2, 2), CellPosition(1, 1)), puzzle.arrows.single().pathCells)
    }

    @Test fun tracesSparseFpuzzlesArrows() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.fpuzzlesPayload(properties = """
            "arrow":[{"cells":["R1C1"],"lines":[["R1C1","R3C3"]]}]
        """))
        assertEquals(listOf(CellPosition(0, 0), CellPosition(1, 1), CellPosition(2, 2)), puzzle.arrows.single().pathCells)
    }

    @Test fun clipsOutsideSegmentsToTheGrid() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties = """
            "arrows":[{"wayPoints":[[-0.5,0.5],[2.5,0.5]]},{"wayPoints":[[-0.5,0.5],[-0.5,2.5]]}]
        """))
        assertEquals(listOf(CellPosition(0, 0), CellPosition(1, 0), CellPosition(2, 0)), puzzle.arrows[0].pathCells)
        assertTrue(puzzle.arrows[1].pathCells.isEmpty())
    }
}
