package de.sudokupad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val Ink = Color(0xFF202522)
private val Moss = Color(0xFF3D6547)
private val Sage = Color(0xFFDDE9DD)
private val Paper = Color(0xFFF7F6F0)
private val Butter = Color(0xFFF1E6BD)
private val ErrorRed = Color(0xFFC94E45)

internal enum class InputMode(val label: String, val mark: String) {
    Digit("Digit", "5"), Corner("Corner", "1·2"), Centre("Centre", "12"), Colour("Colour", "")
}

enum class AppUpdateState { None, Ready, Applying, Failed }

@Composable
@androidx.compose.ui.tooling.preview.Preview
fun App(
    indexCache: SudokuIndexCache? = null,
    storage: AppStorage = platformAppStorage(),
    indexRepository: SudokuIndexRepository? = null,
    initialShareLink: String? = null,
    shareBaseUrl: String = DEFAULT_SHARE_BASE_URL,
    appUpdateState: AppUpdateState = AppUpdateState.None,
    onApplyUpdate: () -> Unit = {},
) {
    val localIndexCache = remember(storage, indexCache) { indexCache ?: object : SudokuIndexCache {
        override fun read() = storage.read("ninefold.sudoku-index.v1")
        override fun write(payload: String) = storage.write("ninefold.sudoku-index.v1", payload)
    } }
    val games = remember(storage) { GameRepository(storage) }
    val downloadScope = rememberCoroutineScope()
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Moss, onPrimary = Color.White, primaryContainer = Sage, background = Paper, surface = Color.White, onSurface = Ink, error = ErrorRed),
        typography = Typography(),
    ) {
        val sharedPuzzle = remember(initialShareLink) { runCatching { initialShareLink?.let(::puzzleFromShareLink) } }
        var shareError by remember(initialShareLink) { mutableStateOf(sharedPuzzle.exceptionOrNull()?.message) }
        var activePuzzle by remember(initialShareLink) { mutableStateOf(sharedPuzzle.getOrNull()) }
        val repository = remember(localIndexCache, indexRepository) { indexRepository ?: SudokuIndexRepository(localIndexCache) }
        val current = activePuzzle
        Box(Modifier.fillMaxSize()) {
            if (current == null) SudokuCatalog(repository, games, downloadScope, appUpdateState, onApplyUpdate) { activePuzzle = it }
            else key(current) { SudokuApp(current, games, shareBaseUrl, onImport = { activePuzzle = it }) { activePuzzle = null } }
            shareError?.let { message -> AlertDialog(
                onDismissRequest = { shareError = null },
                title = { Text("Could not open shared puzzle") }, text = { Text(message) },
                confirmButton = { TextButton({ shareError = null }) { Text("Okay") } },
            ) }
            games.storageError?.let { message -> Snackbar(Modifier.align(Alignment.BottomCenter)) { Text(message) } }
        }
    }
}

