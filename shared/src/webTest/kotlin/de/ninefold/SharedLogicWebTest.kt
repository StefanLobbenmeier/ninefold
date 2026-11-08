package de.ninefold

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SharedLogicWebTest {
    @Test
    fun opensStartsAndLeavesPuzzle() = runComposeUiTest {
        val puzzle = SudokuFixtures.classic.copy(title = "Browser puzzle")
        val storage = MemoryAppStorage()
        val index = SudokuIndexRepository(MemorySudokuIndexCache()) {
            """{"schemaVersion":1,"generatedAt":"2026-10-15","sources":[],"entries":[]}"""
        }
        setContent {
            App(storage = storage, indexRepository = index, initialShareLink = puzzleShareLink(puzzle))
        }
        onNodeWithText("Okay, start puzzle").assertIsDisplayed().performClick()
        onNodeWithContentDescription("Erase").assertIsDisplayed()
        onNodeWithContentDescription("Back to puzzles").performClick()
        onNodeWithContentDescription("Continue Browser puzzle").assertIsDisplayed()
        assertEquals(puzzle, GameRepository(storage).game(puzzle)?.puzzle)
    }
}
