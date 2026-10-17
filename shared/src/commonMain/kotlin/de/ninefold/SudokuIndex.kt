package de.ninefold

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

const val SUDOKU_INDEX_URL = "https://stefanlobbenmeier.github.io/sudoku-index/index.json"

interface SudokuIndexCache {
    fun read(): String?
    fun write(payload: String)
}

class MemorySudokuIndexCache : SudokuIndexCache {
    private var payload: String? = null
    override fun read() = payload
    override fun write(payload: String) { this.payload = payload }
}

data class SudokuIndexEntry(val id: String, val title: String, val source: String, val publishedAt: String, val puzzleUrl: String, val videoUrl: String? = null, val thumbnailUrl: String? = null, val durationSeconds: Int? = null)
data class SudokuIndex(val generatedAt: String, val entries: List<SudokuIndexEntry>)

internal fun parseSudokuIndex(payload: String): SudokuIndex {
    val root = Json.parseToJsonElement(payload).jsonObject
    require(root["schemaVersion"]?.jsonPrimitive?.int == 1) { "Unsupported sudoku index version." }
    val sources = root.getValue("sources").jsonArray.associate { item ->
        val source = item.jsonObject
        source.getValue("id").jsonPrimitive.content to source.getValue("name").jsonPrimitive.content
    }
    val entries = root.getValue("entries").jsonArray.map { item ->
        val entry = item.jsonObject
        val url = entry.getValue("puzzleUrl").jsonPrimitive.content
        require(url.startsWith("https://sudokupad.app/") && sudokuPadPuzzleId(url) != null) { "Invalid puzzle URL in index." }
        val metadata = entry["metadata"] as? JsonObject
        val videoId = metadata?.get("youtubeVideoId")?.jsonPrimitive?.contentOrNull?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        SudokuIndexEntry(
            entry.getValue("id").jsonPrimitive.content,
            entry.getValue("title").jsonPrimitive.content,
            sources[entry.getValue("sourceId").jsonPrimitive.content] ?: entry.getValue("sourceId").jsonPrimitive.content,
            entry.getValue("publishedAt").jsonPrimitive.content,
            url,
            entry["links"]?.jsonArray?.firstOrNull { link ->
                link.jsonObject["mediaType"]?.jsonPrimitive?.content == "video"
            }?.jsonObject?.get("url")?.jsonPrimitive?.content?.takeIf { it.startsWith("https://") },
            metadata?.get("thumbnailUrl")?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("https://") }
                ?: videoId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" },
            metadata?.get("durationSeconds")?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
        )
    }.distinctBy { it.id }.sortedByDescending { it.publishedAt }
    return SudokuIndex(root.getValue("generatedAt").jsonPrimitive.content, entries)
}

private val indexClient by lazy { HttpClient() }

class SudokuIndexRepository(
    private val cache: SudokuIndexCache,
    private val fetch: suspend () -> String = {
        val response = indexClient.get(SUDOKU_INDEX_URL)
        require(response.status.isSuccess()) { "Sudoku index returned ${response.status.value}." }
        response.bodyAsText()
    },
) {
    fun cached(): SudokuIndex? = runCatching { cache.read()?.let(::parseSudokuIndex) }.getOrNull()

    suspend fun refresh(): SudokuIndex {
        val payload = withTimeout(15_000) { fetch() }
        val index = parseSudokuIndex(payload)
        // Storage can be disabled or full; a successful download should still be usable.
        runCatching { cache.write(payload) }
        return index
    }
}
