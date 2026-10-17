package de.ninefold

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test

class CatalogFiltersInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun combinesFiltersAndClearsAnEmptyResult() {
        val solved = SudokuFixtures.classic.copy(title = "Short solved puzzle", sourceUrl = "https://sudokupad.app/short")
        val games = GameRepository(MemoryAppStorage(), fetchPuzzleData = { SudokuFixtures.empty })
        games.saveGame(SavedGame(solved, solved.solution!!.map { CellState(value = it.digitToInt()) }, true))
        val index = SudokuIndexRepository(MemorySudokuIndexCache()) {
            """{"schemaVersion":1,"generatedAt":"2026-10-15","sources":[],"entries":[
                {"id":"short","sourceId":"test","title":"Short solved puzzle","publishedAt":"2026-10-15","puzzleUrl":"https://sudokupad.app/short","metadata":{"durationSeconds":900}},
                {"id":"long","sourceId":"test","title":"Long unsolved puzzle","publishedAt":"2026-10-14","puzzleUrl":"https://sudokupad.app/long","metadata":{"durationSeconds":4000}}
            ]}"""
        }
        compose.setContent {
            MaterialTheme { SudokuCatalog(index, games, rememberCoroutineScope(), browseTab = true, onTab = {}) {} }
        }
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Solved", useUnmergedTree = true).performClick()
        compose.onNodeWithText("1 of 2 puzzles").assertExists()
        compose.onNodeWithText("Hard · 60+ min").performClick()
        compose.onNodeWithText("0 of 2 puzzles").assertExists()
        compose.onNodeWithText("No puzzles match these filters.").assertExists()
        compose.onNode(hasText("Clear filters") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Filters").assertExists()
        compose.onNodeWithText("Not solved").performClick()
        compose.onNodeWithText("1 of 2 puzzles").assertExists()
        compose.onNodeWithText("Short solved puzzle").assertDoesNotExist()
        compose.onNodeWithText("Long unsolved puzzle").assertExists()
    }
}
