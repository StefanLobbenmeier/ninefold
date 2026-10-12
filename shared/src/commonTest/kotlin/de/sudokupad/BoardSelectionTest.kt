package de.sudokupad

import kotlin.test.*

class BoardSelectionTest {
    @Test fun fastSweepIncludesEveryCrossedCell() {
        assertEquals((0..8).map { CellPosition(0, it) }.toSet(), boardCellsAlongSegment(.01f, .05f, .99f, .05f))
        assertEquals((0..8).map { CellPosition(it, 4) }.toSet(), boardCellsAlongSegment(.5f, .99f, .5f, .01f))
    }

    @Test fun diagonalSweepAndRevisitsDoNotSelectNeighbors() {
        val expected = (0..8).map { CellPosition(it, it) }.toSet()
        val forward = boardCellsAlongSegment(.01f, .01f, .99f, .99f)
        assertEquals(expected, forward)
        assertEquals(expected, forward + boardCellsAlongSegment(.99f, .99f, .01f, .01f))
    }

    @Test fun outsideCluesAndLeavingBoardDoNotClampToEdgeCells() {
        assertNull(boardCellAt(-.1f, .5f))
        assertNull(boardCellAt(1f, .5f))
        assertEquals(emptySet(), boardCellsAlongSegment(-.2f, -.2f, -.1f, -.1f))
        assertEquals((0..8).map { CellPosition(4, it) }.toSet(), boardCellsAlongSegment(-.2f, .5f, 1.2f, .5f))
    }
}
