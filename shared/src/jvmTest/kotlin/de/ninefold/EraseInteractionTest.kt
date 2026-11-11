package de.ninefold

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class EraseInteractionTest {
    @get:Rule val compose = createComposeRule()

    private fun openPlayer(): Pair<Puzzle, GameRepository> {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(
            givens = "..5" + ".".repeat(78),
        ))
        val repository = GameRepository(MemoryAppStorage())
        repository.saveGame(SavedGame(puzzle, puzzle.startingCells(), started = true))
        compose.setContent {
            MaterialTheme {
                SudokuApp(puzzle, repository, shareBaseUrl = "https://example.com/", onBrowse = {})
            }
        }
        return puzzle to repository
    }

    @Test fun erasingImportedGivenPreservesBoardAndHistory() {
        val (puzzle, repository) = openPlayer()
        compose.onNodeWithContentDescription("Erase").performClick()
        compose.onRoot().performKeyInput { pressKey(Key.Backspace) }
        compose.onNode(hasContentDescription("Row 1, column 3, 5", substring = true)).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(puzzle.startingCells(), repository.game(puzzle)!!.cells)
            assertEquals(emptyList(), repository.game(puzzle)!!.history)
        }
        // The player must still accept real edits after an ignored erase.
        compose.onNode(hasContentDescription("Row 1, column 4,", substring = true)).performClick()
        compose.onRoot().performKeyInput { pressKey(Key.Four) }
        compose.runOnIdle { assertEquals(4, repository.game(puzzle)!!.cells[3].value) }
        compose.onNodeWithContentDescription("Erase").performClick()
        compose.runOnIdle { assertEquals(puzzle.startingCells(), repository.game(puzzle)!!.cells) }
    }

    @Test fun erasingEmptyCellOrEmptySelectionPreservesBoardAndHistory() {
        val (puzzle, repository) = openPlayer()
        compose.onNode(hasContentDescription("Row 1, column 4,", substring = true)).performClick()
        compose.onNodeWithContentDescription("Erase").performClick()
        compose.onRoot().performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithContentDescription("Erase").performClick()
        compose.runOnIdle {
            assertEquals(puzzle.startingCells(), repository.game(puzzle)!!.cells)
            assertEquals(emptyList(), repository.game(puzzle)!!.history)
        }
    }
}
