"""변경 감지형 Codex Stop hook runner 테스트."""

import io
import json
import os
import subprocess
import sys
from pathlib import Path, PurePosixPath
from unittest.mock import MagicMock

import pytest

sys.path.insert(0, str(Path(__file__).parent))
import stop_hook as hook


def invoke_main(monkeypatch, tmp_path, changes, results=None, event=None):
    stdout = io.StringIO()
    run_validation = MagicMock(
        side_effect=results
        or [hook.ValidationResult(success=True, output="", failure_reason="")]
    )
    monkeypatch.setattr(hook, "discover_repo_root", lambda: tmp_path)
    monkeypatch.setattr(hook, "collect_changes", lambda _root: changes)
    monkeypatch.setattr(hook, "discover_head_revision", lambda _root: "test-head")
    monkeypatch.setattr(
        hook,
        "build_suite_command",
        lambda _root, suite, turn_id: ([f"mock-{suite}"], hook.SUITE_TIMEOUTS[suite]),
    )
    monkeypatch.setattr(hook, "run_validation", run_validation)

    return_code = hook.main(
        stdin=io.StringIO(json.dumps(event or {})),
        stdout=stdout,
        environ={},
    )
    return return_code, stdout.getvalue(), run_validation


class TestEarlyExit:
    def test_harness_child_skips_before_json_parsing(self, monkeypatch):
        stdout = io.StringIO()
        parse = MagicMock(side_effect=AssertionError("JSON must not be parsed"))
        monkeypatch.setattr(hook.json, "loads", parse)

        return_code = hook.main(
            stdin=io.StringIO(""),
            stdout=stdout,
            environ={"TODO_QUEST_HARNESS_CHILD": "1"},
        )

        assert return_code == 0
        assert stdout.getvalue() == ""
        parse.assert_not_called()

    def test_plan_mode_skips_without_discovering_repository(self, monkeypatch):
        stdout = io.StringIO()
        discover = MagicMock(side_effect=AssertionError("git must not run"))
        monkeypatch.setattr(hook, "discover_repo_root", discover)

        return_code = hook.main(
            stdin=io.StringIO('{"permission_mode":"plan"}'),
            stdout=stdout,
            environ={},
        )

        assert return_code == 0
        assert stdout.getvalue() == ""
        discover.assert_not_called()

    def test_clean_repository_skips_validation(self, monkeypatch, tmp_path):
        return_code, stdout, run_validation = invoke_main(monkeypatch, tmp_path, [])

        assert return_code == 0
        assert stdout == ""
        run_validation.assert_not_called()

    def test_unrelated_changes_skip_validation(self, monkeypatch, tmp_path):
        (tmp_path / "notes.txt").write_text("unrelated", encoding="utf-8")
        changes = [hook.ChangedPath("notes.txt", " M")]

        return_code, stdout, run_validation = invoke_main(
            monkeypatch, tmp_path, changes
        )

        assert return_code == 0
        assert stdout == ""
        run_validation.assert_not_called()


class TestChangeCollection:
    def test_porcelain_parser_includes_rename_and_delete_markers(self):
        data = b"R  scripts/new.py\0scripts/old.py\0 D app/Gone.kt\0?? app/New.kt\0"

        changes = hook.parse_porcelain_status(data)

        assert hook.ChangedPath("scripts/new.py", "R :new") in changes
        assert hook.ChangedPath("scripts/old.py", "R :old") in changes
        assert hook.ChangedPath("app/Gone.kt", " D") in changes
        assert hook.ChangedPath("app/New.kt", "??") in changes

    def test_collect_changes_uses_one_git_status_invocation(self, monkeypatch, tmp_path):
        completed = subprocess.CompletedProcess(
            args=[], returncode=0, stdout=b" M scripts/execute.py\0", stderr=b""
        )
        run = MagicMock(return_value=completed)
        monkeypatch.setattr(hook.subprocess, "run", run)

        changes = hook.collect_changes(tmp_path)

        assert changes == [hook.ChangedPath("scripts/execute.py", " M")]
        assert run.call_count == 1
        command = run.call_args.args[0]
        assert command == [
            "git",
            "status",
            "--porcelain=v1",
            "-z",
            "--untracked-files=all",
        ]
        assert run.call_args.kwargs["cwd"] == tmp_path


