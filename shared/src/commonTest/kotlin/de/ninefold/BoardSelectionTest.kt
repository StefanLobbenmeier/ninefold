package de.ninefold

import kotlin.test.*

class BoardSelectionTest {
    @Test fun singleSelectionTapTogglesOnlyTheSoleSelectedCell() {
        val first = CellPosition(0, 0)
        val second = CellPosition(0, 1)
        assertEquals(emptySet(), BoardSelectionGesture(setOf(first), first, false).tap())
        assertEquals(setOf(second), BoardSelectionGesture(setOf(first), second, false).tap())
        assertEquals(setOf(first), BoardSelectionGesture(emptySet(), first, false).tap())
        assertEquals(setOf(first), BoardSelectionGesture(setOf(first, second), first, false).tap())
        assertEquals(setOf(second), BoardSelectionGesture(setOf(first, second), second, false).tap())
    }

    @Test fun draggingFromTheSoleSelectedCellKeepsItSelected() {
        val first = CellPosition(0, 0)
        val second = CellPosition(0, 1)
        val gesture = BoardSelectionGesture(setOf(first), first, false)
        assertEquals(setOf(first), gesture.visit())
        assertEquals(setOf(first, second), gesture.visit(setOf(second)))
    }

    @Test fun toolSensitiveSelectionUsesTheActiveModesMatchingRules() {
        val cells = List(81) { CellState() }.toMutableList().also {
            it[0] = CellState(value = 4)
            it[1] = CellState(value = 4)
            it[9] = CellState(centreNotes = setOf(1, 2))
            it[10] = CellState(centreNotes = setOf(1, 2, 9))
            it[18] = CellState(cornerNotes = setOf(3))
            it[19] = CellState(cornerNotes = setOf(3, 8))
            it[27] = CellState(colours = setOf(7))
            it[28] = CellState(colours = setOf(7, 9))
        }

        assertEquals(setOf(CellPosition(0, 0), CellPosition(0, 1)), toolSensitiveSelection(cells, CellPosition(0, 0), InputMode.Digit))
        assertEquals(setOf(CellPosition(1, 0), CellPosition(1, 1)), toolSensitiveSelection(cells, CellPosition(1, 0), InputMode.Centre))
        assertEquals(setOf(CellPosition(2, 0), CellPosition(2, 1)), toolSensitiveSelection(cells, CellPosition(2, 0), InputMode.Corner))
        assertEquals(setOf(CellPosition(3, 0), CellPosition(3, 1)), toolSensitiveSelection(cells, CellPosition(3, 0), InputMode.Colour))
    }

    @Test fun toolSensitiveSelectionFallsBackWhenThePreferredPropertyIsEmpty() {
        val cells = List(81) { CellState() }.toMutableList().also {
            it[0] = CellState(cornerNotes = setOf(6))
            it[10] = CellState(cornerNotes = setOf(5, 6))
        }
        assertEquals(setOf(CellPosition(0, 0), CellPosition(1, 1)), toolSensitiveSelection(cells, CellPosition(0, 0), InputMode.Centre))
        assertEquals(emptySet(), toolSensitiveSelection(cells, CellPosition(8, 8), InputMode.Digit))
    }

    @Test fun removalKeepsUntouchedCellsAndNeverAddsOrRetogglesVisitedCells() {
        val first = CellPosition(0, 0)
        val second = CellPosition(0, 1)
        val untouched = CellPosition(1, 0)
        val unselected = CellPosition(0, 2)
        val gesture = BoardSelectionGesture(setOf(first, second, untouched), first, additive = true)
        assertEquals(setOf(second, untouched), gesture.visit())
        assertEquals(setOf(untouched), gesture.visit(setOf(second, unselected)))
        assertEquals(setOf(untouched), gesture.visit(setOf(first, second)))
    }

