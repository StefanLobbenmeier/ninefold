package de.sudokupad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DynamicFogImportTest {
    @Test fun importedDynamicRevealsRespectAnswersGivensUndoAndSharing() {
        val payload = SudokuFixtures.sudokuPadPayload(givens = "5" + ".".repeat(80), properties = """
            "metadata":{"solution":"${SudokuFixtures.classic.solution}"}, "foglight":[[0,1]],
            "triggereffect":[
                {"trigger":{"type":"cellvalue","cell":"r1c1"},"effect":{"type":"foglight","cells":"r1c1"}},
                {"trigger":{"type":"cellvalue","cell":"r2c2"},"effect":{"type":"foglight","cells":"r2c2,r3c3"}},
                {"trigger":{"type":"cellvalue","cell":"r1c1-r9c9"},"effect":{"type":"foglight","cells":"r1c1-r9c9"}}
            ]""")
        val puzzle = parseSudokuPadPayload(payload)
        val initial = setOf(CellPosition(0, 0), CellPosition(0, 1))
        val revealed = initial + setOf(CellPosition(1, 1), CellPosition(2, 2))
        assertEquals(setOf(CellPosition(0, 1)), puzzle.fogLights)
        assertEquals(3, assertNotNull(puzzle.fogLinks).size)
        assertEquals(initial, puzzle.litCells(puzzle.startingCells()))

        val cells = puzzle.startingCells().toMutableList()
        cells[10] = CellState(value = 8) // Wrong answer must not trigger a reveal.
        assertEquals(initial, puzzle.litCells(cells))
        cells[10] = CellState(value = 7)
        assertEquals(revealed, puzzle.litCells(cells))
        cells[10] = CellState() // Undo refogs the revealed cells.
        assertEquals(initial, puzzle.litCells(cells))
        cells[10] = CellState(value = 7)

        val shared = assertNotNull(puzzleFromShareLink(puzzleShareLink(puzzle)))
        assertEquals(puzzle.fogLinks, shared.fogLinks)
        assertEquals(revealed, shared.litCells(cells))
        val solved = puzzle.solution!!.map { CellState(value = it.digitToInt()) }
        assertEquals(81, puzzle.litCells(solved).size)
    }
}