@pytest.mark.parametrize(
    ("path", "expected"),
    [
        ("scripts/stop_hook.py", ("harness",)),
        ("scripts/phase_manager.py", ("harness",)),
        ("scripts/run_phase_manager.ps1", ("harness",)),
        ("scripts/test_phase_manager.py", ("harness",)),
        ("phases/taxonomy.json", ("harness",)),
        (".agents/skills/harness/SKILL.md", ("harness",)),
        ("docs/DEVELOPMENT.md", ("harness",)),
        ("phases/index.json", ()),
        ("phases/README.md", ()),
        ("app/src/main/java/com/todoquest/App.kt", ("android",)),
        ("settings.gradle.kts", ("android",)),
        ("feature/src/main/AndroidManifest.xml", ("android",)),
        ("feature/src/main/res/values/strings.xml", ("android",)),
        ("feature/src/test/kotlin/RuleTest.kt", ("android",)),
        ("README.md", ()),
    ],
)
def test_classify_changes(path, expected):
    assert hook.classify_changes([hook.ChangedPath(path, " M")]) == expected


def test_classify_changes_selects_both_suites_in_order():
    changes = [
        hook.ChangedPath("app/src/main/java/App.kt", " M"),
        hook.ChangedPath("scripts/test_execute.py", " M"),
    ]

    assert hook.classify_changes(changes) == ("harness", "android")


@pytest.mark.parametrize(
    ("files", "expected_suites"),
    [
        ({"scripts/stop_hook.py": "hook"}, ["harness"]),
        ({"app/src/main/java/App.kt": "android"}, ["android"]),
        (
            {
                "scripts/test_execute.py": "harness",
                "app/src/main/java/App.kt": "android",
            },
            ["harness", "android"],
        ),
    ],
)
def test_main_runs_only_selected_suites_in_order(
    monkeypatch, tmp_path, files, expected_suites
):
    changes = []
    for relative_path, content in files.items():
        path = tmp_path.joinpath(*PurePosixPath(relative_path).parts)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        changes.append(hook.ChangedPath(relative_path, " M"))
    successes = [
        hook.ValidationResult(success=True, output="", failure_reason="")
        for _ in expected_suites
    ]

    _, stdout, run_validation = invoke_main(
        monkeypatch, tmp_path, changes, results=successes
    )

    assert stdout == ""
    assert [call.args[1] for call in run_validation.call_args_list] == expected_suites


