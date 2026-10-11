package de.sudokupad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

private enum class InputMode(val label: String, val mark: String) {
    Digit("Digit", "5"), Corner("Corner", "1·2"), Centre("Centre", "12")
}

@Composable
@androidx.compose.ui.tooling.preview.Preview
fun App() {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Moss, onPrimary = Color.White, primaryContainer = Sage, background = Paper, surface = Color.White, onSurface = Ink, error = ErrorRed),
        typography = Typography(),
    ) { SudokuApp() }
}

@Composable
private fun SudokuApp() {
    var puzzle by remember { mutableStateOf(DemoPuzzle) }
    var cells by remember(puzzle) { mutableStateOf(puzzle.startingCells()) }
    var selected by remember { mutableStateOf(setOf(CellPosition(0, 2))) }
    var mode by remember { mutableStateOf(InputMode.Digit) }
    var showImport by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var showChecking by remember { mutableStateOf(false) }
    var checkRules by remember { mutableStateOf(true) }
    var checkRulesAutomatically by remember { mutableStateOf(true) }
    var includeSpecialRules by remember { mutableStateOf(true) }
    var checkSolution by remember { mutableStateOf(true) }
    var checkSolutionAutomatically by remember { mutableStateOf(true) }
    var manualRulesCheck by remember { mutableStateOf(false) }
    var manualSolutionCheck by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<List<CellState>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<CellState>>>(emptyList()) }
    val rulesResult = remember(puzzle, cells, checkRules, checkRulesAutomatically, includeSpecialRules, manualRulesCheck) {
        if (checkRules && (checkRulesAutomatically || manualRulesCheck)) checkSudokuRules(puzzle, cells, includeSpecialRules) else PuzzleCheckResult()
    }
    val solutionResult = remember(puzzle, cells, checkSolution, checkSolutionAutomatically, manualSolutionCheck) {
        if (checkSolution && (checkSolutionAutomatically || manualSolutionCheck)) checkAgainstSolution(puzzle, cells) else PuzzleCheckResult()
    }
    val checkResult = rulesResult + solutionResult
    val hasActiveCheck = (checkRules && (checkRulesAutomatically || manualRulesCheck)) ||
        (checkSolution && (checkSolutionAutomatically || manualSolutionCheck))

    fun clearManualChecks() { manualRulesCheck = false; manualSolutionCheck = false }
    fun checkNow() { manualRulesCheck = checkRules; manualSolutionCheck = checkSolution }

    fun update(transform: (MutableList<CellState>) -> Unit) {
        history = history + listOf(cells); redoStack = emptyList(); cells = cells.toMutableList().also(transform); clearManualChecks()
    }
    fun enter(number: Int?) = update { next -> selected.forEach { position ->
        val old = next[position.index]
        if (!old.given) next[position.index] = when (mode) {
            InputMode.Digit -> old.copy(value = number, cornerNotes = emptySet(), centreNotes = emptySet())
            InputMode.Corner -> old.copy(cornerNotes = number?.let { old.cornerNotes.toggle(it) } ?: emptySet(), value = null)
            InputMode.Centre -> old.copy(centreNotes = number?.let { old.centreNotes.toggle(it) } ?: emptySet(), value = null)
        }
    } }
    fun undo() { history.lastOrNull()?.let { redoStack = listOf(cells) + redoStack; cells = it; history = history.dropLast(1); clearManualChecks() } }
    fun redo() { redoStack.firstOrNull()?.let { history = history + listOf(cells); cells = it; redoStack = redoStack.drop(1); clearManualChecks() } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Paper)) {
        val compact = maxWidth < 850.dp
        Column(Modifier.fillMaxSize()) {
            TopBar({ showImport = true }, { showRules = true }, { showChecking = true })
            if (compact) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PuzzleHeading(puzzle, checkResult.issueCount, hasActiveCheck); Spacer(Modifier.height(12.dp))
                    SudokuBoard(puzzle, cells, selected, checkResult.cells, invalidPencilMarks = checkResult.pencilMarks) { selected = setOf(it) }
                    Spacer(Modifier.height(16.dp)); Controls(mode, { mode = it }, ::enter, history.isNotEmpty(), redoStack.isNotEmpty(), ::undo, ::redo, checkRules || checkSolution, ::checkNow); Spacer(Modifier.height(24.dp))
                }
            } else {
                Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        PuzzleHeading(puzzle, checkResult.issueCount, hasActiveCheck); Spacer(Modifier.height(18.dp)); SudokuBoard(puzzle, cells, selected, checkResult.cells, invalidPencilMarks = checkResult.pencilMarks) { selected = setOf(it) }
                    }
                    Column(Modifier.widthIn(min = 270.dp, max = 350.dp)) {
                        SideCard("PLAY") { Controls(mode, { mode = it }, ::enter, history.isNotEmpty(), redoStack.isNotEmpty(), ::undo, ::redo, checkRules || checkSolution, ::checkNow) }
                        Spacer(Modifier.height(16.dp))
                        SideCard("PUZZLE NOTES") {
                            puzzle.rules.forEachIndexed { index, rule -> Row(Modifier.padding(vertical = 6.dp)) {
                                Text("${index + 1}", color = Moss, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp)); Text(rule, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                            } }
                            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0xFFE5E5DE))
                            TextButton({ showChecking = true }, Modifier.fillMaxWidth()) { Text("Checking settings") }
                        }
                    }
                }
            }
        }
    }
    if (showImport) ImportDialog({ showImport = false }) { imported ->
        puzzle = imported; cells = imported.startingCells(); mode = InputMode.Digit; selected = setOf(CellPosition(0, 0)); history = emptyList(); redoStack = emptyList(); clearManualChecks(); showImport = false
    }
    if (showRules) RulesDialog(puzzle) { showRules = false }
    if (showChecking) CheckingDialog(
        checkRules, { checkRules = it; manualRulesCheck = false },
        checkRulesAutomatically, { checkRulesAutomatically = it; manualRulesCheck = false },
        includeSpecialRules, { includeSpecialRules = it; manualRulesCheck = false },
        checkSolution, { checkSolution = it; manualSolutionCheck = false },
        checkSolutionAutomatically, { checkSolutionAutomatically = it; manualSolutionCheck = false },
    ) { showChecking = false }
}

