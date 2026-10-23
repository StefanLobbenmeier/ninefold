package de.ninefold

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ninefold.shared.generated.resources.Res
import ninefold.shared.generated.resources.ninefold_icon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

internal enum class DownloadStatus(val label: String) {
    Waiting("Waiting to download"), Downloading("Getting your puzzle ready…"),
    Ready("Ready offline"), Failed("Unavailable · Tap retry"),
}

@Composable
internal fun SudokuCatalog(repository: SudokuIndexRepository, games: GameRepository, downloadScope: CoroutineScope, appUpdateState: AppUpdateState = AppUpdateState.None, onApplyUpdate: () -> Unit = {}, browseTab: Boolean = false, onTab: ((Boolean) -> Unit)? = null, onOpen: (Puzzle) -> Unit) {
    var localTab by remember { mutableStateOf(false) }
    val browsing = if (onTab == null) localTab else browseTab
    fun switchTab(value: Boolean) { if (onTab == null) localTab = value else onTab(value) }
    val inProgress = games.games.values.filter { it.started && !it.completed }.sortedBy { games.recentGames.indexOf(it.puzzle.storageId()).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
    var index by remember(repository) { mutableStateOf(repository.cached()) }
    var refreshing by remember { mutableStateOf(true) }
    var cached by remember { mutableStateOf(index != null) }
    var refreshRequest by remember { mutableStateOf(0) }
    var indexError by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf<String?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var showStorage by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var reimporting by remember { mutableStateOf<String?>(null) }
    var reimportResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val statuses = games.statuses
    val scope = rememberCoroutineScope()
    val playScroll = rememberLazyListState()
    val browseScroll = rememberLazyListState()
    var progressFilter by rememberSaveable { mutableStateOf(CatalogProgress.All) }
    var difficultyFilter by rememberSaveable { mutableStateOf(CatalogDifficulty.All) }
    val completedUrls = remember(games.games) {
        games.games.values.filter { it.completed }.mapNotNull { it.puzzle.sourceUrl }.toSet()
    }
    val entries = index?.entries.orEmpty()
    val filteredEntries = remember(entries, completedUrls, progressFilter, difficultyFilter) {
        entries.filter { progressFilter.matches(it.puzzleUrl in completedUrls) && difficultyFilter.matches(it.durationSeconds) }
    }
    fun scrollToFilters() { scope.launch { browseScroll.scrollToItem(0) } }
    fun resetFilters() {
        progressFilter = CatalogProgress.All
        difficultyFilter = CatalogDifficulty.All
        scrollToFilters()
    }

    fun reimport(sourceUrl: String, title: String, videoUrl: String?) {
        if (reimporting != null || opening != null) return
        reimporting = title
        scope.launch {
            try {
                games.reimportPuzzle(sourceUrl, videoUrl)
                index?.entries?.filter { it.puzzleUrl == sourceUrl }?.forEach {
                    statuses[it.id] = DownloadStatus.Ready
                    games.downloadErrors.remove(it.id)
                }
                reimportResult = true to "Reimported $title successfully."
            } catch (error: Throwable) {
                if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                reimportResult = false to "Kept the old import of $title. ${error.message ?: "Could not fetch or convert this puzzle."}"
            } finally { reimporting = null }
        }
    }

    LaunchedEffect(repository, refreshRequest) {
        refreshing = true
        indexError = null
        try {
            index = repository.refresh()
            cached = false
        } catch (error: Throwable) {
            if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
            cached = index != null
            indexError = if (cached) "Could not refresh. Showing the saved sudoku index."
                else "Could not load the sudoku index. Connect to the internet and try again."
        } finally { refreshing = false }
    }

    LaunchedEffect(index, refreshRequest) {
        games.prepare(index?.entries.orEmpty(), downloadScope)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Surface(color = Paper) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(Res.drawable.ninefold_icon), contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("NINEFOLD", fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 13.sp, modifier = Modifier.weight(1f))
                IconButton({ showStorage = true }, modifier = Modifier.semantics { contentDescription = "Storage settings" }) { AppIcon(Glyph.Settings) }
                IconButton({ refreshRequest++ }, enabled = !refreshing, modifier = Modifier.semantics { contentDescription = "Refresh" }) { AppIcon(Glyph.Refresh) }
                FilledTonalButton({ showImport = true }) { AppIcon(Glyph.Add); Spacer(Modifier.width(6.dp)); Text("Import") }
            }
        }
        if (appUpdateState != AppUpdateState.None) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when (appUpdateState) {
                                AppUpdateState.Applying -> "Switching to the new version…"
                                AppUpdateState.Failed -> "Could not switch versions. Please try again."
                                else -> "New version ready"
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onApplyUpdate, enabled = appUpdateState != AppUpdateState.Applying) {
                            Text(if (appUpdateState == AppUpdateState.Failed) "Retry" else "Reload")
                        }
                    }
                }
            }
        }
        reimporting?.let { title ->
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Reimporting $title…", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        LazyColumn(
            state = if (browsing) browseScroll else playScroll,
            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().weight(1f).align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!browsing) {
                val latest = inProgress.firstOrNull()
                if (latest != null) item {
                    FeaturedGameCard(latest, games.elapsed(latest.puzzle), enabled = opening == null && reimporting == null,
                        onReimport = latest.puzzle.sourceUrl?.let { url -> { reimport(url, latest.puzzle.title, latest.puzzle.videoUrl) } },
                        onOpen = { onOpen(latest.puzzle) })
                } else item {
                    Card(colors = CardDefaults.cardColors(containerColor = Moss), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(28.dp)) {
                            Text("Your next good idea\nstarts with a square.", color = Color.White, fontFamily = editorialFont(), fontSize = 28.sp, lineHeight = 34.sp)
                            Spacer(Modifier.height(20.dp))
                            FilledTonalButton({ switchTab(true) }) { Text("Find a puzzle"); Spacer(Modifier.width(12.dp)); AppIcon(Glyph.Play) }
                        }
                    }
                }
                if (inProgress.size > 1) item { SectionLabel("Pick up another", "${inProgress.size - 1} in progress") }
                items(inProgress.drop(1), key = { "continue.${it.puzzle.storageId()}" }) { game ->
                    SavedGameCard(game, games.elapsed(game.puzzle), "Continue", enabled = opening == null && reimporting == null,
                        onReimport = game.puzzle.sourceUrl?.let { url -> { reimport(url, game.puzzle.title, game.puzzle.videoUrl) } },
                        onOpen = { onOpen(game.puzzle) })
                }
                val solved = games.games.values.filter { it.completed }.reversed()
                if (solved.isNotEmpty()) item { SectionLabel("Well played", "${solved.size} solved") }
                items(solved, key = { "solved.${it.puzzle.storageId()}" }) { game ->
                    SavedGameCard(game, games.elapsed(game.puzzle), "View", enabled = opening == null && reimporting == null,
                        onReimport = game.puzzle.sourceUrl?.let { url -> { reimport(url, game.puzzle.title, game.puzzle.videoUrl) } },
                        onOpen = { onOpen(game.puzzle) })
                }
                item {
                    TextButton({ switchTab(true) }, Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("Explore the puzzle collection"); Spacer(Modifier.width(10.dp)); AppIcon(Glyph.Grid) }
                }
            } else {
            item {
                Text("Find your next challenge", fontFamily = editorialFont(), fontSize = 34.sp, lineHeight = 40.sp)
                if (refreshing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(if (index == null) "Loading puzzles…" else "Refreshing puzzles…")
                }
                indexError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                index?.let { current ->
                    if (current.entries.isEmpty()) Text("No puzzles have been published yet.")
                }
            }
            if (entries.isNotEmpty()) item {
                CatalogFilters(filteredEntries.size, entries.size, progressFilter, difficultyFilter,
                    onProgress = { progressFilter = it; scrollToFilters() },
                    onDifficulty = { difficultyFilter = it; scrollToFilters() }, onReset = ::resetFilters,
                    summary = index?.let { current ->
                        val ready = current.entries.count { statuses[it.id] == DownloadStatus.Ready }
                        "$ready offline · ${if (cached) "Saved index" else "Updated"} ${deviceDate(current.generatedAt.take(10))}"
                    })
            }
            if (entries.isNotEmpty() && filteredEntries.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No puzzles match these filters.")
                    TextButton(::resetFilters) { Text("Clear filters") }
                }
            }
            items(filteredEntries, key = { "catalog.${it.id}" }) { entry ->
                val status = statuses[entry.id] ?: DownloadStatus.Waiting
                val openPuzzle: () -> Unit = {
                    opening = entry.id
                    openError = null
                    scope.launch {
                        try {
                            val puzzle = games.fetchPuzzle(entry)
                            statuses[entry.id] = DownloadStatus.Ready
                            onOpen(puzzle)
                        } catch (error: Throwable) {
                            if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                            statuses[entry.id] = DownloadStatus.Failed
                            games.downloadErrors[entry.id] = error.message.orEmpty()
                            openError = "Could not open ${entry.title}. ${error.message.orEmpty()}"
                        } finally { opening = null }
                    }
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().alpha(if (status == DownloadStatus.Ready) 1f else .5f).combinedClickable(
                        enabled = status == DownloadStatus.Ready && opening == null && reimporting == null,
                        onClick = openPuzzle, onLongClickLabel = "Reimport puzzle",
                        onLongClick = if (status == DownloadStatus.Ready) {
                            { reimport(entry.puzzleUrl, entry.title, entry.videoUrl) }
                        } else null,
                    ),
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val wide = maxWidth >= 600.dp
                        val details: @Composable () -> Unit = {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (entry.thumbnailUrl == null && status == DownloadStatus.Ready) games.cachedPuzzle(entry)?.let { puzzle ->
                                    val saved = games.game(puzzle)
                                    PuzzlePreview(puzzle, saved?.cells ?: remember(puzzle) { puzzle.startingCells() },
                                        Modifier.size(80.dp), saved?.automaticColours ?: emptyMap())
                                    Spacer(Modifier.width(12.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("${entry.source} · ${entry.publishedAt.take(10)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                                    Text(status.label, style = MaterialTheme.typography.bodySmall, color = if (status == DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                    PuzzleSourceLinks(entry.puzzleUrl, entry.videoUrl)
                                }
                                Spacer(Modifier.width(8.dp))
                                PuzzleOpenButton(
                                    action = if (status == DownloadStatus.Failed) "Retry" else "Play",
                                    title = entry.title, enabled = (status == DownloadStatus.Ready || status == DownloadStatus.Failed) && opening == null && reimporting == null,
                                    opening = opening == entry.id, onOpen = openPuzzle,
                                )
                            }
                        }
                        if (wide) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                entry.thumbnailUrl?.let {
                                    VideoThumbnail(it, entry.durationSeconds, Modifier.width(240.dp))
                                }
                                Box(Modifier.weight(1f)) { details() }
                            }
                        } else {
                            Column {
                                entry.thumbnailUrl?.let { VideoThumbnail(it, entry.durationSeconds) }
                                details()
                            }
                        }
                    }
                }
            }
            }
        }
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            NavigationBarItem(selected = !browsing, onClick = { switchTab(false) }, icon = { AppIcon(Glyph.Play) }, label = { Text("Play") })
            NavigationBarItem(selected = browsing, onClick = { switchTab(true) }, icon = { AppIcon(Glyph.Grid) }, label = { Text("Browse") })
        }
    }
    reimportResult?.let { (success, message) -> AlertDialog(
        onDismissRequest = { reimportResult = null },
        title = { Text(if (success) "Reimport successful" else "Reimport failed") },
        text = { Text(message) },
        confirmButton = { TextButton({ reimportResult = null }) { Text("OK") } },
    ) }
    openError?.let { message -> AlertDialog(onDismissRequest = { openError = null }, confirmButton = { TextButton({ openError = null }) { Text("OK") } }, title = { Text("Puzzle unavailable") }, text = { Text(message) }) }
    if (showStorage) StorageSettingsDialog(games) { showStorage = false }
    if (showImport) ImportDialog({ showImport = false }) { showImport = false; onOpen(it) }
}

