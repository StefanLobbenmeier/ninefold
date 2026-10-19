package de.ninefold

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class OutsideClueInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingOutsidePairFadesAndRestoresItWithoutEditingCells() {
        val clue = PuzzleOverlay(GridPoint(1.5, -.5), width = 0.0, height = .8, text = "1 6")
        val puzzle = SudokuFixtures.empty.copy(underlays = listOf(clue))
        val repository = GameRepository(MemoryAppStorage())
        repository.saveGame(SavedGame(puzzle, puzzle.startingCells(), started = true))
        compose.setContent {
            MaterialTheme { SudokuApp(puzzle, repository, "https://example.com/", {}) }
        }
        val node = compose.onNodeWithContentDescription("Outside clue 1 6")
        node.assertIsNotSelected().performTouchInput { click() }
        node.assertIsSelected()
        compose.runOnIdle {
            assertEquals(setOf(clue), repository.game(puzzle)?.fadedOutsideClues)
            assertEquals(puzzle.startingCells(), repository.game(puzzle)?.cells)
        }
        node.performTouchInput { click() }
        node.assertIsNotSelected()
        compose.runOnIdle { assertEquals(emptySet(), repository.game(puzzle)?.fadedOutsideClues) }
    }
}
