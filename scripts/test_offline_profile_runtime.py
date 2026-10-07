#!/usr/bin/env python3
"""Offline checks of evidence rejection. These do not simulate a Paper runtime."""
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

import offline_profile_runtime as subject


class EvidenceTests(unittest.TestCase):
    def candidate(self, phase="candidate-create"):
        return {"phase": phase, "status": "PASS", "events": "3", "patched": "true",
                "history_preserved": "true", "addons_enabled": "45"}

    def control(self):
        return {"phase": "control", "status": "CONTROL_REPRODUCED", "events": "1",
                "patched": "false", "addons_enabled": "45"}

    def good_log(self):
        return "[Server thread/INFO]: Previous clean shutdown: Yes\n"

    def control_log(self):
        return ("[Async/ERROR]: " + subject.EVENT_ERROR + "\njava.lang.NullPointerException\n"
                "at com.balugaq.jeg.core.listeners.GuideHistoryPatchListener.onProfileLoad(GuideHistoryPatchListener.java:39)\n")

    def test_candidate_success_requires_real_callback_evidence(self):
        subject.validate_phase("candidate-create", self.candidate(), self.good_log())

    def test_reload_success(self):
        subject.validate_phase("candidate-reload", self.candidate("candidate-reload"), self.good_log())

    def test_expected_published_control(self):
        subject.validate_phase("control", self.control(), self.control_log())

    def test_missing_callback_is_rejected(self):
        for key, value in [("events", "0"), ("patched", "false"), ("history_preserved", "false"),
                           ("addons_enabled", "44"), ("status", "INCOMPLETE"), ("phase", "control")]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                record = self.candidate()
                record[key] = value
                subject.validate_phase("candidate-create", record, self.good_log())

    def test_candidate_runtime_exception_is_rejected(self):
        with self.assertRaises(ValueError):
            subject.validate_phase("candidate-create", self.candidate(), self.good_log() + self.control_log())

    def test_unrelated_error_is_rejected(self):
        with self.assertRaises(ValueError):
            subject.validate_phase("candidate-create", self.candidate(), self.good_log() + "[Server/ERROR]: Other error\n")

    def test_unknown_phase_is_rejected(self):
        with self.assertRaises(ValueError):
            subject.validate_phase("other", self.candidate(), self.good_log())

    def test_skipped_negative_control_is_rejected(self):
        with self.assertRaises(ValueError):
            subject.validate_phase("control", self.control(), "")

    def test_wrong_control_exception_is_rejected(self):
        with self.assertRaises(ValueError):
            subject.validate_phase("control", self.control(), self.control_log().replace("NullPointerException", "IllegalStateException"))

    def test_duplicate_control_exception_is_rejected(self):
        with self.assertRaises(ValueError):
            subject.validate_phase("control", self.control(), self.control_log() * 2)

    def test_candidate_requires_clean_shutdown(self):
        with self.assertRaises(ValueError):
            subject.validate_phase("candidate-create", self.candidate(), "")

    def test_linkage_errors_always_fail(self):
        with self.assertRaises(ValueError):
            subject.validate_phase("control", self.control(), self.control_log() + "NoSuchMethodError")

    def test_duplicate_fields_fail(self):
        with self.assertRaises(ValueError):
            subject.parse_result("status=FAIL\nstatus=PASS\n")

    def test_result_parser(self):
        self.assertEqual({"status": "FAIL"}, subject.parse_result("status=FAIL\n"))

    def test_candidate_provenance(self):
        with tempfile.TemporaryDirectory() as raw:
            directory = Path(raw)
            jar, meta = directory / "probe.jar", directory / "source.json"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("plugin.yml", "name: JustEnoughGuide\nversion: '2.1.71'\n")
                archive.writestr(subject.LISTENER, b"fixture bytes, not a Java runtime")
            metadata = {"source": "a" * 40, "sha256": subject.checksum(jar)}
            meta.write_text(json.dumps(metadata))
            self.assertEqual("2.1.71", subject.read_candidate(jar, meta, "a" * 40)[0])
            with self.assertRaises(ValueError):
                subject.read_candidate(jar, meta, "b" * 40)
            metadata["sha256"] = "0" * 64
            meta.write_text(json.dumps(metadata))
            with self.assertRaises(ValueError):
                subject.read_candidate(jar, meta, "a" * 40)


if __name__ == "__main__":
    unittest.main()
