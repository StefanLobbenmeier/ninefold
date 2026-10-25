# Ninefold

Ninefold is a cross-platform Sudoku app with offline play and SudokuPad puzzle import.

This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM).

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
      folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
    - Hot reload: `./gradlew :desktopApp:hotRun --auto`
    - Standard run: `./gradlew :desktopApp:run`
- Web app:
    - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
    - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
  - Includes a JVM UI startup smoke test that renders the app, loads a catalog through the real
    HTTP engine from a local server, and opens Import. No internet connection is needed for the test.
    On Linux without a display, use `xvfb-run --auto-servernum ./gradlew :shared:jvmTest` (as in CI).
- Web tests:
    - Wasm target: `./gradlew :shared:wasmJsTest`
    - JS target: `./gradlew :shared:jsTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack
channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
### Screenshot import

Import → Image opens the system image picker. The shared Kotlin implementation finds an axis-aligned
9×9 grid, recognizes printed digits locally, and maps each cell by its position (including empty rows
and columns). No screenshot is uploaded and no OCR service, API key, or model download is required.
The recognizer uses normalized glyph templates rendered from the platform's sans, serif, and monospace
fonts, with stroke and enclosed-shape comparisons. It is intended for clear classic-sudoku screenshots;
unusual fonts, pencil marks, handwriting, rotated/perspective photos, and very low-resolution images
can require corrections. Automatic recognition is not guaranteed for every screenshot.

Every import opens an editable review before changing the current puzzle:

- Compare the original screenshot and imported clues; amber cells indicate uncertain readings.
- Select any cell and use the number buttons or Erase to correct it. Adjust grid area lets you mark
  the grid's opposite corners and rerun recognition; if detection fails, manual entry remains available.
- Shared validation checks duplicate clues in rows, columns, and boxes, then searches for up to two
  solutions. It distinguishes invalid, unsolvable, uniquely solvable, and ambiguous starting grids.
  Checks rerun after edits, are cancellable, and stop after five seconds if still inconclusive.
- Confirm that you have double-checked the clues before importing. Invalid or unsolvable grids may
  still be imported with an explicit warning. Cancelling preserves the current game and its history.

Recognition, crop coordinates, validation, and review UI live in `shared/src/commonMain`.
FileKit supplies the native/browser image pickers; Desktop initializes FileKit with the application ID.
There are no separate platform OCR implementations to add.

### SudokuPad URL import

Square grids from 1×1 through 9×9 are supported, including 4×4 and 6×6. Imported regions
are preserved for drawing and checking; puzzles without region data use regular boxes
(for example, 2×3 boxes for a 6×6 grid). The board, number pad, selection, fog, saved games,
and share links use the imported size.

Import → Puzzle link accepts links such as `https://sudokupad.app/hDNMJqPmTh`. The app fetches the
corresponding package through SudokuPad's own CORS-enabled
`https://sudokupad.svencodes.com/ctclegacy/{encoded-id}` endpoint and decodes both SudokuPad's
`scl`/compact-JSON representation and the common `fpuz` representation. The main site's
`/api/puzzle/{id}` endpoint restricts cross-origin requests to `https://localhost`; the alternate
endpoint works from the JS and Wasm web builds as well as the native builds.

Imported puzzles retain metadata, givens, solutions, killer cages, sum arrows, colored lines, fog,
cell marks, and generic overlays such as Kropki/XV marks and outside-grid clues. Dynamic fog retains
SudokuPad trigger/effect links: all trigger cells must be correct to reveal the linked cells, instead
of clearing a 3×3 neighborhood. Previously imported puzzles need to be imported again to recover
links discarded by older versions. Explicit fpuz
thermometers and palindromes retain their semantics. Thermometers render a bulb at the first cell;
optional special-rule checks enforce increasing digits, room for blank cells, and valid pencil marks.
Styled custom lines default to display-only; the
review's line-rule dialog lets the solver assign German/Dutch whisper, renban, palindrome, thermometer,
or region-sum meaning without guessing based only on a color. Killer sums, sum arrows, assigned simple
line rules, and recognizable skyscraper/diagonal-sum outside clues participate in optional rule checks.
Cages without printed totals retain their outlines and uniqueness checks; totals stated only in prose
remain in the rules text and are not inferred by the checker. Reimport previously saved puzzles to
recover cages discarded by older versions.
Unknown artwork is still rendered and its original rule text is kept.

Validation: `./gradlew :shared:jvmTest` includes solver cases and generated screenshot fixtures covering
margins, sparse grids, multiple fonts, sizes, and dark backgrounds. The Web upload/review/edit flow was
also checked in the browser. JS, Wasm, JVM, Android, and iOS simulator source compilation is checked.
Before shipping native builds, smoke-test on devices:

