package de.ninefold

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class CompletionInteractionTest {
    @get:Rule val compose = createComposeRule()

    private fun openOneMoveFromCompletion(successMessage: String? = null): GameRepository {
        val puzzle = SudokuFixtures.classic.copy(title = "Completion test", successMessage = successMessage)
        val cells = puzzle.solution!!.map { CellState(value = it.digitToInt()) }.toMutableList()
            .also { it[2] = CellState() }
        return GameRepository(MemoryAppStorage()).also { repository ->
            repository.saveSettings(CheckingSettings(
                rules = false, automaticRules = false, solution = false, automaticSolution = false,
            ))
            repository.saveGame(SavedGame(puzzle, cells, started = true))
            compose.setContent {
                MaterialTheme {
                    SudokuApp(puzzle, repository, shareBaseUrl = "https://example.com/", onBrowse = {})
                }
            }
        }
    }

    @Test fun finalDigitChecksImmediatelyAndShowsGenericCompletionWithTime() {
        val repository = openOneMoveFromCompletion()

        compose.onNode(hasText("4") and !hasContentDescription("Row", substring = true)).performClick()

        compose.onNodeWithText("Puzzle complete!").assertIsDisplayed()
        compose.onNodeWithText("Congratulations — you solved the puzzle!").assertIsDisplayed()
        compose.onNodeWithText("Completed in 00:00").assertIsDisplayed()
        assertTrue(repository.games.values.single().completed)
    }

    @Test fun completionUsesImportedCustomMessage() {
        openOneMoveFromCompletion("The treasure was friendship.")

        compose.onNode(hasText("4") and !hasContentDescription("Row", substring = true)).performClick()

        compose.onNodeWithText("The treasure was friendship.").assertIsDisplayed()
        compose.onNodeWithText("Completed in 00:00").assertIsDisplayed()
    }
}