@Composable
internal fun SudokuApp(puzzle: Puzzle, repository: GameRepository, shareBaseUrl: String, onImport: (Puzzle) -> Unit, onBrowse: () -> Unit) {
    val saved = remember(puzzle) { repository.game(puzzle) }
    var cells by remember(puzzle) { mutableStateOf(saved?.cells ?: puzzle.startingCells()) }
    var finalized by remember(puzzle) { mutableStateOf(cells) }
    var colourSettings by remember { mutableStateOf<CellColour?>(null) }
    var selected by remember { mutableStateOf(setOf(CellPosition(0, 2))) }
    val mode = InputMode.entries.firstOrNull { it.name == repository.settings.inputMode } ?: InputMode.Digit
    var showImport by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(saved?.started != true) }
    var started by remember(puzzle) { mutableStateOf(saved?.started ?: false) }
    val multiSelect = repository.settings.multiSelect
    val clock = remember(puzzle) { GameClock().also { it.restore(repository.elapsed(puzzle)) } }
    var elapsed by remember(puzzle) { mutableStateOf(repository.elapsed(puzzle)) }
    var foreground by remember(puzzle) { mutableStateOf(true) }
    DisposableEffect(puzzle) {
        val unsubscribe = observePageVisibility { visible ->
            // Stop synchronously: rendering/coroutines may be suspended as soon as the page hides.
            clock.setForeground(visible)
            foreground = visible
            elapsed = clock.elapsedSeconds()
            repository.saveElapsed(puzzle, elapsed)
        }
        onDispose { unsubscribe() }
    }
    val completed = remember(puzzle, finalized) { puzzle.isComplete(finalized) }
    LaunchedEffect(started, completed, puzzle, foreground) {
        if (started) {
            if (completed) clock.stop() else clock.start()
            elapsed = clock.elapsedSeconds()
            repository.saveElapsed(puzzle, elapsed)
            while (!completed && foreground) {
                delay(200)
                val next = clock.elapsedSeconds()
                if (next != elapsed) { elapsed = next; repository.saveElapsed(puzzle, elapsed) }
            }
        }
    }
    var showChecking by remember { mutableStateOf(false) }
    val settings = repository.settings
    val checkRules = settings.rules
    val checkRulesAutomatically = settings.automaticRules
    val includeSpecialRules = settings.specialRules
    val checkSolution = settings.solution
    val checkSolutionAutomatically = settings.automaticSolution
    var manualRulesCheck by remember { mutableStateOf(false) }
    var manualSolutionCheck by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(saved?.history ?: emptyList()) }
    var redoStack by remember { mutableStateOf(saved?.redo ?: emptyList()) }
    val rulesResult = remember(puzzle, cells, checkRules, checkRulesAutomatically, includeSpecialRules, manualRulesCheck) {
        if (checkRules && (checkRulesAutomatically || manualRulesCheck)) checkSudokuRules(puzzle, cells, includeSpecialRules) else PuzzleCheckResult()
    }
    val solutionResult = remember(puzzle, finalized, selected, checkSolution, checkSolutionAutomatically, manualSolutionCheck) {
        if (checkSolution && (checkSolutionAutomatically || manualSolutionCheck)) checkAgainstSolution(puzzle, finalized).excluding(selected) else PuzzleCheckResult()
    }
    val checkResult = rulesResult + solutionResult

    fun save() {
        repository.saveGame(SavedGame(puzzle, cells, started, history, redoStack))
        repository.saveElapsed(puzzle, clock.elapsedSeconds())
    }
    DisposableEffect(puzzle) {
        onDispose { clock.stop(); repository.saveElapsed(puzzle, clock.elapsedSeconds()) }
    }

    fun clearManualChecks() { manualRulesCheck = false; manualSolutionCheck = false }
    fun checkNow() { manualRulesCheck = checkRules; manualSolutionCheck = checkSolution }

    fun update(transform: (MutableList<CellState>) -> Unit) {
        if (!started) return
        val next = cells.toMutableList().also(transform)
        if (next == cells) return
        history = history + listOf(cells); redoStack = emptyList(); cells = next; finalized = finalizedCells(cells, finalized, selected); clearManualChecks(); save()
    }
    fun enter(number: Int?) = update { next ->
        val entered = applyInput(next, selected, mode, number)
        next.clear(); next.addAll(entered)
    }
    fun undo() { history.lastOrNull()?.let { redoStack = listOf(cells) + redoStack; cells = it; history = history.dropLast(1); finalized = finalizedCells(cells, finalized, selected); clearManualChecks(); save() } }
    fun redo() { redoStack.firstOrNull()?.let { history = history + listOf(cells); cells = it; redoStack = redoStack.drop(1); finalized = finalizedCells(cells, finalized, selected); clearManualChecks(); save() } }

    fun setMultiple(enabled: Boolean) {
        repository.saveSettings(repository.settings.copy(multiSelect = enabled))
    }

    fun changeSelection(next: Set<CellPosition>) {
        val leaving = selected - next
        if (started && leaving.isNotEmpty()) {
            update { board ->
                val coloured = applyAutomaticColours(board, leaving, repository.settings.automaticColours)
                board.clear(); board.addAll(coloured)
            }
        }
        selected = next
        finalized = finalizedCells(cells, finalized, next)
    }
    fun select(position: CellPosition) {
        if (colourSettings != null) return
        changeSelection(BoardSelectionGesture(selected, position, multiSelect).visit())
    }
    val clearSelection by rememberUpdatedState { changeSelection(emptySet()) }


    BoxWithConstraints(Modifier.fillMaxSize().background(Paper).safeDrawingPadding().pointerInput(Unit) {
        awaitEachGesture {
            // Board gestures and controls consume their presses before the final pass.
            // An unhandled press is on the background (including outside clues).
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            if (!down.isConsumed && colourSettings == null) clearSelection()
        }
    }) {
        val compact = maxWidth < 850.dp
        Column(Modifier.fillMaxSize()) {
            TopBar({ clearSelection(); showImport = true }, { clearSelection(); showRules = true }, { clearSelection(); showChecking = true }, { clearSelection(); onBrowse() })
            if (compact) {
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PuzzleHeading(puzzle, compact = true, elapsed = elapsed)
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        val boardSize = minOf(maxWidth, maxHeight)
                        SudokuBoard(puzzle, cells, selected, checkResult.cells, invalidPencilMarks = checkResult.pencilMarks, modifier = Modifier.size(boardSize), additiveSelection = multiSelect, onDragSelection = { if (colourSettings == null) changeSelection(it) }, onSelect = ::select)
                    }
                    SelectionControls(multiSelect, ::setMultiple, selected.size)
                    Controls(mode, { repository.saveSettings(repository.settings.copy(inputMode = it.name)) }, ::enter, history.isNotEmpty(), redoStack.isNotEmpty(), ::undo, ::redo, checkRules || checkSolution, ::checkNow, onColourSettings = { colourSettings = it }, automaticColours = settings.automaticColours, compact = true)
                }
            } else {
                Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        PuzzleHeading(puzzle, elapsed = elapsed); Spacer(Modifier.height(18.dp)); SudokuBoard(puzzle, cells, selected, checkResult.cells, invalidPencilMarks = checkResult.pencilMarks, additiveSelection = multiSelect, onDragSelection = { if (colourSettings == null) changeSelection(it) }) { select(it) }
                    }
                    Column(Modifier.widthIn(min = 270.dp, max = 350.dp)) {
                        SideCard("PLAY") { SelectionControls(multiSelect, ::setMultiple, selected.size); Controls(mode, { repository.saveSettings(repository.settings.copy(inputMode = it.name)) }, ::enter, history.isNotEmpty(), redoStack.isNotEmpty(), ::undo, ::redo, checkRules || checkSolution, ::checkNow, onColourSettings = { colourSettings = it }, automaticColours = settings.automaticColours) }
                        Spacer(Modifier.height(16.dp))
                        SideCard("PUZZLE NOTES") {
                            puzzle.rules.forEachIndexed { index, rule -> Row(Modifier.padding(vertical = 6.dp)) {
                                Text("${index + 1}", color = Moss, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp)); Text(rule, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                            } }
                            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0xFFE5E5DE))
                            TextButton({ clearSelection(); showChecking = true }, Modifier.fillMaxWidth()) { Text("Checking settings") }
                        }
                    }
                }
            }
        }
    }
    colourSettings?.let { colour ->
        AutomaticColourDialog(colour, settings.automaticColours[colour.number] ?: AutomaticColourRule(), { rule ->
            repository.saveSettings(repository.settings.copy(automaticColours = repository.settings.automaticColours + (colour.number to rule)))
        }) { colourSettings = null }
    }
    if (showImport) ImportDialog({ showImport = false }) { imported ->
        showImport = false; onImport(imported)
    }
    if (showRules) RulesDialog(puzzle, shareBaseUrl, starting = !started) {
        if (!started) { clock.start(); started = true; save() }
        showRules = false
    }
    if (showChecking) CheckingDialog(
        checkRules, { repository.saveSettings(settings.copy(rules = it)); manualRulesCheck = false },
        checkRulesAutomatically, { repository.saveSettings(settings.copy(automaticRules = it)); manualRulesCheck = false },
        includeSpecialRules, { repository.saveSettings(settings.copy(specialRules = it)); manualRulesCheck = false },
        checkSolution, { repository.saveSettings(settings.copy(solution = it)); manualSolutionCheck = false },
        checkSolutionAutomatically, { repository.saveSettings(settings.copy(automaticSolution = it)); manualSolutionCheck = false },
    ) { showChecking = false }
}


