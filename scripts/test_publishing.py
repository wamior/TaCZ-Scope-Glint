import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import publish
from envfile import load_env


class EnvFileTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / ".env"

    def test_quotes_and_existing_environment(self):
        self.path.write_text('MODRINTH_TOKEN="local#value"\nCURSEFORGE_TOKEN=local\n', encoding="utf-8")
        with patch.dict(os.environ, {"MODRINTH_TOKEN": "from-ci"}, clear=True):
            load_env(self.path)
            self.assertEqual(os.environ["MODRINTH_TOKEN"], "from-ci")
            self.assertEqual(os.environ["CURSEFORGE_TOKEN"], "local")

    def test_missing_and_empty_values_do_not_override(self):
        with patch.dict(os.environ, {}, clear=True):
            load_env(self.path)
            self.path.write_text("MODRINTH_TOKEN=\nCURSEFORGE_TOKEN=''\n", encoding="utf-8")
            load_env(self.path)
            self.assertNotIn("MODRINTH_TOKEN", os.environ)

    def test_shell_text_is_not_executed(self):
        self.path.write_text("MODRINTH_TOKEN=$(shell-command)\n", encoding="utf-8")
        with patch.dict(os.environ, {}, clear=True):
            load_env(self.path)
            self.assertEqual(os.environ["MODRINTH_TOKEN"], "$(shell-command)")

    def test_invalid_lines_do_not_disclose_values(self):
        self.path.write_text("OTHER_TOKEN=secret-test-value\n", encoding="utf-8")
        with self.assertRaises(ValueError) as result:
            load_env(self.path)
        self.assertNotIn("secret-test-value", str(result.exception))


class PublishingTests(unittest.TestCase):
    def test_tacz_is_required_on_both_platforms(self):
        item = {"name": "Scope Glint", "version_number": "1.1.0+1.21.1-neoforge", "loader": "neoforge",
                "game_versions": ["1.21.1"], "java": "21", "dependencies": [("tacz", "required")]}
        mr = publish.metadata("modrinth", item, "project", "Fixes", "release")
        self.assertEqual(mr["dependencies"], [{"project_id": "SzzJttH8", "dependency_type": "required"}])
        cf = publish.metadata("curseforge", item, 123, "Fixes", "release")
        self.assertEqual(cf["relations"]["projects"], [{"slug": "timeless-and-classics-zero", "type": "requiredDependency"}])
        self.assertNotIn("Server", cf["gameVersionNames"])

    def test_modrinth_duplicate_identical_bytes_are_skipped(self):
        item = {"sha512": "same", "version_number": "1.1.0+1.20.1-forge"}
        versions = [{"id": "known", "version_number": item["version_number"], "files": [{"hashes": {"sha512": "same"}}]}]
        self.assertEqual(publish.remote_match(item, versions), "known")

    def test_existing_version_with_changed_bytes_is_rejected(self):
        item = {"sha512": "new", "version_number": "1.1.0+1.20.1-forge"}
        versions = [{"id": "known", "version_number": item["version_number"], "files": [{"hashes": {"sha512": "old"}}]}]
        with self.assertRaises(publish.PublishError):
            publish.remote_match(item, versions)

    def test_curseforge_uncertain_upload_is_not_retried(self):
        item = {"filename": "scope.jar", "sha256": "same"}
        key = publish.state_key("curseforge", "123", item)
        with self.assertRaises(publish.PublishError):
            publish.upload(Path("."), [("curseforge", None, "123", [{}], [])], [item],
                           {key: {"sha256": "same", "status": "uncertain"}}, Path("unused.json"))

    def test_minecraft_tag_ignores_other_games_with_same_name(self):
        catalog = [{"id": 11, "name": "1.20.1", "gameVersionTypeID": 1},
                   {"id": 12, "name": "1.20.1", "gameVersionTypeID": 2}]
        types = [{"id": 1, "slug": "bukkit"}, {"id": 2, "slug": "minecraft-1-20"}]
        self.assertEqual(publish.curseforge_tag_id("1.20.1", "minecraft", catalog, types), 12)

    def test_multipart_contains_metadata_and_actual_bytes(self):
        body, content_type = publish.multipart("data", {"name": "Scope Glint"}, "scope.jar", b"PK-test")
        self.assertIn(b'name="data"', body)
        self.assertIn(b'filename="scope.jar"', body)
        self.assertIn(b"PK-test", body)
        self.assertTrue(content_type.startswith("multipart/form-data; boundary="))


if __name__ == "__main__":
    unittest.main()
