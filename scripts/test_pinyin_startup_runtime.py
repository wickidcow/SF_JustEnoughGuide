#!/usr/bin/env python3
"""Acceptance-parser failure fixtures; the native workflow runs the real plugin controls."""
import unittest
from pinyin_startup_runtime import parse_result, validate_phase, validate_source
from wait_release_gates import matching_run


class PinyinEvidenceTest(unittest.TestCase):
    def candidate(self, phase="candidate-off"):
        result = {"phase": phase, "status": "PASS", "jeg_enabled": "true",
                  "pinyin": str(phase == "candidate-on").lower(), "guides_owned": "2",
                  "ordinary_search": "true", "pinyin_search_respects_config": "true",
                  "dictionaries": "20", "class_origin": "candidate", "connected_players": "1",
                  "search_coverage": "connected-player",
                  "shared_chars": "1000"}
        log = f"Done (1s)!\nJEG_PINYIN_PROBE_PASS phase={phase}\nStopping server\n"
        return [phase, result, log, [], True, 0, 0, [], {"status": "PASS", "phase": phase, "server_stopping": "true"}]

    def control(self):
        return ["control", {"phase": "control", "status": "CONTROL_REPRODUCED", "jeg_enabled": "false"},
                "Done (1s)!\nError occurred while enabling JustEnoughGuide v2.1.72\n"
                "Failed to download library\nJEG_OFFLINE_ACQUISITION_BLOCKED\n"
                "JEG_PINYIN_PROBE_CONTROL_REPRODUCED phase=control\nStopping server\n",
                ["https://repo1.maven.org/maven2/com/github/houbb/pinyin/0.4.0/pinyin-0.4.0.jar"], True, 0, None, [],
                {"status": "PASS", "phase": "control", "server_stopping": "true"}]

    def test_complete_native_evidence(self):
        for arguments in (self.control(), self.candidate(), self.candidate("candidate-on")):
            validate_phase(*arguments)

    def test_missing_or_wrong_candidate_claims_are_refused(self):
        for field in self.candidate()[1]:
            for replacement in (None, "incorrect"):
                with self.subTest(field=field, replacement=replacement):
                    arguments = self.candidate()
                    if replacement is None:
                        del arguments[1][field]
                    else:
                        arguments[1][field] = replacement
                    with self.assertRaises((RuntimeError, ValueError)):
                        validate_phase(*arguments)

    def test_candidate_acquisition_is_not_an_allowed_warning(self):
        arguments = self.candidate()
        arguments[3] = self.control()[3]
        with self.assertRaisesRegex(RuntimeError, "acquired"):
            validate_phase(*arguments)

    def test_shared_cache_must_be_real(self):
        arguments = self.candidate()
        arguments[1]["shared_chars"] = "1"
        with self.assertRaisesRegex(RuntimeError, "OpenCC"):
            validate_phase(*arguments)

    def test_milestone_coverage_is_explicit_and_cannot_claim_a_player(self):
        arguments = self.candidate()
        with self.assertRaises(RuntimeError):
            validate_phase(*arguments, minecraft="26.3")
        arguments[1].update(connected_players="0", search_coverage="native-cache-and-api")
        arguments[6] = None
        validate_phase(*arguments, minecraft="26.3")
        with self.assertRaises(RuntimeError):
            validate_phase(*arguments, minecraft="26.2")

    def test_unarmed_cached_or_aborted_run_cannot_pass(self):
        for index, value in ((4, False), (5, 1), (6, 1), (7, ["plugins/JustEnoughGuide/lib/pinyin.jar"])):
            arguments = self.candidate()
            arguments[index] = value
            with self.subTest(index=index), self.assertRaises(RuntimeError):
                validate_phase(*arguments)

    def test_candidate_linkage_error_cannot_hide_behind_pass_marker(self):
        for failure in ("Error occurred while enabling", "Error occurred while disabling", "NoClassDefFoundError",
                        "ClassNotFoundException", "NoSuchMethodError", "ExceptionInInitializerError", "JEG_PINYIN_PROBE_FAIL",
                        "AbstractMethodError", "NoSuchFieldError", "IncompatibleClassChangeError", "UnsupportedClassVersionError",
                        "BootstrapMethodError", "LinkageError", "Could not pass event AsyncProfileLoadEvent",
                        "Encountered an unexpected exception", "The server has not responded for 60 seconds",
                        "Considering it to be crashed", "Task #9 for JustEnoughGuide generated an exception",
                        'Exception in thread "Server thread"'):
            arguments = self.candidate()
            arguments[2] += failure
            with self.subTest(failure=failure), self.assertRaises(RuntimeError):
                validate_phase(*arguments)

    def test_control_requires_disabled_released_plugin_and_real_acquisition(self):
        for index, value in ((3, []), (6, 0)):
            arguments = self.control()
            arguments[index] = value
            with self.assertRaises(RuntimeError):
                validate_phase(*arguments)
        arguments = self.control()
        arguments[1]["jeg_enabled"] = "true"
        with self.assertRaises(RuntimeError):
            validate_phase(*arguments)

    def test_control_unrelated_startup_failure_is_refused(self):
        for marker in ("JEG_OFFLINE_ACQUISITION_BLOCKED", "Failed to download library", "JustEnoughGuide v2.1.72"):
            arguments = self.control()
            arguments[2] = arguments[2].replace(marker, "unrelated failure")
            with self.subTest(marker=marker), self.assertRaises(RuntimeError):
                validate_phase(*arguments)

    def test_missing_lifecycle_or_marker_is_refused(self):
        for marker in ("Done (", "JEG_PINYIN_PROBE_PASS phase=candidate-off"):
            arguments = self.candidate()
            arguments[2] = arguments[2].replace(marker, "")
            with self.subTest(marker=marker), self.assertRaises(RuntimeError):
                validate_phase(*arguments)
        for shutdown in ({}, {"status": "PASS", "phase": "control", "server_stopping": "true"},
                         {"status": "PASS", "phase": "candidate-off", "server_stopping": "false"}):
            arguments = self.candidate()
            arguments[8] = shutdown
            with self.assertRaisesRegex(RuntimeError, "lifecycle"):
                validate_phase(*arguments)

    def test_duplicate_and_malformed_records_are_refused(self):
        for text in ("status=FAIL\nstatus=PASS\n", "PASS\n"):
            with self.assertRaises(RuntimeError):
                parse_result(text)

    def test_release_gate_uses_only_latest_exact_master_push(self):
        head = "a" * 40
        good = {"id": 1, "head_sha": head, "head_branch": "master", "event": "push", "status": "completed", "conclusion": "success"}
        self.assertIsNone(matching_run([{**good, "head_sha": "b" * 40}, {**good, "head_branch": "feature"},
                                        {**good, "event": "pull_request"}], head))
        newest = {**good, "id": 2, "status": "in_progress", "conclusion": None}
        self.assertEqual(newest, matching_run([good, newest], head))

    def test_ci_artifact_source_must_match_checkout_and_workflow(self):
        head = "a" * 40
        source = {"source_commit": head, "working_tree_clean": True}
        validate_source(source, head, head, False)
        for metadata, checkout, workflow, dirty in (({**source, "source_commit": "b" * 40}, head, head, False),
                (source, head, "b" * 40, False), ({**source, "working_tree_clean": False}, head, head, False),
                (source, head, head, True)):
            with self.assertRaises(RuntimeError):
                validate_source(metadata, checkout, workflow, dirty)

    def test_local_dirty_proof_is_explicit(self):
        head = "a" * 40
        source = {"source_commit": head, "working_tree_clean": False}
        validate_source(source, head, None, True)
        with self.assertRaises(RuntimeError):
            validate_source(source, head, None, False)


if __name__ == "__main__":
    unittest.main()