@Composable
private fun TopBar(onImport: () -> Unit, onRules: () -> Unit, onChecking: () -> Unit, onBrowse: () -> Unit) {
    Surface(shadowElevation = 1.dp, color = Color.White) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 480.dp
            Row(Modifier.fillMaxWidth().height(if (narrow) 52.dp else 64.dp).padding(horizontal = if (narrow) 12.dp else 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Moss), contentAlignment = Alignment.Center) { Text("9", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp) }
                if (!narrow) { Spacer(Modifier.width(10.dp)); Text("NINEFOLD", fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) }
                Spacer(Modifier.weight(1f))
                TextButton(onBrowse) { Text("Puzzles") }; TextButton(onRules) { Text("Rules") }; TextButton(onChecking) { Text("Checks") }; Spacer(Modifier.width(4.dp)); Button(onImport, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = if (narrow) 10.dp else 16.dp, vertical = 10.dp)) { Text(if (narrow) "+" else "+  Import") }
            }
        }
    }
}

@Composable
private fun PuzzleHeading(puzzle: Puzzle, compact: Boolean = false, elapsed: Long = 0) {
    Row(Modifier.widthIn(max = 660.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            val types = buildList {
                if (puzzle.fogLights != null) add("FOG")
                if (puzzle.cages.isNotEmpty()) add("KILLER")
                if (puzzle.arrows.isNotEmpty()) add("ARROW")
                if (puzzle.lines.isNotEmpty()) add("LINES")
                if (isEmpty()) add("CLASSIC")
            }
            Text(types.joinToString(" · "), color = Moss, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(puzzle.title, fontSize = if (compact) 20.sp else 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("by ${puzzle.author}", color = Color(0xFF737772), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatElapsed(elapsed), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SudokuBoard(puzzle: Puzzle, cells: List<CellState>, selected: Set<CellPosition>, conflicts: Set<Int>, uncertain: Set<Int> = emptySet(), invalidPencilMarks: Map<Int, Set<Int>> = emptyMap(), modifier: Modifier = Modifier, additiveSelection: Boolean = false, onDragSelection: ((Set<CellPosition>) -> Unit)? = null, onSelect: (CellPosition) -> Unit) {
    val currentSelection by rememberUpdatedState(selected)
    val currentDragSelection by rememberUpdatedState(onDragSelection)
    val currentAdditive by rememberUpdatedState(additiveSelection)
    val hasOutside = (puzzle.overlays + puzzle.underlays).any { it.center.row !in 0.0..9.0 || it.center.column !in 0.0..9.0 } ||
        puzzle.arrows.any { arrow -> arrow.points.any { it.row !in 0.0..9.0 || it.column !in 0.0..9.0 } }
    val padCells = if (hasOutside) 1 else 0
    val litCells = remember(puzzle, cells) { puzzle.litCells(cells) }
    val revealedMarks = remember(puzzle, litCells) {
        if (puzzle.fogLights == null) emptyList()
        else (puzzle.underlays + puzzle.overlays).filter { it.isRevealedEdgeMark(litCells) }
    }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.widthIn(max = 600.dp).fillMaxWidth().aspectRatio(1f)) {
        val digitSize = minOf(24f, maxWidth.value / (9 + padCells * 2) * .6f).sp
        val inset = maxWidth * (padCells.toFloat() / (9 + padCells * 2))
        Box(Modifier.fillMaxSize().background(if (hasOutside) Paper else Color.White)) {
            Box(Modifier.fillMaxSize().padding(inset).background(Color.White))
            Canvas(Modifier.fillMaxSize().padding(inset)) {
                val cellWidth = size.width / 9
                val cellHeight = size.height / 9
                val edge = 2.dp.toPx()
                clipRect(edge, edge, size.width - edge, size.height - edge) {
                    cells.forEachIndexed { index, cell ->
                        drawCellColours(cell.colours,
                            Offset(index % 9 * cellWidth, index / 9 * cellHeight),
                            Size(cellWidth, cellHeight))
                    }
                }
            }
            PuzzleGraphics(puzzle, padCells, measurer, hiddenOverlays = revealedMarks)
            Box(Modifier.fillMaxSize().padding(inset).then(
                if (onDragSelection == null) Modifier else Modifier.pointerInput(puzzle) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val start = boardCellAt(down.position.x / size.width, down.position.y / size.height)
                        if (start != null) {
                            val gesture = BoardSelectionGesture(currentSelection, start, currentAdditive)
                            var gestureSelection = gesture.visit()
                            var previous = down.position
                            down.consume()
                            currentDragSelection?.invoke(gestureSelection)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val crossed = boardCellsAlongSegment(
                                    previous.x / size.width, previous.y / size.height,
                                    change.position.x / size.width, change.position.y / size.height,
                                )
                                val next = gesture.visit(crossed)
                                if (next != gestureSelection) {
                                    gestureSelection = next
                                    currentDragSelection?.invoke(next)
                                }
                                previous = change.position
                                change.consume()
                            } while (change.pressed)
                        }
                    }
                }
            )) {
                Column(Modifier.fillMaxSize()) { repeat(9) { row -> Row(Modifier.weight(1f)) { repeat(9) { column ->
            val position = CellPosition(row, column); val cell = cells[position.index]; val isSelected = position in selected
            val peer = selected.any { it.row == row || it.column == column || (it.row / 3 == row / 3 && it.column / 3 == column / 3) }
            val same = cell.value != null && selected.any { cells[it.index].value == cell.value }
            val background = when { isSelected -> Butter.copy(alpha = .92f); position.index in conflicts -> Color(0xEFFFF1EC); position.index in uncertain -> Color(0xEFFFEDBD); same -> Color(0xDDE6D9A8); peer -> Color(0x33F1F3EC); else -> Color.Transparent }.let { if (cell.colours.isNotEmpty()) it.copy(alpha = minOf(it.alpha, .18f)) else it }
            val textBackdrop = if (CellColour.DarkGrey.number in cell.colours) Color.White.copy(alpha = .85f) else Color.Transparent
            val fogged = puzzle.fogLights != null && position !in litCells
            Box(Modifier.weight(1f).fillMaxHeight().semantics { this.selected = isSelected; contentDescription = "Row ${row + 1}, column ${column + 1}, ${cell.value ?: "empty"}${if (isSelected) ", selected" else ""}${if (cell.cornerNotes.isNotEmpty()) ", corner notes ${cell.cornerNotes.sorted().joinToString()}" else ""}${if (cell.centreNotes.isNotEmpty()) ", centre notes ${cell.centreNotes.sorted().joinToString()}" else ""}${if (cell.colours.isNotEmpty()) ", colours ${cell.colours.sorted().mapNotNull(CellColour::fromNumber).joinToString { it.label }}" else ""}${if (position.index in uncertain) ", uncertain reading" else ""}" }.clickable { onSelect(position) }, contentAlignment = Alignment.Center) {
                if (fogged) Canvas(Modifier.matchParentSize()) {
                    drawRect(Color(0xFFB4B7B5))
                    drawCellColours(cell.colours)
                }
                Box(Modifier.matchParentSize().background(background))
                when {
                    cell.value != null && (!cell.given || !fogged) -> Text(cell.value.toString(), modifier = Modifier.background(textBackdrop), fontSize = digitSize, fontWeight = if (cell.given) FontWeight.Black else FontWeight.Medium, color = if (position.index in conflicts) ErrorRed else if (cell.given) Ink else Moss)
                    cell.value == null -> {
                        if (cell.cornerNotes.isNotEmpty()) CornerNotes(cell.cornerNotes, invalidPencilMarks[position.index].orEmpty(), textBackdrop)
                        if (cell.centreNotes.isNotEmpty()) CentreNotes(cell.centreNotes, invalidPencilMarks[position.index].orEmpty(), textBackdrop)
                    }
                }
                if (isSelected) Canvas(Modifier.matchParentSize()) {
                    val width = 2.dp.toPx()
                    // Paint inside exposed edges so neighboring cells share one continuous outline.
                    selectionBorders(position, selected).forEach { edge ->
                        when (edge) {
                            SelectionBorder.Top -> drawRect(Moss, size = Size(size.width, width))
                            SelectionBorder.Right -> drawRect(Moss, Offset(size.width - width, 0f), Size(width, size.height))
                            SelectionBorder.Bottom -> drawRect(Moss, Offset(0f, size.height - width), Size(size.width, width))
                            SelectionBorder.Left -> drawRect(Moss, size = Size(width, size.height))
                        }
                    }
                }
                if (!fogged) puzzle.cages.firstOrNull { position in it.cells }?.let { cage -> if (position == cage.cells.minBy { it.index }) Text(cage.total.toString(), Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 1.dp), fontSize = 8.sp, color = Color(0xFF765A7C), fontWeight = FontWeight.Bold) }
            }
                } } } }
                CageOverlay(puzzle.cages.mapNotNull { cage ->
                    val visible = cage.cells.intersect(litCells)
                    cage.copy(cells = visible).takeIf { visible.isNotEmpty() }
                })
            }
            // Keep cell boundaries above the opaque fog layer.
            Canvas(Modifier.fillMaxSize().padding(inset)) {
                val thin = .55.dp.toPx()
                val thick = 1.5.dp.toPx()
                val color = Ink.copy(alpha = .65f)
                for (line in 1..8) {
                    val width = if (line % 3 == 0) thick else thin
                    val x = size.width * line / 9f
                    val y = size.height * line / 9f
                    drawLine(color, Offset(x, 0f), Offset(x, size.height), width)
                    drawLine(color, Offset(0f, y), Offset(size.width, y), width)
                }
            }
            // Edge clues are drawn whole above fog and grid lines once either neighbor is lit.
            if (revealedMarks.isNotEmpty()) PuzzleGraphics(puzzle, padCells, measurer, revealedMarks)
            Box(Modifier.fillMaxSize().padding(inset).border(2.dp, Ink))
        }
    }
}

private fun DrawScope.drawCellColours(colours: Set<Int>, topLeft: Offset = Offset.Zero, area: Size = size) {
    val swatches = colours.sorted().mapNotNull(CellColour::fromNumber)
    swatches.forEachIndexed { stripe, colour ->
        drawRect(colour.swatch,
            topLeft + Offset(stripe * area.width / swatches.size, 0f),
            Size(area.width / swatches.size, area.height))
    }
}

@Composable
private fun BoxScope.PuzzleGraphics(
    puzzle: Puzzle, padCells: Int, measurer: androidx.compose.ui.text.TextMeasurer,
    revealedMarks: List<PuzzleOverlay>? = null, hiddenOverlays: List<PuzzleOverlay> = emptyList(),
) {
    Canvas(Modifier.matchParentSize()) {
        val total = (9 + padCells * 2).toFloat()
        val cell = size.width / total
        fun at(point: GridPoint) = Offset(((point.column + padCells) * cell).toFloat(), ((point.row + padCells) * cell).toFloat())
        fun overlay(item: PuzzleOverlay) {
            val center = at(item.center)
            val area = Size((item.width * cell).toFloat(), (item.height * cell).toFloat())
            val topLeft = center - Offset(area.width / 2, area.height / 2)
            item.backgroundColor?.toComposeColor()?.let { color ->
                if (item.rounded) drawOval(color, topLeft, area) else drawRect(color, topLeft, area)
            }
            item.borderColor?.toComposeColor()?.let { color ->
                if (item.rounded) drawOval(color, topLeft, area, style = Stroke(item.thickness.toFloat().coerceAtLeast(1f)))
                else drawRect(color, topLeft, area, style = Stroke(item.thickness.toFloat().coerceAtLeast(1f)))
            }
            item.text?.takeIf(String::isNotEmpty)?.let { text ->
                val style = androidx.compose.ui.text.TextStyle(color = item.textColor?.toComposeColor() ?: Ink, fontSize = (max(8f, area.height * .48f) / density).sp, textAlign = TextAlign.Center)
                val measured = measurer.measure(text, style)
                drawText(measured, topLeft = center - Offset(measured.size.width / 2f, measured.size.height / 2f))
            }
        }
        if (revealedMarks != null) {
            revealedMarks.forEach(::overlay)
            return@Canvas
        }
        puzzle.underlays.filterNot { it in hiddenOverlays }.forEach(::overlay)
        puzzle.lines.forEach { line ->
            val path = Path(); line.points.firstOrNull()?.let { path.moveTo(at(it).x, at(it).y) }
            line.points.drop(1).forEach { path.lineTo(at(it).x, at(it).y) }
            drawPath(path, line.color.toComposeColor() ?: Color.Gray, style = Stroke((line.thickness / 50.0 * cell).toFloat().coerceAtLeast(2f)))
        }
        puzzle.arrows.forEach { arrow ->
            val color = arrow.color.toComposeColor() ?: Color.Gray
            arrow.points.zipWithNext().forEach { (a, b) -> drawLine(color, at(a), at(b), (arrow.thickness / 50.0 * cell).toFloat().coerceAtLeast(1.5f)) }
            val tip = arrow.points.lastOrNull()?.let(::at); val previous = arrow.points.dropLast(1).lastOrNull()?.let(::at)
            if (tip != null && previous != null) {
                val angle = atan2((tip.y - previous.y).toDouble(), (tip.x - previous.x).toDouble())
                val length = (arrow.headLength * cell).toFloat()
                drawLine(color, tip, Offset(tip.x - length * cos(angle - .55).toFloat(), tip.y - length * sin(angle - .55).toFloat()), 2f)
                drawLine(color, tip, Offset(tip.x - length * cos(angle + .55).toFloat(), tip.y - length * sin(angle + .55).toFloat()), 2f)
            }
        }
        puzzle.overlays.filterNot { it in hiddenOverlays }.forEach(::overlay)
    }
}

private fun String.toComposeColor(): Color? = runCatching {
    val hex = removePrefix("#")
    when (hex.length) {
        3 -> Color(("FF" + hex.map { "$it$it" }.joinToString("")).toLong(16))
        4 -> Color((hex.last().let { "$it$it" } + hex.dropLast(1).map { "$it$it" }.joinToString("")).toLong(16))
        6 -> Color(("FF$hex").toLong(16))
        8 -> Color((hex.takeLast(2) + hex.dropLast(2)).toLong(16))
        else -> error("Unsupported color")
    }
}.getOrNull()

@Composable
private fun BoxScope.CageOverlay(cages: List<KillerCage>) {
    Canvas(Modifier.matchParentSize()) {
        val cw = size.width / 9f; val ch = size.height / 9f; val cageColor = Color(0xFF826B87); val inset = 4.dp.toPx()
        cages.forEach { cage -> cage.cells.forEach { cell ->
            val left = cell.column * cw + inset; val top = cell.row * ch + inset; val right = (cell.column + 1) * cw - inset; val bottom = (cell.row + 1) * ch - inset
            val effect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
            if (CellPosition(cell.row - 1, cell.column) !in cage.cells) drawLine(cageColor, Offset(left, top), Offset(right, top), 1.dp.toPx(), pathEffect = effect)
            if (CellPosition(cell.row + 1, cell.column) !in cage.cells) drawLine(cageColor, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx(), pathEffect = effect)
            if (CellPosition(cell.row, cell.column - 1) !in cage.cells) drawLine(cageColor, Offset(left, top), Offset(left, bottom), 1.dp.toPx(), pathEffect = effect)
            if (CellPosition(cell.row, cell.column + 1) !in cage.cells) drawLine(cageColor, Offset(right, top), Offset(right, bottom), 1.dp.toPx(), pathEffect = effect)
        } }
    }
}

@Composable
private fun CornerNotes(notes: Set<Int>, invalid: Set<Int>, backdrop: Color = Color.Transparent) {
    val sortedNotes = notes.sorted()
    val corners = listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)
    Box(Modifier.fillMaxSize().padding(3.dp)) {
        corners.forEachIndexed { corner, alignment ->
            // Fill the corners first, then pack additional marks beside them.
            val cornerNotes = sortedNotes.filterIndexed { index, _ -> index % corners.size == corner }
            if (cornerNotes.isNotEmpty()) {
                Text(buildAnnotatedString {
                    cornerNotes.forEach { number ->
                        withStyle(SpanStyle(color = if (number in invalid) ErrorRed else Moss)) { append(number.toString()) }
                    }
                }, modifier = Modifier.align(alignment).background(backdrop), fontSize = 8.sp, lineHeight = 8.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CentreNotes(notes: Set<Int>, invalid: Set<Int>, backdrop: Color = Color.Transparent) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val noteSize = minOf(10f, maxWidth.value / notes.size.coerceAtLeast(1)).sp
        Text(buildAnnotatedString {
            notes.sorted().forEach { number ->
                withStyle(SpanStyle(color = if (number in invalid) ErrorRed else Moss)) { append(number.toString()) }
            }
        }, modifier = Modifier.background(backdrop), fontSize = noteSize, lineHeight = noteSize, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SideCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E2DA))) {
        Column(Modifier.padding(18.dp)) { Text(label, color = Color(0xFF83877F), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp); Spacer(Modifier.height(12.dp)); content() }
    }
}

@Composable
private fun Controls(mode: InputMode, onMode: (InputMode) -> Unit, onNumber: (Int?) -> Unit, canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, canCheck: Boolean, onCheck: () -> Unit, onColourSettings: (CellColour) -> Unit, automaticColours: Map<Int, AutomaticColourRule>, compact: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..9).chunked(3).forEach { numbers ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    numbers.forEach { number ->
                        val colour = if (mode == InputMode.Colour) CellColour.fromNumber(number) else null
                        val buttonModifier = Modifier.weight(1f).height(if (compact) 44.dp else 52.dp)
                        if (colour != null) Surface(
                            modifier = buttonModifier.semantics { contentDescription = "${colour.label}, colour $number" }
                                .combinedClickable(onClick = { onNumber(number) }, onLongClickLabel = "Automatic colouring settings", onLongClick = { onColourSettings(colour) }),
                            shape = RoundedCornerShape(10.dp), color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD3D6CD)),
                        ) {
                            Box(Modifier.fillMaxSize().padding(7.dp).clip(RoundedCornerShape(5.dp)).background(colour.swatch), contentAlignment = Alignment.Center) {
                                if (automaticColours[number]?.enabled == true) Text("A", color = if (colour == CellColour.DarkGrey) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else OutlinedButton({ onNumber(number) }, buttonModifier, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(0.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD3D6CD))) {
                            Text(number.toString(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.weight(1f))
                OutlinedButton({ onNumber(null) }, Modifier.weight(1f).height(if (compact) 44.dp else 52.dp).semantics { contentDescription = if (mode == InputMode.Colour) "Zero, clear selected colours" else "Zero, erase selected cells" }, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(0.dp)) { Text("0", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
                OutlinedButton({ onNumber(null) }, Modifier.weight(1f).height(if (compact) 44.dp else 52.dp).semantics { contentDescription = if (mode == InputMode.Colour) "Erase colours" else "Erase" }, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(0.dp)) { Text("Erase", fontSize = 13.sp) }
            }
        }
        Column(Modifier.width(72.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InputMode.entries.forEach { item -> val active = item == mode
                Surface(Modifier.fillMaxWidth().height(if (compact) 44.dp else 52.dp).semantics { selected = active }.clickable { onMode(item) }, shape = RoundedCornerShape(10.dp), color = if (active) Moss else Color(0xFFF0F1EA)) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        if (item == InputMode.Colour) Canvas(Modifier.size(24.dp, 18.dp)) {
                            listOf(CellColour.Red, CellColour.Yellow, CellColour.Blue).forEachIndexed { index, colour ->
                                drawRect(colour.swatch, Offset(index * size.width / 3, 2.dp.toPx()), Size(size.width / 3, size.height - 4.dp.toPx()))
                            }
                        } else Text(item.mark, color = if (active) Color.White else Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(item.label, color = if (active) Color.White else Color.Gray, fontSize = 10.sp)
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onUndo, Modifier.weight(1f), enabled = canUndo) { Text("Undo") }
        TextButton(onRedo, Modifier.weight(1f), enabled = canRedo) { Text("Redo") }
        TextButton(onCheck, Modifier.weight(1f), enabled = canCheck) { Text("Check") }
    }
}

@Composable
private fun AutomaticColourDialog(colour: CellColour, rule: AutomaticColourRule, onRule: (AutomaticColourRule) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)).background(colour.swatch))
            Text("${colour.label} colouring")
        } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckOption("Automatic colouring", "Apply when you leave a cell.", rule.enabled) { onRule(rule.copy(enabled = it)) }
            Text("Choose matching numbers", fontWeight = FontWeight.SemiBold)
            (1..9).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { number ->
                        FilterChip(selected = number in rule.numbers, onClick = {
                            onRule(rule.copy(numbers = if (number in rule.numbers) rule.numbers - number else rule.numbers + number))
                        }, label = { Text(number.toString()) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            Text("Matches an entered digit or centre marks made up entirely of these numbers. Already coloured cells are skipped. If several colours match, the first in the palette applies.", color = Color.Gray, fontSize = 12.sp)
        } },
        confirmButton = { Button(onDismiss) { Text("Done") } },
    )
}

@Composable
private fun CheckingDialog(
    checkRules: Boolean, onCheckRules: (Boolean) -> Unit,
    automaticRules: Boolean, onAutomaticRules: (Boolean) -> Unit,
    includeSpecialRules: Boolean, onIncludeSpecialRules: (Boolean) -> Unit,
    checkSolution: Boolean, onCheckSolution: (Boolean) -> Unit,
    automaticSolution: Boolean, onAutomaticSolution: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onDismiss) { Text("Done") } },
        title = { Text("Checking settings") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckOption("Check Sudoku rules", "Rows, columns, boxes, and impossible pencil marks.", checkRules, onCheckRules)
            IndentedCheckOption("Check automatically", automaticRules, onAutomaticRules, checkRules)
            IndentedCheckOption("Include special rules", includeSpecialRules, onIncludeSpecialRules, checkRules)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            CheckOption("Check against solution", "Wrong digits and pencil marks that exclude the solution.", checkSolution, onCheckSolution)
            IndentedCheckOption("Check automatically", automaticSolution, onAutomaticSolution, checkSolution)
            Text("Solution checks wait until you leave a cell. Rule checks can run while you type. Checks that are not automatic run when you press Check.", color = Color.Gray, fontSize = 12.sp, lineHeight = 17.sp)
        } },
        shape = RoundedCornerShape(22.dp),
    )
}

