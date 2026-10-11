#!/usr/bin/env python3
"""Refresh a source-neutral SudokuPad index and retain previously discovered entries."""
import argparse
import datetime as dt
import json
import pathlib
import re
import urllib.request
import xml.etree.ElementTree as ET

ATOM = "http://www.w3.org/2005/Atom"
MEDIA = "http://search.yahoo.com/mrss/"
YOUTUBE = "http://www.youtube.com/xml/schemas/2015"


def utc_timestamp(value: str) -> str:
    return dt.datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(dt.timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def youtube_entries(source: dict, payload: bytes) -> list[dict]:
    root = ET.fromstring(payload)
    if root.tag != f"{{{ATOM}}}feed":
        raise ValueError("YouTube returned an invalid feed")
    entries = []
    for video in root.findall(f"{{{ATOM}}}entry"):
        description = video.findtext(f"{{{MEDIA}}}group/{{{MEDIA}}}description", "")
        video_id = video.findtext(f"{{{YOUTUBE}}}videoId", "")
        if not re.fullmatch(r"[A-Za-z0-9_-]{11}", video_id):
            continue
        published = utc_timestamp(video.findtext(f"{{{ATOM}}}published", ""))
        title = video.findtext(f"{{{ATOM}}}title", "")
        puzzle_ids = []
        for url in re.findall(r"https?://(?:www\.)?sudokupad\.app/[^\s<>\"']+", description, re.I):
            puzzle_id = re.split(r"[?#]", re.sub(r"^https?://(?:www\.)?sudokupad\.app/", "", url, flags=re.I))[0]
            puzzle_id = puzzle_id.rstrip(".,;)]!").strip("/").removeprefix("api/puzzle/")
            if re.fullmatch(r"[A-Za-z0-9_./-]+", puzzle_id) and ".." not in puzzle_id:
                puzzle_ids.append(puzzle_id)
        for puzzle_id in dict.fromkeys(puzzle_ids):
            entries.append({
                "id": f"{source['id']}:{video_id}:{puzzle_id}",
                "sourceId": source["id"],
                "title": title,
                "publishedAt": published,
                "puzzleUrl": f"https://sudokupad.app/{puzzle_id}",
                "links": [{"role": "walkthrough", "mediaType": "video", "url": f"https://www.youtube.com/watch?v={video_id}"}],
                "metadata": {"youtubeVideoId": video_id},
            })
    return entries


# New providers can register another adapter without changing the index contract or app.
ADAPTERS = {"youtube": youtube_entries}


def merge_index(previous: dict, sources: list[dict], updates: list[dict], generated_at: str) -> dict:
    if previous.get("schemaVersion", 1) != 1:
        raise ValueError("Unsupported existing index version; refusing to overwrite it")
    known_sources = {source["id"]: source for source in previous.get("sources", [])}
    for source in sources:
        known_sources[source["id"]] = {**known_sources.get(source["id"], {}), **source}
    known_entries = {entry["id"]: entry for entry in previous.get("entries", [])}
    for entry in updates:
        # Preserve provider extensions added by other index publishers.
        old = known_entries.get(entry["id"], {})
        known_entries[entry["id"]] = {**old, **entry, "metadata": {**old.get("metadata", {}), **entry.get("metadata", {})}}
    return {
        **previous,
        "schemaVersion": 1,
        "generatedAt": generated_at,
        "sources": list(known_sources.values()),
        "entries": sorted(known_entries.values(), key=lambda entry: entry["publishedAt"], reverse=True),
    }


def write_resources(index: dict, output: pathlib.Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    data = json.dumps(index, ensure_ascii=True, indent=2) + "\n"
    (output / "index.json").write_text(data, encoding="utf-8")
    (output / "index.js").write_text("globalThis.sudokuIndex = JSON.parse(" + json.dumps(data) + ");\n", encoding="utf-8")
    (output / ".nojekyll").touch()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--sources", type=pathlib.Path, default=pathlib.Path(__file__).with_name("index-sources.json"))
    args = parser.parse_args()
    previous_file = args.output / "index.json"
    previous = json.loads(previous_file.read_text()) if previous_file.exists() else {}
    sources = json.loads(args.sources.read_text())
    updates = []
    for source in sources:
        adapter = ADAPTERS[source["type"]]
        request = urllib.request.Request(source["feedUrl"], headers={"User-Agent": "SudokuPad puzzle index"})
        with urllib.request.urlopen(request, timeout=30) as response:
            updates.extend(adapter(source, response.read()))
    # Fetch/parse every source before writing anything; failed refreshes preserve the old index.
    generated_at = utc_timestamp(dt.datetime.now(dt.timezone.utc).isoformat())
    write_resources(merge_index(previous, sources, updates, generated_at), args.output)


if __name__ == "__main__":
    main()
