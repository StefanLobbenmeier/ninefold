package de.ninefold

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KropkiRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun whiteClueBackingOnlyHidesGridAndDarkCellsHaveNoTextRectangle() {
        val clue = PuzzleOverlay(GridPoint(2.0, 2.5), .8, .8, "#FFFFFF", text = "X")
        val puzzle = SudokuFixtures.empty.copy(overlays = listOf(clue))
        val cells = puzzle.startingCells().map { it.copy(colours = setOf(CellColour.DarkGrey.number)) }.toMutableList()
        cells[14] = cells[14].copy(value = 7)
        cells[20] = cells[20].copy(centreNotes = setOf(1, 2))
        compose.setContent {
            MaterialTheme {
                SudokuBoard(puzzle, cells, emptySet(), emptySet(),
                    modifier = Modifier.width(450.dp).testTag("board"), onSelect = {})
            }
        }
        val pixels = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        val inset = pixels.width * 2f / 450
        val axis = BoardAxis(pixels.width - inset * 2, pixels.width / 450f)
        fun sample(row: Double, column: Double) = pixels[
            (inset + axis.at(column)).toInt(), (inset + axis.at(row)).toInt()]
        val fill = sample(1.2, 5.2)
        assertEquals(fill, sample(2.0, 2.2), "Clue backing must remove the grid without hiding colour")
        for (row in 125..175) for (column in 535..565) {
            val pixel = sample(row / 100.0, column / 100.0)
            assertTrue(pixel.red <= fill.red + .01f, "Entered digit must have no white text rectangle")
        }
        assertNotEquals(fill, sample(2.0, 1.2), "Grid outside the clue must remain visible")
    }

    @Test fun arbitraryArtworkCoversThinAndBoxGridLines() {
        // Larger circles and square artwork must not depend on Kropki/quadruple recognition.
        val marks = listOf(
            PuzzleOverlay(GridPoint(2.0, 2.5), .8, .8, "#FF0000", rounded = true),
            PuzzleOverlay(GridPoint(3.0, 4.5), .8, .8, "#FF0000", rounded = true),
            PuzzleOverlay(GridPoint(5.5, 3.0), .8, .8, "#FF0000", rounded = false),
        )
        val puzzle = SudokuFixtures.empty.copy(overlays = marks.take(2), underlays = marks.drop(2))
        compose.setContent {
            MaterialTheme {
                SudokuBoard(puzzle, puzzle.startingCells(), emptySet(), emptySet(),
                    modifier = Modifier.width(450.dp).testTag("board"), onSelect = {})
            }
        }
        val pixels = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        // The board reserves a two-dp inset on each side.
        val inset = pixels.width * 2f / 450
        val axis = BoardAxis(pixels.width - inset * 2, pixels.width / 450f)
        marks.forEach { mark ->
            val x = (inset + axis.at(mark.center.column)).toInt()
            val y = (inset + axis.at(mark.center.row)).toInt()
            assertEquals(Color.Red, pixels[x, y], "Artwork must cover every grid line")
        }
    }

    @Test fun dotsCoverGridLinesAndRenderAtThreeQuartersSize() {
        val marks = listOf(2.0, 3.0).flatMap { edge ->
            listOf(GridPoint(edge, 2.5), GridPoint(5.5, edge)).map { center ->
                PuzzleOverlay(center, .4, .4, "#FFFFFF", "#000000", rounded = true)
            }
        }.let { white -> white + white.map { it.copy(center = it.center.copy(row = it.center.row + 3), backgroundColor = "#000000") } }
        val puzzle = SudokuFixtures.empty.copy(overlays = marks.take(4), underlays = marks.drop(4))
        compose.setContent {
            MaterialTheme {
                SudokuBoard(puzzle, puzzle.startingCells(), emptySet(), emptySet(),
                    modifier = Modifier.width(450.dp).testTag("board"), onSelect = {})
            }
        }
        val pixels = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        val cell = pixels.width / 9f
        marks.forEach { mark ->
            val x = (mark.center.column * cell).toInt()
            val y = (mark.center.row * cell).toInt()
            val horizontal = mark.center.row % 1.0 == 0.0
            val inside = (cell * .08f).toInt()
            val outside = (cell * .18f).toInt()
            val fill = if (mark.backgroundColor == "#FFFFFF") Color.White else Color.Black
            assertEquals(fill, pixels[x, y], "Dot center must cover the grid")
            assertEquals(fill, pixels[x + if (horizontal) inside else 0, y + if (horizontal) 0 else inside])
            // The old radius was .2 cells; the new radius is .15 cells.
            assertEquals(Color.White, pixels[x + if (horizontal) 0 else outside, y + if (horizontal) outside else 0])
            assertNotEquals(Color.White, pixels[x + if (horizontal) outside else 0, y + if (horizontal) 0 else outside],
                "Grid must remain visible outside the smaller dot")
        }
    }
}
