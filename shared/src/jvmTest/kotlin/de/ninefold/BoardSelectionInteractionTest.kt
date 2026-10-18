package de.ninefold

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
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
        val puzzle = SudokuFixtures.empty.copy(title = "Selection test")
        val repository = GameRepository(MemoryAppStorage())
        repository.saveGame(SavedGame(puzzle, puzzle.startingCells(), started = true))
        compose.setContent {
            MaterialTheme {
                Box(if (compact) Modifier.width(400.dp) else Modifier) {
                    SudokuApp(puzzle, repository, shareBaseUrl = "https://example.com/", onBrowse = {})
                }
            }
        }
    }

    @Test fun pauseOpensRulesAndResumeRestoresKeyboardInput() {
        openPlayer()
        compose.onNodeWithContentDescription("Pause game").performClick()
        compose.onNodeWithText("Resume puzzle").assertIsDisplayed()
        compose.onNodeWithText("Game paused. Your timer resumes when you close the rules.").assertIsDisplayed()
        compose.onNodeWithText("Resume puzzle").performClick()
        compose.onRoot().performKeyInput { pressKey(Key.Five) }
        cell(1, 3).assertContentDescriptionContains("column 3, 5", substring = true)
        compose.onNodeWithText("CLASSIC").assertDoesNotExist()
        compose.onNodeWithText("Checking settings").assertDoesNotExist()
        compose.onNodeWithContentDescription("Checking settings").assertExists()
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

    @Test fun clicksAndDragsStartFreshWhenMultipleIsOff() {
        openPlayer()
        clickCell(2, 2)
        clickCell(2, 2)
        assertSelection(2 to 2)
        drag(3, 1, 3)
        assertSelection(3 to 1, 3 to 2, 3 to 3)
        // Starting inside the group replaces it and includes newly crossed cells.
        drag(3, 2, 4)
        assertSelection(3 to 2, 3 to 3, 3 to 4)
        clickCell(3, 3)
        assertSelection(3 to 3)
        clickCell(3, 3)
        assertSelection(3 to 3)
        clickCell(4, 4)
        assertSelection(4 to 4)
    }

    @Test fun multipleAddsCellsAndSwitchingItOffMakesNextGestureReplaceTheGroup() {
        openPlayer()
        clickCell(2, 2)
        compose.onNodeWithContentDescription("Select multiple").performTouchInput { click() }
        clickCell(2, 3)
        clickCell(2, 2)
        assertSelection(2 to 3)
        drag(3, 1, 3)
        assertSelection(2 to 3, 3 to 1, 3 to 2, 3 to 3)
        drag(3, 2, 4)
        assertSelection(2 to 3, 3 to 1)
        compose.onNodeWithContentDescription("Select multiple").performTouchInput { click() }
        assertSelection(2 to 3, 3 to 1)
        clickCell(3, 1)
        assertSelection(3 to 1)
    }

    @Test fun revisitingCellsDuringRemovalDoesNotSelectThemAgain() {
        openPlayer()
        drag(2, 1, 3)
        compose.onNodeWithContentDescription("Select multiple").performTouchInput { click() }
        val start = cell(2, 1).fetchSemanticsNode().boundsInRoot.center
        val end = cell(2, 4).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(start); moveTo(end); moveTo(start); up() }
        assertSelection()
    }

    @Test fun semanticClicksStartFreshWhenMultipleIsOff() {
        openPlayer()
        cell(2, 2).performClick()
        assertSelection(2 to 2)
        cell(2, 2).performClick()
        assertSelection(2 to 2)
        drag(2, 1, 3)
        cell(2, 2).performClick()
        assertSelection(2 to 2)
    }

    @Test fun mouseClicksAndDragsStartFreshWhenMultipleIsOff() {
        openPlayer()
        cell(2, 2).performMouseInput { click() }
        assertSelection(2 to 2)
        cell(2, 2).performMouseInput { click() }
        assertSelection(2 to 2)
        val start = cell(3, 1).fetchSemanticsNode().boundsInRoot.center
        val end = cell(3, 3).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performMouseInput { moveTo(start); press(); moveTo(end); release() }
        assertSelection(3 to 1, 3 to 2, 3 to 3)
        compose.onRoot().performMouseInput { moveTo(end); press(); moveTo(start); release() }
        assertSelection(3 to 1, 3 to 2, 3 to 3)
        cell(3, 2).performMouseInput { click() }
        assertSelection(3 to 2)
    }

    @Test fun controlModifiedMouseClicksAddAndRemoveWithoutChangingTheSavedMode() {
        openPlayer()
        val target = cell(2, 2).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performMultiModalInput {
            key { keyDown(Key.CtrlLeft) }
            mouse { click(target) }
            key { keyUp(Key.CtrlLeft) }
        }
        assertSelection(1 to 3, 2 to 2)
        compose.onRoot().performMultiModalInput {
            key { keyDown(Key.CtrlLeft) }
            mouse { click(target) }
            key { keyUp(Key.CtrlLeft) }
        }
        assertSelection(1 to 3)
        compose.onNodeWithContentDescription("Select multiple").assertIsOff()
    }

    @Test fun keyboardMovesExtendsAndTransformsSelection() {
        openPlayer()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        assertSelection(1 to 4)
        compose.onRoot().performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionDown)
            keyUp(Key.ShiftLeft)
        }
        assertSelection(1 to 4, 2 to 4)
        compose.onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.A)
            keyUp(Key.CtrlLeft)
        }
        assertSelection(*(1..9).flatMap { row -> (1..9).map { column -> row to column } }.toTypedArray())
        compose.onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
            pressKey(Key.A)
            keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
        assertSelection()
    }

    @Test fun keyboardEntersDigitsSwitchesModesAndSupportsUndoRedo() {
        openPlayer()
        compose.onRoot().performKeyInput { pressKey(Key.Five) }
        cell(1, 3).assertContentDescriptionContains("column 3, 5", substring = true)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.X); pressKey(Key.Three) }
        cell(1, 4).assertContentDescriptionContains("corner notes 3", substring = true)
        compose.onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Four)
            keyUp(Key.CtrlLeft)
        }
        cell(1, 4).assertContentDescriptionContains("centre notes 4", substring = true)
        compose.onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Z)
            pressKey(Key.Y)
            keyUp(Key.CtrlLeft)
        }
        cell(1, 4).assertContentDescriptionContains("centre notes 4", substring = true)
        compose.onNodeWithText("Corner").assertIsSelected()
    }

    @Test fun altTemporarilyTogglesLettersInBothDirections() {
        openPlayer()
        compose.onRoot().performKeyInput { keyDown(Key.AltLeft) }
        compose.onNodeWithText("A–Z").assertExists()
        compose.onRoot().performKeyInput { pressKey(Key.A); keyUp(Key.AltLeft) }
        cell(1, 3).assertContentDescriptionContains("A", substring = true)
        compose.onNodeWithText("1–9").performClick()
        compose.onNodeWithText("A–Z").assertExists()
        compose.onRoot().performKeyInput { keyDown(Key.AltLeft) }
        compose.onNodeWithText("1–9").assertExists()
        compose.onRoot().performKeyInput { keyUp(Key.AltLeft) }
        compose.onNodeWithText("A–Z").assertExists()
    }

    @Test fun heldModifiersPreviewTheTemporaryInputMode() {
        openPlayer()
        compose.onRoot().performKeyInput { keyDown(Key.ShiftLeft) }
        compose.onNodeWithText("Corner").assertIsSelected()
        compose.onRoot().performKeyInput { keyDown(Key.CtrlLeft) }
        compose.onNodeWithText("Colour").assertIsSelected()
        compose.onRoot().performKeyInput { keyUp(Key.ShiftLeft) }
        compose.onNodeWithText("Centre").assertIsSelected()
        compose.onRoot().performKeyInput { keyUp(Key.CtrlLeft) }
        compose.onNodeWithText("Digit").assertIsSelected()
    }

    @Test fun doubleTapAndLongPressSelectMatchingContentForTheActiveTool() {
        val puzzle = SudokuFixtures.empty.copy(
            title = "Selection test",
            givens = "4" + ".".repeat(19) + "4" + ".".repeat(60),
        )
        val repository = GameRepository(MemoryAppStorage())
        repository.saveGame(SavedGame(puzzle, puzzle.startingCells(), started = true))
        compose.setContent { MaterialTheme { SudokuApp(puzzle, repository, "https://example.com/", {}) } }

        cell(1, 1).performTouchInput { doubleClick() }
        assertSelection(1 to 1, 3 to 3)
        clickCell(5, 5)
        cell(3, 3).performTouchInput { longClick() }
        assertSelection(1 to 1, 3 to 3)
    }

    @Test fun adjacentSelectedCellsRenderOnlyExposedBoldEdges() {
        val puzzle = SudokuFixtures.empty
        val selected = setOf(CellPosition(0, 0), CellPosition(0, 1), CellPosition(1, 0), CellPosition(1, 1))
        compose.setContent {
            MaterialTheme {
                SudokuBoard(puzzle, puzzle.startingCells(), selected, emptySet(), modifier = Modifier.width(450.dp), onSelect = {})
            }
        }
        val topLeft = cell(1, 1).captureToImage().toPixelMap()
        val moss = Moss
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
        compose.onNodeWithContentDescription("Select multiple").performTouchInput { click() }
        clickCell(4, 4)
        compose.onNodeWithText("Selection test", substring = true).performTouchInput { click() }
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
        compose.onNodeWithContentDescription("Undo").performClick()
        cell(2, 2).assertContentDescriptionContains("colours Red", substring = true)
        compose.onNodeWithContentDescription("Redo").performClick()
        cell(2, 2).assertContentDescriptionContains("colours Red, Blue", substring = true)
        compose.onNodeWithContentDescription("Erase colours").performClick()
        cell(2, 2).assertContentDescriptionEquals("Row 2, column 2, 5, selected")
        assertSelection(2 to 1, 2 to 2, 2 to 3)
    }

    @Test fun colourStripesStayVisibleOnSelectedCellsAndUnderPuzzleLines() {
        val puzzle = SudokuFixtures.empty.copy(lines = listOf(
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

    @Test fun automaticColoursUpdateWhileEditingAndWhenConfigurationChanges() {
        openPlayer(compact = true)
        clickCell(2, 2)
        compose.onNodeWithText("Colour").performClick()
        cell(2, 2).assertIsSelected()
        compose.onNodeWithContentDescription("Red, colour 7").performTouchInput { longClick() }
        cell(2, 2).assertIsSelected()
        compose.onNodeWithText("Red colouring").assertExists()
        compose.onNode(isToggleable() and !hasContentDescription("Select multiple")).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("2").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Done").performSemanticsAction(SemanticsActions.OnClick) { it() }
        cell(2, 2).assertContentDescriptionEquals("Row 2, column 2, empty, selected")
        compose.onNodeWithText("Centre").performClick()
        compose.onNodeWithText("2").performSemanticsAction(SemanticsActions.OnClick) { it() }
        cell(2, 2).assertContentDescriptionEquals("Row 2, column 2, empty, selected, centre notes 2, colours Red")
        clickCell(3, 3)
        cell(2, 2).assertContentDescriptionEquals("Row 2, column 2, empty, centre notes 2, colours Red")
        compose.onNodeWithContentDescription("Undo").performClick()
        cell(2, 2).assertContentDescriptionEquals("Row 2, column 2, empty")
        compose.onNodeWithContentDescription("Redo").performClick()
        cell(2, 2).assertContentDescriptionContains("colours Red", substring = true)
        clickCell(2, 2)
        compose.onNodeWithText("Colour").performClick()
        compose.onNodeWithContentDescription("Blue, colour 9").performClick()
        clickCell(3, 3)
        compose.onNodeWithContentDescription("Red, colour 7").performTouchInput { longClick() }
        // Changing the matching numbers updates an unselected cell with existing content.
        compose.onNode(hasText("2") and hasAnyAncestor(isDialog())).performSemanticsAction(SemanticsActions.OnClick) { it() }
        cell(2, 2).assertContentDescriptionEquals("Row 2, column 2, empty, centre notes 2, colours Blue")
        compose.onNode(hasText("2") and hasAnyAncestor(isDialog())).performSemanticsAction(SemanticsActions.OnClick) { it() }
        cell(2, 2).assertContentDescriptionContains("colours Red, Blue", substring = true)
        compose.onNode(isToggleable() and !hasContentDescription("Select multiple")).performSemanticsAction(SemanticsActions.OnClick) { it() }
        cell(2, 2).assertContentDescriptionEquals("Row 2, column 2, empty, centre notes 2, colours Blue")
        compose.onNodeWithText("Done").performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

}
