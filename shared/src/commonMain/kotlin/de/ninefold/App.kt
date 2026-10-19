package de.ninefold

import kotlin.math.roundToInt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal val Ink = Color(0xFF202522)
internal val Moss = Color(0xFF28564C)
internal val Sage = Color(0xFFDDE9DD)
internal val Paper = Color(0xFFF7F5EF)
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
        colorScheme = lightColorScheme(primary = Moss, onPrimary = Color.White, primaryContainer = Sage, onPrimaryContainer = Moss, secondary = Moss, secondaryContainer = Sage, onSecondaryContainer = Moss, background = Paper, surface = Color.White, onSurface = Ink, onSurfaceVariant = Color(0xFF68756D), outline = Color(0xFFC6CEC5), error = ErrorRed),
        typography = Typography(),
    ) {
        val sharedPuzzle = remember(initialShareLink) { runCatching { initialShareLink?.let(::puzzleFromShareLink) } }
        var shareError by remember(initialShareLink) { mutableStateOf(sharedPuzzle.exceptionOrNull()?.message) }
        var browseTab by remember { mutableStateOf(false) }
        var activePuzzle by remember(initialShareLink) { mutableStateOf(sharedPuzzle.getOrNull()) }
        val repository = remember(localIndexCache, indexRepository) { indexRepository ?: SudokuIndexRepository(localIndexCache) }
        val screenState = rememberSaveableStateHolder()
        val current = activePuzzle
        Box(Modifier.fillMaxSize()) {
            if (current == null) screenState.SaveableStateProvider("catalog") { SudokuCatalog(repository, games, downloadScope, appUpdateState, onApplyUpdate, browseTab, { browseTab = it }) { activePuzzle = it } }
            else key(current) { SudokuApp(current, games, shareBaseUrl) { activePuzzle = null } }
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
internal fun SudokuApp(puzzle: Puzzle, repository: GameRepository, shareBaseUrl: String, onBrowse: () -> Unit) {
    val saved = remember(puzzle) { repository.game(puzzle) }
    var cells by remember(puzzle) { mutableStateOf(saved?.cells ?: puzzle.startingCells()) }
    val digitCounts = remember(cells) { cells.mapNotNull { it.value }.groupingBy { it }.eachCount() }
    var automaticColours by remember(puzzle) { mutableStateOf(puzzle.initialAutomaticColours(saved)) }
    var finalized by remember(puzzle) { mutableStateOf(cells) }
    var colourSettings by remember { mutableStateOf<CellColour?>(null) }
    var selected by remember { mutableStateOf(setOf(CellPosition(0, 2))) }
    val mode = InputMode.entries.firstOrNull { it.name == repository.settings.inputMode } ?: InputMode.Digit
    var altHeld by remember { mutableStateOf(false) }
    val letters = repository.settings.letters != altHeld
    var temporaryMode by remember { mutableStateOf<InputMode?>(null) }
    val effectiveMode = temporaryMode ?: mode
    var showRules by remember { mutableStateOf(saved?.started != true) }
    var paused by remember(puzzle) { mutableStateOf(false) }
    var started by remember(puzzle) { mutableStateOf(saved?.started ?: false) }
    val multiSelect = repository.settings.multiSelect
    val clock = remember(puzzle) { GameClock().also { it.restore(repository.elapsed(puzzle)) } }
    val completionResult = remember(puzzle, cells) { puzzle.completionCheck(cells) }
    val completed = completionResult?.cells?.isEmpty() == true
    val elapsed = rememberGameElapsed(puzzle, repository, clock, started, completed, paused = paused)
    var completionAcknowledged by remember(puzzle) { mutableStateOf(completed) }
    var showSuccess by remember(puzzle) { mutableStateOf(false) }
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
    val checkResult = rulesResult + solutionResult + (completionResult ?: PuzzleCheckResult())

    LaunchedEffect(completed) {
        if (completed && !completionAcknowledged) showSuccess = true
        completionAcknowledged = completed
    }

    fun save() {
        repository.saveGame(SavedGame(puzzle, cells, started, history, redoStack, automaticColours))
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
    fun enter(number: Int?, inputMode: InputMode = mode) = update { next ->
        val entered = applyInput(next, selected, inputMode, number)
        next.clear(); next.addAll(entered)
    }
    fun undo() { history.lastOrNull()?.let { redoStack = listOf(cells) + redoStack; cells = it; history = history.dropLast(1); finalized = finalizedCells(cells, finalized, selected); clearManualChecks(); save() } }
    fun redo() { redoStack.firstOrNull()?.let { history = history + listOf(cells); cells = it; redoStack = redoStack.drop(1); finalized = finalizedCells(cells, finalized, selected); clearManualChecks(); save() } }

    fun setMultiple(enabled: Boolean) {
        repository.saveSettings(repository.settings.copy(multiSelect = enabled))
    }

    fun changeSelection(next: Set<CellPosition>) {
        selected = next
        finalized = finalizedCells(cells, finalized, next)
    }
    fun select(position: CellPosition) {
        if (colourSettings != null) return
        changeSelection(BoardSelectionGesture(selected, position, multiSelect).tap())
    }
    fun smartSelect(position: CellPosition, additive: Boolean) {
        if (colourSettings != null) return
        val matches = toolSensitiveSelection(cells, position, effectiveMode)
        if (matches.isNotEmpty()) changeSelection(if (additive) selected + matches else matches)
    }
    fun setMode(next: InputMode) = repository.saveSettings(repository.settings.copy(inputMode = next.name))
    fun handleShortcut(shortcut: GameShortcut) {
        when (shortcut) {
            is GameShortcut.Enter -> enter(shortcut.number, shortcut.mode ?: effectiveMode)
            is GameShortcut.MoveSelection -> selected.lastOrNull()?.let { anchor ->
                val next = CellPosition(
                    (anchor.row + shortcut.rowDelta + 9) % 9,
                    (anchor.column + shortcut.columnDelta + 9) % 9,
                )
                changeSelection(if (shortcut.extend) selected + next else setOf(next))
            }
            is GameShortcut.SelectMode -> setMode(shortcut.mode)
            is GameShortcut.CycleMode -> {
                val modes = InputMode.entries
                setMode(modes[(mode.ordinal + shortcut.delta + modes.size) % modes.size])
            }
            GameShortcut.SelectAll -> changeSelection((0 until 81).mapTo(linkedSetOf()) { CellPosition(it / 9, it % 9) })
            GameShortcut.ClearSelection -> changeSelection(emptySet())
            GameShortcut.InvertSelection -> changeSelection((0 until 81).mapTo(linkedSetOf()) { CellPosition(it / 9, it % 9) } - selected)
            GameShortcut.Undo -> undo()
            GameShortcut.Redo -> redo()
        }
    }
    val clearSelection by rememberUpdatedState { changeSelection(emptySet()) }

    val focusRequester = remember { FocusRequester() }
    val back by rememberUpdatedState(onBrowse)
    PlayerBackHandler(onBrowse)
    LaunchedEffect(puzzle) { repository.markPlayed(puzzle) }
    LaunchedEffect(puzzle, showRules, showChecking, showSuccess, colourSettings) {
        if (showRules || showChecking || showSuccess || colourSettings != null) { temporaryMode = null; altHeld = false }
        if (!showRules && !showChecking && !showSuccess && colourSettings == null) focusRequester.requestFocus()
    }
    BoxWithConstraints(Modifier.fillMaxSize().focusRequester(focusRequester).onFocusChanged {
        if (!it.hasFocus) { temporaryMode = null; altHeld = false }
    }.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || showRules || showChecking || showSuccess || colourSettings != null) return@onPreviewKeyEvent false
        altHeld = event.isAltPressed
        temporaryMode = when {
            (event.isCtrlPressed || event.isMetaPressed) && event.isShiftPressed -> InputMode.Colour
            event.isCtrlPressed || event.isMetaPressed -> InputMode.Centre
            event.isShiftPressed -> InputMode.Corner
            else -> null
        }
        val shortcut = gameShortcut(
            event.key,
            primaryModifier = event.isCtrlPressed || event.isMetaPressed,
            shift = event.isShiftPressed,
            alt = event.isAltPressed,
            letters = repository.settings.letters != event.isAltPressed,
        ) ?: return@onPreviewKeyEvent false
        handleShortcut(shortcut)
        true
    }.onKeyEvent { event ->
        if (event.type == KeyEventType.KeyUp) {
            altHeld = event.isAltPressed
            temporaryMode = when {
                (event.isCtrlPressed || event.isMetaPressed) && event.isShiftPressed -> InputMode.Colour
                event.isCtrlPressed || event.isMetaPressed -> InputMode.Centre
                event.isShiftPressed -> InputMode.Corner
                else -> null
            }
        }
        false
    }.focusable().edgeSwipeBack { back() }.background(Paper).safeDrawingPadding().pointerInput(Unit) {
        awaitEachGesture {
            // Board gestures and controls consume their presses before the final pass.
            // An unhandled press is on the background (including outside clues).
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            if (!down.isConsumed && colourSettings == null) clearSelection()
        }
    }) {
        val compact = maxWidth <= maxHeight - 52.dp
        val landscapePhone = !compact && maxHeight < 600.dp
        Column(Modifier.fillMaxSize()) {
            TopBar(puzzle, shareBaseUrl, desktop = !compact, elapsed = elapsed, canPause = started && !completed, onPause = { paused = true; showRules = true }) { clearSelection(); onBrowse() }
            if (compact) {
                PortraitPlayerLayout(
                    modifier = Modifier.fillMaxSize(),
                    board = {
                        SudokuBoard(puzzle, cells, selected, checkResult.cells, automaticColours = automaticColours, invalidPencilMarks = checkResult.pencilMarks, modifier = Modifier.fillMaxSize(), additiveSelection = multiSelect, onDragSelection = { if (colourSettings == null) changeSelection(it) }, onToolSelect = ::smartSelect, onSelect = ::select)
                    },
                ) {
                    Controls(effectiveMode, letters, { repository.saveSettings(repository.settings.copy(letters = !repository.settings.letters)) }, digitCounts, ::setMode, { enter(it, effectiveMode) }, history.isNotEmpty(), redoStack.isNotEmpty(), ::undo, ::redo, checkRules || checkSolution, ::checkNow, onColourSettings = { colourSettings = it }, automaticColours = automaticColours, multiple = multiSelect, onMultiple = { setMultiple(!multiSelect) }, onRules = { showRules = true }, onChecking = { showChecking = true }, compact = true)
                }
            } else {
                DesktopPlayerLayout(
                    modifier = Modifier.fillMaxSize(),
                    board = {
                        SudokuBoard(puzzle, cells, selected, checkResult.cells, automaticColours = automaticColours, invalidPencilMarks = checkResult.pencilMarks, modifier = Modifier.fillMaxSize(), additiveSelection = multiSelect, onDragSelection = { if (colourSettings == null) changeSelection(it) }, onToolSelect = ::smartSelect, onSelect = ::select)
                    },
                ) {
                    DesktopPlayerSidebar(
                        rules = {
                            SideCard("PUZZLE NOTES") {
                                RulesText(puzzle.rules)
                            }
                        },
                        input = {
                            val density = LocalDensity.current
                            val inputScale = if (landscapePhone) 1f else 0.6f
                            // Reduce the panel height while keeping the small mode labels readable.
                            val inputFontScale = if (landscapePhone) 1f else 0.8f
                            CompositionLocalProvider(LocalDensity provides Density(density.density * inputScale, density.fontScale * inputFontScale / inputScale)) {
                                SideCard("PLAY") { Controls(effectiveMode, letters, { repository.saveSettings(repository.settings.copy(letters = !repository.settings.letters)) }, digitCounts, ::setMode, { enter(it, effectiveMode) }, history.isNotEmpty(), redoStack.isNotEmpty(), ::undo, ::redo, checkRules || checkSolution, ::checkNow, onColourSettings = { colourSettings = it }, automaticColours = automaticColours, multiple = multiSelect, onMultiple = { setMultiple(!multiSelect) }, onRules = { showRules = true }, onChecking = { showChecking = true }, compact = landscapePhone) }
                            }
                        },
                    )
                }
            }
        }
    }
    colourSettings?.let { colour ->
        AutomaticColourDialog(colour, automaticColours[colour.number] ?: AutomaticColourRule(), { rule ->
            automaticColours = automaticColours + (colour.number to rule)
            save()
        }) { colourSettings = null }
    }
    if (showRules) RulesDialog(puzzle, starting = !started, paused = paused) {
        if (!started) { clock.start(); started = true; save() }
        paused = false
        showRules = false
    }
    if (showChecking) CheckingDialog(
        checkRules, { repository.saveSettings(settings.copy(rules = it)); manualRulesCheck = false },
        checkRulesAutomatically, { repository.saveSettings(settings.copy(automaticRules = it)); manualRulesCheck = false },
        includeSpecialRules, { repository.saveSettings(settings.copy(specialRules = it)); manualRulesCheck = false },
        checkSolution, { repository.saveSettings(settings.copy(solution = it)); manualSolutionCheck = false },
        checkSolutionAutomatically, { repository.saveSettings(settings.copy(automaticSolution = it)); manualSolutionCheck = false },
    ) { showChecking = false }
    if (showSuccess) CompletionDialog(puzzle, elapsed, { showSuccess = false }) {
        showSuccess = false
        onBrowse()
    }
}


@Composable
private fun TopBar(puzzle: Puzzle, shareBaseUrl: String, desktop: Boolean, elapsed: Long, canPause: Boolean, onPause: () -> Unit, onBrowse: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val link = remember(puzzle, shareBaseUrl) { runCatching { puzzleShareLink(puzzle, shareBaseUrl) } }
    val copyLink: (String) -> Unit = { url ->
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            val message = try {
                clipboard.setClipEntry(puzzleLinkClipEntry(url))
                "Puzzle link copied"
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { "Could not copy puzzle link. Please try again." }
            snackbar.showSnackbar(message)
        }
    }
    PopupSnackbar(snackbar)

    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = if (desktop) 12.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBrowse, Modifier.semantics { contentDescription = "Back to puzzles" }) { AppIcon(Glyph.Back) }
        if (desktop) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Ink)) { append(puzzle.title) }
                withStyle(SpanStyle(color = Color(0xFF737772))) { append("   by ${puzzle.author}") }
            }, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(puzzle.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("by ${puzzle.author}", color = Color(0xFF737772), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(formatElapsed(elapsed), color = Ink, fontSize = if (desktop) 16.sp else 13.sp, fontWeight = FontWeight.SemiBold)
        IconButton(onPause, enabled = canPause, modifier = Modifier.semantics { contentDescription = "Pause game" }) { AppIcon(Glyph.Pause) }
        IconButton({
            link.fold(
                onSuccess = { url -> sharePuzzle(url, puzzle.title) { copyLink(url) } },
                onFailure = { error -> scope.launch { snackbar.showSnackbar(error.message ?: "Could not share puzzle") } },
            )
        }, Modifier.semantics { contentDescription = "Share puzzle" }) { AppIcon(Glyph.Share) }
    }
}

