package de.sudokupad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PuzzlePreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewPreservesArtworkAndFogAtThumbnailSize() {
        val puzzle = SudokuFixtures.empty.copy(fogLights = setOf(CellPosition(0, 0)), fogLinks = emptyList(),
            underlays = listOf(PuzzleOverlay(GridPoint(.5, .5), 1.0, 1.0, backgroundColor = "#FF0000")))
        compose.setContent {
            MaterialTheme { PuzzlePreview(puzzle, puzzle.startingCells(), Modifier.size(88.dp)) }
        }
        val pixels = compose.onNodeWithContentDescription("Preview of ${puzzle.title}")
            .captureToImage().toPixelMap()
        val red = pixels[pixels.width / 18, pixels.height / 18]
        assertTrue(red.red > .9f && red.green < .1f && red.blue < .1f)
        assertEquals(Color(0xFFB4B7B5), pixels[pixels.width / 2, pixels.height / 2])
    }

    @Test fun tappingPreviewOpensParentCardWithoutExposingCellActions() {
        val puzzle = SudokuFixtures.classic
        var opened = false
        compose.setContent {
            MaterialTheme {
                Box(Modifier.clickable { opened = true }) {
                    PuzzlePreview(puzzle, puzzle.startingCells(), Modifier.size(88.dp))
                }
            }
        }
        compose.onAllNodes(hasContentDescription("Row", substring = true))
            .assertCountEquals(0)
        compose.onNodeWithContentDescription("Preview of ${puzzle.title}")
            .performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(opened) }
    }
}