@Composable
private fun SavedGameCard(game: SavedGame, elapsed: Long, action: String, enabled: Boolean = true, onReimport: (() -> Unit)? = null, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(
        enabled = enabled, onClick = onOpen, onLongClickLabel = "Reimport puzzle", onLongClick = onReimport,
    ), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PuzzlePreview(game.puzzle, game.cells, Modifier.size(72.dp), game.automaticColours)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(game.puzzle.title, fontWeight = FontWeight.Bold)
                Text("by ${game.puzzle.author} · ${formatElapsed(elapsed)}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            PuzzleOpenButton(action, game.puzzle.title, enabled = enabled, onOpen = onOpen)
        }
    }
}

@Composable
private fun PuzzleSourceLinks(sourceUrl: String?, videoUrl: String?) {
    val uriHandler = LocalUriHandler.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        sourceUrl?.let { url ->
            TextButton(onClick = { uriHandler.openUri(url) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("SudokuPad")
            }
        }
        videoUrl?.let { url ->
            TextButton(onClick = { uriHandler.openUri(url) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Source video")
            }
        }
    }
}

@Composable
private fun PuzzleOpenButton(action: String, title: String, enabled: Boolean = true, opening: Boolean = false, onOpen: () -> Unit) {
    FilledIconButton(
        onClick = onOpen, enabled = enabled,
        modifier = Modifier.semantics { contentDescription = if (opening) "Opening $title" else "$action $title" },
    ) {
        if (opening) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else AppIcon(if (action == "Retry") Glyph.Refresh else Glyph.Play)
    }
}

@Composable
private fun SectionLabel(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(detail, color = Moss, fontSize = 12.sp)
    }
}