class TestFingerprintCache:
    def test_success_fingerprint_is_reused(self, monkeypatch, tmp_path):
        path = tmp_path / "scripts" / "stop_hook.py"
        path.parent.mkdir()
        path.write_text("same", encoding="utf-8")
        changes = [hook.ChangedPath("scripts/stop_hook.py", " M")]

        first = invoke_main(monkeypatch, tmp_path, changes)
        second = invoke_main(monkeypatch, tmp_path, changes)

        assert first[1] == second[1] == ""
        assert first[2].call_count == 1
        second[2].assert_not_called()

    def test_content_change_at_same_path_runs_again(self, monkeypatch, tmp_path):
        path = tmp_path / "scripts" / "stop_hook.py"
        path.parent.mkdir()
        path.write_text("first", encoding="utf-8")
        changes = [hook.ChangedPath("scripts/stop_hook.py", " M")]

        invoke_main(monkeypatch, tmp_path, changes)
        path.write_text("second", encoding="utf-8")
        _, _, second_run = invoke_main(monkeypatch, tmp_path, changes)

        assert second_run.call_count == 1

    def test_repository_revision_changes_fingerprint(self, tmp_path):
        path = tmp_path / "scripts" / "stop_hook.py"
        path.parent.mkdir()
        path.write_text("same change", encoding="utf-8")
        changes = [hook.ChangedPath("scripts/stop_hook.py", " M")]
        command = hook.suite_command_signature("harness")

        first = hook.fingerprint_suite(
            tmp_path, "harness", changes, command, "head-one"
        )
        second = hook.fingerprint_suite(
            tmp_path, "harness", changes, command, "head-two"
        )

        assert first != second

    def test_phase_manager_contract_content_changes_harness_fingerprint(self, tmp_path):
        path = tmp_path / "phases" / "taxonomy.json"
        path.parent.mkdir()
        path.write_text('{"version": 1}', encoding="utf-8")
        changes = [hook.ChangedPath("phases/taxonomy.json", " M")]
        command = hook.suite_command_signature("harness")

        first = hook.fingerprint_suite(
            tmp_path, "harness", changes, command, "same-head"
        )
        path.write_text('{"version": 2}', encoding="utf-8")
        second = hook.fingerprint_suite(
            tmp_path, "harness", changes, command, "same-head"
        )

        assert first != second

    def test_phase_manager_test_in_signature_changes_harness_fingerprint(self, tmp_path):
        path = tmp_path / "scripts" / "stop_hook.py"
        path.parent.mkdir()
        path.write_text("same change", encoding="utf-8")
        changes = [hook.ChangedPath("scripts/stop_hook.py", " M")]
        current = hook.suite_command_signature("harness")
        legacy = [item for item in current if item != "scripts/test_phase_manager.py"]

        assert hook.fingerprint_suite(
            tmp_path, "harness", changes, legacy, "same-head"
        ) != hook.fingerprint_suite(
            tmp_path, "harness", changes, current, "same-head"
        )

    def test_suite_fingerprints_are_reused_independently(self, monkeypatch, tmp_path):
        harness_path = tmp_path / "scripts" / "stop_hook.py"
        android_path = tmp_path / "app" / "src" / "main" / "java" / "App.kt"
        harness_path.parent.mkdir(parents=True)
        android_path.parent.mkdir(parents=True)
        harness_path.write_text("harness", encoding="utf-8")
        android_path.write_text("android-v1", encoding="utf-8")
        changes = [
            hook.ChangedPath("scripts/stop_hook.py", " M"),
            hook.ChangedPath("app/src/main/java/App.kt", " M"),
        ]
        successes = [hook.ValidationResult(True, "", "")] * 2

        invoke_main(monkeypatch, tmp_path, changes, results=successes)
        android_path.write_text("android-v2", encoding="utf-8")
        _, _, second_run = invoke_main(
            monkeypatch,
            tmp_path,
            changes,
            results=[hook.ValidationResult(True, "", "")],
        )

        assert [call.args[1] for call in second_run.call_args_list] == ["android"]

    def test_deleted_file_marker_changes_fingerprint(self, tmp_path):
        path = tmp_path / "scripts" / "execute.py"
        path.parent.mkdir()
        path.write_bytes(b"present")
        changes = [hook.ChangedPath("scripts/execute.py", " D")]
        command = ["python", "-m", "pytest"]

        present = hook.fingerprint_suite(tmp_path, "harness", changes, command)
        path.unlink()
        deleted = hook.fingerprint_suite(tmp_path, "harness", changes, command)

        assert present != deleted

    def test_failed_validation_is_not_cached(self, monkeypatch, tmp_path):
        path = tmp_path / "scripts" / "stop_hook.py"
        path.parent.mkdir()
        path.write_text("change", encoding="utf-8")
        changes = [hook.ChangedPath("scripts/stop_hook.py", " M")]
        failed = hook.ValidationResult(False, "failure output", "pytest failed")

        _, stdout, run_validation = invoke_main(
            monkeypatch, tmp_path, changes, results=[failed]
        )

        payload = json.loads(stdout)
        assert payload["continue"] is False
        assert run_validation.call_count == 1
        cache = hook.load_cache(tmp_path)
        assert "harness" not in cache["success"]

    def test_cache_write_is_atomic(self, tmp_path):
        cache = {"schema_version": hook.CACHE_SCHEMA_VERSION, "success": {"harness": "abc"}}

        hook.write_cache(tmp_path, cache)

        assert hook.cache_path(tmp_path).read_text(encoding="utf-8")
        leftovers = list(hook.cache_path(tmp_path).parent.glob("cache.json.tmp-*"))
        assert leftovers == []


