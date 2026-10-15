package de.sudokupad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    var showImport by remember { mutableStateOf(false) }
    var reimporting by remember { mutableStateOf<String?>(null) }
    var reimportResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val statuses = games.statuses
    val scope = rememberCoroutineScope()
    val playScroll = rememberLazyListState()
    val browseScroll = rememberLazyListState()

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
                AppIcon(Glyph.Grid)
                Spacer(Modifier.width(10.dp))
                Text("NINEFOLD", fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 13.sp, modifier = Modifier.weight(1f))
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
                item {
                    Text("YOUR DAILY MOMENT OF CLARITY", color = Moss, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(if (inProgress.isEmpty()) "Make room for a little wonder." else "Welcome back.", fontFamily = editorialFont(), fontSize = 36.sp, lineHeight = 42.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("One grid. Endless possibilities.", color = Moss, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
                }
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
                Text("Find your next challenge.", fontFamily = editorialFont(), fontSize = 34.sp, lineHeight = 40.sp)
                Text("A collection of curious grids, ready for a quiet moment.", modifier = Modifier.padding(top = 4.dp))
                if (refreshing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(if (index == null) "Loading puzzles…" else "Refreshing puzzles…")
                }
                indexError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                games.storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                index?.let { current ->
                    val ready = current.entries.count { statuses[it.id] == DownloadStatus.Ready }
                    Text("${current.entries.size} puzzles · $ready ready offline · ${if (cached) "Saved index" else "Updated"} ${current.generatedAt.take(10)}", style = MaterialTheme.typography.bodySmall)
                    if (current.entries.isEmpty()) Text("No puzzles have been published yet.")
                }
            }
            items(index?.entries.orEmpty(), key = { "catalog.${it.id}" }) { entry ->
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
                    entry.thumbnailUrl?.let { VideoThumbnail(it, entry.durationSeconds) }
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
    if (showImport) ImportDialog({ showImport = false }) { showImport = false; onOpen(it) }
}

@Composable
private fun SavedGameCard(game: SavedGame, elapsed: Long, action: String, enabled: Boolean = true, onReimport: (() -> Unit)? = null, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(
        enabled = enabled, onClick = onOpen, onLongClickLabel = "Reimport puzzle", onLongClick = onReimport,
    ), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Text("RIGHT WHERE YOU LEFT OFF", fontSize = 10.sp, letterSpacing = 1.8.sp, color = Sage)
            Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(game.puzzle.title, fontFamily = editorialFont(), fontSize = 28.sp, lineHeight = 32.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("by ${game.puzzle.author}", color = Sage, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                MiniatureGrid(game, Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)).background(Color.White))
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

@Composable
private fun MiniatureGrid(game: SavedGame, modifier: Modifier) {
    Canvas(modifier) {
        val cell = size.width / 9
        game.cells.forEachIndexed { index, state ->
            if (state.value != null) drawCircle(Moss.copy(alpha = if (state.given) .8f else .35f), cell * .18f, Offset((index % 9 + .5f) * cell, (index / 9 + .5f) * cell))
        }
        for (i in 1..8) {
            val weight = if (i % 3 == 0) 1.2.dp.toPx() else .4.dp.toPx()
            drawLine(Moss.copy(alpha = .3f), Offset(i * cell, 0f), Offset(i * cell, size.height), weight)
            drawLine(Moss.copy(alpha = .3f), Offset(0f, i * cell), Offset(size.width, i * cell), weight)
        }
    }
}
