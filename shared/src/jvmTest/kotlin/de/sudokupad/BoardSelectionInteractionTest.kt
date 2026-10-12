package de.sudokupad

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class BoardSelectionInteractionTest {
    @get:Rule val compose = createComposeRule()

    private fun openPlayer(compact: Boolean = false) {
        val puzzle = Puzzle("Selection test", "Test author", ".".repeat(81))
        val repository = GameRepository(MemoryAppStorage())
        repository.saveGame(SavedGame(puzzle, puzzle.startingCells(), started = true))
        compose.setContent {
            MaterialTheme {
                Box(if (compact) Modifier.width(400.dp) else Modifier) {
                    SudokuApp(puzzle, repository, onImport = {}, onBrowse = {})
                }
            }
        }
    }

    private fun cell(row: Int, column: Int) = compose.onNode(
        hasContentDescription("Row $row, column $column,", substring = true),
    )

    private fun clickCell(row: Int, column: Int) {
        cell(row, column).performTouchInput { click() }
    }

    private fun drag(row: Int, from: Int, to: Int) {
        val start = cell(row, from).fetchSemanticsNode().boundsInRoot.center
        val end = cell(row, to).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(start); moveTo(end); up() }
    }

    private fun assertSelection(vararg positions: Pair<Int, Int>) {
        assertEquals(positions.size, compose.onAllNodes(
            isSelected() and hasContentDescription("Row ", substring = true),
        ).fetchSemanticsNodes().size)
        positions.forEach { (row, column) -> cell(row, column).assertIsSelected() }
    }

    @Test fun clicksAndDragsReplacePreviousSelectionWhenMultipleIsOff() {
        openPlayer()
        clickCell(2, 2)
        clickCell(2, 2)
        assertSelection(2 to 2)
        drag(3, 1, 3)
        assertSelection(3 to 1, 3 to 2, 3 to 3)
        // Starting a new drag on an already selected cell still replaces the old group.
        drag(3, 2, 4)
        assertSelection(3 to 2, 3 to 3, 3 to 4)
        clickCell(3, 3)
        assertSelection(3 to 3)
    }

    @Test fun multipleAddsCellsAndSwitchingItOffMakesNextGestureReplaceTheGroup() {
        openPlayer()
        clickCell(2, 2)
        compose.onNodeWithText("Select multiple").performTouchInput { click() }
        clickCell(2, 3)
        clickCell(2, 2)
        assertSelection(2 to 2, 2 to 3)
        drag(3, 1, 3)
        assertSelection(2 to 2, 2 to 3, 3 to 1, 3 to 2, 3 to 3)
        drag(3, 2, 4)
        assertSelection(2 to 2, 2 to 3, 3 to 1, 3 to 2, 3 to 3, 3 to 4)
        compose.onNodeWithText("Select multiple").performTouchInput { click() }
        assertSelection(2 to 2, 2 to 3, 3 to 1, 3 to 2, 3 to 3, 3 to 4)
        clickCell(3, 3)
        assertSelection(3 to 3)
    }

    @Test fun backgroundClearsSelectionWhileKeypadKeepsIt() {
        openPlayer()
        checkBackgroundAndKeypad()
    }

    @Test fun compactBackgroundClearsSelectionWhileKeypadKeepsIt() {
        openPlayer(compact = true)
        checkBackgroundAndKeypad()
    }

    private fun checkBackgroundAndKeypad() {
        drag(2, 1, 3)
        compose.onNode(hasText("5") and !hasText("Digit")).performTouchInput { click() }
        assertSelection(2 to 1, 2 to 2, 2 to 3)
        cell(2, 2).assertContentDescriptionContains("Row 2, column 2, 5", substring = true)
        val boardY = cell(4, 4).fetchSemanticsNode().boundsInRoot.center.y
        compose.onRoot().performTouchInput { click(Offset(1f, boardY)) }
        assertSelection()
        compose.onNodeWithText("Select multiple").performTouchInput { click() }
        clickCell(4, 4)
        compose.onNodeWithText("Selection test").performTouchInput { click() }
        assertSelection()
    }
}
