package de.sudokupad

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BoardSelectionInteractionTest {
    @get:Rule val compose = createComposeRule()

    private fun openPlayer(compact: Boolean = false) {
        val puzzle = Puzzle("Selection test", "Test author", ".".repeat(81))
        val repository = GameRepository(MemoryAppStorage())
        repository.saveGame(SavedGame(puzzle, puzzle.startingCells(), started = true))
        compose.setContent {
            MaterialTheme {
                Box(if (compact) Modifier.width(400.dp) else Modifier) {
                    SudokuApp(puzzle, repository, shareBaseUrl = "https://example.com/", onImport = {}, onBrowse = {})
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

    @Test fun clicksAndDragsRemoveSelectedCellsWhenMultipleIsOff() {
        openPlayer()
        clickCell(2, 2)
        clickCell(2, 2)
        assertSelection()
        drag(3, 1, 3)
        assertSelection(3 to 1, 3 to 2, 3 to 3)
        // Removal preserves untouched cells and does not add unselected cells crossed later.
        drag(3, 2, 4)
        assertSelection(3 to 1)
        clickCell(3, 1)
        assertSelection()
        clickCell(3, 3)
        assertSelection(3 to 3)
        clickCell(4, 4)
        assertSelection(4 to 4)
    }

    @Test fun multipleAddsCellsAndSwitchingItOffMakesNextGestureReplaceTheGroup() {
        openPlayer()
        clickCell(2, 2)
        compose.onNodeWithText("Select multiple").performTouchInput { click() }
        clickCell(2, 3)
        clickCell(2, 2)
        assertSelection(2 to 3)
        drag(3, 1, 3)
        assertSelection(2 to 3, 3 to 1, 3 to 2, 3 to 3)
        drag(3, 2, 4)
        assertSelection(2 to 3, 3 to 1)
        compose.onNodeWithText("Select multiple").performTouchInput { click() }
        assertSelection(2 to 3, 3 to 1)
        clickCell(4, 4)
        assertSelection(4 to 4)
    }

    @Test fun revisitingCellsDuringRemovalDoesNotSelectThemAgain() {
        openPlayer()
        drag(2, 1, 3)
        val start = cell(2, 1).fetchSemanticsNode().boundsInRoot.center
        val end = cell(2, 4).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(start); moveTo(end); moveTo(start); up() }
        assertSelection()
    }

    @Test fun semanticClicksAlsoToggleSelection() {
        openPlayer()
        cell(2, 2).performClick()
        assertSelection(2 to 2)
        cell(2, 2).performClick()
        assertSelection()
    }

    @Test fun mouseClicksAndDragsRemoveSelectedCells() {
        openPlayer()
        cell(2, 2).performMouseInput { click() }
        assertSelection(2 to 2)
        cell(2, 2).performMouseInput { click() }
        assertSelection()
        val start = cell(3, 1).fetchSemanticsNode().boundsInRoot.center
        val end = cell(3, 3).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performMouseInput { moveTo(start); press(); moveTo(end); release() }
        assertSelection(3 to 1, 3 to 2, 3 to 3)
        compose.onRoot().performMouseInput { moveTo(end); press(); moveTo(start); release() }
        assertSelection()
    }

    @Test fun adjacentSelectedCellsRenderOnlyExposedBoldEdges() {
        val puzzle = Puzzle("Border test", "Test author", ".".repeat(81))
        val selected = setOf(CellPosition(0, 0), CellPosition(0, 1), CellPosition(1, 0), CellPosition(1, 1))
        compose.setContent {
            MaterialTheme {
                SudokuBoard(puzzle, puzzle.startingCells(), selected, emptySet(), modifier = Modifier.width(450.dp), onSelect = {})
            }
        }
        val topLeft = cell(1, 1).captureToImage().toPixelMap()
        val moss = Color(0xFF3D6547)
        assertEquals(moss, topLeft[topLeft.width / 2, 1])
        assertEquals(moss, topLeft[1, topLeft.height / 2])
        assertNotEquals(moss, topLeft[topLeft.width - 2, topLeft.height / 2])
        assertNotEquals(moss, topLeft[topLeft.width / 2, topLeft.height - 2])
        val bottomRight = cell(2, 2).captureToImage().toPixelMap()
        assertEquals(moss, bottomRight[bottomRight.width - 2, bottomRight.height / 2])
        assertEquals(moss, bottomRight[bottomRight.width / 2, bottomRight.height - 2])
        assertNotEquals(moss, bottomRight[1, bottomRight.height / 2])
        assertNotEquals(moss, bottomRight[bottomRight.width / 2, 1])
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
    @Test fun colourPaletteSupportsGroupUndoRedoAndIndependentErase() {
        openPlayer(compact = true)
        drag(2, 1, 3)
        compose.onNode(hasText("5") and !hasText("Digit")).performClick()
        compose.onNodeWithText("Colour").performClick()
        compose.onNodeWithContentDescription("Red, colour 7").performClick()
        compose.onNodeWithContentDescription("Blue, colour 9").performClick()
        for (column in 1..3) cell(2, column).assertContentDescriptionContains("colours Red, Blue", substring = true)
        compose.onNodeWithText("Undo").performClick()
        cell(2, 2).assertContentDescriptionContains("colours Red", substring = true)
        compose.onNodeWithText("Redo").performClick()
        cell(2, 2).assertContentDescriptionContains("colours Red, Blue", substring = true)
        compose.onNodeWithContentDescription("Erase colours").performClick()
        cell(2, 2).assertContentDescriptionEquals("Row 2, column 2, 5, selected")
        assertSelection(2 to 1, 2 to 2, 2 to 3)
    }

    @Test fun colourStripesStayVisibleOnSelectedCellsAndUnderPuzzleLines() {
        val puzzle = Puzzle("Colour rendering", "Test", ".".repeat(81), lines = listOf(
            PuzzleLine(listOf(GridPoint(.5, .1), GridPoint(.5, .9)), color = "#000000", thickness = 4.0)
        ))
        val cells = puzzle.startingCells().toMutableList().also { it[0] = CellState(colours = setOf(7, 9)) }
        compose.setContent {
            MaterialTheme {
                SudokuBoard(puzzle, cells, setOf(CellPosition(0, 0)), emptySet(), modifier = Modifier.width(450.dp), onSelect = {})
            }
        }
        val pixels = cell(1, 1).captureToImage().toPixelMap()
        val red = pixels[pixels.width / 4, pixels.height / 4]
        val blue = pixels[pixels.width * 3 / 4, pixels.height / 4]
        kotlin.test.assertTrue(red.red > red.blue)
        kotlin.test.assertTrue(blue.blue > blue.red)
        kotlin.test.assertTrue(pixels[pixels.width / 2, pixels.height / 2].red < .3f)
    }

    @Test fun longPressConfiguresColourWithoutPaintingAndAppliesOnlyOnLeavingCell() {
        openPlayer(compact = true)
        clickCell(2, 2)
        compose.onNodeWithText("Colour").performClick()
        cell(2, 2).assertIsSelected()
        compose.onNodeWithContentDescription("Red, colour 7").performTouchInput { longClick() }
        cell(2, 2).assertIsSelected()
        compose.onNodeWithText("Red colouring").assertExists()
        compose.onNode(isToggleable()).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("2").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Done").performSemanticsAction(SemanticsActions.OnClick) { it() }
        cell(2, 2).assertContentDescriptionEquals("Row 2, column 2, empty, selected")
        compose.onNodeWithText("Centre").performClick()
        compose.onNodeWithText("2").performSemanticsAction(SemanticsActions.OnClick) { it() }
        cell(2, 2).assertContentDescriptionEquals("Row 2, column 2, empty, selected, centre notes 2")
        clickCell(3, 3)
        cell(2, 2).assertContentDescriptionEquals("Row 2, column 2, empty, centre notes 2, colours Red")
        compose.onNodeWithText("Undo").performClick()
        cell(2, 2).assertContentDescriptionEquals("Row 2, column 2, empty, centre notes 2")
        compose.onNodeWithText("Redo").performClick()
        cell(2, 2).assertContentDescriptionContains("colours Red", substring = true)
    }

}
