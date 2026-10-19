package de.ninefold

import kotlin.test.Test
import kotlin.test.assertEquals

class BoardGeometryTest {
    @Test fun onlyOutsideTextCanBeFaded() {
        val clue = PuzzleOverlay(GridPoint(1.5, -.5), width = 0.0, text = "1 6")
        kotlin.test.assertTrue(clue.isOutsideTextClue())
        kotlin.test.assertTrue(clue.copy(center = GridPoint(9.5, 1.5)).isOutsideTextClue())
        kotlin.test.assertFalse(clue.copy(center = GridPoint(1.5, 1.5)).isOutsideTextClue())
        kotlin.test.assertFalse(clue.copy(text = " ").isOutsideTextClue())
        kotlin.test.assertFalse(clue.copy(text = null).isOutsideTextClue())
    }

    @Test fun ordinaryGridNeedsNoOutsideMargin() {
        assertEquals(0f, SudokuFixtures.classic.boardPaddingCells())
    }

    @Test fun decorativeArrowsReserveSpaceOnEverySide() {
        val points = listOf(GridPoint(-.8, 4.5), GridPoint(9.8, 4.5),
            GridPoint(4.5, -.8), GridPoint(4.5, 9.8))
        points.forEach { point ->
            val puzzle = SudokuFixtures.empty.copy(lines = listOf(PuzzleLine(listOf(GridPoint(4.5, 4.5), point))))
            assertEquals(1f, puzzle.boardPaddingCells())
        }
    }

    @Test fun distantArtworkIncludesStrokeAndArrowHead() {
        val line = PuzzleLine(listOf(GridPoint(4.5, 9.5), GridPoint(4.5, 11.0)), thickness = 10.0)
        val puzzle = SudokuFixtures.empty.copy(lines = listOf(line))
        assertEquals(2.1f, puzzle.boardPaddingCells(), .00001f)
        val arrow = PuzzleArrow(line.points, thickness = 10.0, headLength = .4)
        assertEquals(2.5f, puzzle.copy(lines = emptyList(), arrows = listOf(arrow)).boardPaddingCells())
    }

    @Test fun overlayEdgesCountEvenWhenTheirCentersAreInside() {
        val mark = PuzzleOverlay(GridPoint(0.0, 4.5), width = 1.0, height = 3.0)
        val puzzle = SudokuFixtures.empty.copy(overlays = listOf(mark))
        assertEquals(1.5f, puzzle.boardPaddingCells())
        assertEquals(1.5f, puzzle.copy(overlays = emptyList(), underlays = listOf(mark)).boardPaddingCells())
    }

    @Test fun collapsedBoardKeepsCellMeasurementsNonnegative() {
        for (extent in listOf(0f, 1f, 2f)) {
            val axis = BoardAxis(extent, 2f)
            assertEquals(0f, axis.cellSize)
            assertEquals(extent, axis.start(8) + axis.cellSize)
        }
    }

    @Test fun boxDividersReserveExtraSpaceWithoutReducingCellInteriors() {
        val axis = BoardAxis(452f, 1f)
        assertEquals(50f, axis.cellSize)
        for (index in 0..8) {
            assertEquals(index / 9f + .5f / 9, axis.normalized(axis.at(index + .5)), .00001f)
            if (index < 8) {
                val expectedGap = if (index % 3 == 2) 1f else 0f
                assertEquals(expectedGap, axis.start(index + 1) - axis.start(index) - axis.cellSize)
            }
        }
        assertEquals(150.5f, axis.at(3.0))
        assertEquals(301.5f, axis.at(6.0))
        assertEquals(1 / 3f, axis.normalized(150.5f))
        assertEquals(2 / 3f, axis.normalized(301.5f))
        assertEquals(452f, axis.at(9.0))
        assertEquals(-25f, axis.at(-.5))
        assertEquals(477f, axis.at(9.5))
    }
}
