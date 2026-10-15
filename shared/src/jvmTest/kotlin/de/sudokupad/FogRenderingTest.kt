package de.sudokupad

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlin.test.assertTrue

class FogRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun paintedFogKeepsStripesAndOuterBorderWhileHidingPuzzleArtwork() {
        val puzzle = Puzzle("Fog rendering", "Test", ".".repeat(81), fogLights = emptySet(), fogLinks = emptyList(),
            lines = listOf(PuzzleLine(listOf(GridPoint(.5, .1), GridPoint(.5, .9)), "#000000")))
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(value = 8, given = true, colours = setOf(7, 9))
        }
        compose.setContent {
            MaterialTheme {
                SudokuBoard(puzzle, cells, emptySet(), emptySet(), modifier = Modifier.width(450.dp).testTag("board"), onSelect = {})
            }
        }
        val pixels = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        val cell = pixels.width / 9
        assertEquals(CellColour.fromNumber(7)!!.swatch, pixels[cell / 4, cell / 2])
        assertEquals(CellColour.fromNumber(9)!!.swatch, pixels[cell * 3 / 4, cell / 2])
        // The given stays hidden even though the player's paint is visible.
        assertEquals(CellColour.fromNumber(9)!!.swatch, pixels[cell * 3 / 5, cell / 2])
        assertEquals(Color(0xFFB4B7B5), pixels[cell * 3 / 2, cell / 2])
        val ink = Color(0xFF202522)
        assertEquals(ink, pixels[pixels.width / 2, 1])
        assertEquals(ink, pixels[1, pixels.height / 2])
        assertEquals(ink, pixels[pixels.width - 2, pixels.height / 2])
        assertEquals(ink, pixels[pixels.width / 2, pixels.height - 2])
    }

    @Test fun wholeXVAndDotsMatchFullyLitRenderingFromEitherSide() {
        val marks = listOf("V", "X", null).flatMapIndexed { index, text ->
            listOf(GridPoint(2.0 + index * 2, 4.5), GridPoint(2.5 + index * 2, 7.0)).map { center ->
                PuzzleOverlay(center, .5, .5, if (text == null) "#000000" else "#FFFFFF", text = text, rounded = text == null)
            }
        }
        assertWholeEdgeMarks(marks)
    }

    @Test fun originalWetBlanketsCluesRenderWholeFromEitherSide() {
        val puzzle = parseSudokuPadPayload(javaClass.getResource("/puzzles/hkzobuop6w.scl")!!.readText())
        assertWholeEdgeMarks(puzzle.overlays + puzzle.underlays)
    }

    private fun assertWholeEdgeMarks(marks: List<PuzzleOverlay>) {
        var lit by mutableStateOf((0..8).flatMap { row -> (0..8).map { CellPosition(row, it) } }.toSet())
        compose.setContent {
            MaterialTheme {
                val puzzle = Puzzle("Edge marks", "Test", ".".repeat(81), fogLights = lit,
                    overlays = marks.take(3), underlays = marks.drop(3), fogLinks = emptyList())
                SudokuBoard(puzzle, puzzle.startingCells(), emptySet(), emptySet(), modifier = Modifier.width(450.dp).testTag("board"), onSelect = {})
            }
        }
        val fullyLit = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        val cell = fullyLit.width / 9f
        for (side in 0..1) {
            compose.runOnIdle {
                lit = marks.map { mark ->
                    if (mark.center.row % 1.0 == 0.0) CellPosition(mark.center.row.toInt() - side, mark.center.column.toInt())
                    else CellPosition(mark.center.row.toInt(), mark.center.column.toInt() - side)
                }.toSet()
            }
            val halfLit = compose.onNodeWithTag("board").captureToImage().toPixelMap()
            marks.forEach { mark ->
                val x = (mark.center.column * cell).toInt()
                val y = (mark.center.row * cell).toInt()
                val radius = (cell * if (mark.text.isNullOrBlank()) minOf(.2, mark.width * .4, mark.height * .4) else .2).toInt()
                var compared = 0
                for (dy in -radius..radius) for (dx in -radius..radius) {
                    if (mark.rounded && dx * dx + dy * dy > radius * radius) continue
                    val expected = fullyLit[x + dx, y + dy]
                    // Transparent text boxes inherit fog behind antialiased edges. Compare
                    // solid glyph pixels so the reference still catches a clipped V or X.
                    if (mark.backgroundColor != null || expected == Color.Black) {
                        compared++
                        assertEquals(expected, halfLit[x + dx, y + dy], "${mark.text ?: "Dot"}, side $side, pixel $dx,$dy")
                    }
                }
                assertTrue(compared > 0, "No solid pixels checked for ${mark.text ?: "Dot"}")
            }
        }
        compose.runOnIdle { lit = emptySet() }
        val hidden = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        marks.forEach { mark ->
            assertEquals(Color(0xFFB4B7B5), hidden[(mark.center.column * cell + cell * .1).toInt(), (mark.center.row * cell + cell * .1).toInt()])
        }
    }
}
