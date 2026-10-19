#!/usr/bin/env python3
"""Refresh a source-neutral SudokuPad index and retain previously discovered entries."""
import argparse
import datetime as dt
import json
import logging
import os
import urllib.parse
import pathlib
import re
import urllib.request
import xml.etree.ElementTree as ET

ATOM = "http://www.w3.org/2005/Atom"
MEDIA = "http://search.yahoo.com/mrss/"
YOUTUBE = "http://www.youtube.com/xml/schemas/2015"
LOGGER = logging.getLogger(__name__)


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
                "metadata": {"youtubeVideoId": video_id, "thumbnailUrl": f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"},
            })
    return entries


def enrich_video_metadata(entries: list[dict], api_key: str, fetch=None) -> None:
    """Optional YouTube Data API enrichment. RSS itself does not include running time."""
    def request_json(url):
        with urllib.request.urlopen(url, timeout=30) as response:
            return json.load(response)
    fetch = fetch or request_json
    ids = list(dict.fromkeys(entry.get("metadata", {}).get("youtubeVideoId") for entry in entries))
    ids = [video_id for video_id in ids if video_id]
    for start in range(0, len(ids), 50):
        query = urllib.parse.urlencode({"part": "contentDetails", "id": ",".join(ids[start:start + 50]), "key": api_key})
        payload = fetch("https://www.googleapis.com/youtube/v3/videos?" + query)
        durations = {}
        for item in payload.get("items", []):
            match = re.fullmatch(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?", item.get("contentDetails", {}).get("duration", ""))
            if match:
                hours, minutes, seconds = (int(value or 0) for value in match.groups())
                duration = hours * 3600 + minutes * 60 + seconds
                if duration > 0:
                    durations[item["id"]] = duration
        for entry in entries:
            metadata = entry.get("metadata", {})
            if metadata.get("youtubeVideoId") in durations:
                metadata["durationSeconds"] = durations[metadata["youtubeVideoId"]]


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
        LOGGER.info("Fetching source %s from %s", source["id"], source["feedUrl"])
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                entries = adapter(source, response.read())
        except Exception as error:
            LOGGER.error("Failed to refresh source %s from %s: %s: %s",
                         source["id"], source["feedUrl"], type(error).__name__, error)
            raise
        LOGGER.info("Fetched %d entries from source %s", len(entries), source["id"])
        updates.extend(entries)
    api_key = os.environ.get("YOUTUBE_API_KEY")
    if api_key:
        enrich_video_metadata(updates, api_key)
    # Fetch/parse every source before writing anything; failed refreshes preserve the old index.
    generated_at = utc_timestamp(dt.datetime.now(dt.timezone.utc).isoformat())
    write_resources(merge_index(previous, sources, updates, generated_at), args.output)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    main()
