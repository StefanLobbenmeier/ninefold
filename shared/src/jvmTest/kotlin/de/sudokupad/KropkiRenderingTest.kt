package de.sudokupad

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

class KropkiRenderingTest {
    @get:Rule val compose = createComposeRule()

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
