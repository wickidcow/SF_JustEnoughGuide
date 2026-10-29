#!/usr/bin/env python3
"""Negative controls for byte-exact generated-resource verification."""
from pathlib import Path
import tempfile
import unittest

from scripts.verify_resource_expansion import verify


class ResourceExpansionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.before = Path(self.temp.name) / "before"
        self.after = Path(self.temp.name) / "after"
        for root in (self.before, self.after):
            root.mkdir()
            (root / "plugin.yml").write_bytes(b"name: JustEnoughGuide\nversion: 2.1.74\n")
            (root / "config.yml").write_bytes(b"guide-layout: unchanged\n")
            (root / "dictionary.bin").write_bytes(bytes(range(256)))

    def check(self):
        return verify(self.before, self.after, "2.1.74")

    def test_equal_text_and_binary_outputs_pass(self):
        self.assertEqual(len(self.check()), 3)

    def test_changed_configuration_bytes_fail(self):
        (self.after / "config.yml").write_bytes(b"guide-layout: changed\n")
        with self.assertRaisesRegex(ValueError, "bytes differ"):
            self.check()

    def test_missing_resource_fails(self):
        (self.after / "dictionary.bin").unlink()
        with self.assertRaisesRegex(ValueError, "names differ"):
            self.check()

    def test_added_resource_fails(self):
        (self.after / "unexpected.yml").write_text("unexpected: true\n")
        with self.assertRaisesRegex(ValueError, "names differ"):
            self.check()

    def test_identical_unexpanded_or_wrong_versions_fail(self):
        for version in ("${version}", "2.1.75", ""):
            for root in (self.before, self.after):
                (root / "plugin.yml").write_text(f"version: {version}\n")
            with self.subTest(version=version), self.assertRaisesRegex(ValueError, "not expanded"):
                self.check()

    def test_missing_or_duplicate_plugin_version_fails(self):
        for content in ("name: JustEnoughGuide\n", "version: 2.1.74\nversion: 2.1.74\n"):
            for root in (self.before, self.after):
                (root / "plugin.yml").write_text(content)
            with self.subTest(content=content), self.assertRaisesRegex(ValueError, "not expanded"):
                self.check()

    def test_empty_or_missing_outputs_fail(self):
        for root in (self.before, self.after):
            for child in root.iterdir():
                child.unlink()
        with self.assertRaisesRegex(ValueError, "Empty"):
            self.check()
        self.before.rmdir()
        with self.assertRaisesRegex(ValueError, "Missing"):
            self.check()

    def test_symlink_resource_fails(self):
        (self.after / "linked.yml").symlink_to(self.before / "config.yml")
        with self.assertRaisesRegex(ValueError, "Symlink"):
            self.check()


if __name__ == "__main__":
    unittest.main()
