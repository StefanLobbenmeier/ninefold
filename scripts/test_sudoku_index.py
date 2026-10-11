import importlib.util
import json
import pathlib
import tempfile
import unittest

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
    def test_youtube_links_and_xml_entities(self):
        payload = feed(video("aaaaaaaaaaa", "<![CDATA[https://sudokupad.app/one) https://www.sudokupad.app/two?setting=1 https://sudokupad.app/one]]>"))
        entries = publisher.youtube_entries(SOURCE, payload)
        self.assertEqual(["https://sudokupad.app/one", "https://sudokupad.app/two"], [entry["puzzleUrl"] for entry in entries])
        self.assertEqual("A & B 🧩", entries[0]["title"])
        self.assertEqual("2026-10-01T12:00:00Z", entries[0]["publishedAt"])
        self.assertEqual("https://www.youtube.com/watch?v=aaaaaaaaaaa", entries[0]["links"][0]["url"])

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
