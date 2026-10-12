package de.sudokupad

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
                "puzzleUrl": "https://sudokupad.app/startup"
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
                        Json.encodeToString(Puzzle("Startup puzzle", "Test author", ".".repeat(81))))
                }
                val repository = SudokuIndexRepository(cache) {
                    client.get("http://127.0.0.1:${server.address.port}/index.json").bodyAsText()
                }
                FileKit.init(appId = "de.sudokupad")
                compose.setContent { App(indexCache = cache, storage = storage, indexRepository = repository) }

                compose.onNodeWithText("NINEFOLD").assertIsDisplayed()
                compose.onNodeWithText("Browse sudokus").assertIsDisplayed()
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithText("1 puzzles · 1 ready offline · Updated 2026-10-12")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("Startup puzzle").assertIsDisplayed()
                compose.onNodeWithText("1 puzzles · 1 ready offline · Updated 2026-10-12").assertIsDisplayed()
                compose.onNodeWithText("Play").assertIsEnabled()
                assertEquals("Startup puzzle", repository.cached()?.entries?.single()?.title)

                // Opening Import also composes the FileKit picker integration.
                compose.onNodeWithText("Import").performClick()
                compose.onNodeWithText("SudokuPad link").assertIsDisplayed()
            }
        } finally {
            server.stop(0)
        }
    }
}