@Composable
private fun CheckOption(label: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChecked)
        Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.SemiBold); Text(description, color = Color.Gray, fontSize = 12.sp) }
    }
}

@Composable
private fun IndentedCheckOption(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, enabled: Boolean) {
    Row(Modifier.padding(start = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChecked, enabled = enabled)
        Text(label, color = if (enabled) Ink else Color.Gray)
    }
}

@Composable
private fun SelectionControls(multiple: Boolean, onMultiple: (Boolean) -> Unit, count: Int) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = multiple, onClick = { onMultiple(!multiple) }, label = { Text("Select multiple") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Sage, selectedLabelColor = Moss))
        Spacer(Modifier.weight(1f))
        Text(if (multiple || count > 1) "$count cells selected" else "Tap or drag to select", color = Moss, fontSize = 12.sp)
    }
}

@Composable
private fun RulesDialog(puzzle: Puzzle, shareBaseUrl: String, starting: Boolean, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val shareLink = remember(puzzle, shareBaseUrl) { runCatching { puzzleShareLink(puzzle, shareBaseUrl) } }
    var copied by remember { mutableStateOf(false) }
    var copyError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!starting) onDismiss() },
        confirmButton = { Button(onDismiss) { Text(if (starting) "Okay, start puzzle" else "Okay") } },
        containerColor = Color.White,
        title = { Column { Text(puzzle.title, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("by ${puzzle.author}", style = MaterialTheme.typography.bodyMedium, color = Moss) } },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            Text("RULES", color = Moss, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            puzzle.rules.forEach { Text("•  $it", Modifier.padding(vertical = 5.dp), lineHeight = 20.sp) }
            if (puzzle.videoUrl != null || puzzle.sourceUrl != null) HorizontalDivider(Modifier.padding(vertical = 8.dp))
            puzzle.videoUrl?.let { url -> TextButton({ uriHandler.openUri(url) }) { Text("Watch source video") } }
            puzzle.sourceUrl?.let { url -> TextButton({ uriHandler.openUri(url) }) { Text("Open original SudokuPad") } }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("SHARE PUZZLE", color = Moss, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text("Open this link on another device to import this puzzle.", Modifier.padding(vertical = 5.dp), fontSize = 12.sp)
            shareLink.getOrNull()?.let { url ->
                OutlinedTextField(value = url, onValueChange = {}, readOnly = true,
                    label = { Text("Puzzle link") }, maxLines = 2, modifier = Modifier.fillMaxWidth())
                TextButton({ scope.launch {
                    try {
                        clipboard.setClipEntry(puzzleLinkClipEntry(url))
                        copied = true; copyError = null
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) { copyError = "Could not copy the link. Select it above to copy manually." }
                } }) { Text(if (copied) "Copied!" else "Copy puzzle link") }
            }
            (copyError ?: shareLink.exceptionOrNull()?.message)?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
            if (starting) Text("Your timer starts when you tap Okay.", color = Color.Gray, fontSize = 12.sp)
        } }, shape = RoundedCornerShape(22.dp),
    )
}
