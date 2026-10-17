package de.ninefold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpRect
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopPlayerLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun squareUsesAvailableSpaceAndControlsStayAttachedAcrossDesktopSizes() {
        val viewport = mutableStateOf(1366 to 768)
        compose.setContent {
            val (width, height) = viewport.value
            DesktopPlayerLayout(
                modifier = Modifier.requiredSize(width.dp, height.dp).testTag("player"),
                heading = { Box(Modifier.height(80.dp)) },
                board = {
                    SudokuBoard(SudokuFixtures.classic, SudokuFixtures.classic.startingCells(),
                        emptySet(), emptySet(), modifier = Modifier.fillMaxSize().testTag("board"), onSelect = {})
                },
                controls = { Box(Modifier.fillMaxSize().testTag("controls")) {
                    Box(Modifier.height(52.dp).testTag("button"))
                } },
            )
        }
        // Unclipped bounds use each node's local density; compare in the root's units.
        fun bounds(tag: String): DpRect {
            val node = compose.onNodeWithTag(tag)
            val rect = node.getUnclippedBoundsInRoot()
            val scale = node.fetchSemanticsNode().layoutInfo.density.density / compose.density.density
            return DpRect(rect.left * scale, rect.top * scale, rect.right * scale, rect.bottom * scale)
        }
        listOf(1366 to 768, 1920 to 1080, 3840 to 2160, 3440 to 1440, 850 to 900).forEach { size ->
            compose.runOnIdle { viewport.value = size }
            val player = bounds("player")
            val board = bounds("board")
            val controls = bounds("controls")
            assertEquals((board.right - board.left), (board.bottom - board.top))
            assertTrue(kotlin.math.abs((controls.left - board.right).value - 28f) < 1f)
            val expectedBoard = minOf(size.first - 378, ((size.first - 28) / (1f + 350f / 600f)).toInt(), size.second - 98)
            val expectedControls = maxOf(350, (expectedBoard * (350f / 600f)).roundToInt())
            assertEquals(expectedBoard.dp, board.right - board.left)
            assertTrue(kotlin.math.abs((controls.right - controls.left).value - expectedControls) < 1f)
            val button = bounds("button")
            assertTrue(kotlin.math.abs((button.bottom - button.top).value - 52f * expectedControls / 350) < 1f)
            assertTrue(board.bottom <= player.bottom)
            assertTrue(controls.right <= player.right)
            assertTrue(kotlin.math.abs(((board.left - player.left) - (player.right - controls.right)).value) <= 1f)
        }
    }
}
