package de.sudokupad

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GameTimerTest {
    @Test fun displayKeepsTickingWhenHideAndShowAreConflatedBeforeRendering() = runTest {
        val puzzle = SudokuFixtures.empty
        val repository = GameRepository(MemoryAppStorage())
        repository.saveElapsed(puzzle, 60)
        val clock = GameClock { testScheduler.currentTime }.also { it.restore(60) }
        val frames = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frames)
        val runner = launch(frames) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) {}
            override fun insertBottomUp(index: Int, instance: Unit) {}
            override fun remove(index: Int, count: Int) {}
            override fun move(from: Int, to: Int, count: Int) {}
            override fun onClear() {}
        }, recomposer)
        var visibility: (Boolean) -> Unit = {}
        var displayed = -1L
        var unsubscribed = false
        try {
            composition.setContent {
                val elapsed = rememberGameElapsed(puzzle, repository, clock, true, false) { listener ->
                    visibility = listener
                    listener(true)
                    val unsubscribe: () -> Unit = { unsubscribed = true }
                    unsubscribe
                }
                SideEffect { displayed = elapsed }
            }
            runCurrent()
            assertEquals(60, displayed)

            repeat(2) {
                visibility(false)
                // Run the timer while background rendering is suspended. No frame is sent.
                advanceTimeBy(1_000)
                runCurrent()
                assertEquals(60L + it, clock.elapsedSeconds())
                visibility(true)
                runCurrent()
                Snapshot.sendApplyNotifications()
                runCurrent()
                frames.sendFrame(testScheduler.currentTime * 1_000_000)
                runCurrent()

                advanceTimeBy(1_000)
                runCurrent()
                Snapshot.sendApplyNotifications()
                runCurrent()
                frames.sendFrame(testScheduler.currentTime * 1_000_000)
                runCurrent()
                assertEquals(61L + it, displayed)
                assertEquals(displayed, repository.elapsed(puzzle))
            }
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.join()
        }
        assertEquals(true, unsubscribed)
    }
}
