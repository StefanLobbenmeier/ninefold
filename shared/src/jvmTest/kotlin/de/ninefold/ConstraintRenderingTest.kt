package de.ninefold

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
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

    @Test fun cagesKeepTheSameSizeAndDashPatternAcrossBoxDividers() {
        val puzzle = SudokuFixtures.empty.copy(cages = (0..8).map {
            KillerCage(null, setOf(CellPosition(it, it)))
        })
        compose.setContent {
            Canvas(Modifier.size(584.dp).testTag("outline")) {
                drawRect(Color.White)
                val gap = 4.dp.toPx()
                val cell = BoardAxis(size.width, gap).cellSize
                val width = 1.dp.toPx()
                val outlines = Path().apply {
                    puzzle.cages.forEach {
                        addPath(cageOutline(it.cells, cell, cell, cageInset(cell), strokeWidth = width, boxGap = gap))
                    }
                }
                clipPath(cageGapMask(cell, cell, strokeWidth = width, boxGap = gap), ClipOp.Difference) {
                    drawIntoCanvas { it.drawPath(outlines, cageOutlinePaint(width)) }
                }
            }
        }
        val pixels = compose.onNodeWithTag("outline").captureToImage().toPixelMap()
        val axis = BoardAxis(pixels.width.toFloat(), pixels.width * 4f / 584)
        val cell = axis.cellSize.roundToInt()
        assertTrue((0 until cell).any { x -> (0 until cell).any { y -> pixels[x, y] != Color.White } })
        for (index in 1..8) {
            val offset = axis.start(index).roundToInt()
            for (x in 0 until cell) for (y in 0 until cell) {
                assertEquals(pixels[x, y], pixels[offset + x, offset + y],
                    "Cage in cell $index must match the first cell, including beside box dividers")
            }
        }
    }

    @Test fun cagePatternRepeatsAndMirrorsAcrossEveryCellBorder() {
        compose.setContent {
            Canvas(Modifier.size(192.dp).testTag("outline")) {
                drawRect(Color.White)
                // Exercise odd and even widths in separate halves of the image.
                for (width in listOf(1f, 2f)) {
                    val cell = size.width / 3
                    translate(0f, if (width == 1f) 0f else size.height / 2) {
                        clipPath(cageGapMask(cell, size.height / 6, 3, 3, strokeWidth = width), ClipOp.Difference) {
                            drawRect(Color.Magenta, size = androidx.compose.ui.geometry.Size(size.width, size.height / 2))
                        }
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("outline").captureToImage().toPixelMap()
        val cellWidth = pixels.width / 3
        val cellHeight = pixels.height / 6
        for (offsetY in listOf(0, pixels.height / 2)) {
            for (x in 0 until cellWidth) for (y in 0 until cellHeight) {
                for (row in 0..2) for (column in 0..2) {
                    assertEquals(pixels[x, y + offsetY], pixels[x + column * cellWidth, y + row * cellHeight + offsetY],
                        "Every cell must use the same pattern")
                }
                assertEquals(pixels[x, y + offsetY], pixels[2 * cellWidth - 1 - x, y + offsetY],
                    "The pattern must mirror across vertical cell borders")
                assertEquals(pixels[x, y + offsetY], pixels[x, 2 * cellHeight - 1 - y + offsetY],
                    "The pattern must mirror across horizontal cell borders")
            }
        }
    }

    @Test fun cageMaskPreservesGridAndGivesInnerAndOuterCornersTheSamePattern() {
        val puzzle = SudokuFixtures.empty.copy(cages = listOf(KillerCage(null,
            setOf(CellPosition(0, 1), CellPosition(1, 0), CellPosition(1, 1)))))
        val purple = Color(0xFF826B87)
        compose.setContent {
            Canvas(Modifier.size(192.dp).testTag("outline")) {
                // Artwork and grid underneath the cage must survive every gap.
                drawRect(Color.Cyan)
                val cell = size.width / 3
                drawLine(Color.Black, androidx.compose.ui.geometry.Offset(cell, 0f),
                    androidx.compose.ui.geometry.Offset(cell, size.height), 2.dp.toPx())
                drawLine(Color.Black, androidx.compose.ui.geometry.Offset(0f, cell),
                    androidx.compose.ui.geometry.Offset(size.width, cell), 2.dp.toPx())
                val outline = cageOutline(puzzle.cages.single().cells, cell, cell, cageInset(cell), strokeWidth = 2.dp.toPx())
                clipPath(cageGapMask(cell, cell, 3, 3, strokeWidth = 2.dp.toPx()), ClipOp.Difference) {
                    drawIntoCanvas { it.drawPath(outline, cageOutlinePaint(2.dp.toPx(), purple)) }
                }
            }
        }
        val pixels = compose.onNodeWithTag("outline").captureToImage().toPixelMap()
        val scale = pixels.width / 192f
        fun sample(x: Int, y: Int) = pixels[(x * scale).toInt(), (y * scale).toInt()]
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            assertEquals(pixels[x, y], pixels[y, x], "Both arms of every inner and outer L must match")
        }
        // The two long runs cross a cell boundary: their dashes must mirror there.
        for (distance in 0 until 55) {
            assertEquals(sample(63 - distance, 124), sample(64 + distance, 124))
            assertEquals(sample(124, 63 - distance), sample(124, 64 + distance))
        }
        for (coordinate in 0..191) {
            assertEquals(Color.Black, sample(64, coordinate), "The cage gap must preserve the underlying vertical grid line")
            assertEquals(Color.Black, sample(coordinate, 64), "The cage gap must preserve the underlying horizontal grid line")
        }
        assertEquals(Color.Cyan, sample(62, 54), "Artwork outside the grid stroke must remain visible")
        assertEquals(purple, sample(68, 68), "The concave elbow must remain joined")
    }

    @Test fun fractionalCellSizesAndOddAndEvenStrokesHaveSolidColourAndUncutShafts() {
        val puzzle = SudokuFixtures.empty.copy(cages = listOf(KillerCage(null,
            setOf(CellPosition(0, 1), CellPosition(1, 0), CellPosition(1, 1)))))
        val cases = listOf(31f to 1f, 50f to 1f, 60.333f to 1f, 83f to 1f, 60f to 2f, 83f to 3f)
        val purple = Color(0xFF826B87)
        compose.setContent {
            Canvas(Modifier.size(540.dp, 360.dp).testTag("outline")) {
                drawRect(Color.White)
                cases.forEachIndexed { index, (cellDp, width) ->
                    val cell = cellDp.dp.toPx()
                    translate((index % 3 * 180).dp.toPx().roundToInt().toFloat(),
                        (index / 3 * 180).dp.toPx().roundToInt().toFloat()) {
                        val outline = cageOutline(puzzle.cages.single().cells, cell, cell,
                            cageInset(cell), strokeWidth = width)
                        clipPath(cageGapMask(cell, cell, 3, 3, strokeWidth = width), ClipOp.Difference) {
                            drawIntoCanvas { it.drawPath(outline, cageOutlinePaint(width, purple)) }
                        }
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("outline").captureToImage().toPixelMap()
        var coloured = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val colour = pixels[x, y]
            if (colour != Color.White) {
                coloured++
                assertEquals(purple, colour, "No partial coverage or darker corner at ($x, $y)")
            }
        }
        assertTrue(coloured > 0)
        val scale = pixels.width / 540f
        cases.forEachIndexed { index, (cell, width) ->
            val left = (index % 3 * 180 * scale).roundToInt()
            val top = (index / 3 * 180 * scale).roundToInt()
            var shafts = 0
            for (x in (1.25f * cell * scale).roundToInt()..(1.75f * cell * scale).roundToInt()) {
                val thickness = (0 until (cell * scale / 4).toInt()).count { y -> pixels[left + x, top + y] == purple }
                if (thickness > 0) {
                    shafts++
                    assertEquals(width.toInt(), thickness, "Parallel mask strips must not shave the straight shaft")
                }
            }
            assertTrue(shafts > 0)
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
