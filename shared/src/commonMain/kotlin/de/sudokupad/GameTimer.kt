package de.sudokupad

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

@Composable
internal fun rememberGameElapsed(
    puzzle: Puzzle,
    repository: GameRepository,
    clock: GameClock,
    started: Boolean,
    completed: Boolean,
    observeVisibility: ((Boolean) -> Unit) -> (() -> Unit) = ::observePageVisibility,
): Long {
    var elapsed by remember(puzzle) { mutableStateOf(repository.elapsed(puzzle)) }
    DisposableEffect(puzzle, clock) {
        val unsubscribe = observeVisibility { visible ->
            // Save synchronously before the browser suspends rendering and coroutines.
            clock.setForeground(visible)
            elapsed = clock.elapsedSeconds()
            repository.saveElapsed(puzzle, elapsed)
        }
        onDispose { unsubscribe() }
    }
    LaunchedEffect(started, completed, puzzle, clock) {
        if (started) {
            if (completed) clock.stop() else clock.start()
            elapsed = clock.elapsedSeconds()
            repository.saveElapsed(puzzle, elapsed)
            // Visibility pauses the clock, not this observer. Hide/show can be conflated
            // before recomposition, so a loop that exits on hide may never restart.
            while (!completed) {
                delay(200)
                val next = clock.elapsedSeconds()
                if (next != elapsed) {
                    elapsed = next
                    repository.saveElapsed(puzzle, elapsed)
                }
            }
        }
    }
    return elapsed
}
