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

    @Test fun imperfectDiagonalSkipsOrthogonalNeighbors() {
        assertEquals(setOf(CellPosition(0, 0), CellPosition(1, 1)),
            boardCellsAlongSegment(.5f / 9, .5f / 9, 1.5f / 9, 1.8f / 9))
    }

    @Test fun dragCanDodgeCellsAlongGridLines() {
        val path = listOf(.5f / 9 to .5f / 9, 1f / 9 to .5f / 9,
            1f / 9 to 1.5f / 9, 1.5f / 9 to 1.5f / 9)
        val visited = path.zipWithNext().flatMap { (start, end) ->
            boardCellsAlongSegment(start.first, start.second, end.first, end.second)
        }.toSet()
        assertEquals(setOf(CellPosition(0, 0), CellPosition(1, 1)), visited)
        assertEquals(emptySet(), boardCellsAlongSegment(0f, 1f / 9, 1f, 1f / 9))
    }

    @Test fun clicksKeepFullCellTargetsButDragsUseOnlyInnerHalf() {
        assertEquals(CellPosition(0, 0), boardCellAt(.1f / 9, .5f / 9))
        assertEquals(emptySet(), boardCellsAlongSegment(.1f / 9, .5f / 9, .2f / 9, .5f / 9))
        assertEquals(setOf(CellPosition(0, 0)), boardCellsAlongSegment(.3f / 9, .3f / 9, .7f / 9, .7f / 9))
    }

    @Test fun invalidCoordinatesDoNotSelectCells() {
        assertEquals(emptySet(), boardCellsAlongSegment(Float.NaN, .5f, .5f, .5f))
        assertEquals(emptySet(), boardCellsAlongSegment(.5f, .5f, Float.POSITIVE_INFINITY, .5f))
    }
}
