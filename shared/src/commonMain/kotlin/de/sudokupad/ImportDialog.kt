package de.sudokupad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    var templates by remember { mutableStateOf<List<DigitTemplate>?>(null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var bounds by remember { mutableStateOf<GridBounds?>(null) }
    var gray by remember { mutableStateOf<GrayImage?>(null) }
    var grid by remember { mutableStateOf<String?>(null) }
    var uncertain by remember { mutableStateOf(emptySet<Int>()) }
    var selected by remember { mutableStateOf(CellPosition(0, 0)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var tab by remember { mutableStateOf(0) }
    var code by remember { mutableStateOf("") }
    var adjusting by remember { mutableStateOf(false) }
    var firstCorner by remember { mutableStateOf<Offset?>(null) }
    var validation by remember { mutableStateOf<ImportValidation?>(null) }
    var checking by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    val parsed = remember(code) { parseClassicGrid(code) }

    LaunchedEffect(grid) {
        validation = null; confirmed = false
        val current = grid ?: return@LaunchedEffect
        checking = true
        try { validation = withTimeoutOrNull(5_000) { validateClassicImport(current) } }
        finally { checking = false }
    }

    suspend fun recognize(area: GridBounds) {
        bounds = area
        val examples = templates ?: createDigitTemplates(measurer).also { templates = it }
        val result = recognizeGrid(requireNotNull(gray), area, examples)
        grid = result.grid; uncertain = result.uncertain; confirmed = false
        message = if (result.grid.all { it == '.' }) "No digits were recognized. Check the grid area or enter the clues manually." else null
    }

    fun runRecognition(area: GridBounds) {
        job?.cancel()
        job = scope.launch {
            busy = true; message = null
            try { recognize(area) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "Could not read this grid. Adjust the grid area or enter the clues manually." }
            finally { busy = false }
        }
    }

    // Remember outside the Dialog's transient composition so native pickers survive presentation.
    val picker = rememberFilePickerLauncher(type = FileKitType.Image, onError = {
        busy = false; message = "Could not open the image picker. Please try again."
    }) { file ->
        if (file != null) {
            job?.cancel()
            job = scope.launch {
                busy = true; message = null; grid = null; image = null; gray = null; bounds = null
                uncertain = emptySet(); confirmed = false; adjusting = false; firstCorner = null
                try {
                    val bytes = file.readBytes()
                    require(bytes.size <= 25_000_000) { "Choose an image smaller than 25 MB." }
                    val bitmap = bytes.decodeToImageBitmap().importSize()
                    image = bitmap; gray = bitmap.grayscale()
                    val detected = detectGrid(requireNotNull(gray))
                    if (detected == null) {
                        grid = ".".repeat(81); adjusting = true
                        message = "The grid could not be located. Mark its top-left and bottom-right corners, or enter the clues manually."
                    } else recognize(detected)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { message = "Could not read this image. Try a clear PNG or JPEG screenshot with the entire grid visible." }
                finally { busy = false }
            }
        }
    }

    fun edit(number: Int?) {
        grid = grid?.toCharArray()?.also { it[selected.index] = number?.digitToChar() ?: '.' }?.concatToString()
        uncertain = uncertain - selected.index; confirmed = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(12.dp).widthIn(max = 900.dp).fillMaxWidth().heightIn(max = 900.dp), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (grid == null) "Bring in a puzzle" else "Double-check your import", fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onDismiss) { Text("Cancel") }
                }
                if (grid == null) {
                    PrimaryTabRow(tab) {
                        Tab(tab == 0, { tab = 0 }, enabled = !busy, text = { Text("Image") })
                        Tab(tab == 1, { tab = 1 }, enabled = !busy, text = { Text("Puzzle code") })
                    }
                    if (tab == 0) {
                        Text("Choose a screenshot of a classic 9×9 sudoku. Keep the whole grid visible. Recognition happens on your device.")
                        Button({ picker.launch() }, enabled = !busy) { Text("Choose image") }
                    } else {
                        OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("81-character grid") }, supportingText = { Text("Use 1–9 for clues and dots or zeros for empty cells. Line breaks are allowed.") }, minLines = 3)
                        if (code.isNotBlank() && parsed == null) Text("Enter exactly 81 cells. Puzzle links and variants are not supported here.", color = MaterialTheme.colorScheme.error)
                        Button({ grid = parsed; uncertain = emptySet() }, enabled = parsed != null && !busy) { Text("Review grid") }
                    }
                }
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Reading the screenshot…") }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (grid != null) {
                    Text("Compare every clue with the original, even if the checks pass. Select any cell to add, replace, or erase a digit.")
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val compact = maxWidth < 620.dp
                        val source: @Composable () -> Unit = {
                            image?.let { bitmap ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Original screenshot", fontWeight = FontWeight.SemiBold)
                                    if (adjusting) Text(if (firstCorner == null) "Tap the grid’s top-left corner." else "Tap the grid’s bottom-right corner.")
                                    Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
                                        Image(bitmap, "Original sudoku screenshot", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                                        Canvas(Modifier.fillMaxSize().pointerInput(adjusting, firstCorner, busy, bitmap) {
                                            if (adjusting && !busy) detectTapGestures { tap ->
                                                val point = Offset(tap.x / size.width * bitmap.width, tap.y / size.height * bitmap.height)
                                                val first = firstCorner
                                                if (first == null) firstCorner = point else {
                                                    val area = GridBounds(min(first.x, point.x).roundToInt().coerceIn(0, bitmap.width), min(first.y, point.y).roundToInt().coerceIn(0, bitmap.height), max(first.x, point.x).roundToInt().coerceIn(0, bitmap.width), max(first.y, point.y).roundToInt().coerceIn(0, bitmap.height))
                                                    firstCorner = null
                                                    if (area.width >= 90 && area.height >= 90) { adjusting = false; runRecognition(area) }
                                                    else message = "Select the entire 9×9 grid, at least 90 pixels wide and high."
                                                }
                                            }
                                        }) {
                                            val sx = size.width / bitmap.width; val sy = size.height / bitmap.height
                                            bounds?.let { drawRect(Color(0xFFCF6600), Offset(it.left * sx, it.top * sy), Size(it.width * sx, it.height * sy), style = Stroke(2.dp.toPx())) }
                                            firstCorner?.let { drawCircle(Color(0xFFCF6600), 5.dp.toPx(), Offset(it.x * sx, it.y * sy)) }
                                        }
                                    }
                                    TextButton({ adjusting = !adjusting; firstCorner = null }, enabled = !busy) { Text(if (adjusting) "Cancel adjustment" else "Adjust grid area") }
                                }
                            }
                        }
                        val editor: @Composable () -> Unit = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val current = requireNotNull(grid)
                                val puzzle = remember(current) { Puzzle("", "", current) }
                                val conflicts = remember(current) { conflictsFor(puzzle, puzzle.startingCells()) }
                                Text("Imported clues · row ${selected.row + 1}, column ${selected.column + 1}", fontWeight = FontWeight.SemiBold)
                                SudokuBoard(puzzle, puzzle.startingCells(), setOf(selected), conflicts, uncertain) { if (!busy) selected = it }
                                if (uncertain.isNotEmpty()) Text("${uncertain.size} uncertain cells (amber): ${uncertain.sorted().joinToString { "r${it / 9 + 1}c${it % 9 + 1}" }}. Please check these especially.", fontSize = 12.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    (1..9).forEach { number ->
                                        OutlinedButton({ edit(number) }, Modifier.weight(1f), enabled = !busy, contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(6.dp)) { Text(number.toString()) }
                                    }
                                }
                                Row {
                                    TextButton({ edit(null) }, enabled = !busy) { Text("Erase cell") }
                                    TextButton({ uncertain = uncertain - selected.index }, enabled = !busy && selected.index in uncertain) { Text("Mark cell checked") }
                                }
                            }
                        }
                        if (!compact && image != null) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Column(Modifier.weight(1f)) { source() }; Column(Modifier.weight(1f)) { editor() }
                        } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { source(); editor() }
                    }
                    val status = validation?.status
                    val warning = status == ImportStatus.Conflicts || status == ImportStatus.Unsolvable || (!checking && status == null)
                    Surface(color = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                        Text(when {
                            checking -> "Checking starting clues and solutions…"
                            status == ImportStatus.Conflicts -> "Starting clues break sudoku rules. Red cells repeat a digit in a row, column, or box. This may be an import error; correct the clues before playing."
                            status == ImportStatus.Unsolvable -> "No immediate rule conflicts, but this grid has no solution. Check for a misread or extra clue; the original puzzle may also be invalid."
                            status == ImportStatus.Unique -> "No rule conflicts. This grid has exactly one solution. Please still compare every clue with the screenshot."
                            status == ImportStatus.Multiple -> "No rule conflicts. This grid is solvable, but has multiple solutions. Check for missing or misread clues."
                            else -> "The solution check took too long, so solvability is unconfirmed. You can keep editing or import after carefully reviewing."
                        }, Modifier.padding(12.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(confirmed, { confirmed = it }, enabled = !busy && !checking)
                        Text("I have double-checked the imported clues.", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({ grid = null; image = null; gray = null; bounds = null; message = null; confirmed = false }, enabled = !busy) { Text("Choose another puzzle") }
                        Button({ grid?.let(onImport) }, enabled = confirmed && !busy && !checking) { Text(if (warning) "Import with warning" else "Import puzzle") }
                    }
                }
            }
        }
    }
}