@Composable
private fun PopupSnackbar(state: SnackbarHostState) {
    if (state.currentSnackbarData != null) {
        androidx.compose.ui.window.Popup(alignment = Alignment.BottomCenter) {
            SnackbarHost(state, Modifier.padding(16.dp))
        }
    }
}

/** Keep input at the bottom and let lengthy rules scroll in the space above it. */
@Composable
internal fun DesktopPlayerSidebar(
    rules: @Composable () -> Unit,
    input: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Box(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())) {
            rules()
        }
        Spacer(Modifier.height(16.dp))
        // Also keep every input action reachable in very short landscape windows.
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            input()
        }
    }
}

// In portrait the square gets the width first; input fills the space below.
@Composable
internal fun PortraitPlayerLayout(
    modifier: Modifier = Modifier,
    board: @Composable () -> Unit,
    controls: @Composable () -> Unit,
) {
    Layout(content = {
        Box { board() }
        BoxWithConstraints {
            val density = LocalDensity.current
            val scale = minOf(maxWidth / 350.dp, maxHeight / 258.dp, 1.3f).coerceAtLeast(0.01f)
            CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) { controls() }
            }
        }
    }, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val frameWidth = minOf(width, (height / 1.5f).toInt())
        val frameHeight = minOf(height, (frameWidth * 2.1f).toInt())
        val margin = 5.dp.roundToPx().coerceAtMost(frameWidth)
        val boardSize = frameWidth - margin
        val controlsWidth = minOf(boardSize, 455.dp.roundToPx())
        val remaining = (frameHeight - frameWidth).coerceAtLeast(0)
        val boardPlaceable = measurables[0].measure(Constraints.fixed(boardSize, boardSize))
        val controlsPlaceable = measurables[1].measure(Constraints.fixed(controlsWidth, remaining))
        layout(width, height) {
            val top = (height - frameHeight) / 2
            val controlsLeft = (width - controlsWidth) / 2
            boardPlaceable.placeRelative((width - boardSize) / 2, top + margin / 2)
            controlsPlaceable.placeRelative(controlsLeft, top + frameWidth)
        }
    }
}