- Android: gallery selection/cancellation, PNG/JPEG decoding, and review on a small screen.
- iOS: Photos picker presentation/dismissal, file decoding, and safe-area/scroll behavior.
- Desktop: native picker behavior on each packaged OS (including Linux's desktop portal).

### Puzzle catalog and offline browsing

The Browse tab shows the newest puzzles from
`https://stefanlobbenmeier.github.io/sudoku-index/index.json`. Select Play to load a puzzle into the
player. The back arrow, a swipe from the left edge, or system/browser Back returns to the previous tab. Import is available from the main menu. The Play tab features the last played unfinished puzzle, with smaller cards for other ongoing and solved puzzles. Unavailable puzzles are muted until ready; failed downloads have a Retry action.

The app saves the last valid index locally and displays it immediately on subsequent
visits; use Refresh to request a new index. Failed requests or invalid index responses preserve the saved list. Refresh
retries the request; first visits without a connection show an error until a download succeeds.
All catalog puzzles are eagerly fetched, converted, and saved with up to four downloads at once,
while browsing. Opening a puzzle cancels unfinished catalog requests; pending puzzle downloads resume
when returning to the catalog. Each entry shows waiting, fetching/converting, ready offline, or failed status.
Ready offline means the converted puzzle was successfully stored; opening it makes no puzzle request.
Refresh or an entry's Retry button retries failures. Long-press a saved SudokuPad puzzle in Continue,
Solved, or a ready offline catalog entry to fetch and convert it again with the current importer.
A successful reimport replaces the saved conversion while keeping moves, history, and elapsed time;
changed givens are updated throughout the board and history. A failed reimport keeps the old puzzle
and displays the error. A dialog confirms successful reimports. Preparing a large catalog can take longer than a
few seconds, depending on the connection. Production builds prepare a service worker that downloads
and verifies the complete app (including all Wasm modules and chunks) before installing a release.
Subsequent launches use that cached release immediately, even on a slow connection. Web startup shows
a loading indicator until the bundled heading font is decoded, so headings appear in their final font
on the first frame, including offline. Updates download
into a separate cache; failed or interrupted downloads preserve the working release. A complete update
shows a “New version ready” banner at the top of Home. Select Reload to activate it and reload
all open app tabs (tabs playing a puzzle defer the reload until returning to the catalog).
Checks run on launch, every five minutes, and when returning to the app, but pause while a puzzle
is open or the app is hidden. Returning to the catalog resumes checks;
updates also activate naturally after all app tabs close. Fetched puzzles and fallback fonts have a separate cache that
survives app updates. The first visit needs a connection until the app download completes.
Browser storage must be available for persistent
offline access.

Outbound links, including source videos and original SudokuPad pages, use a shared web link handler.
In an iOS 17+ home-screen app, cross-origin HTTPS links use `x-safari-https:` with `_self`
to request Safari outside the PWA without creating a blank popup. This intentionally opens
Safari even when another browser is the default, and source videos also take this route.
Only the leading scheme changes; paths, query strings, and fragments are preserved.
This is an undocumented, best-effort workaround based on
[Safari scheme research](https://christiantietze.de/posts/2023/05/safari-for-mac-url-scheme/).
There is no supported web API to force the default browser; see
[WebKit's open request](https://bugs.webkit.org/show_bug.cgi?id=240025).
Older/unknown iOS versions and non-HTTPS links retain normal system link handling, which may
show a browser sheet or open an installed app. Regular tabs and same-origin links use `_blank`.
The web app cannot detect whether Safari accepted the scheme (or is available), so there is
no timed retry that might open a duplicate destination after returning to the puzzle.
On iOS home-screen apps, a shorter viewport after an outbound link triggers up to three viewport
policy resets to request fresh layout without reloading the puzzle. Recovery checks run on resume,
resize, and the next touch if iOS omits resume events. Rotation/window-width changes invalidate the
saved dimensions; text editing and zoom defer recovery.

Validate on an installed iOS 17+ PWA: open an original SudokuPad link and a source video,
confirm Safari opens outside the PWA, and return to the puzzle. Repeat with another browser
set as default (Safari should still open), and on an iPad using its desktop user agent.
Check that the board fills the available height and bottom controls respond; repeat the trip,
rotate while away, and open/close the import keyboard afterward. Also check a normal Safari
tab and an HTTP link using the browser-sheet fallback. Automated callback tests cover routing
and viewport recovery, but cannot confirm the native Safari handoff or browser-sheet viewport bug.

Every move (including pencil marks, erase, undo, and redo) saves the board and undo/redo history.
The timer saves independently whenever its displayed second changes and when leaving the player.
Reopening resumes from the last saved board and elapsed time; time away from the player does not count.
In the browser/PWA, the timer also pauses while the page is hidden (such as when switching
tabs or apps) and resumes when visible. Page restore and focus events also resume the timer when
returning to an iOS PWA without a visibility-change event. Elapsed time is saved immediately on these changes.
Completed grids retain their frozen timer. The menu has Continue sudokus and Solved sudokus sections,
including manually imported games. Checking preferences, input mode, and multiple selection persist
across puzzles and restarts. Native storage uses Android preferences, iOS user defaults, and local files
in `~/.ninefold` on Desktop. Failed local saves display an error.

Catalog parsing, refresh, cache preservation, and unavailable storage are covered by
`./gradlew :shared:jvmTest`.

### Playing on desktop

The top bar shows the puzzle title and author, elapsed time, pause, and sharing. Long titles
truncate while the timer and actions stay visible. The sidebar uses a smaller keypad so more
of the rules fit beside the board. Pause opens the rules and stops the timer, including while
switching tabs or apps; Resume puzzle (or dismissing the rules) continues it. Checking options
are available from the keypad's settings button.

### Playing on a phone

The player keeps the board and a T9-style number keypad within the available screen height,
including native safe areas. Each new puzzle opens its rules first, with links to the source
video and original SudokuPad when available. Tap Okay to start the timer. Filling the last cell
runs the solution and supported-rule checks regardless of checking preferences. A valid grid
freezes the timer and opens a completion screen with the solve time and any custom SudokuPad
success message; undoing the final digit resumes the timer.

Drag across cells to select a group, or enable Select multiple to add to the current selection
by tapping or dragging. With Select multiple enabled, start on a selected cell to remove it and
any cells you drag across; untouched cells stay selected. With Select multiple off, every tap or drag
starts a new selection, including when it starts on a selected cell. Adjacent selected cells share a bold outside outline. The keypad places 1–9 in three rows with multiple selection, undo, and an erase icon below;
Digit, Corner, Centre, and Colour sit beside the keypad. Choose Corner or
Centre to enter pencil marks in all empty selected cells. A note is added to every selected cell
if any are missing it; tapping that number again removes it from all of them. Filled digits and
givens are preserved when adding notes. For each editable selected cell, Erase (or 0) clears the
active number category first. If that category is empty, it clears the remaining digits and notes;
if all number categories are empty, it clears colours. Undo restores the whole group in one step.

Choose Colour to replace the digits with SudokuPad's nine default swatches: light grey, grey,
dark grey, green, purple, orange, red, yellow, and blue. Colours apply to all selected cells,
including givens and filled digits. Selecting a colour adds it to the group if any cell lacks it;
selecting it again removes it from the group. Multiple colours appear as stripes behind the
puzzle clues and digits. In Colour mode, Erase and 0 clear only colours. Colours and colour
undo/redo history are saved with the game.

Long-press a colour swatch to configure automatic colouring. Each colour has its own enable switch
and matching numbers, saved per game across restarts. Disabled presets are green for 1–3,
yellow for 4–6, red for 7–9, blue for even numbers, and orange for odd numbers. Enabled swatches show their configured numbers in ascending order.
On first opening a new game, rule text enables blue and orange for odd/even or parity rules,
green (1–4) for V clues, green (1–4) and red (6–9) for X/XV clues, and
green (1–4), yellow (5), and red (6–9) for German whisper lines (also detected from assigned
line rules). Entropic rules enable green (1–3), yellow (4–6), and red (7–9); if combined
with whisper or X/V rules, the latter digit groups take priority. Other special rules leave
colouring opt-in because they do not define fixed digit groups. Saved settings
always take priority, so disabling a colour or customizing its numbers persists across restarts.
Colours are calculated live from each cell's digit or nonempty centre marks, which must all belong
to the configured numbers. Corner marks do not trigger colouring. All matching colours appear
alongside manual colours. Editing cells or changing or disabling a rule updates the whole grid
immediately. Automatic colours are never stored on cells or added to undo history; manual colours
and their undo/redo history stay independent.
Ordinary solution feedback waits until you leave the cell; final-grid and Sudoku rule checks run immediately.
Keypad presses and input-mode changes keep the current selection.

### Sharing puzzles

Rules includes a selectable Puzzle link and Copy puzzle link for every puzzle, including catalog,
SudokuPad, screenshot, and manual imports. Opening the link in the web app imports the puzzle and
shows its rules. The link contains the complete puzzle (including variant clues, assigned line rules,
artwork, metadata, and solution when available); moves, notes, and elapsed time are not shared.
Existing progress on the receiving device resumes if it already has that puzzle saved.
The puzzle data is encoded in the URL fragment, so no upload or puzzle hosting service is needed.
Native apps generate links to the hosted web app; web builds use their current host and path.

On Desktop, iOS, Android, or an installed PWA, open Import → Puzzle link, paste the shared URL,
and select Load puzzle. Review it, confirm the clues, and select Import puzzle. Shared links decode
locally and work offline. This tab also accepts SudokuPad URLs (which require a connection).
Opening a shared HTTPS link directly currently targets the web app; native app deep linking is not
configured. Pasting inside the installed app or PWA imports into that app’s own saved games.

### Video cards

Catalog metadata supports optional `thumbnailUrl` (HTTPS) and `durationSeconds` (positive integer).
The YouTube exporter includes thumbnails automatically. Set the optional `YOUTUBE_API_KEY`
repository secret (or environment variable when running the exporter locally) to enrich videos with
running times from the YouTube Data API. The key also enables a fallback to the channel’s latest
50 uploads when its RSS feed fails. If both requests fail, the existing index is preserved and the
refresh fails visibly. Without a key, RSS is required; thumbnails still work and no duration is invented.
Older indexes with `youtubeVideoId` also receive thumbnails. Missing images use a grid placeholder.