@Composable
private fun FeaturedGameCard(game: SavedGame, elapsed: Long, enabled: Boolean, onReimport: (() -> Unit)?, onOpen: () -> Unit) {
    val filled = game.cells.count { it.value != null && !it.given }
    val total = game.cells.count { !it.given }.coerceAtLeast(1)
    Card(Modifier.fillMaxWidth().combinedClickable(enabled = enabled, onClick = onOpen, onLongClickLabel = "Reimport puzzle", onLongClick = onReimport),
        shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Moss, contentColor = Color.White)) {
        Column(Modifier.padding(24.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                val details: @Composable () -> Unit = {
                    Column {
                        Text(game.puzzle.title, fontFamily = editorialFont(), fontSize = 28.sp, lineHeight = 32.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("by ${game.puzzle.author}", color = Sage, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                if (maxWidth >= 560.dp) {
                    val previewSize = (maxWidth * .5f).coerceAtMost(400.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f).height(previewSize), contentAlignment = Alignment.CenterStart) { details() }
                        PuzzlePreview(game.puzzle, game.cells, Modifier.size(previewSize), game.automaticColours)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        details()
                        PuzzlePreview(game.puzzle, game.cells,
                            Modifier.align(Alignment.CenterHorizontally).widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f),
                            game.automaticColours)
                    }
                }
            }
            LinearProgressIndicator(progress = { filled.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Color(0xFFDAD7AA), trackColor = Color.White.copy(alpha = .16f))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${formatElapsed(elapsed)} · ${filled * 100 / total}% filled", color = Sage, fontSize = 12.sp, modifier = Modifier.weight(1f))
                FilledTonalButton(onOpen, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Continue ${game.puzzle.title}" }) {
                    Text("Continue"); Spacer(Modifier.width(8.dp)); AppIcon(Glyph.Play)
                }
            }
        }
    }
}

/** Render at playing size so digits, notes, and constraint strokes scale together. */
@Composable
internal fun PuzzlePreview(
    puzzle: Puzzle,
    cells: List<CellState>,
    modifier: Modifier = Modifier,
    automaticColours: Map<Int, AutomaticColourRule> = emptyMap(),
) {
    Layout(
        content = {
            SudokuBoard(puzzle, cells, emptySet(), emptySet(), interactive = false,
                automaticColours = automaticColours, onSelect = {})
        },
        modifier = modifier.clip(RoundedCornerShape(4.dp)).clearAndSetSemantics {
            contentDescription = "Preview of ${puzzle.title}"
        },
    ) { measurables, constraints ->
        val side = constraints.constrainWidth(88.dp.roundToPx())
            .coerceAtMost(constraints.constrainHeight(88.dp.roundToPx()))
        val boardSize = 360.dp.roundToPx()
        val board = measurables.single().measure(Constraints.fixed(boardSize, boardSize))
        layout(side, side) {
            board.placeWithLayer(0, 0) {
                scaleX = side.toFloat() / boardSize
                scaleY = scaleX
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}