// SudokuPad fits a landscape frame between 1.7:1 and 2.1:1, with a 5% board margin.
// Metadata lives in the top bar, leaving the full sidebar height for rules and input.
@Composable
internal fun DesktopPlayerLayout(
    modifier: Modifier = Modifier,
    board: @Composable () -> Unit,
    controls: @Composable () -> Unit,
) {
    Layout(content = {
        Box { board() }
        BoxWithConstraints {
            val density = LocalDensity.current
            val scale = minOf(maxWidth / 350.dp, maxHeight / 500.dp, 1.3f).coerceAtLeast(0.01f)
            // Scale the whole panel together: touch targets, icons, labels, spacing and notes.
            CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
                controls()
            }
        }
    }, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val slot = minOf(height, (width / 1.7f).toInt())
        val frameWidth = minOf(width, (height * 2.1f).toInt())
        val margin = (slot * 0.05f).roundToInt()
        val boardSize = (slot - margin).coerceAtLeast(0)
        val controlsWidth = (frameWidth - slot - margin).coerceIn(0, 455.dp.roundToPx())
        val boardPlaceable = measurables[0].measure(Constraints.fixed(boardSize, boardSize))
        val controlsPlaceable = measurables[1].measure(Constraints.fixed(controlsWidth, slot))
        layout(width, height) {
            val left = (width - frameWidth) / 2
            val top = (height - slot) / 2
            val sidebarLeft = left + slot + margin / 2
            boardPlaceable.placeRelative(left + margin / 2, top + margin / 2)
            controlsPlaceable.placeRelative(sidebarLeft, top)
        }
    }
}