    @Test fun nonAdditiveGestureStartsFreshEvenOnASelectedCell() {
        val first = CellPosition(0, 0)
        val second = CellPosition(0, 1)
        val untouched = CellPosition(1, 0)
        val unselected = CellPosition(0, 2)
        val gesture = BoardSelectionGesture(setOf(first, second, untouched), first, additive = false)
        assertEquals(setOf(first), gesture.visit())
        assertEquals(setOf(first, second, unselected), gesture.visit(setOf(second, unselected)))
        assertEquals(setOf(first, second, unselected), gesture.visit(setOf(first, second)))
    }

    @Test fun addingKeepsItsOperationAcrossPreviouslySelectedCells() {
        val first = CellPosition(0, 0)
        val second = CellPosition(0, 1)
        val untouched = CellPosition(1, 0)
        val initial = setOf(second, untouched)
        assertEquals(setOf(first, second), BoardSelectionGesture(initial, first, false).visit(setOf(second)))
        assertEquals(setOf(first, second, untouched), BoardSelectionGesture(initial, first, true).visit(setOf(second)))
    }

    @Test fun selectedBlockOnlyHasBordersOnItsPerimeter() {
        val block = (0..2).flatMap { row -> (0..2).map { CellPosition(row, it) } }.toSet()
        assertEquals(emptySet(), selectionBorders(CellPosition(1, 1), block))
        assertEquals(setOf(SelectionBorder.Top, SelectionBorder.Left), selectionBorders(CellPosition(0, 0), block))
        assertEquals(setOf(SelectionBorder.Right, SelectionBorder.Bottom), selectionBorders(CellPosition(2, 2), block))
        assertEquals(emptySet(), selectionBorders(CellPosition(3, 3), block))
    }

    @Test fun outlinesFollowHolesConcaveCornersAndDisconnectedCells() {
        val block = (0..2).flatMap { row -> (0..2).map { CellPosition(row, it) } }.toSet()
        val ring = block - CellPosition(1, 1)
        assertEquals(setOf(SelectionBorder.Top, SelectionBorder.Bottom), selectionBorders(CellPosition(0, 1), ring))
        val corner = setOf(CellPosition(0, 0), CellPosition(0, 1), CellPosition(1, 0))
        assertEquals(setOf(SelectionBorder.Top, SelectionBorder.Right, SelectionBorder.Bottom), selectionBorders(CellPosition(0, 1), corner))
        val diagonal = setOf(CellPosition(0, 0), CellPosition(1, 1), CellPosition(8, 8))
        diagonal.forEach { assertEquals(SelectionBorder.entries.toSet(), selectionBorders(it, diagonal)) }
    }

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

    @Test fun clicksKeepFullCellTargetsButDragsUseInnerThreeQuarters() {
        assertEquals(CellPosition(0, 0), boardCellAt(.1f / 9, .5f / 9))
        assertEquals(emptySet(), boardCellsAlongSegment(.05f / 9, .5f / 9, .1f / 9, .5f / 9))
        assertEquals(setOf(CellPosition(0, 0)), boardCellsAlongSegment(.15f / 9, .15f / 9, .85f / 9, .15f / 9))
        assertEquals(setOf(CellPosition(0, 0)), boardCellsAlongSegment(.85f / 9, .15f / 9, .85f / 9, .85f / 9))
    }

    @Test fun multipleSelectionDragsUseFullCellTargets() {
        assertEquals(setOf(CellPosition(0, 0)),
            boardCellsAlongSegment(.01f / 9, .01f / 9, .99f / 9, .01f / 9, additive = true))
        assertEquals(setOf(CellPosition(0, 0)),
            boardCellsAlongSegment(.99f / 9, .01f / 9, .99f / 9, .99f / 9, additive = true))
        assertEquals((0..8).map { CellPosition(0, it) }.toSet(),
            boardCellsAlongSegment(0f, .01f / 9, 1f, .01f / 9, additive = true))
    }

    @Test fun invalidCoordinatesDoNotSelectCells() {
        assertEquals(emptySet(), boardCellsAlongSegment(Float.NaN, .5f, .5f, .5f))
        assertEquals(emptySet(), boardCellsAlongSegment(.5f, .5f, Float.POSITIVE_INFINITY, .5f))
    }
}
