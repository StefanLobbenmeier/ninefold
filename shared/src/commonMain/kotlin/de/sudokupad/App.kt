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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

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
    var showErrors by remember { mutableStateOf(true) }
    var history by remember { mutableStateOf<List<List<CellState>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<CellState>>>(emptyList()) }
    val conflicts = remember(puzzle, cells, showErrors) { if (showErrors) conflictsFor(puzzle, cells) else emptySet() }

    fun update(transform: (MutableList<CellState>) -> Unit) {
        history = history + listOf(cells); redoStack = emptyList(); cells = cells.toMutableList().also(transform)
    }
    fun enter(number: Int?) = update { next -> selected.forEach { position ->
        val old = next[position.index]
        if (!old.given) next[position.index] = when (mode) {
            InputMode.Digit -> old.copy(value = number, cornerNotes = emptySet(), centreNotes = emptySet())
            InputMode.Corner -> old.copy(cornerNotes = number?.let { old.cornerNotes.toggle(it) } ?: emptySet(), value = null)
            InputMode.Centre -> old.copy(centreNotes = number?.let { old.centreNotes.toggle(it) } ?: emptySet(), value = null)
        }
    } }
    fun undo() { history.lastOrNull()?.let { redoStack = listOf(cells) + redoStack; cells = it; history = history.dropLast(1) } }
    fun redo() { redoStack.firstOrNull()?.let { history = history + listOf(cells); cells = it; redoStack = redoStack.drop(1) } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Paper)) {
        val compact = maxWidth < 850.dp
        Column(Modifier.fillMaxSize()) {
            TopBar({ showImport = true }, { showRules = true })
            if (compact) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PuzzleHeading(puzzle, conflicts.size); Spacer(Modifier.height(12.dp))
                    SudokuBoard(puzzle, cells, selected, conflicts) { selected = setOf(it) }
                    Spacer(Modifier.height(16.dp)); Controls(mode, { mode = it }, ::enter, history.isNotEmpty(), redoStack.isNotEmpty(), ::undo, ::redo); Spacer(Modifier.height(24.dp))
                }
            } else {
                Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        PuzzleHeading(puzzle, conflicts.size); Spacer(Modifier.height(18.dp)); SudokuBoard(puzzle, cells, selected, conflicts) { selected = setOf(it) }
                    }
                    Column(Modifier.widthIn(min = 270.dp, max = 350.dp)) {
                        SideCard("PLAY") { Controls(mode, { mode = it }, ::enter, history.isNotEmpty(), redoStack.isNotEmpty(), ::undo, ::redo) }
                        Spacer(Modifier.height(16.dp))
                        SideCard("PUZZLE NOTES") {
                            puzzle.rules.forEachIndexed { index, rule -> Row(Modifier.padding(vertical = 6.dp)) {
                                Text("${index + 1}", color = Moss, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp)); Text(rule, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                            } }
                            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0xFFE5E5DE))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(showErrors, { showErrors = it }, colors = SwitchDefaults.colors(checkedTrackColor = Moss)); Spacer(Modifier.width(8.dp))
                                Column { Text("Live mistake check", fontWeight = FontWeight.SemiBold); Text("Solution + puzzle rules", fontSize = 12.sp, color = Color.Gray) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showImport) ImportDialog({ showImport = false }) { grid ->
        puzzle = Puzzle("Imported classic", "Imported puzzle", grid); cells = puzzle.startingCells(); mode = InputMode.Digit; selected = setOf(CellPosition(0, 0)); history = emptyList(); redoStack = emptyList(); showImport = false
    }
    if (showRules) RulesDialog(puzzle) { showRules = false }
}

private fun Set<Int>.toggle(value: Int) = if (value in this) this - value else this + value

@Composable
private fun TopBar(onImport: () -> Unit, onRules: () -> Unit) {
    Surface(shadowElevation = 1.dp, color = Color.White) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Moss), contentAlignment = Alignment.Center) { Text("9", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp) }
            Spacer(Modifier.width(10.dp)); Text("NINEFOLD", fontWeight = FontWeight.Black, letterSpacing = 1.2.sp); Spacer(Modifier.weight(1f))
            TextButton(onRules) { Text("Rules") }; Spacer(Modifier.width(4.dp)); Button(onImport, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) { Text("＋  Import") }
        }
    }
}