@Composable
private fun CompletionDialog(puzzle: Puzzle, elapsed: Long, onViewPuzzle: () -> Unit, onBrowse: () -> Unit) {
    AlertDialog(
        onDismissRequest = onViewPuzzle,
        title = { Text("Puzzle complete!") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(puzzle.successMessage ?: "Congratulations — you solved the puzzle!", lineHeight = 20.sp)
            Text("Completed in ${formatElapsed(elapsed)}", color = Moss, fontWeight = FontWeight.Bold)
        } },
        confirmButton = { Button(onBrowse) { Text("Back to puzzles") } },
        dismissButton = { TextButton(onViewPuzzle) { Text("View puzzle") } },
        shape = RoundedCornerShape(22.dp),
    )
}

@Composable
internal fun SudokuBoard(puzzle: Puzzle, cells: List<CellState>, selected: Set<CellPosition>, conflicts: Set<Int>, uncertain: Set<Int> = emptySet(), invalidPencilMarks: Map<Int, Set<Int>> = emptyMap(), modifier: Modifier = Modifier, additiveSelection: Boolean = false, onDragSelection: ((Set<CellPosition>) -> Unit)? = null, onToolSelect: ((CellPosition, Boolean) -> Unit)? = null, automaticColours: Map<Int, AutomaticColourRule> = emptyMap(), interactive: Boolean = true, onSelect: (CellPosition) -> Unit) {
    val currentSelection by rememberUpdatedState(selected)
    val currentDragSelection by rememberUpdatedState(onDragSelection)
    val currentToolSelect by rememberUpdatedState(onToolSelect)
    val currentAdditive by rememberUpdatedState(additiveSelection)
    val hasOutside = (puzzle.overlays + puzzle.underlays).any { it.center.row !in 0.0..9.0 || it.center.column !in 0.0..9.0 } ||
        puzzle.arrows.any { arrow -> arrow.points.any { it.row !in 0.0..9.0 || it.column !in 0.0..9.0 } }
    val padCells = if (hasOutside) 1 else 0
    val litCells = remember(puzzle, cells) { puzzle.litCells(cells) }
    val revealedMarks = remember(puzzle, litCells) {
        (puzzle.underlays + puzzle.overlays).filter {
            it.isRevealedEdgeMark(litCells) || it.isRevealedQuadruple(puzzle.quadruples, litCells)
        }
    }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f)) {
        val cellSize = maxWidth / (9 + padCells * 2)
        val digitSize = (cellSize.value * .6f).sp
        val density = LocalDensity.current
        val inset = with(density) {
            (maxWidth * (padCells.toFloat() / (9 + padCells * 2)) + 2.dp).roundToPx().toDp()
        }
        // Reserve the extra divider width; match the pixel rounding used by layout spacers.
        val boxGap = with(density) { (1.5.dp - .55.dp).roundToPx().toDp() }
        Box(Modifier.fillMaxSize().background(if (hasOutside) Paper else Color.White)) {
            Box(Modifier.fillMaxSize().padding(inset).background(Color.White))
            Canvas(Modifier.fillMaxSize().padding(inset)) {
                val x = BoardAxis(size.width, boxGap.toPx())
                val y = BoardAxis(size.height, boxGap.toPx())
                clipRect {
                    cells.forEachIndexed { index, cell ->
                        drawCellColours(cell.displayColours(automaticColours),
                            Offset(x.start(index % 9), y.start(index / 9)),
                            Size(x.cellSize, y.cellSize))
                    }
                }
            }
            PuzzleGraphics(puzzle, inset, boxGap, measurer, hiddenOverlays = revealedMarks)
            Box(Modifier.fillMaxSize().padding(inset).then(
                if (!interactive || onDragSelection == null) Modifier else Modifier.pointerInput(puzzle) {
                    var lastTapPosition: CellPosition? = null
                    var lastTapTime = Long.MIN_VALUE
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val x = BoardAxis(size.width.toFloat(), boxGap.toPx())
                        val y = BoardAxis(size.height.toFloat(), boxGap.toPx())
                        val start = boardCellAt(x.normalized(down.position.x), y.normalized(down.position.y))
                        if (start != null) {
                            val modifiers = currentEvent.keyboardModifiers
                            val additive = currentAdditive || modifiers.isCtrlPressed || modifiers.isMetaPressed
                            val isDoubleTap = start == lastTapPosition && down.uptimeMillis - lastTapTime <= 400L
                            val gesture = BoardSelectionGesture(currentSelection, start, additive)
                            var gestureSelection = gesture.visit()
                            var previous = down.position
                            var moved = false
                            var lastEventTime = down.uptimeMillis
                            down.consume()
                            currentDragSelection?.invoke(gestureSelection)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val crossed = boardCellsAlongSegment(
                                    x.normalized(previous.x), y.normalized(previous.y),
                                    x.normalized(change.position.x), y.normalized(change.position.y),
                                    additive = additive,
                                )
                                val next = gesture.visit(crossed)
                                if (next != gestureSelection) {
                                    moved = true
                                    gestureSelection = next
                                    currentDragSelection?.invoke(next)
                                }
                                previous = change.position
                                lastEventTime = change.uptimeMillis
                                change.consume()
                            } while (change.pressed)
                            val isLongPress = !moved && lastEventTime - down.uptimeMillis >= 500L
                            if (isDoubleTap || isLongPress) {
                                currentToolSelect?.invoke(start, additive)
                                lastTapPosition = null
                                lastTapTime = Long.MIN_VALUE
                            } else if (!moved) {
                                currentDragSelection?.invoke(gesture.tap())
                                lastTapPosition = start
                                lastTapTime = lastEventTime
                            } else {
                                lastTapPosition = null
                                lastTapTime = Long.MIN_VALUE
                            }
                        }
                    }
                }
            )) {
                BoardCells(boxGap) { repeat(9) { row -> repeat(9) { column ->
            val position = CellPosition(row, column); val cell = cells[position.index]; val isSelected = position in selected
            val colours = cell.displayColours(automaticColours)
            val same = cell.value != null && selected.any { cells[it.index].value == cell.value }
            val background = when { isSelected -> Color.LightGray.copy(alpha = .3f); position.index in conflicts -> Color(0xEFFFF1EC); position.index in uncertain -> Color(0xEFFFEDBD); same -> Color(0xDDE6D9A8); else -> Color.Transparent }.let { if (colours.isNotEmpty()) it.copy(alpha = minOf(it.alpha, .18f)) else it }
            val textBackdrop = if (CellColour.DarkGrey.number in colours) Color.White.copy(alpha = .85f) else Color.Transparent
            val fogged = puzzle.fogLights != null && position !in litCells
            Box(Modifier.fillMaxSize().semantics { this.selected = isSelected; contentDescription = "Row ${row + 1}, column ${column + 1}, ${cell.value?.symbolText() ?: "empty"}${if (isSelected) ", selected" else ""}${if (cell.cornerNotes.isNotEmpty()) ", corner notes ${cell.cornerNotes.sorted().joinToString { it.symbolText() }}" else ""}${if (cell.centreNotes.isNotEmpty()) ", centre notes ${cell.centreNotes.sorted().joinToString { it.symbolText() }}" else ""}${if (colours.isNotEmpty()) ", colours ${colours.sorted().mapNotNull(CellColour::fromNumber).joinToString { it.label }}" else ""}${if (position.index in uncertain) ", uncertain reading" else ""}" }.then(if (interactive) Modifier.clickable { onSelect(position) } else Modifier), contentAlignment = Alignment.Center) {
                if (fogged) Canvas(Modifier.matchParentSize()) {
                    drawRect(Color(0xFFB4B7B5))
                    drawCellColours(colours)
                }
                Box(Modifier.matchParentSize().background(background))
                when {
                    cell.value != null && (!cell.given || !fogged) -> Text(cell.value.symbolText(), modifier = Modifier.background(textBackdrop), fontSize = digitSize, fontWeight = if (cell.given) FontWeight.Black else FontWeight.Medium, color = if (position.index in conflicts) ErrorRed else if (cell.given) Ink else Moss)
                    cell.value == null -> {
                        if (cell.cornerNotes.isNotEmpty()) CornerNotes(cell.cornerNotes, invalidPencilMarks[position.index].orEmpty(), cellSize, textBackdrop)
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
            }
                } } }
                CageOverlay(boxGap, puzzle.cages.mapNotNull { cage ->
                    val visible = cage.cells.intersect(litCells)
                    cage.copy(cells = visible).takeIf { visible.isNotEmpty() }
                })
                // Paint totals after the cage borders so the backing softens the dashes beneath them.
                BoardCells(boxGap) { repeat(9) { row -> repeat(9) { column ->
                    val position = CellPosition(row, column)
                    val cage = puzzle.cages.firstOrNull { it.total != null && position == it.cells.minByOrNull { cell -> cell.index } }
                    Box(Modifier.fillMaxSize()) {
                        if (cage != null && position in litCells) Text(
                            cage.total.toString(),
                            Modifier.align(Alignment.TopStart)
                                .padding(start = 3.dp, top = 1.dp)
                                .background(Color.White.copy(alpha = .9f), RoundedCornerShape(1.dp))
                                .padding(horizontal = 1.dp),
                            fontSize = (cellSize.value * .2f).sp,
                            lineHeight = (cellSize.value * .225f).sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                } } }
            }
            // Keep cell boundaries above the opaque fog layer.
            Canvas(Modifier.fillMaxSize().padding(inset)) {
                val thin = .55.dp.toPx()
                val thick = 1.5.dp.toPx()
                val color = Ink.copy(alpha = .65f)
                val horizontal = BoardAxis(size.width, boxGap.toPx())
                val vertical = BoardAxis(size.height, boxGap.toPx())
                for (line in 1..8) {
                    val width = if (line % 3 == 0) thick else thin
                    val x = horizontal.divider(line)
                    val y = vertical.divider(line)
                    drawLine(color, Offset(x, 0f), Offset(x, size.height), width)
                    drawLine(color, Offset(0f, y), Offset(size.width, y), width)
                }
            }
            // Edge clues and fully revealed quadruples cover the grid with their backing shapes.
            if (revealedMarks.isNotEmpty()) PuzzleGraphics(puzzle, inset, boxGap, measurer, revealedMarks)
            Canvas(Modifier.fillMaxSize().padding(inset)) {
                val width = 2.dp.toPx()
                // The complete stroke sits outside the cell area.
                drawRect(Ink, Offset(-width / 2, -width / 2),
                    Size(size.width + width, size.height + width), style = Stroke(width))
            }
        }
    }
}

/** Use the same rounded cell edges for layout, grid lines, and cage dashes. */
@Composable
private fun BoardCells(boxGap: Dp, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxSize()) { children, constraints ->
        val x = BoardAxis(constraints.maxWidth.toFloat(), boxGap.toPx())
        val y = BoardAxis(constraints.maxHeight.toFloat(), boxGap.toPx())
        val placements = children.mapIndexed { index, child ->
            val left = x.start(index % 9).roundToInt()
            val top = y.start(index / 9).roundToInt()
            val width = (x.start(index % 9) + x.cellSize).roundToInt() - left
            val height = (y.start(index / 9) + y.cellSize).roundToInt() - top
            Triple(child.measure(Constraints.fixed(width, height)), left, top)
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placements.forEach { (cell, left, top) -> cell.place(left, top) }
        }
    }
}

