# GAS puzzle sources

Investigated October 17, 2026. Counts below describe the public sources at that
time, not a guarantee of importable puzzles. No downloaded puzzle payloads are
stored in this repository.

## Added: Lucian's Place

The [official GAS resource page](https://sudokutheory.com/gas/) links
[Lucian's Daily GAS playlist](https://www.youtube.com/playlist?list=PLirFk5QVdTRbakKfWe07j5Mjw010p2vG8).
The [channel](https://www.youtube.com/@luciansrule/videos) is still publishing
daily solves: the latest observed video covers October 15, 2026. The playlist
lists 1,198 videos.

Its [YouTube RSS feed](https://www.youtube.com/feeds/videos.xml?channel_id=UCbQtdQ21VY3M6xjnwA05uOg)
contains direct SudokuPad links in video descriptions. It works with the existing
`youtube` adapter, including walkthrough links, thumbnails, and optional duration
enrichment. It is now registered in `scripts/index-sources.json`.

RSS only exposes recent uploads. Our index retains entries after they leave the
feed, but adding this source does not backfill the playlist. The channel also
has other content; the current adapter includes only uploads with SudokuPad
links, rather than filtering playlist membership.

The [official GAS channel](https://www.youtube.com/@GenuinelyApproachableSudoku/videos)
currently stops at the July 3, 2026 puzzle. It is useful for historical walkthroughs
but cannot supply current daily puzzles on its own. The GAS resource page also
links a [timotab playlist](https://www.youtube.com/playlist?list=PLZaQ4HDFuibc9WKV7Nv05RXOCFOfrW-9H);
its current coverage was not verified in this investigation.

## Recommended next: Grandmaster Puzzles

The [GAS tag archive](https://www.gmpuzzles.com/blog/tag/gas/) is an active
publication source endorsed by the GAS resource page. Its public WordPress API
returned 500 tagged posts, from June 7, 2025 through October 16, 2026.

Useful endpoints:

- [Recent full-content RSS](https://www.gmpuzzles.com/blog/tag/gas/feed/): seven
  posts in the observed response. `?paged=2` returned the preceding seven.
- [GAS tag lookup](https://www.gmpuzzles.com/blog/wp-json/wp/v2/tags?slug=gas):
  currently tag ID `237`.
- [Paginated posts](https://www.gmpuzzles.com/blog/wp-json/wp/v2/posts?tags=237&per_page=100&page=1&_fields=id,date_gmt,link,title,content):
  up to 100 posts per page; five pages covered the observed archive.

Posts provide titles, dates, authors, puzzle images, playable links, and solution
PDF links. Recent posts also include downloadable SudokuPad replays. These are
replays, not narrated walkthrough videos. Of the 500 posts, 341 contained YouTube
links; the last such post was dated July 3, 2026.

Playable links commonly use `gmpuzzles.com/s/...` (with or without `www`), which
redirects to SudokuPad. A sampled current link returned a 301 directly to
`https://sudokupad.app/...`. A publisher adapter would need to resolve these
redirects before emitting entries: the app's index parser requires canonical
SudokuPad URLs. One tagged post was an interactive CAPTCHA announcement rather
than a normal linked puzzle, so tag membership alone is insufficient.

Suggested integration: backfill through the API, refresh via RSS, retain post
attribution and solution links, and attach videos when present. Cache redirect
results and merge by canonical puzzle identity so the same puzzle discovered
through Lucian, GAS, or CTC does not create another catalog card. The existing
publisher and app deduplicate by entry ID only; cross-source merging would be
additional work. Keep network resolution injectable and use synthetic feed/post
fixtures in tests.

## Older archive: community tracker

The GAS resource page recommends
[Xail's CtC Daily Puzzle Tracker](https://docs.google.com/spreadsheets/d/1eFc-khH4InTgSBb59i2g7-Fkp9HLMwrYQwlHs-mSJm4/edit).
The public `GAS Data Import (Do Not Touch)` tab is available as
[CSV without authentication](https://docs.google.com/spreadsheets/d/1eFc-khH4InTgSBb59i2g7-Fkp9HLMwrYQwlHs-mSJm4/gviz/tq?tqx=out:csv&gid=223648215).
Use the tab ID: the default export returns the instructions tab.

The observed CSV contains 2,032 data rows, spanning June 7, 2021 to October 16,
2026, including bonus/multipart entries. Columns include `Name`, `Rules`, `Author`,
`CtC App`, `Date` (day/month/year), and `CtC Solve Video`.

There are 1,089 rows with `sudokupad.app` puzzle links, 871 with TinyURL links,
and 1,063 with YouTube links in the video column. These are row counts, not unique
or validated puzzles. Other rows contain legacy CTC app links, missing values,
and occasional unrelated URLs; one direct puzzle URL is repeated. Older
shortlinks need resolution and destination validation, and legacy puzzle formats
need explicit handling. Video matches should use puzzle identity where possible;
dates alone are ambiguous for bonus/multipart puzzles. Headers and tab IDs are
community maintained and can change.

This is the strongest candidate for a pre-June-2025 backfill. Avoid exposing it
as a second copy of every daily GAS puzzle in the catalog.

## Other options

The GAS site's PDF archive currently lists 53 monthly/20-day compilations,
ending June 2025, plus a ZIP of packs 1–50. It is useful as a historical fallback,
but extracting per-puzzle metadata and links from PDFs is more work than using
the tracker. Discord is the original daily publication location, but the public
web/API/CSV sources above avoid requiring a Discord account or bot integration.

The existing app index supports entries without videos. Full catalog integration
of these sources can happen in the publisher; it does not require making videos
mandatory or changing the app's index schema.