@Composable
private fun PuzzleHeading(puzzle: Puzzle, errorCount: Int) {
    Row(Modifier.widthIn(max = 660.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(if (puzzle.cages.isEmpty()) "CLASSIC" else "KILLER · CLASSIC", color = Moss, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(puzzle.title, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("by ${puzzle.author}", color = Color(0xFF737772), fontSize = 13.sp)
        }
        Surface(shape = RoundedCornerShape(50), color = if (errorCount > 0) Color(0xFFFFE7E3) else Sage) {
            Text(if (errorCount > 0) "$errorCount conflict${if (errorCount == 1) "" else "s"}" else "Looking good", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = if (errorCount > 0) ErrorRed else Moss, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun SudokuBoard(puzzle: Puzzle, cells: List<CellState>, selected: Set<CellPosition>, conflicts: Set<Int>, uncertain: Set<Int> = emptySet(), onSelect: (CellPosition) -> Unit) {
    Box(Modifier.widthIn(max = 600.dp).fillMaxWidth().aspectRatio(1f).background(Color.White).border(2.dp, Ink)) {
        Column(Modifier.fillMaxSize()) { repeat(9) { row -> Row(Modifier.weight(1f)) { repeat(9) { column ->
            val position = CellPosition(row, column); val cell = cells[position.index]; val isSelected = position in selected
            val peer = selected.any { it.row == row || it.column == column || (it.row / 3 == row / 3 && it.column / 3 == column / 3) }
            val same = cell.value != null && selected.any { cells[it.index].value == cell.value }
            val background = when { position.index in conflicts -> Color(0xFFFFE1DC); isSelected -> Butter; position.index in uncertain -> Color(0xFFFFEDBD); same -> Color(0xFFE6D9A8); peer -> Color(0xFFF1F3EC); else -> Color.Transparent }
            Box(Modifier.weight(1f).fillMaxHeight().background(background).semantics { contentDescription = "Row ${row + 1}, column ${column + 1}, ${cell.value ?: "empty"}${if (position.index in uncertain) ", uncertain reading" else ""}" }.clickable { onSelect(position) }.drawBehind {
                val thin = .55.dp.toPx(); val thick = 1.5.dp.toPx()
                drawLine(Ink.copy(alpha = .65f), Offset(size.width, 0f), Offset(size.width, size.height), if ((column + 1) % 3 == 0) thick else thin)
                drawLine(Ink.copy(alpha = .65f), Offset(0f, size.height), Offset(size.width, size.height), if ((row + 1) % 3 == 0) thick else thin)
            }, contentAlignment = Alignment.Center) {
                when {
                    cell.value != null -> Text(cell.value.toString(), fontSize = 24.sp, fontWeight = if (cell.given) FontWeight.Black else FontWeight.Medium, color = if (position.index in conflicts) ErrorRed else if (cell.given) Ink else Moss)
                    cell.cornerNotes.isNotEmpty() -> CornerNotes(cell.cornerNotes)
                    cell.centreNotes.isNotEmpty() -> Text(cell.centreNotes.sorted().joinToString(""), fontSize = 10.sp, color = Moss, textAlign = TextAlign.Center)
                }
                puzzle.cages.firstOrNull { position in it.cells }?.let { cage -> if (position == cage.cells.minBy { it.index }) Text(cage.total.toString(), Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 1.dp), fontSize = 8.sp, color = Color(0xFF765A7C), fontWeight = FontWeight.Bold) }
            }
        } } } }
        CageOverlay(puzzle.cages)
    }
}

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
private fun CornerNotes(notes: Set<Int>) {
    Column(Modifier.fillMaxSize().padding(3.dp)) { repeat(3) { row -> Row(Modifier.weight(1f)) { repeat(3) { col ->
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { val number = row * 3 + col + 1; if (number in notes) Text(number.toString(), fontSize = 8.sp, color = Moss) }
    } } } }
}

@Composable
private fun SideCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E2DA))) {
        Column(Modifier.padding(18.dp)) { Text(label, color = Color(0xFF83877F), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp); Spacer(Modifier.height(12.dp)); content() }
    }
}

@Composable
private fun Controls(mode: InputMode, onMode: (InputMode) -> Unit, onNumber: (Int?) -> Unit, canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit) {
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
}

@Composable
private fun RulesDialog(puzzle: Puzzle, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onDismiss) { Text("Got it") } }, title = { Text(puzzle.title) }, text = { Column {
        Text("RULES", color = Moss, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp); Spacer(Modifier.height(8.dp)); puzzle.rules.forEach { Text("•  $it", Modifier.padding(vertical = 5.dp), lineHeight = 20.sp) }; Spacer(Modifier.height(8.dp)); Text(if (puzzle.solution != null) "This puzzle includes a solution, so incorrect digits can be highlighted immediately." else "No solution was included. Rule conflicts can still be detected.", color = Color.Gray, fontSize = 12.sp)
    } }, shape = RoundedCornerShape(22.dp))
}
