package de.ninefold

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface AppStorage {
    fun read(key: String): String?
    fun write(key: String, payload: String)
}

class MemoryAppStorage : AppStorage {
    private val records = mutableMapOf<String, String>()
    override fun read(key: String) = records[key]
    override fun write(key: String, payload: String) { records[key] = payload }
}

expect fun platformAppStorage(): AppStorage

@Serializable
internal data class CheckingSettings(
    val inputMode: String = "Digit", val multiSelect: Boolean = false,
    val rules: Boolean = true, val automaticRules: Boolean = true,
    val specialRules: Boolean = true, val solution: Boolean = true, val automaticSolution: Boolean = true,
)

@Serializable
internal data class SavedGame(
    val puzzle: Puzzle, val cells: List<CellState>, val started: Boolean,
    val history: List<List<CellState>> = emptyList(), val redo: List<List<CellState>> = emptyList(),
    val automaticColours: Map<Int, AutomaticColourRule> = DefaultAutomaticColours,
) {
    val completed: Boolean get() = puzzle.isComplete(cells)
}

internal fun Puzzle.storageId(): String = sourceUrl ?: Json.encodeToString(copy(videoUrl = null))

/** Converted puzzle data, moves, and small timer records have independent storage keys. */
internal class GameRepository(
    private val storage: AppStorage,
    private val refetchPuzzleData: suspend (String) -> Puzzle = ::reimportSudokuPadPuzzle,
    private val fetchPuzzleData: suspend (String) -> Puzzle = ::importSudokuPadPuzzle,
) {
    private val json = Json { ignoreUnknownKeys = true }
    var storageError by mutableStateOf<String?>(null)
        private set
    private inline fun <reified T> read(key: String): T? = runCatching {
        storage.read("ninefold.$key.v1")?.let { json.decodeFromString<T>(it) }
    }.getOrNull()
    private inline fun <reified T> write(key: String, value: T): Boolean = try {
        storage.write("ninefold.$key.v1", json.encodeToString(value)); true
    } catch (error: Throwable) {
        storageError = "Could not save locally. Progress and offline access may be lost: ${error.message.orEmpty()}"
        false
    }
    var settings by mutableStateOf(read<CheckingSettings>("settings") ?: CheckingSettings())
        private set
    var games by mutableStateOf(read<List<String>>("games").orEmpty().mapNotNull { id ->
        read<SavedGame>("game.$id")?.takeIf { it.cells.size == 81 }?.let { id to it.copy(automaticColours = DefaultAutomaticColours + it.automaticColours) }
    }.toMap())
        private set
    private val puzzles = mutableMapOf<String, Puzzle>()
    private val fetchLocks = mutableMapOf<String, Mutex>()

    val statuses = mutableStateMapOf<String, DownloadStatus>()
    val downloadErrors = mutableStateMapOf<String, String>()
    private val downloads = mutableMapOf<String, Job>()
    private val slots = Semaphore(4)

    fun prepare(entries: List<SudokuIndexEntry>, scope: CoroutineScope) {
        entries.forEach { entry ->
            if (cachedPuzzle(entry) != null) statuses[entry.id] = DownloadStatus.Ready
            else if (downloads[entry.id]?.isActive != true) {
                statuses[entry.id] = DownloadStatus.Waiting
                downloadErrors.remove(entry.id)
                downloads[entry.id] = scope.launch {
                    slots.withPermit {
                        statuses[entry.id] = DownloadStatus.Downloading
                        try {
                            fetchPuzzle(entry)
                            statuses[entry.id] = DownloadStatus.Ready
                        } catch (error: Throwable) {
                            if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                            statuses[entry.id] = DownloadStatus.Failed
                            downloadErrors[entry.id] = error.message ?: "Try again when connected to the internet."
                        }
                    }
                }
            }
        }
    }

    fun saveSettings(value: CheckingSettings) { settings = value; write("settings", settings) }
    var recentGames by mutableStateOf(read<List<String>>("recent-games") ?: games.keys.reversed())
        private set
    fun markPlayed(puzzle: Puzzle) {
        recentGames = listOf(puzzle.storageId()) + recentGames.filter { it != puzzle.storageId() }
        write("recent-games", recentGames)
    }
    fun game(puzzle: Puzzle) = games[puzzle.storageId()]
    fun saveGame(game: SavedGame) {
        val id = game.puzzle.storageId()
        games = games + (id to game)
        write("game.$id", game)
        write("games", games.keys.toList())
    }
    fun elapsed(puzzle: Puzzle): Long = (read<Long>("timer.${puzzle.storageId()}") ?: 0L).coerceAtLeast(0)
    fun saveElapsed(puzzle: Puzzle, seconds: Long) { write("timer.${puzzle.storageId()}", seconds) }
    fun cachedPuzzle(entry: SudokuIndexEntry): Puzzle? = puzzles[entry.puzzleUrl]
        ?: read<Puzzle>("puzzle.${entry.puzzleUrl}")?.also { puzzles[entry.puzzleUrl] = it }

    suspend fun reimportPuzzle(
        sourceUrl: String,
        videoUrl: String? = null,
        fetch: suspend (String) -> Puzzle = refetchPuzzleData,
    ): Puzzle = fetchLocks.getOrPut(sourceUrl) { Mutex() }.withLock {
        require(sudokuPadPuzzleId(sourceUrl) != null) { "This puzzle has no SudokuPad source to reimport." }
        val converted = withTimeout(15_000) { fetch(sourceUrl) }
        // Keep the storage identity and catalog video even if the converter's metadata changes.
        val oldGame = games[sourceUrl]
        val oldPuzzle = oldGame?.puzzle ?: puzzles[sourceUrl] ?: read<Puzzle>("puzzle.$sourceUrl")
        val puzzle = converted.copy(sourceUrl = sourceUrl, videoUrl = videoUrl ?: oldPuzzle?.videoUrl ?: converted.videoUrl)
        val starting = puzzle.startingCells()
        fun reconcile(cells: List<CellState>) = cells.mapIndexed { index, cell ->
            val clue = starting[index]
            when {
                clue.given -> clue.copy(colours = cell.colours)
                cell.given -> CellState(colours = cell.colours)
                else -> cell
            }
        }
        val updatedGame = oldGame?.copy(puzzle = puzzle, cells = reconcile(oldGame.cells),
            history = oldGame.history.map(::reconcile), redo = oldGame.redo.map(::reconcile))
        // Do not publish the replacement until both persistent records have been saved.
        if (updatedGame != null) check(write("game.$sourceUrl", updatedGame)) {
            "Could not save the reimported puzzle."
        }
        if (!write("puzzle.$sourceUrl", puzzle)) {
            if (oldGame != null) write("game.$sourceUrl", oldGame)
            error("Could not save the reimported puzzle for offline play.")
        }
        puzzles[sourceUrl] = puzzle
        if (updatedGame != null) games = games + (sourceUrl to updatedGame)
        puzzle
    }

    suspend fun fetchPuzzle(entry: SudokuIndexEntry, fetch: suspend (String) -> Puzzle = fetchPuzzleData): Puzzle = fetchLocks.getOrPut(entry.puzzleUrl) { Mutex() }.withLock {
        cachedPuzzle(entry)?.let { return@withLock it }
        val puzzle = withTimeout(15_000) { fetch(entry.puzzleUrl) }
            .let { it.copy(videoUrl = entry.videoUrl ?: it.videoUrl) }
        check(write("puzzle.${entry.puzzleUrl}", puzzle)) { "Puzzle converted, but could not save it for offline play." }
        puzzles[entry.puzzleUrl] = puzzle
        puzzle
    }
}
