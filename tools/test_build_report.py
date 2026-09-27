#!/usr/bin/env python3
"""Tests for build_report.py: the evidence checks that let SpecTrace distrust its own agents.

Run from the repo root:  python3 -m unittest discover -s tools -v
"""
import json
import os
import subprocess
import sys
import tempfile
import textwrap
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_report  # noqa: E402

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "build_report.py")
PKG = "com.example"


def git(cwd, *args):
    subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True,
                   env={**os.environ, "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t",
                        "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t"})


class Fixture:
    """A throwaway repo with an app, requirements, trace claims and Surefire results."""

    def __init__(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        self.app = os.path.join(self.root, "app")
        self.out = os.path.join(self.root, "out")
        self.tests = os.path.join(self.app, "src/test/java/com/example")
        self.reports = os.path.join(self.app, "target/surefire-reports")
        for d in (self.out, self.tests, self.reports):
            os.makedirs(d)
        git(self.root, "init", "-q")

    def requirements(self, *ids):
        self._json("requirements.json", {"document": "DOC-1", "version": "1.0",
                                          "requirements": [{"id": i, "title": i} for i in ids]})

    def claims(self, module, *entries):
        self._json(f"trace-{module}.json", {"module": module, "requirements": list(entries)})

    def run_info(self, **info):
        self._json("run.json", info)

    def test_class(self, name, methods):
        """methods: {method_name: [tags]}"""
        body = "".join(
            "".join(f'    @Tag("{t}")\n' for t in tags) + f"    @Test\n    void {m}() {{}}\n\n"
            for m, tags in methods.items())
        with open(os.path.join(self.tests, f"{name}.java"), "w") as f:
            f.write(f"package {PKG};\n\nclass {name} {{\n{body}}}\n")

    def results(self, name, outcomes):
        """outcomes: {method_name: 'pass' | 'fail'}"""
        cases = "".join(
            f'<testcase classname="{PKG}.{name}" name="{m}">'
            + ('<failure message="expected: 754.90 but was: 754.89"/>' if o == "fail" else "")
            + "</testcase>" for m, o in outcomes.items())
        with open(os.path.join(self.reports, f"TEST-{PKG}.{name}.xml"), "w") as f:
            f.write(f'<testsuite name="{PKG}.{name}">{cases}</testsuite>')

    def commit_and_tag(self, tag=None):
        git(self.root, "add", "-A")
        git(self.root, "commit", "-q", "-m", "snapshot", "--allow-empty")
        if tag:
            git(self.root, "tag", tag)

    def build(self, baseline=None):
        ref = build_report.resolve_baseline(self.app, baseline)
        rows, metrics, _ = build_report.build(self.out, self.app, ref, "target")
        return {r["id"]: r for r in rows}, metrics

    def cli(self, *args):
        return subprocess.run([sys.executable, SCRIPT, "--out", self.out, "--app", self.app, *args],
                              capture_output=True, text=True)

    def _json(self, name, data):
        with open(os.path.join(self.out, name), "w") as f:
            json.dump(data, f)


def claim(rid, status, *methods, cls="ReqTest"):
    return {"id": rid, "status": status,
            "tests": [{"class": f"{PKG}.{cls}", "method": m, "new": True} for m in methods]}


class StatusFromEvidence(unittest.TestCase):

    def setUp(self):
        self.f = Fixture()
        self.addCleanup(self.f.tmp.cleanup)

    def test_agent_claiming_covered_is_overruled_by_a_failing_test(self):
        self.f.requirements("REQ-A-01")
        self.f.test_class("ReqTest", {"rounds": ["REQ-A-01"]})
        self.f.results("ReqTest", {"rounds": "fail"})
        self.f.claims("a", claim("REQ-A-01", "COVERED", "rounds"))

        rows, metrics = self.f.build()

        self.assertEqual(rows["REQ-A-01"]["status"], "FAILING")
        self.assertEqual(metrics["agent_disagreements"], ["REQ-A-01"])
        self.assertIn("754.89", rows["REQ-A-01"]["tests"][0]["message"])

    def test_tagged_test_is_found_even_if_the_agent_forgot_to_list_it(self):
        self.f.requirements("REQ-A-01")
        self.f.test_class("ReqTest", {"t1": ["REQ-A-01"]})
        self.f.results("ReqTest", {"t1": "pass"})
        self.f.claims("a", {"id": "REQ-A-01", "status": "NEW_TEST_PASS", "tests": []})

        rows, _ = self.f.build()

        self.assertEqual([t["method"] for t in rows["REQ-A-01"]["tests"]], ["t1"])

    def test_listed_but_untagged_test_is_flagged(self):
        self.f.requirements("REQ-A-01")
        self.f.test_class("ReqTest", {"t1": []})
        self.f.results("ReqTest", {"t1": "pass"})
        self.f.claims("a", claim("REQ-A-01", "NEW_TEST_PASS", "t1"))

        rows, _ = self.f.build()

        self.assertFalse(rows["REQ-A-01"]["tests"][0]["tagged"])

    def test_listed_test_that_never_ran_is_unverified(self):
        self.f.requirements("REQ-A-01")
        self.f.test_class("ReqTest", {"t1": ["REQ-A-01"]})
        self.f.claims("a", claim("REQ-A-01", "NEW_TEST_PASS", "t1"))

        rows, _ = self.f.build()

        self.assertEqual(rows["REQ-A-01"]["status"], "UNVERIFIED")

    def test_requirement_without_tests_is_untested_unless_agent_says_not_implemented(self):
        self.f.requirements("REQ-A-01", "REQ-A-02")
        self.f.claims("a", {"id": "REQ-A-01", "status": "NOT_IMPLEMENTED", "tests": []})

        rows, _ = self.f.build()

        self.assertEqual(rows["REQ-A-01"]["status"], "NOT_IMPLEMENTED")
        self.assertEqual(rows["REQ-A-02"]["status"], "UNTESTED")

    def test_agent_claiming_not_implemented_with_a_passing_test_is_a_disagreement(self):
        self.f.requirements("REQ-A-01")
        self.f.test_class("ReqTest", {"t1": ["REQ-A-01"]})
        self.f.results("ReqTest", {"t1": "pass"})
        self.f.claims("a", {"id": "REQ-A-01", "status": "NOT_IMPLEMENTED", "tests": []})

        rows, metrics = self.f.build()

        self.assertNotEqual(rows["REQ-A-01"]["status"], "NOT_IMPLEMENTED")
        self.assertEqual(metrics["agent_disagreements"], ["REQ-A-01"])


class NewVersusExistingTests(unittest.TestCase):

    def setUp(self):
        self.f = Fixture()
        self.addCleanup(self.f.tmp.cleanup)
        self.f.requirements("REQ-A-01", "REQ-A-02")
        self.f.test_class("OldTest", {"old": ["REQ-A-01"]})
        self.f.commit_and_tag("spectrace-baseline")
        self.f.test_class("ReqTest", {"added": ["REQ-A-02"]})
        self.f.results("OldTest", {"old": "pass"})
        self.f.results("ReqTest", {"added": "pass"})

    def test_existing_test_is_covered_and_bob_test_is_new(self):
        rows, metrics = self.f.build()

        self.assertEqual(rows["REQ-A-01"]["status"], "COVERED")
        self.assertEqual(rows["REQ-A-02"]["status"], "NEW_TEST_PASS")
        self.assertEqual(metrics["tests_added"], 1)

    def test_committing_bob_tests_does_not_make_them_look_pre_existing(self):
        # Regression: comparing against HEAD turned all of Bob's tests into "Covered" once committed.
        self.f.commit_and_tag()

        rows, _ = self.f.build()

        self.assertEqual(rows["REQ-A-02"]["status"], "NEW_TEST_PASS")

    def test_without_the_tag_head_is_the_baseline(self):
        git(self.f.root, "tag", "-d", "spectrace-baseline")
        self.f.commit_and_tag()

        rows, _ = self.f.build()

        self.assertEqual(rows["REQ-A-02"]["status"], "COVERED")


class TimingAndGate(unittest.TestCase):

    def setUp(self):
        self.f = Fixture()
        self.addCleanup(self.f.tmp.cleanup)
        self.f.requirements("REQ-A-01", "REQ-A-02")
        self.f.test_class("ReqTest", {"t1": ["REQ-A-01"]})
        self.f.results("ReqTest", {"t1": "fail"})
        self.f.claims("a", claim("REQ-A-01", "NEW_TEST_PASS", "t1"),
                      {"id": "REQ-A-02", "status": "NOT_IMPLEMENTED", "tests": []})

    def test_replay_reports_no_timing_instead_of_a_fake_one(self):
        self.f.run_info(mode="replay")

        _, metrics = self.f.build()

        self.assertEqual(metrics["timing"], "replay")
        self.assertIsNone(metrics["spectrace_minutes"])
        self.assertIsNone(metrics["time_saved_minutes"])

    def test_recorded_run_keeps_its_measured_time(self):
        self.f.run_info(started_at="2020-01-01T00:00:00Z", elapsed_minutes=12.5)

        _, metrics = self.f.build()

        self.assertEqual((metrics["timing"], metrics["spectrace_minutes"]), ("recorded", 12.5))

    def test_live_timing_is_frozen_at_the_first_report(self):
        self.f.run_info(started_at="2020-01-01T00:00:00Z")
        _, first = self.f.build()
        with open(os.path.join(self.f.out, "run.json")) as fh:
            finished = json.load(fh)["finished_at"]

        _, second = self.f.build()

        self.assertEqual(first["spectrace_minutes"], second["spectrace_minutes"])
        self.assertTrue(finished)

    def test_fail_on_sets_exit_code(self):
        self.assertEqual(self.f.cli().returncode, 0)
        gated = self.f.cli("--fail-on", "FAILING")
        self.assertEqual(gated.returncode, 1)
        self.assertIn("GATE FAILED: REQ-A-01", gated.stdout)
        self.assertEqual(self.f.cli("--fail-on", "COVERED").returncode, 0)

    def test_fail_on_disagreement(self):
        result = self.f.cli("--fail-on-disagreement")
        self.assertEqual(result.returncode, 1)
        self.assertIn("REQ-A-01 (disagreement)", result.stdout)

    def test_unknown_gate_status_is_rejected(self):
        result = self.f.cli("--fail-on", "BROKEN")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("unknown status", result.stderr)


class ReferenceCheck(unittest.TestCase):
    """Subagents cite file:line for each implementation; stale or invented locations are flagged."""

    SOURCE = textwrap.dedent("""\
        package com.example;

        public class Svc {
            private final String pinHash;

            public synchronized Transfer transfer(String from, String to) {
                if (from.equals(to)) {
                    throw new IllegalArgumentException();
                }
                return null;
            }

            private static String nextReference() {
                return "TRF-1";
            }
        }
        """)

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.path = os.path.join(tmp.name, "Svc.java")
        with open(self.path, "w") as f:
            f.write(self.SOURCE)

    def check(self, symbol, line):
        return build_report.check_reference({"file": self.path, "symbol": symbol, "line": line})

    def test_declaration_body_and_field_lines_are_accepted(self):
        self.assertIsNone(self.check("Svc#transfer", 6))
        self.assertIsNone(self.check("Svc#transfer", 8))
        self.assertIsNone(self.check("Svc#pinHash", 4))
        self.assertIsNone(self.check("Svc#nextReference", 14))

    def test_line_inside_another_method_is_flagged(self):
        self.assertIn("inside nextReference(), not transfer", self.check("Svc#transfer", 14))

    def test_line_past_end_of_file_is_flagged(self):
        self.assertIn("outside", self.check("Svc#transfer", 400))

    def test_missing_file_is_flagged(self):
        self.assertIn("does not exist",
                      build_report.check_reference({"file": self.path + ".nope", "symbol": "Svc#x", "line": 1}))

    def test_bad_reference_fails_the_disagreement_gate(self):
        f = Fixture()
        self.addCleanup(f.tmp.cleanup)
        f.requirements("REQ-A-01")
        f.test_class("ReqTest", {"t1": ["REQ-A-01"]})
        f.results("ReqTest", {"t1": "pass"})
        entry = claim("REQ-A-01", "NEW_TEST_PASS", "t1")
        entry["implementation"] = [{"file": self.path, "symbol": "Svc#transfer", "line": 14}]
        f.claims("a", entry)

        result = f.cli("--fail-on-disagreement")

        self.assertEqual(result.returncode, 1)
        self.assertIn("REQ-A-01 (bad reference)", result.stdout)


class ProjectConfig(unittest.TestCase):

    def test_repo_config_parses_values_without_inline_comments(self):
        # Regression: a hand-rolled fallback parser read `app_dir: bank-app   # comment` as the whole line.
        root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        cfg = build_report.load_config(os.path.join(root, "spectrace.yaml"))

        self.assertEqual(cfg["app_dir"], "bank-app")
        self.assertEqual([m["name"] for m in cfg["modules"]], ["accounts", "transfers", "loans", "security"])
        self.assertEqual(cfg["result_format"], "surefire")


class TagScanner(unittest.TestCase):

    def test_multiple_tags_and_parameterized_tests(self):
        src = textwrap.dedent("""
            package com.example;
            class T {
                @Test
                @Tag("REQ-A-01") @Tag("REQ-A-02")
                void both() {}

                void helper() {}

                @ParameterizedTest
                @Tag("REQ-B-01")
                public void param(int x) {}
            }
        """)
        found = build_report.scan_tags(src)
        self.assertEqual(found["REQ-A-01"], [("com.example.T", "both")])
        self.assertEqual(found["REQ-A-02"], [("com.example.T", "both")])
        self.assertEqual(found["REQ-B-01"], [("com.example.T", "param")])
        self.assertNotIn("helper", str(found))


if __name__ == "__main__":
    unittest.main()
