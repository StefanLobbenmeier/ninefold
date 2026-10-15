package de.sudokupad

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class CatalogReimportTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedPuzzleOffersRetryAndOpensOnlyAfterSuccessfulConversion() {
        var fail = true
        val puzzle = SudokuFixtures.empty.copy(title = "Unavailable puzzle")
        val games = GameRepository(MemoryAppStorage(), fetchPuzzleData = {
            if (fail) error("Synthetic conversion failure")
            puzzle
        })
        val index = SudokuIndexRepository(MemorySudokuIndexCache()) {
            """{"schemaVersion":1,"generatedAt":"2026-10-15","sources":[],"entries":[
                {"id":"unavailable","sourceId":"test","title":"Unavailable puzzle","publishedAt":"2026-10-15","puzzleUrl":"https://sudokupad.app/unavailable"}
            ]}"""
        }
        var opened: Puzzle? = null
        compose.setContent {
            MaterialTheme { SudokuCatalog(index, games, rememberCoroutineScope()) { opened = it } }
        }
        compose.onNodeWithText("Browse").performClick()
        compose.waitUntil { games.statuses["unavailable"] == DownloadStatus.Failed }
        compose.onNodeWithText("Unavailable puzzle").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Retry Unavailable puzzle").assertIsEnabled()
        assertEquals(null, opened)
        fail = false
        compose.onNodeWithContentDescription("Retry Unavailable puzzle").performClick()
        compose.waitUntil { opened != null }
        assertEquals("Unavailable puzzle", opened?.title)
    }

    @Test fun longPressReimportsAndFailurePreservesTheReplacement() {
        val storage = MemoryAppStorage()
        val original = SudokuFixtures.classic.copy(title = "Saved puzzle", sourceUrl = "https://sudokupad.app/test")
        var fail = false
        var requests = 0
        val games = GameRepository(storage, refetchPuzzleData = {
            requests++
            if (fail) error("Conversion failed")
            original.copy(title = "Updated puzzle")
        })
        games.saveGame(SavedGame(original, original.startingCells(), true))
        val index = SudokuIndexRepository(MemorySudokuIndexCache()) {
            """{"schemaVersion":1,"generatedAt":"2026-10-15","sources":[],"entries":[]}"""
        }
        var opened: Puzzle? = null
        compose.setContent {
            MaterialTheme { SudokuCatalog(index, games, rememberCoroutineScope()) { opened = it } }
        }
        compose.onNodeWithText("Saved puzzle").performTouchInput { longClick() }
        compose.waitUntil { requests == 1 }
        compose.onNodeWithText("Reimport successful").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Updated puzzle").assertIsDisplayed()
        assertEquals(null, opened)
        fail = true
        compose.onNodeWithText("Updated puzzle").performTouchInput { longClick() }
        compose.waitUntil { requests == 2 }
        compose.onNodeWithText("Reimport failed").assertIsDisplayed()
        compose.onNodeWithText("Kept the old import of Updated puzzle. Conversion failed").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithContentDescription("Continue Updated puzzle").performClick()
        compose.runOnIdle { assertEquals("Updated puzzle", opened?.title) }
        assertEquals("Updated puzzle", GameRepository(storage).game(original)?.puzzle?.title)
    }
}
