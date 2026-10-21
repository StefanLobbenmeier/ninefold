import importlib.util
import json
import pathlib
import tempfile
import unittest
from unittest import mock
import urllib.error

spec = importlib.util.spec_from_file_location("sudoku_index", pathlib.Path(__file__).with_name("update-sudoku-index.py"))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)

SOURCE = {"id": "cracking-the-cryptic", "name": "Cracking the Cryptic", "type": "youtube"}


def video(video_id, description, published="2026-10-01T12:00:00+00:00"):
    return f"""<entry><yt:videoId>{video_id}</yt:videoId><title>A &amp; B &#x1F9E9;</title>
    <published>{published}</published><updated>2099-01-01T00:00:00Z</updated>
    <media:group><media:description>{description}</media:description></media:group></entry>"""


def feed(*entries):
    return (f'<feed xmlns="{publisher.ATOM}" xmlns:yt="{publisher.YOUTUBE}" xmlns:media="{publisher.MEDIA}">' + "".join(entries) + '</feed>').encode()


class SudokuIndexTest(unittest.TestCase):
    def test_failed_refresh_logs_source_and_preserves_existing_index(self):
        source = {**SOURCE, "id": "synthetic-channel", "feedUrl": "https://example.test/feed"}
        error = urllib.error.HTTPError(source["feedUrl"], 404, "Not Found", {}, None)
        with tempfile.TemporaryDirectory() as directory:
            output = pathlib.Path(directory)
            previous = '{"schemaVersion": 1, "entries": []}\n'
            (output / "index.json").write_text(previous)
            sources_file = output / "sources.json"
            sources_file.write_text(json.dumps([source]))
            with mock.patch("sys.argv", ["publisher", "--output", directory, "--sources", str(sources_file)]), \
                    mock.patch.dict(publisher.os.environ, {}, clear=True), \
                    mock.patch.object(publisher.urllib.request, "urlopen", side_effect=error), \
                    self.assertLogs(publisher.LOGGER, level="INFO") as logs:
                with self.assertRaises(urllib.error.HTTPError) as raised:
                    publisher.main()
            self.assertIs(error, raised.exception)
            self.assertIn("Fetching source synthetic-channel from https://example.test/feed", logs.output[0])
            self.assertIn("Failed to refresh source synthetic-channel from https://example.test/feed: HTTPError: HTTP Error 404: Not Found", logs.output[1])
            self.assertEqual(previous, (output / "index.json").read_text())
            self.assertFalse((output / "index.js").exists())

    def test_rss_404_falls_back_to_api_with_equivalent_entries(self):
        source = {**SOURCE, "feedUrl": "https://example.test/feed?channel_id=synthetic-channel"}
        responses = [
            {"items": [{"contentDetails": {"relatedPlaylists": {"uploads": "synthetic-uploads"}}}]},
            {"items": [{"snippet": {"title": "A & B 🧩", "description": "https://sudokupad.app/one"},
                        "contentDetails": {"videoId": "aaaaaaaaaaa", "videoPublishedAt": "2026-10-01T12:00:00Z"}}]},
        ]
        error = urllib.error.HTTPError(source["feedUrl"], 404, "Not Found", {}, None)
        with mock.patch.object(publisher.urllib.request, "urlopen", side_effect=error), \
                mock.patch.object(publisher, "request_json", side_effect=responses) as fetch, \
                self.assertLogs(publisher.LOGGER, level="WARNING"):
            entries = publisher.fetch_source(source, "synthetic-key")
        self.assertEqual(publisher.youtube_entries(source, feed(video("aaaaaaaaaaa", "https://sudokupad.app/one"))), entries)
        queries = [publisher.urllib.parse.parse_qs(publisher.urllib.parse.urlsplit(call.args[0]).query)
                   for call in fetch.call_args_list]
        self.assertEqual(["synthetic-channel"], queries[0]["id"])
        self.assertEqual(["synthetic-uploads"], queries[1]["playlistId"])
        self.assertEqual(["50"], queries[1]["maxResults"])

    def test_failed_api_fallback_preserves_existing_resources(self):
        source = {**SOURCE, "feedUrl": "https://example.test/feed?channel_id=synthetic-channel"}
        error = urllib.error.HTTPError(source["feedUrl"], 404, "Not Found", {}, None)
        with tempfile.TemporaryDirectory() as directory:
            output = pathlib.Path(directory)
            publisher.write_resources({"schemaVersion": 1, "entries": []}, output)
            before = {p.name: p.read_bytes() for p in output.iterdir()}
            sources_file = output / "sources.json"
            sources_file.write_text(json.dumps([source]))
            with mock.patch("sys.argv", ["publisher", "--output", directory, "--sources", str(sources_file)]), \
                    mock.patch.dict(publisher.os.environ, {"YOUTUBE_API_KEY": "synthetic-key"}), \
                    mock.patch.object(publisher.urllib.request, "urlopen", side_effect=error), \
                    self.assertLogs(publisher.LOGGER, level="WARNING"):
                with self.assertRaisesRegex(RuntimeError, "YouTube Data API returned HTTP 404"):
                    publisher.main()
            for name, data in before.items():
                self.assertEqual(data, (output / name).read_bytes())

    def test_successful_rss_does_not_call_api(self):
        response = mock.MagicMock()
        response.__enter__.return_value.read.return_value = feed()
        with mock.patch.object(publisher.urllib.request, "urlopen", return_value=response), \
                mock.patch.object(publisher, "youtube_api_entries") as fallback:
            self.assertEqual([], publisher.fetch_source({**SOURCE, "feedUrl": "https://example.test/feed"}, "synthetic-key"))
        fallback.assert_not_called()

    def test_youtube_links_and_xml_entities(self):
        payload = feed(video("aaaaaaaaaaa", "<![CDATA[https://sudokupad.app/one) https://www.sudokupad.app/two?setting=1 https://sudokupad.app/one]]>"))
        entries = publisher.youtube_entries(SOURCE, payload)
        self.assertEqual(["https://sudokupad.app/one", "https://sudokupad.app/two"], [entry["puzzleUrl"] for entry in entries])
        self.assertEqual("A & B 🧩", entries[0]["title"])
        self.assertEqual("2026-10-01T12:00:00Z", entries[0]["publishedAt"])
        self.assertEqual("https://www.youtube.com/watch?v=aaaaaaaaaaa", entries[0]["links"][0]["url"])

    def test_video_metadata_enrichment_is_optional_and_uses_injected_fetch(self):
        entries = publisher.youtube_entries(SOURCE, feed(video("aaaaaaaaaaa", "https://sudokupad.app/one")))
        self.assertEqual("https://i.ytimg.com/vi/aaaaaaaaaaa/hqdefault.jpg", entries[0]["metadata"]["thumbnailUrl"])
        self.assertNotIn("durationSeconds", entries[0]["metadata"])
        requests = []
        def fetch(url):
            requests.append(url)
            return {"items": [{"id": "aaaaaaaaaaa", "contentDetails": {"duration": "PT1H2M3S"}}]}
        publisher.enrich_video_metadata(entries, "synthetic-key", fetch=fetch)
        self.assertEqual(3723, entries[0]["metadata"]["durationSeconds"])
        self.assertEqual(1, len(requests))
        publisher.enrich_video_metadata(entries, "synthetic-key", fetch=lambda _: {"items": []})
        self.assertEqual(3723, entries[0]["metadata"]["durationSeconds"])

    def test_unsafe_links_and_non_puzzle_uploads_are_omitted(self):
        payload = feed(video("aaaaaaaaaaa", "https://sudokupad.app/../escape https://sudokupad.app/ https://sudokupad.app.evil/puzzle"), video("bbbbbbbbbbb", "No puzzle"), video("bad", "https://sudokupad.app/one"))
        self.assertEqual([], publisher.youtube_entries(SOURCE, payload))
        with self.assertRaises(ValueError):
            publisher.youtube_entries(SOURCE, b"<html>error</html>")

    def test_merge_retains_old_entries_other_sources_and_extensions(self):
        old = {"id": "old", "sourceId": "other", "publishedAt": "2025-01-01T00:00:00Z", "metadata": {"custom": True}}
        previous = {"schemaVersion": 1, "extra": True, "sources": [{"id": "other", "name": "Other"}], "entries": [old]}
        fresh = publisher.youtube_entries(SOURCE, feed(video("aaaaaaaaaaa", "https://sudokupad.app/one")))
        result = publisher.merge_index(previous, [SOURCE], fresh, "2026-10-12T00:00:00Z")
        self.assertEqual([fresh[0]["id"], "old"], [entry["id"] for entry in result["entries"]])
        self.assertEqual(old, result["entries"][1])
        self.assertEqual({"other", SOURCE["id"]}, {source["id"] for source in result["sources"]})
        self.assertTrue(result["extra"])
        result["entries"][0]["metadata"]["extension"] = 42
        again = publisher.merge_index(result, [SOURCE], fresh, result["generatedAt"])
        self.assertEqual(2, len(again["entries"]))
        self.assertEqual(42, again["entries"][0]["metadata"]["extension"])

    def test_resources_are_equivalent_and_version_is_checked(self):
        index = publisher.merge_index({}, [SOURCE], [], "2026-10-12T00:00:00Z")
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory)
            publisher.write_resources(index, path)
            self.assertEqual(index, json.loads((path / "index.json").read_text()))
            javascript = (path / "index.js").read_text()
            encoded = javascript.removeprefix("globalThis.sudokuIndex = JSON.parse(").removesuffix(");\n")
            self.assertEqual(index, json.loads(json.loads(encoded)))
        with self.assertRaises(ValueError):
            publisher.merge_index({"schemaVersion": 2}, [SOURCE], [], "2026-10-12T00:00:00Z")


if __name__ == "__main__":
    unittest.main()
