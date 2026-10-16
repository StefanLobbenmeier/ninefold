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

class ThermometerRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun importedThermometersHaveBulbsOnlyAtTheirStarts() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.fpuzzlesPayload(properties =
            """"thermometer":[{"lines":[["R2C2","R2C3","R2C4"],["R5C5","R6C5","R7C5"]]}]"""))
        compose.setContent {
            MaterialTheme {
                SudokuBoard(puzzle, puzzle.startingCells(), emptySet(), emptySet(),
                    modifier = Modifier.width(450.dp).testTag("board"), onSelect = {})
            }
        }
        val pixels = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        val cell = pixels.width / 9f
        val gray = Color(0xFFB8B8B8)
        puzzle.lines.forEach { line ->
            val horizontal = line.points.first().row == line.points.last().row
            fun sample(point: GridPoint, perpendicular: Float): Color = pixels[
                ((point.column + if (horizontal) 0f else perpendicular) * cell).toInt(),
                ((point.row + if (horizontal) perpendicular else 0f) * cell).toInt()]
            assertEquals(gray, sample(line.points.first(), .25f), "Bulb must be wider than the stem")
            assertEquals(Color.White, sample(line.points.first(), .4f), "Bulb must stay inside its cell")
            assertEquals(gray, sample(line.points[1], .05f), "Stem must remain visible")
            assertEquals(Color.White, sample(line.points.last(), .25f), "Tip must not have a bulb")
        }
    }
}
