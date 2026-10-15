package de.sudokupad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DynamicFogImportTest {
    /** Original payload from https://sudokupad.app/hkzobuop6w, by Jorava. */
    @Test fun wetBlanketsRetainsItsDynamicReveals() {
        val payload = javaClass.getResource("/puzzles/hkzobuop6w.scl")!!.readText()
        val puzzle = parseSudokuPadPayload(payload, "https://sudokupad.app/hkzobuop6w")
        assertEquals("Wet Blankets 2: Soggy Monday", puzzle.title)
        assertEquals(93, assertNotNull(puzzle.fogLinks).size)
        assertEquals(setOf(CellPosition(3, 3), CellPosition(4, 3), CellPosition(5, 3),
            CellPosition(5, 5), CellPosition(5, 1)), puzzle.fogLights)

        val cells = puzzle.startingCells().toMutableList()
        // The given also has a self-reveal link.
        val initial = puzzle.fogLights!! + CellPosition(4, 5)
        assertEquals(initial, puzzle.litCells(cells))
        val trigger = CellPosition(3, 5)
        val target = CellPosition(4, 7)
        val answer = puzzle.solution!![trigger.index].digitToInt()
        cells[trigger.index] = CellState(value = answer % 9 + 1)
        assertEquals(initial, puzzle.litCells(cells))
        cells[trigger.index] = CellState(value = answer)
        assertEquals(initial + trigger + target, puzzle.litCells(cells))
        assertEquals(initial, puzzle.litCells(puzzle.startingCells()))

        val shared = assertNotNull(puzzleFromShareLink(puzzleShareLink(puzzle)))
        assertEquals(puzzle.fogLinks, shared.fogLinks)
        assertEquals(initial + trigger + target, shared.litCells(cells))
        val solved = puzzle.solution.map { CellState(value = it.digitToInt()) }
        assertEquals(81, puzzle.litCells(solved).size)
    }
}
