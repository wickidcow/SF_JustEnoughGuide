#!/usr/bin/env python3
"""Synthetic ZIP/header tests; actual class linkage is tested separately on Java/Paper."""
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path
from verify_guide_runtime_jar import REQUIRED, PREFIX, verify

HEADER = b'\xca\xfe\xba\xbe\x00\x00\x00\x41'

class GuideJarTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.jar = self.root / 'plugin.jar'
    def write(self, missing=None, additions=None):
        with zipfile.ZipFile(self.jar, 'w') as jar:
            for name in sorted(REQUIRED - set(missing or [])):
                jar.writestr(name, HEADER)
            jar.writestr('plugin.yml', "name: JustEnoughGuide\nversion: '2.1.70'\n")
            for name, data in (additions or {}).items():
                jar.writestr(name, data)
    def test_complete_archive(self):
        self.write()
        self.assertEqual(len(REQUIRED), verify(self.jar, version='2.1.70'))
    def test_missing_reported_class(self):
        name = PREFIX + 'utils/clickhandler/OnDisplay$ItemGroup.class'
        self.write(missing=[name])
        with self.assertRaisesRegex(ValueError, 'OnDisplay\\$ItemGroup'):
            verify(self.jar)
    def test_missing_other_compiled_nested_class(self):
        self.write()
        classes = self.root / 'classes'
        path = classes / (PREFIX + 'Additional$Nested.class')
        path.parent.mkdir(parents=True)
        path.write_bytes(HEADER)
        with self.assertRaisesRegex(ValueError, 'Additional'):
            verify(self.jar, classes)
    def test_empty_compilation_refused(self):
        self.write()
        with self.assertRaisesRegex(ValueError, 'No compiled'):
            verify(self.jar, self.root / 'missing')
    def test_bad_archive(self):
        self.jar.write_bytes(b'not a jar')
        with self.assertRaises(zipfile.BadZipFile):
            verify(self.jar)
    def test_duplicate_entries(self):
        self.write()
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(self.jar, 'a') as jar:
                jar.writestr('plugin.yml', 'duplicate')
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            verify(self.jar)
    def test_version_mismatch(self):
        self.write()
        with self.assertRaisesRegex(ValueError, 'version'):
            verify(self.jar, version='9.0')
    def test_runtime_ceiling_preview_and_invalid_headers(self):
        for data in (b'bad', HEADER[:6] + b'\x00\x45', HEADER[:4] + b'\xff\xff\x00\x41'):
            with self.subTest(data=data):
                self.write(additions={PREFIX + 'Extra.class': data})
                with self.assertRaises(ValueError): verify(self.jar)
    def test_test_libraries_not_shipped(self):
        for name in ('org/junit/Test.class', 'org/mockbukkit/Test.class', 'net/byteflux/libby/Manager.class', 'audit/Probe.class'):
            with self.subTest(name=name):
                self.write(additions={name: HEADER})
                with self.assertRaisesRegex(ValueError, 'Unexpected'): verify(self.jar)

if __name__ == '__main__': unittest.main()
