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
        listOf(1366 to 716, 1920 to 1028, 3840 to 2108, 3440 to 1388, 1024 to 716, 844 to 338,
            1536 to 812, 1280 to 668, 960 to 488).forEach { size ->
            compose.runOnIdle { viewport.value = size }
            val player = bounds("player")
            val board = bounds("board")
            val controls = bounds("controls")
            assertEquals((board.right - board.left), (board.bottom - board.top))
            val slot = minOf(size.second, (size.first / 1.7f).toInt())
            val margin = (slot * 0.05f).roundToInt()
            val expectedBoard = slot - margin
            val frameWidth = minOf(size.first, (size.second * 2.1f).toInt())
            val expectedControls = minOf(455, frameWidth - slot - margin)
            assertTrue(kotlin.math.abs((controls.left - board.right).value - margin) <= 1f)
            assertEquals(expectedBoard.dp, board.right - board.left)
            assertTrue(kotlin.math.abs((controls.right - controls.left).value - expectedControls) < 1f)
            val button = bounds("button")
            assertTrue(kotlin.math.abs((button.bottom - button.top).value - 52f * minOf(expectedControls / 350f, slot / 500f, 1.3f)) < 1f)
            assertTrue(board.bottom <= player.bottom)
            assertTrue(controls.right <= player.right)
            assertEquals(player.top + ((size.second - slot) / 2 + margin / 2).dp, board.top)
            assertTrue(bounds("button").bottom <= controls.bottom)
            // The sidebar can use the entire frame height.
            assertTrue(controls.top <= board.top)
        }
    }
    @Test fun portraitGivesBoardWidthBeforeMetadataAndInput() {
        val viewport = mutableStateOf(390 to 792)
        compose.setContent {
            val (width, height) = viewport.value
            PortraitPlayerLayout(
                modifier = Modifier.requiredSize(width.dp, height.dp).testTag("portrait"),
                heading = { Box(Modifier.height(80.dp).testTag("heading")) },
                board = { Box(Modifier.fillMaxSize().testTag("board")) },
                controls = { Box(Modifier.fillMaxSize().testTag("controls")) },
            )
        }
        listOf(390 to 792, 375 to 615, 850 to 848, 768 to 972).forEach { size ->
            compose.runOnIdle { viewport.value = size }
            val player = compose.onNodeWithTag("portrait").getUnclippedBoundsInRoot()
            val board = compose.onNodeWithTag("board").getUnclippedBoundsInRoot()
            val heading = compose.onNodeWithTag("heading").getUnclippedBoundsInRoot()
            val expectedSize = minOf(size.first, (size.second / 1.5f).toInt()) - 5
            assertEquals(expectedSize.dp, board.right - board.left)
            assertEquals(board.right - board.left, board.bottom - board.top)
            assertTrue(board.bottom <= heading.top)
            assertTrue(board.bottom <= player.bottom)
            assertEquals(80.dp, heading.bottom - heading.top)
        }
    }

    @Test fun sidebarKeepsInputAtBottomWithShortAndLongRules() {
        val rulesHeight = mutableStateOf(100)
        compose.setContent {
            DesktopPlayerSidebar(
                modifier = Modifier.requiredSize(350.dp, 700.dp).testTag("sidebar"),
                rules = { Box(Modifier.height(rulesHeight.value.dp).testTag("rules")) },
                input = { Box(Modifier.height(300.dp).testTag("input")) },
            )
        }
        listOf(100, 2000).forEach { height ->
            compose.runOnIdle { rulesHeight.value = height }
            val sidebar = compose.onNodeWithTag("sidebar").getUnclippedBoundsInRoot()
            val input = compose.onNodeWithTag("input").getUnclippedBoundsInRoot()
            val rules = compose.onNodeWithTag("rules").fetchSemanticsNode().boundsInRoot
            assertEquals(sidebar.bottom, input.bottom)
            assertEquals(300.dp, input.bottom - input.top)
            assertTrue(rules.bottom <= compose.onNodeWithTag("input").fetchSemanticsNode().boundsInRoot.top)
            if (height == 100) {
                val shortRules = compose.onNodeWithTag("rules").getUnclippedBoundsInRoot()
                assertEquals(16.dp, input.top - shortRules.bottom)
            }
        }
    }

}
