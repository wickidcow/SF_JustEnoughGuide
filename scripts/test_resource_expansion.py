#!/usr/bin/env python3
"""Negative controls for byte-exact generated-resource verification."""
from pathlib import Path
import tempfile
import unittest

from scripts.verify_resource_expansion import verify, with_project_version


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


class BaselineVersionInputTest(unittest.TestCase):
    def test_version_bump_preserves_all_other_baseline_bytes(self):
        baseline = 'plugins { java }\nversion = "2.1.74"\nexpand(project.properties)\n'
        expected = baseline.replace('"2.1.74"', '"2.1.75"')
        self.assertEqual(with_project_version(baseline, "2.1.75"), expected)

    def test_same_version_and_qualified_versions_are_supported(self):
        baseline = 'version = "2.1.74"\n'
        self.assertEqual(with_project_version(baseline, "2.1.74"), baseline)
        self.assertEqual(with_project_version(baseline, "2.1.75-SNAPSHOT+test.1"),
                         'version = "2.1.75-SNAPSHOT+test.1"\n')

    def test_missing_or_duplicate_baseline_version_fails(self):
        for baseline in ('plugins { java }\n', 'version = "1"\nversion = "2"\n'):
            with self.subTest(baseline=baseline), self.assertRaisesRegex(ValueError, "exactly one"):
                with_project_version(baseline, "2.1.75")

    def test_unsafe_version_cannot_inject_build_script(self):
        for version in ("", '2.1.75"', "2.1.75\n", "${version}", "a\\b", "a b"):
            with self.subTest(version=version), self.assertRaisesRegex(ValueError, "Unsafe"):
                with_project_version('version = "2.1.74"\n', version)


if __name__ == "__main__":
    unittest.main()
