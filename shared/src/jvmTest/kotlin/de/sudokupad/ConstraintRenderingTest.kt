package de.sudokupad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ConstraintRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun linesAndArrowShaftsHaveRoundTipsAndJoinsWithoutWiderJoints() {
        fun points(row: Double) = listOf(GridPoint(row, 1.5), GridPoint(row, 2.5), GridPoint(row + 1, 2.5))
        val puzzle = SudokuFixtures.empty.copy(
            lines = listOf(PuzzleLine(points(1.5), "#888888", 10.0),
                PuzzleLine(points(4.5), "#888888", 10.0, LineRule.Thermometer)),
            arrows = listOf(PuzzleArrow(points(7.5), "#888888", 10.0)),
        )
        compose.setContent {
            MaterialTheme {
                SudokuBoard(puzzle, puzzle.startingCells(), emptySet(), emptySet(),
                    modifier = Modifier.width(450.dp).testTag("board"), onSelect = {})
            }
        }
        val pixels = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        val cell = pixels.width / 9f
        fun sample(row: Double, column: Double) = pixels[(column * cell).toInt(), (row * cell).toInt()]
        val gray = Color(0xFF888888)
        for (row in listOf(1.5, 4.5, 7.5)) {
            assertEquals(gray, sample(row - .04, 2.54), "Round join must fill the outside of the bend")
            assertEquals(Color.White, sample(row - .09, 2.59), "Join at row $row must round off the sharp miter (cell size $cell)")
            assertEquals(Color.White, sample(row + .15, 2.65), "Join must stay the same width as the shaft")
        }
        assertEquals(gray, sample(1.5, 1.44), "Ordinary line starts must have round caps")
        assertEquals(gray, sample(2.56, 2.5), "Ordinary line ends must have round caps")
        assertEquals(gray, sample(5.56, 2.5), "Thermometer tip must have a round cap")
        assertEquals(Color.White, sample(5.65, 2.5), "Thermometer tip must not grow a bulb")
        assertEquals(gray, sample(7.5, 1.44), "Arrow shafts must have round caps")
    }

    @Test fun cageOutlinesCrossSharedBordersAndMeetAtConcaveCorners() {
        val puzzle = SudokuFixtures.empty.copy(cages = listOf(KillerCage(null,
            setOf(CellPosition(0, 0), CellPosition(0, 1), CellPosition(1, 0)))))
        compose.setContent {
            Canvas(Modifier.size(150.dp).testTag("outline")) {
                drawRect(Color.White)
                drawPath(cageOutline(puzzle.cages.single().cells, 50.dp.toPx(), 50.dp.toPx(), 4.dp.toPx()),
                    Color.Black, style = Stroke(2.dp.toPx()))
            }
        }
        val pixels = compose.onNodeWithTag("outline").captureToImage().toPixelMap()
        val scale = pixels.width / 150f
        fun sample(x: Int, y: Int) = pixels[(x * scale).toInt(), (y * scale).toInt()]
        for (x in 44..56) assertEquals(Color.Black, sample(x, 4), "Top edge must cross the shared border")
        for (y in 44..56) assertEquals(Color.Black, sample(4, y), "Left edge must cross the shared border")
        assertEquals(Color.Black, sample(46, 46), "Inset edges must meet at a concave corner")
        assertEquals(Color.White, sample(50, 25), "Shared internal edges must stay open")
    }

    @Test fun rectangularCageHasNoInteriorSeamsOrHoles() {
        val puzzle = SudokuFixtures.empty.copy(cages = listOf(KillerCage(null,
            setOf(CellPosition(0, 0), CellPosition(0, 1), CellPosition(1, 0), CellPosition(1, 1)))))
        compose.setContent {
            Canvas(Modifier.size(100.dp).testTag("outline")) {
                drawRect(Color.White)
                drawPath(cageOutline(puzzle.cages.single().cells, 50.dp.toPx(), 50.dp.toPx(), 4.dp.toPx()), Color.Black)
            }
        }
        val pixels = compose.onNodeWithTag("outline").captureToImage().toPixelMap()
        val scale = pixels.width / 100f
        for (x in 43..57) for (y in 43..57) {
            assertEquals(Color.Black, pixels[(x * scale).toInt(), (y * scale).toInt()], "Four joined cells must form one interior")
        }
    }
}