private fun Set<Int>.toggle(value: Int) = if (value in this) this - value else this + value

@Composable
private fun TopBar(onImport: () -> Unit, onRules: () -> Unit, onChecking: () -> Unit) {
    Surface(shadowElevation = 1.dp, color = Color.White) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 480.dp
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Moss), contentAlignment = Alignment.Center) { Text("9", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp) }
                if (!narrow) { Spacer(Modifier.width(10.dp)); Text("NINEFOLD", fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) }
                Spacer(Modifier.weight(1f))
                TextButton(onRules) { Text("Rules") }; TextButton(onChecking) { Text("Checks") }; Spacer(Modifier.width(4.dp)); Button(onImport, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = if (narrow) 10.dp else 16.dp, vertical = 10.dp)) { Text(if (narrow) "＋" else "＋  Import") }
            }
        }
    }
}

@Composable
private fun PuzzleHeading(puzzle: Puzzle, errorCount: Int, checked: Boolean) {
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
            Text(puzzle.title, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("by ${puzzle.author}", color = Color(0xFF737772), fontSize = 13.sp)
        }
        Surface(shape = RoundedCornerShape(50), color = if (!checked) Butter else if (errorCount > 0) Color(0xFFFFE7E3) else Sage) {
            Text(when {
                !checked -> "Not checked"
                errorCount > 0 -> "$errorCount issue${if (errorCount == 1) "" else "s"}"
                else -> "Looking good"
            }, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = if (checked && errorCount > 0) ErrorRed else Moss, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun SudokuBoard(puzzle: Puzzle, cells: List<CellState>, selected: Set<CellPosition>, conflicts: Set<Int>, uncertain: Set<Int> = emptySet(), invalidPencilMarks: Map<Int, Set<Int>> = emptyMap(), onSelect: (CellPosition) -> Unit) {
    val hasOutside = (puzzle.overlays + puzzle.underlays).any { it.center.row !in 0.0..9.0 || it.center.column !in 0.0..9.0 } ||
        puzzle.arrows.any { arrow -> arrow.points.any { it.row !in 0.0..9.0 || it.column !in 0.0..9.0 } }
    val padCells = if (hasOutside) 1 else 0
    val litCells = remember(puzzle, cells) { puzzle.litCells(cells) }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.widthIn(max = 600.dp).fillMaxWidth().aspectRatio(1f)) {
        val inset = maxWidth * (padCells.toFloat() / (9 + padCells * 2))
        Box(Modifier.fillMaxSize().background(if (hasOutside) Paper else Color.White)) {
            Box(Modifier.fillMaxSize().padding(inset).background(Color.White).border(2.dp, Ink))
            PuzzleGraphics(puzzle, padCells, measurer)
            Box(Modifier.fillMaxSize().padding(inset)) {
                Column(Modifier.fillMaxSize()) { repeat(9) { row -> Row(Modifier.weight(1f)) { repeat(9) { column ->
            val position = CellPosition(row, column); val cell = cells[position.index]; val isSelected = position in selected
            val peer = selected.any { it.row == row || it.column == column || (it.row / 3 == row / 3 && it.column / 3 == column / 3) }
            val same = cell.value != null && selected.any { cells[it.index].value == cell.value }
            val background = when { position.index in conflicts -> Color(0xEFFFF1EC); isSelected -> Butter.copy(alpha = .82f); position.index in uncertain -> Color(0xEFFFEDBD); same -> Color(0xDDE6D9A8); peer -> Color(0xCCF1F3EC); else -> Color.Transparent }
            val fogged = puzzle.fogLights != null && position !in litCells
            Box(Modifier.weight(1f).fillMaxHeight().background(background).semantics { contentDescription = "Row ${row + 1}, column ${column + 1}, ${cell.value ?: "empty"}${if (position.index in uncertain) ", uncertain reading" else ""}" }.clickable { onSelect(position) }.drawBehind {
                val thin = .55.dp.toPx(); val thick = 1.5.dp.toPx()
                drawLine(Ink.copy(alpha = .65f), Offset(size.width, 0f), Offset(size.width, size.height), if ((column + 1) % 3 == 0) thick else thin)
                drawLine(Ink.copy(alpha = .65f), Offset(0f, size.height), Offset(size.width, size.height), if ((row + 1) % 3 == 0) thick else thin)
            }, contentAlignment = Alignment.Center) {
                if (fogged) Box(Modifier.matchParentSize().background(Color(0xFFB4B7B5)))
                when {
                    cell.value != null && (!cell.given || !fogged) -> Text(cell.value.toString(), fontSize = 24.sp, fontWeight = if (cell.given) FontWeight.Black else FontWeight.Medium, color = if (position.index in conflicts) ErrorRed else if (cell.given) Ink else Moss)
                    cell.cornerNotes.isNotEmpty() -> CornerNotes(cell.cornerNotes, invalidPencilMarks[position.index].orEmpty())
                    cell.centreNotes.isNotEmpty() -> CentreNotes(cell.centreNotes, invalidPencilMarks[position.index].orEmpty())
                }
                if (!fogged) puzzle.cages.firstOrNull { position in it.cells }?.let { cage -> if (position == cage.cells.minBy { it.index }) Text(cage.total.toString(), Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 1.dp), fontSize = 8.sp, color = Color(0xFF765A7C), fontWeight = FontWeight.Bold) }
            }
                } } } }
                CageOverlay(puzzle.cages.mapNotNull { cage ->
                    val visible = cage.cells.intersect(litCells)
                    cage.copy(cells = visible).takeIf { visible.isNotEmpty() }
                })
            }
        }
    }
}

private fun Puzzle.litCells(cells: List<CellState>): Set<CellPosition> {
    val initial = fogLights ?: return buildSet { repeat(9) { row -> repeat(9) { column -> add(CellPosition(row, column)) } } }
    return buildSet {
        addAll(initial)
        cells.forEachIndexed { index, cell ->
            val position = CellPosition(index / 9, index % 9)
            if (cell.given) add(position)
            else if (cell.value != null && (solution?.getOrNull(index)?.digitToIntOrNull() == cell.value || solution == null)) {
                for (row in position.row - 1..position.row + 1) for (column in position.column - 1..position.column + 1) {
                    if (row in 0..8 && column in 0..8) add(CellPosition(row, column))
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PuzzleGraphics(puzzle: Puzzle, padCells: Int, measurer: androidx.compose.ui.text.TextMeasurer) {
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
        puzzle.underlays.forEach(::overlay)
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
        puzzle.overlays.forEach(::overlay)
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
private fun CornerNotes(notes: Set<Int>, invalid: Set<Int>) {
    Column(Modifier.fillMaxSize().padding(3.dp)) { repeat(3) { row -> Row(Modifier.weight(1f)) { repeat(3) { col ->
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { val number = row * 3 + col + 1; if (number in notes) Text(number.toString(), fontSize = 8.sp, color = if (number in invalid) ErrorRed else Moss) }
    } } } }
}

@Composable
private fun CentreNotes(notes: Set<Int>, invalid: Set<Int>) {
    Text(buildAnnotatedString {
        notes.sorted().forEach { number ->
            withStyle(SpanStyle(color = if (number in invalid) ErrorRed else Moss)) { append(number.toString()) }
        }
    }, fontSize = 10.sp, textAlign = TextAlign.Center)
}

@Composable
private fun SideCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E2DA))) {
        Column(Modifier.padding(18.dp)) { Text(label, color = Color(0xFF83877F), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp); Spacer(Modifier.height(12.dp)); content() }
    }
}

@Composable
private fun Controls(mode: InputMode, onMode: (InputMode) -> Unit, onNumber: (Int?) -> Unit, canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, canCheck: Boolean, onCheck: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { InputMode.entries.forEach { item -> val active = item == mode
        Surface(Modifier.weight(1f).clickable { onMode(item) }, shape = RoundedCornerShape(10.dp), color = if (active) Moss else Color(0xFFF0F1EA)) {
            Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(item.mark, color = if (active) Color.White else Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text(item.label, color = if (active) Color.White else Color.Gray, fontSize = 10.sp) }
        }
    } }
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { col ->
        val number = row * 3 + col + 1
        OutlinedButton({ onNumber(number) }, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(0.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD3D6CD))) { Text(number.toString(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
    } } } }
    Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onUndo, Modifier.weight(1f), enabled = canUndo) { Text("↶  Undo") }; TextButton({ onNumber(null) }, Modifier.weight(1f)) { Text("⌫  Erase") }; TextButton(onRedo, Modifier.weight(1f), enabled = canRedo) { Text("Redo  ↷") }
    }
    OutlinedButton(onCheck, Modifier.fillMaxWidth(), enabled = canCheck) { Text("Check sudoku") }
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
            Text("Checks that are not automatic run when you press Check sudoku.", color = Color.Gray, fontSize = 12.sp, lineHeight = 17.sp)
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
private fun RulesDialog(puzzle: Puzzle, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onDismiss) { Text("Got it") } }, title = { Text(puzzle.title) }, text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        Text("RULES", color = Moss, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp); Spacer(Modifier.height(8.dp)); puzzle.rules.forEach { Text("•  $it", Modifier.padding(vertical = 5.dp), lineHeight = 20.sp) }; Spacer(Modifier.height(8.dp)); Text(if (puzzle.solution != null) "This puzzle includes a solution, so incorrect digits can be highlighted immediately." else "No solution was included. Rule conflicts can still be detected.", color = Color.Gray, fontSize = 12.sp)
    } }, shape = RoundedCornerShape(22.dp))
}
