package de.sudokupad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.roundToInt

class ConstraintRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun thinCageDashesHaveOneUniformColourIncludingTheirInnerCorner() {
        val puzzle = SudokuFixtures.empty.copy(cages = listOf(KillerCage(null,
            setOf(CellPosition(0, 1), CellPosition(1, 0), CellPosition(1, 1)))))
        val purple = Color(0xFF826B87)
        compose.setContent {
            Canvas(Modifier.size(181.dp).testTag("outline")) {
                drawRect(Color.White)
                val width = 1.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
                val path = cageDashes(puzzle.cages.single().cells, size.width / 3, size.height / 3,
                    4.dp.toPx(), width, 4.dp.toPx(), 3.dp.toPx())
                drawIntoCanvas { it.drawPath(path, cageOutlinePaint(width, purple)) }
            }
        }
        val pixels = compose.onNodeWithTag("outline").captureToImage().toPixelMap()
        var colouredPixels = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val pixel = pixels[x, y]
            if (pixel != Color.White) {
                colouredPixels++
                assertEquals(purple, pixel, "Cage pixel ($x, $y) must not have a gray fringe or darker overlap")
            }
        }
        assertTrue(colouredPixels > 0)
    }

    @Test fun straightRunsHaveWholeEqualDashesAndEqualGapsIncludingBothCornerEnds() {
        // Convex/convex, convex/concave and concave/concave runs differ by 2 insets.
        for (cell in listOf(31f, 50f, 60.333f, 83f)) for (span in 1..5) {
            for (insetAdjustment in listOf(-8f, 0f, 8f)) {
                val length = cell * span + insetAdjustment
                val intervals = cageDashIntervals(length, arm = 3.5f, dash = 4f, preferredGap = 3f)
                assertTrue(intervals.isNotEmpty())
                val gaps = mutableListOf(intervals.first().first - 3.5f)
                intervals.forEachIndexed { index, (start, end) ->
                    assertEquals(4f, end - start, .0001f, "Every dash must have its full length")
                    if (index > 0) gaps += start - intervals[index - 1].second
                }
                gaps += length - 3.5f - intervals.last().second
                gaps.forEach { assertEquals(gaps.first(), it, .0001f, "Gaps must be even up to both Ls") }
                assertTrue(gaps.first() > 0f)
            }
        }
    }

    @Test fun innerAndOuterCornersAndStraightDashesRenderSymmetrically() {
        val puzzle = SudokuFixtures.empty.copy(cages = listOf(KillerCage(null,
            setOf(CellPosition(0, 1), CellPosition(1, 0), CellPosition(1, 1)))))
        compose.setContent {
            Canvas(Modifier.size(180.dp).testTag("outline")) {
                drawRect(Color.White)
                drawIntoCanvas { it.drawPath(cageDashes(puzzle.cages.single().cells,
                    60.dp.toPx(), 60.dp.toPx(), 4.dp.toPx(), 1f, 4.dp.toPx(), 3.dp.toPx()),
                    cageOutlinePaint(1f)) }
            }
        }
        val pixels = compose.onNodeWithTag("outline").captureToImage().toPixelMap()
        val scale = pixels.width / 180f
        val elbows = listOf(listOf(64, 4, 1, 1), listOf(116, 4, -1, 1),
            listOf(116, 116, -1, -1), listOf(4, 116, 1, -1),
            listOf(4, 64, 1, 1), listOf(64, 64, -1, -1))
        for ((x, y, horizontal, vertical) in elbows) {
            val cornerX = (x * scale).toInt()
            val cornerY = (y * scale).toInt()
            val armPixels = (4 * scale).roundToInt()
            for ((dx, dy) in listOf(horizontal to 0, 0 to vertical)) {
                for (distance in 0..armPixels) {
                    val expected = if (distance < armPixels) Color(0xFF826B87) else Color.White
                    assertEquals(expected, pixels[cornerX + dx * distance, cornerY + dy * distance],
                        "Corner ($x, $y) must have equal full arms in every orientation")
                }
            }
        }
        // This L and its concave corner are symmetric about the main diagonal.
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            assertEquals(pixels[x, y], pixels[y, x], "Reflection at ($x, $y) must preserve arms and gaps")
        }
    }

    @Test fun cageDashesReachTheFarEdgeOfACornerAndOverlapsKeepTheirColour() {
        compose.setContent {
            Canvas(Modifier.size(80.dp).testTag("outline")) {
                drawRect(Color.White)
                val path = Path().apply {
                    moveTo(20.dp.toPx(), 40.dp.toPx())
                    lineTo(40.dp.toPx(), 40.dp.toPx())
                    lineTo(40.dp.toPx(), 20.dp.toPx())
                    moveTo(30.dp.toPx(), 30.dp.toPx())
                    lineTo(30.dp.toPx(), 50.dp.toPx())
                }
                val paint = cageOutlinePaint(4.dp.toPx(), Color.Black.copy(alpha = .5f))
                drawIntoCanvas { it.drawPath(path, paint) }
            }
        }
        val pixels = compose.onNodeWithTag("outline").captureToImage().toPixelMap()
        val scale = pixels.width / 80f
        fun sample(x: Int, y: Int) = pixels[(x * scale).toInt(), (y * scale).toInt()]
        val stroke = sample(25, 40)
        assertEquals(stroke, sample(41, 41), "Corner dash must extend half a stroke beyond its centerline")
        assertEquals(stroke, sample(30, 40), "Overlapping translucent strokes must retain the shaft colour")
    }

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
