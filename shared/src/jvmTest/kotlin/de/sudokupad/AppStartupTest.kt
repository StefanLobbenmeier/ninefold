package de.sudokupad

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isToggleable
import com.sun.net.httpserver.HttpServer
import io.github.vinceglb.filekit.FileKit
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.net.InetSocketAddress
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AppStartupTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pastedSharedLinkReviewsAndImportsWithoutChangingAssignedRules() {
        FileKit.init(appId = "de.sudokupad")
        val storage = MemoryAppStorage()
        val puzzle = SudokuFixtures.classic.copy(title = "Pasted puzzle", lines = listOf(
            PuzzleLine(listOf(GridPoint(.5, .5), GridPoint(1.5, .5)), rule = LineRule.GermanWhisper),
            PuzzleLine(listOf(GridPoint(2.5, 2.5), GridPoint(3.5, 2.5))),
        ))
        compose.setContent { App(storage = storage, initialShareLink = puzzleShareLink(SudokuFixtures.classic)) }
        compose.onNodeWithText("Okay, start puzzle").performClick()
        compose.onNodeWithText("+  Import").performClick()
        compose.onNodeWithText("Puzzle link").performClick()
        compose.onNodeWithText("Puzzle URL").performTextInput("https://example.com/#puzzle=v1.???")
        compose.onNodeWithText("Load puzzle").performClick()
        compose.onNodeWithText("This puzzle link is damaged or incomplete.").assertIsDisplayed()
        assertEquals(1, GameRepository(storage).games.size)
        compose.onNodeWithText("Puzzle URL").performTextClearance()
        compose.onNodeWithText("Puzzle URL").performTextInput("  " + puzzleShareLink(puzzle) + "  ")
        compose.onNodeWithText("Load puzzle").performClick()
        compose.onNodeWithText("Double-check your import").assertIsDisplayed()
        compose.onNodeWithText("Assign line rules (1 display only)").assertIsDisplayed()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Import puzzle").performScrollTo().performClick()
        compose.onAllNodesWithText("Pasted puzzle").assertCountEquals(2)
        compose.onNodeWithText("Okay, start puzzle").performClick()
        compose.waitForIdle()
        assertEquals(puzzle, GameRepository(storage).game(puzzle)?.puzzle)
        assertEquals(SudokuFixtures.classic, GameRepository(storage).game(SudokuFixtures.classic)?.puzzle)
    }

    @Test
    fun sharedPuzzleOpensRulesAndIsSavedWhenStarted() {
        val storage = MemoryAppStorage()
        val puzzle = SudokuFixtures.classic.copy(title = "Shared puzzle")
        compose.setContent { App(storage = storage, initialShareLink = puzzleShareLink(puzzle)) }
        compose.onAllNodesWithText("Shared puzzle").assertCountEquals(2)
        compose.onNodeWithText("Puzzle link").assertIsDisplayed()
        compose.onNodeWithText("Copy puzzle link").assertIsEnabled()
        compose.onNodeWithText("Okay, start puzzle").performClick()
        compose.waitForIdle()
        assertEquals(puzzle, GameRepository(storage).game(puzzle)?.puzzle)
        compose.onNodeWithText("Rules").performClick()
        compose.onNodeWithText("Copy puzzle link").performClick()
        compose.onNodeWithText("Copied!").assertIsDisplayed()
    }

    @Test
    fun applicationStartsAndLoadsCatalog() {
        // Use the real JVM HTTP engine against a local server so startup exercises
        // Ktor, coroutines, JSON parsing, Compose, Skiko, and Material without internet access.
        val payload = """{
            "schemaVersion": 1,
            "generatedAt": "2026-10-12T00:00:00Z",
            "sources": [{"id": "test", "name": "Test source"}],
            "entries": [{
                "id": "startup",
                "sourceId": "test",
                "title": "Startup puzzle",
                "publishedAt": "2026-10-12T00:00:00Z",
                "puzzleUrl": "https://sudokupad.app/startup",
                "links": [{"mediaType": "video", "url": "https://www.youtube.com/watch?v=startup"}]
            }]
        }"""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/index.json") { exchange ->
            val bytes = payload.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            HttpClient().use { client ->
                val cache = MemorySudokuIndexCache()
                val storage = MemoryAppStorage().also {
                    // Seed offline puzzle data so automatic preparation stays local.
                    it.write("ninefold.puzzle.https://sudokupad.app/startup.v1",
                        Json.encodeToString(SudokuFixtures.empty.copy(title = "Startup puzzle")))
                }
                val repository = SudokuIndexRepository(cache) {
                    client.get("http://127.0.0.1:${server.address.port}/index.json").bodyAsText()
                }
                FileKit.init(appId = "de.sudokupad")
                val openedUrls = mutableListOf<String>()
                val uriHandler = object : UriHandler {
                    override fun openUri(uri: String) { openedUrls += uri }
                }
                compose.setContent {
                    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                        App(indexCache = cache, storage = storage, indexRepository = repository)
                    }
                }

                compose.onNodeWithText("NINEFOLD").assertIsDisplayed()
                compose.onNodeWithText("Browse sudokus").assertIsDisplayed()
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithText("1 puzzles · 1 ready offline · Updated 2026-10-12")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("Startup puzzle").assertIsDisplayed()
                compose.onNodeWithText("1 puzzles · 1 ready offline · Updated 2026-10-12").assertIsDisplayed()
                compose.onNodeWithContentDescription("Play Startup puzzle").assertIsEnabled()
                compose.onNodeWithText("SudokuPad").performClick()
                compose.onNodeWithText("Source video").performClick()
                assertEquals(listOf("https://sudokupad.app/startup", "https://www.youtube.com/watch?v=startup"), openedUrls)
                compose.onNodeWithText("Browse sudokus").assertIsDisplayed()
                assertEquals("Startup puzzle", repository.cached()?.entries?.single()?.title)

                // Opening Import also composes the FileKit picker integration.
                compose.onNodeWithText("Import").performClick()
                compose.onNodeWithText("Puzzle link").assertIsDisplayed()
                compose.onNodeWithText("Cancel").performClick()

                // The card body opens the player, independently of the source buttons.
                compose.onNodeWithText("Startup puzzle").performClick()
                compose.onNodeWithText("Okay, start puzzle").assertIsDisplayed()
            }
        } finally {
            server.stop(0)
        }
    }
}