private fun DrawScope.drawCellColours(colours: Set<Int>, topLeft: Offset = Offset.Zero, area: Size = size) {
    val swatches = colours.sorted().mapNotNull(CellColour::fromNumber)
    swatches.forEachIndexed { stripe, colour ->
        drawRect(colour.swatch.copy(alpha = .7f),
            topLeft + Offset(stripe * area.width / swatches.size, 0f),
            Size(area.width / swatches.size, area.height))
    }
}

private fun DrawScope.drawConstraintLine(points: List<Offset>, color: Color, width: Float) {
    val path = Path()
    // Separate round-capped segments give every waypoint a circle of diameter width.
    // Stroke them together so translucent lines do not darken where segments meet.
    points.zipWithNext().forEach { (start, end) ->
        path.moveTo(start.x, start.y)
        path.lineTo(end.x, end.y)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
}

@Composable
private fun BoxScope.PuzzleGraphics(
    puzzle: Puzzle, inset: Dp, boxGap: Dp, measurer: androidx.compose.ui.text.TextMeasurer,
    revealedMarks: List<PuzzleOverlay>? = null, hiddenOverlays: List<PuzzleOverlay> = emptyList(),
) {
    Canvas(Modifier.matchParentSize()) {
        val padding = inset.toPx()
        val x = BoardAxis(size.width - 2 * padding, boxGap.toPx())
        val y = BoardAxis(size.height - 2 * padding, boxGap.toPx())
        val cell = x.cellSize
        fun at(point: GridPoint) = Offset(padding + x.at(point.column), padding + y.at(point.row))
        fun overlay(item: PuzzleOverlay) {
            val center = at(item.center)
            val scale = if (item.isKropkiDot()) .75f else 1f
            val area = Size((item.width * cell * scale).toFloat(), (item.height * cell * scale).toFloat())
            val topLeft = center - Offset(area.width / 2, area.height / 2)
            // Zero-width XV boxes size the text; stroking them paints a line behind the glyph.
            if (area.width > 0f && area.height > 0f) {
                item.backgroundColor?.toComposeColor()?.let { color ->
                    if (item.rounded) drawOval(color, topLeft, area) else drawRect(color, topLeft, area)
                }
                item.borderColor?.toComposeColor()?.let { color ->
                    if (item.rounded) drawOval(color, topLeft, area, style = Stroke(item.thickness.toFloat().coerceAtLeast(1f)))
                    else drawRect(color, topLeft, area, style = Stroke(item.thickness.toFloat().coerceAtLeast(1f)))
                }
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
            val color = line.color.toComposeColor() ?: Color.Gray
            val width = (line.thickness / 50.0 * cell).toFloat().coerceAtLeast(2f)
            drawConstraintLine(line.points.map(::at), color, width)
            if (line.rule == LineRule.Thermometer) line.points.firstOrNull()?.let { bulb ->
                drawCircle(color, radius = cell * .35f, center = at(bulb))
            }
        }
        puzzle.arrows.forEach { arrow ->
            val color = arrow.color.toComposeColor() ?: Color.Gray
            val width = (arrow.thickness / 50.0 * cell).toFloat().coerceAtLeast(1.5f)
            drawConstraintLine(arrow.points.map(::at), color, width)
            val tip = arrow.points.lastOrNull()?.let(::at); val previous = arrow.points.dropLast(1).lastOrNull()?.let(::at)
            if (tip != null && previous != null) {
                val angle = atan2((tip.y - previous.y).toDouble(), (tip.x - previous.x).toDouble())
                val length = (arrow.headLength * cell).toFloat()
                drawConstraintLine(listOf(
                    Offset(tip.x - length * cos(angle - .55).toFloat(), tip.y - length * sin(angle - .55).toFloat()),
                    tip,
                    Offset(tip.x - length * cos(angle + .55).toFloat(), tip.y - length * sin(angle + .55).toFloat()),
                ), color, width)
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
private fun BoxScope.CageOverlay(boxGap: Dp, cages: List<KillerCage>) {
    Box(Modifier.matchParentSize().drawWithCache {
        val width = 1.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
        val gap = boxGap.toPx()
        val cw = BoardAxis(size.width, gap).cellSize
        val ch = BoardAxis(size.height, gap).cellSize
        val outlines = Path().apply {
            cages.forEach { cage ->
                addPath(cageOutline(cage.cells, cw, ch, cageInset(cw), insetY = cageInset(ch), strokeWidth = width, boxGap = gap))
            }
        }
        val gaps = cageGapMask(cw, ch, strokeWidth = width, boxGap = gap)
        val paint = cageOutlinePaint(width)
        onDrawBehind {
            clipPath(gaps, ClipOp.Difference) {
                drawIntoCanvas { it.drawPath(outlines, paint) }
            }
        }
    })
}

@Composable
private fun CornerNotes(notes: Set<Int>, invalid: Set<Int>, cellSize: Dp, backdrop: Color = Color.Transparent) {
    val sortedNotes = notes.sorted()
    val corners = listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)
    // Stay inside the cage outlines while leaving room for the centre marks.
    Box(Modifier.fillMaxSize().padding(horizontal = cellSize * .15f, vertical = cellSize * .1f)) {
        corners.forEachIndexed { corner, alignment ->
            // Fill the corners first, then pack additional marks beside them.
            val cornerNotes = sortedNotes.filterIndexed { index, _ -> index % corners.size == corner }
            if (cornerNotes.isNotEmpty()) {
                Text(buildAnnotatedString {
                    cornerNotes.forEach { number ->
                        withStyle(SpanStyle(color = if (number in invalid) ErrorRed else Moss)) { append(number.symbolText()) }
                    }
                }, modifier = Modifier.align(alignment).background(backdrop), fontSize = (cellSize.value * .25f).sp, lineHeight = (cellSize.value * .25f).sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CentreNotes(notes: Set<Int>, invalid: Set<Int>, backdrop: Color = Color.Transparent) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val noteSize = minOf(maxWidth.value * .275f, maxWidth.value / notes.size.coerceAtLeast(1)).sp
        Text(buildAnnotatedString {
            notes.sorted().forEach { number ->
                withStyle(SpanStyle(color = if (number in invalid) ErrorRed else Moss)) { append(number.symbolText()) }
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
private fun Controls(mode: InputMode, letters: Boolean, onLetters: () -> Unit, digitCounts: Map<Int, Int>, onMode: (InputMode) -> Unit, onNumber: (Int?) -> Unit, canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, canCheck: Boolean, onCheck: () -> Unit, onColourSettings: (CellColour) -> Unit, automaticColours: Map<Int, AutomaticColourRule>, multiple: Boolean, onMultiple: () -> Unit, onRules: () -> Unit, onChecking: () -> Unit, compact: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (compact) Column(Modifier.width(48.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactAction("Undo", Glyph.Undo, onUndo, canUndo)
            CompactAction("Redo", Glyph.Redo, onRedo, canRedo)
            CompactAction("Check", Glyph.Check, onCheck, canCheck)
            FilledTonalIconToggleButton(multiple, { onMultiple() }, Modifier.size(48.dp).semantics { contentDescription = "Select multiple" }, shape = RoundedCornerShape(12.dp)) { AppIcon(Glyph.Multiple) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.heightIn(max = if (compact) 156.dp else 170.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (if (letters && mode != InputMode.Colour) 10..35 else 1..9).chunked(3).forEach { numbers ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        numbers.forEach { number ->
                            val colour = if (mode == InputMode.Colour) CellColour.fromNumber(number) else null
                            val buttonModifier = Modifier.weight(1f).height(if (compact) 48.dp else 52.dp)
                            if (colour != null) Surface(
                                modifier = buttonModifier.semantics { contentDescription = "${colour.label}, colour $number" }
                                    .combinedClickable(onClick = { onNumber(number) }, onLongClickLabel = "Automatic colouring settings", onLongClick = { onColourSettings(colour) }),
                                shape = RoundedCornerShape(10.dp), color = Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD3D6CD)),
                            ) {
                                Box(Modifier.fillMaxSize().padding(7.dp).clip(RoundedCornerShape(5.dp)).background(colour.swatch), contentAlignment = Alignment.Center) {
                                    automaticColours[number]?.takeIf { it.enabled }?.let { rule ->
                                        Text(rule.numbers.sorted().joinToString(""), color = if (colour == CellColour.DarkGrey) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = if (rule.numbers.size > 5) 10.sp else 12.sp)
                                    }
                                }
                            } else OutlinedButton({ onNumber(number) }, buttonModifier, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(0.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD3D6CD)), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (compact) Color.White else Color.Transparent)) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(number.symbolText(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                    val count = digitCounts[number] ?: 0
                                    if (count > 0) Text(
                                        count.toString(),
                                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 6.dp)
                                            .semantics { contentDescription = "$count placed" },
                                        fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, color = Moss,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (compact) {
                    FilledTonalButton(onLetters, Modifier.weight(1f).height(48.dp).semantics { contentDescription = "Switch numbers and letters" }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(0.dp)) { Text(if (letters) "A–Z" else "1–9") }
                    FilledTonalIconButton({ onNumber(null) }, Modifier.weight(1f).height(48.dp).semantics { contentDescription = if (mode == InputMode.Colour) "Erase colours" else "Erase" }, shape = RoundedCornerShape(12.dp)) { AppIcon(Glyph.Erase) }
                } else {
                    FilledTonalIconToggleButton(multiple, { onMultiple() }, Modifier.weight(1f).height(52.dp).semantics { contentDescription = "Select multiple" }, shape = RoundedCornerShape(10.dp)) { AppIcon(Glyph.Multiple) }
                    IconButton(onUndo, Modifier.weight(1f).height(52.dp).semantics { contentDescription = "Undo" }, enabled = canUndo) { AppIcon(Glyph.Undo) }
                    IconButton({ onNumber(null) }, Modifier.weight(1f).height(52.dp).semantics { contentDescription = if (mode == InputMode.Colour) "Erase colours" else "Erase" }) { AppIcon(Glyph.Erase) }
                }
            }
        }
        Column(Modifier.width(if (compact) 60.dp else 72.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!compact) FilledTonalButton(onLetters, Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) { Text(if (letters) "A–Z" else "1–9") }
            InputMode.entries.forEach { item -> val active = item == mode
                Surface(Modifier.fillMaxWidth().height(if (compact) 48.dp else 52.dp).semantics { selected = active }.clickable { onMode(item) }, shape = RoundedCornerShape(10.dp), color = if (active) Moss else Color(0xFFF0F1EA)) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        if (item == InputMode.Colour) Canvas(Modifier.size(24.dp, 18.dp)) {
                            listOf(CellColour.Red, CellColour.Yellow, CellColour.Blue).forEachIndexed { index, colour ->
                                drawRect(colour.swatch, Offset(index * size.width / 3, 2.dp.toPx()), Size(size.width / 3, size.height - 4.dp.toPx()))
                            }
                        } else Text(item.mark, color = if (active) Color.White else Ink, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                        Text(item.label, color = if (active) Color.White else Color.Gray, fontSize = 10.sp, lineHeight = 12.sp)
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onRules, Modifier.weight(1f)) { AppIcon(Glyph.Rules); Spacer(Modifier.width(6.dp)); Text("Rules") }
        if (!compact) TextButton(onCheck, Modifier.weight(1f), enabled = canCheck) { AppIcon(Glyph.Check); Spacer(Modifier.width(6.dp)); Text("Check") }
        if (!compact) IconButton(onRedo, Modifier.semantics { contentDescription = "Redo" }, enabled = canRedo) { AppIcon(Glyph.Redo) }
        if (compact) TextButton(onChecking, Modifier.weight(1f)) { AppIcon(Glyph.Settings); Spacer(Modifier.width(6.dp)); Text("Settings") }
        else IconButton(onChecking, Modifier.semantics { contentDescription = "Checking settings" }) { AppIcon(Glyph.Settings) }
    }
}

@Composable
private fun CompactAction(label: String, glyph: Glyph, onClick: () -> Unit, enabled: Boolean = true) {
    FilledTonalIconButton(onClick, Modifier.size(48.dp).semantics { contentDescription = label }, enabled = enabled, shape = RoundedCornerShape(12.dp)) {
        AppIcon(glyph)
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
            CheckOption("Automatic colouring", "Update matching cells instantly in this game.", rule.enabled) { onRule(rule.copy(enabled = it)) }
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
private fun RulesDialog(puzzle: Puzzle, starting: Boolean, paused: Boolean = false, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = { if (!starting) onDismiss() },
        confirmButton = { Button(onDismiss) { Text(if (starting) "Okay, start puzzle" else if (paused) "Resume puzzle" else "Okay") } },
        containerColor = Color.White,
        title = { Column { Text(puzzle.title, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("by ${puzzle.author}", style = MaterialTheme.typography.bodyMedium, color = Moss) } },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            Text("RULES", color = Moss, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            RulesText(puzzle.rules)
            if (puzzle.videoUrl != null || puzzle.sourceUrl != null) HorizontalDivider(Modifier.padding(vertical = 8.dp))
            puzzle.videoUrl?.let { url -> TextButton({ uriHandler.openUri(url) }) { Text("Watch source video") } }
            puzzle.sourceUrl?.let { url -> TextButton({ uriHandler.openUri(url) }) { Text("Open original SudokuPad") } }
            if (paused) Text("Game paused. Your timer resumes when you close the rules.", color = Color.Gray, fontSize = 12.sp)
            if (starting) Text("Your timer starts when you tap Okay.", color = Color.Gray, fontSize = 12.sp)
        } }, shape = RoundedCornerShape(22.dp),
    )
}
