package de.sudokupad

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SudokuIndexTest {
    private fun payload(title: String = "New puzzle") = """{
        "schemaVersion": 1, "generatedAt": "2026-10-12T00:00:00Z",
        "sources": [{"id":"ctc", "name":"Cracking the Cryptic", "extra":"retained by publisher"}],
        "entries": [
            {"id":"older", "sourceId":"other", "title":"Older puzzle", "publishedAt":"2026-10-10T00:00:00Z", "puzzleUrl":"https://sudokupad.app/older"},
            {"id":"new", "sourceId":"ctc", "title":"$title", "publishedAt":"2026-10-12T00:00:00Z", "puzzleUrl":"https://sudokupad.app/new", "links":[], "metadata":{}}
        ]
    }"""

    @Test fun parsesPublishedContractAndOrdersNewestFirst() {
        val index = parseSudokuIndex(payload())
        assertEquals(listOf("new", "older"), index.entries.map { it.id })
        assertEquals("Cracking the Cryptic", index.entries.first().source)
        assertEquals("other", index.entries.last().source)
    }

    @Test fun refreshPersistsValidIndexForNextVisit() = runTest {
        val cache = MemorySudokuIndexCache()
        val repository = SudokuIndexRepository(cache) { payload() }
        assertNull(repository.cached())
        val refreshed = repository.refresh()
        assertEquals(refreshed, SudokuIndexRepository(cache).cached())
    }

    @Test fun offlineRefreshRetainsSavedIndex() = runTest {
        val cache = MemorySudokuIndexCache().also { it.write(payload("Saved puzzle")) }
        val repository = SudokuIndexRepository(cache) { throw IllegalStateException("Offline") }
        assertFailsWith<IllegalStateException> { repository.refresh() }
        assertEquals("Saved puzzle", repository.cached()?.entries?.first()?.title)
    }

    @Test fun invalidRefreshDoesNotReplaceSavedIndex() = runTest {
        val cache = MemorySudokuIndexCache().also { it.write(payload("Saved puzzle")) }
        val repository = SudokuIndexRepository(cache) { payload().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2") }
        assertFailsWith<IllegalArgumentException> { repository.refresh() }
        assertEquals("Saved puzzle", repository.cached()?.entries?.first()?.title)
    }

    @Test fun corruptOrUnavailableCacheDoesNotPreventRefresh() = runTest {
        val corrupt = MemorySudokuIndexCache().also { it.write("broken") }
        assertNull(SudokuIndexRepository(corrupt).cached())
        val disabled = object : SudokuIndexCache {
            override fun read(): String? = error("Storage disabled")
            override fun write(payload: String) = error("Storage disabled")
        }
        val repository = SudokuIndexRepository(disabled) { payload() }
        assertNull(repository.cached())
        assertEquals(2, repository.refresh().entries.size)
    }

    @Test fun cancellationDoesNotOverwriteCache() = runTest {
        val cache = MemorySudokuIndexCache().also { it.write(payload("Saved puzzle")) }
        val repository = SudokuIndexRepository(cache) { throw CancellationException() }
        assertFailsWith<CancellationException> { repository.refresh() }
        assertEquals("Saved puzzle", repository.cached()?.entries?.first()?.title)
    }

    @Test fun rejectsUnexpectedPuzzleHosts() {
        assertFailsWith<IllegalArgumentException> { parseSudokuIndex(payload().replace("https://sudokupad.app/new", "https://example.com/new")) }
    }
}