class TestFailureContract:
    def test_invalid_event_outputs_only_continuation_json(self):
        stdout = io.StringIO()

        return_code = hook.main(
            stdin=io.StringIO("not-json"), stdout=stdout, environ={}
        )

        payload = json.loads(stdout.getvalue())
        assert return_code == 0
        assert payload["continue"] is False
        assert payload["systemMessage"] == "Relevant project validation failed"
        assert set(payload) == {"continue", "stopReason", "systemMessage"}

    def test_missing_required_tool_outputs_only_continuation_json(
        self, monkeypatch, tmp_path
    ):
        path = tmp_path / "scripts" / "stop_hook.py"
        path.parent.mkdir()
        path.write_text("change", encoding="utf-8")
        monkeypatch.setattr(hook, "discover_repo_root", lambda: tmp_path)
        monkeypatch.setattr(
            hook,
            "collect_changes",
            lambda _root: [hook.ChangedPath("scripts/stop_hook.py", " M")],
        )
        monkeypatch.setattr(hook, "discover_head_revision", lambda _root: "test-head")
        monkeypatch.setattr(
            hook,
            "build_suite_command",
            MagicMock(side_effect=hook.RequiredToolMissing("missing .venv Python")),
        )
        stdout = io.StringIO()

        return_code = hook.main(
            stdin=io.StringIO("{}"), stdout=stdout, environ={}
        )

        payload = json.loads(stdout.getvalue())
        assert return_code == 0
        assert payload == {
            "continue": False,
            "stopReason": "missing .venv Python",
            "systemMessage": "Relevant project validation failed",
        }

    def test_stop_reason_contains_only_limited_output_tail(self, tmp_path, monkeypatch):
        output = "prefix" + ("x" * (hook.STOP_REASON_TAIL_LIMIT + 500)) + "tail"
        run = MagicMock(
            return_value=subprocess.CompletedProcess([], 1, stdout=output.encode(), stderr=None)
        )
        monkeypatch.setattr(hook.subprocess, "run", run)

        result = hook.run_validation(
            tmp_path, "harness", ["mock-pytest"], timeout=120
        )

        assert result.success is False
        assert result.failure_reason.endswith("tail")
        assert "prefix" not in result.failure_reason
        assert "build/codex-stop-hook/latest-harness.log" in result.failure_reason
        assert len(result.failure_reason) <= hook.STOP_REASON_TAIL_LIMIT

    def test_timeout_is_logged_without_traceback(self, tmp_path, monkeypatch):
        timeout = subprocess.TimeoutExpired(
            cmd=["mock-pytest"], timeout=120, output=b"partial output"
        )
        monkeypatch.setattr(hook.subprocess, "run", MagicMock(side_effect=timeout))

        result = hook.run_validation(
            tmp_path, "harness", ["mock-pytest"], timeout=120
        )

        log = (tmp_path / "build" / "codex-stop-hook" / "latest-harness.log")
        assert result.success is False
        assert "timed out" in result.failure_reason
        assert "partial output" in log.read_text(encoding="utf-8")
        assert "Traceback" not in result.failure_reason


class TestCommands:
    def test_harness_signature_orders_all_three_test_modules_before_basetemp(self):
        signature = hook.suite_command_signature("harness")

        assert signature[3:6] == [
            "scripts/test_stop_hook.py",
            "scripts/test_execute.py",
            "scripts/test_phase_manager.py",
        ]
        assert signature[6:] == [
            "--basetemp",
            "build/codex-stop-hook/pytest-<turn>",
        ]

    def test_harness_command_uses_project_venv_and_turn_basetemp(self, tmp_path):
        python = tmp_path / ".venv" / ("Scripts" if os.name == "nt" else "bin") / (
            "python.exe" if os.name == "nt" else "python"
        )
        python.parent.mkdir(parents=True)
        python.write_bytes(b"")

        command, timeout = hook.build_suite_command(tmp_path, "harness", "turn-123")

        assert command[0] == str(python)
        assert command[1:6] == [
            "-m",
            "pytest",
            "scripts/test_stop_hook.py",
            "scripts/test_execute.py",
            "scripts/test_phase_manager.py",
        ]
        assert command[-2:] == [
            "--basetemp",
            str(tmp_path / "build" / "codex-stop-hook" / "pytest-turn-123"),
        ]
        assert timeout == 120

    def test_android_validation_is_one_mocked_gradle_invocation(
        self, tmp_path, monkeypatch
    ):
        wrapper_name = "gradlew.bat" if os.name == "nt" else "gradlew"
        wrapper = tmp_path / wrapper_name
        wrapper.write_bytes(b"")
        command, timeout = hook.build_suite_command(tmp_path, "android", "unused")
        completed = subprocess.CompletedProcess(command, 0, stdout=b"BUILD SUCCESSFUL", stderr=None)
        run = MagicMock(return_value=completed)
        monkeypatch.setattr(hook.subprocess, "run", run)

        result = hook.run_validation(tmp_path, "android", command, timeout)

        assert result.success is True
        assert run.call_count == 1
        assert run.call_args.kwargs["env"]["PYTHONUTF8"] == "1"
        assert command[1:] == [
            "test",
            "lint",
            "assembleDebug",
            "--offline",
            "--console=plain",
        ]
        assert run.call_args.kwargs["timeout"] == 720

    def test_validation_creates_artifact_parent_before_subprocess(
        self, tmp_path, monkeypatch
    ):
        def assert_parent_exists(*_args, **_kwargs):
            artifact_dir = tmp_path / "build" / "codex-stop-hook"
            assert artifact_dir.is_dir()
            return subprocess.CompletedProcess([], 0, stdout=b"ok", stderr=None)

        monkeypatch.setattr(hook.subprocess, "run", assert_parent_exists)

        result = hook.run_validation(
            tmp_path, "harness", ["mock-pytest"], timeout=120
        )

        assert result.success is True
