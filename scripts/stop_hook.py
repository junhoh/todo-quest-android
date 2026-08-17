#!/usr/bin/env python3
"""Run only the project validations relevant to the current Codex turn."""

from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Mapping, Sequence, TextIO


CACHE_SCHEMA_VERSION = 1
CLASSIFICATION_VERSION = 1
STOP_REASON_TAIL_LIMIT = 4_000
SYSTEM_MESSAGE = "Relevant project validation failed"
SUITE_ORDER = ("harness", "android")
SUITE_TIMEOUTS = {"harness": 120, "android": 720}

HARNESS_EXACT_PATHS = {
    ".codex/hooks.json",
    ".codex/rules/harness.rules",
    "docs/DEVELOPMENT.md",
    "phases/taxonomy.json",
    "requirements-dev.txt",
    "scripts/execute.py",
    "scripts/phase_manager.py",
    "scripts/run_harness.ps1",
    "scripts/run_phase_manager.ps1",
    "scripts/stop_hook.py",
    "scripts/test_execute.py",
    "scripts/test_phase_manager.py",
    "scripts/test_stop_hook.py",
}
ROOT_GRADLE_PATHS = {
    "build.gradle",
    "build.gradle.kts",
    "gradle.properties",
    "settings.gradle",
    "settings.gradle.kts",
}


class HookFailure(RuntimeError):
    """A user-facing hook failure that must block continuation."""


class RequiredToolMissing(HookFailure):
    """A required local interpreter or wrapper is unavailable."""


@dataclass(frozen=True, order=True)
class ChangedPath:
    path: str
    status: str


@dataclass(frozen=True)
class ValidationResult:
    success: bool
    output: str
    failure_reason: str


def _decode(data: bytes | str | None) -> str:
    if data is None:
        return ""
    if isinstance(data, str):
        return data
    return data.decode("utf-8", errors="replace")


def _normalize_path(path: str) -> str:
    normalized = path.replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized


def discover_repo_root() -> Path:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    except OSError as exc:
        raise RequiredToolMissing(f"git is required by the Stop hook: {exc}") from exc

    if result.returncode != 0:
        detail = _decode(result.stderr).strip() or "not inside a Git repository"
        raise HookFailure(f"Unable to determine repository root: {detail}")

    root_text = _decode(result.stdout).strip()
    if not root_text:
        raise HookFailure("Unable to determine repository root: git returned an empty path")
    return Path(root_text).resolve()


def parse_porcelain_status(data: bytes) -> list[ChangedPath]:
    """Parse ``git status --porcelain=v1 -z`` without relying on line boundaries."""
    records = data.split(b"\0")
    changes: list[ChangedPath] = []
    index = 0
    while index < len(records):
        record = records[index]
        index += 1
        if not record:
            continue
        if len(record) < 4 or record[2:3] != b" ":
            raise HookFailure("Unable to parse Git status output")

        status = record[:2].decode("ascii", errors="replace")
        path = _normalize_path(record[3:].decode("utf-8", errors="surrogateescape"))
        if "R" in status or "C" in status:
            if index >= len(records) or not records[index]:
                raise HookFailure("Unable to parse renamed path from Git status output")
            old_path = _normalize_path(
                records[index].decode("utf-8", errors="surrogateescape")
            )
            index += 1
            changes.append(ChangedPath(path, f"{status}:new"))
            changes.append(ChangedPath(old_path, f"{status}:old"))
        else:
            changes.append(ChangedPath(path, status))

    return sorted(set(changes))


def collect_changes(root: Path) -> list[ChangedPath]:
    try:
        result = subprocess.run(
            [
                "git",
                "status",
                "--porcelain=v1",
                "-z",
                "--untracked-files=all",
            ],
            cwd=root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    except OSError as exc:
        raise RequiredToolMissing(f"git is required by the Stop hook: {exc}") from exc

    if result.returncode != 0:
        detail = _decode(result.stderr).strip() or "git status failed"
        raise HookFailure(f"Unable to inspect repository changes: {detail}")
    return parse_porcelain_status(result.stdout)


def discover_head_revision(root: Path) -> str:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--verify", "HEAD"],
            cwd=root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    except OSError as exc:
        raise RequiredToolMissing(f"git is required by the Stop hook: {exc}") from exc

    if result.returncode != 0:
        detail = _decode(result.stderr).strip() or "unable to resolve HEAD"
        raise HookFailure(f"Unable to determine repository revision: {detail}")
    revision = _decode(result.stdout).strip()
    if not revision:
        raise HookFailure("Unable to determine repository revision: empty HEAD")
    return revision


def _is_harness_path(path: str) -> bool:
    return (
        path in HARNESS_EXACT_PATHS
        or path == ".agents/skills/harness"
        or path.startswith(".agents/skills/harness/")
    )


def _is_android_path(path: str) -> bool:
    if (
        path == "app"
        or path.startswith("app/")
        or path in ROOT_GRADLE_PATHS
        or path == "gradle"
        or path.startswith("gradle/")
        or path in {"gradlew", "gradlew.bat"}
        or path.endswith((".kt", ".kts"))
        or PurePosixPath(path).name == "AndroidManifest.xml"
    ):
        return True

    parts = PurePosixPath(path).parts
    return "src" in parts and "res" in parts and parts.index("src") < parts.index("res")


def _path_matches_suite(path: str, suite: str) -> bool:
    normalized = _normalize_path(path)
    if suite == "harness":
        return _is_harness_path(normalized)
    if suite == "android":
        return _is_android_path(normalized)
    raise ValueError(f"Unknown validation suite: {suite}")


def classify_changes(changes: Sequence[ChangedPath]) -> tuple[str, ...]:
    selected = {
        suite
        for change in changes
        for suite in SUITE_ORDER
        if _path_matches_suite(change.path, suite)
    }
    return tuple(suite for suite in SUITE_ORDER if suite in selected)


def suite_command_signature(suite: str) -> list[str]:
    if suite == "harness":
        interpreter = ".venv/Scripts/python.exe" if os.name == "nt" else ".venv/bin/python"
        return [
            interpreter,
            "-m",
            "pytest",
            "scripts/test_stop_hook.py",
            "scripts/test_execute.py",
            "scripts/test_phase_manager.py",
            "--basetemp",
            "build/codex-stop-hook/pytest-<turn>",
        ]
    if suite == "android":
        wrapper = "gradlew.bat" if os.name == "nt" else "gradlew"
        return [
            wrapper,
            "test",
            "lint",
            "assembleDebug",
            "--offline",
            "--console=plain",
        ]
    raise ValueError(f"Unknown validation suite: {suite}")


def _path_for_change(root: Path, relative_path: str) -> Path | None:
    pure_path = PurePosixPath(_normalize_path(relative_path))
    if pure_path.is_absolute() or ".." in pure_path.parts:
        return None
    return root.joinpath(*pure_path.parts)


def fingerprint_suite(
    root: Path,
    suite: str,
    changes: Sequence[ChangedPath],
    command: Sequence[str],
    repository_revision: str = "",
) -> str:
    digest = hashlib.sha256()
    header = {
        "classification_version": CLASSIFICATION_VERSION,
        "suite": suite,
        "command": list(command),
        "repository_revision": repository_revision,
    }
    digest.update(json.dumps(header, sort_keys=True, separators=(",", ":")).encode("utf-8"))

    relevant = sorted(
        change for change in changes if _path_matches_suite(change.path, suite)
    )
    for change in relevant:
        path = _normalize_path(change.path)
        digest.update(b"\0path\0")
        digest.update(path.encode("utf-8", errors="surrogatepass"))
        digest.update(b"\0status\0")
        digest.update(change.status.encode("ascii", errors="replace"))

        disk_path = _path_for_change(root, path)
        if disk_path is None:
            content = b"<invalid-path>"
        elif disk_path.is_file():
            content = disk_path.read_bytes()
        elif disk_path.exists():
            content = b"<non-file>"
        else:
            content = b"<deleted>"
        digest.update(b"\0content-length\0")
        digest.update(str(len(content)).encode("ascii"))
        digest.update(b"\0content\0")
        digest.update(content)

    return digest.hexdigest()


def cache_path(root: Path) -> Path:
    return root / "build" / "codex-stop-hook" / "cache.json"


def _empty_cache() -> dict:
    return {"schema_version": CACHE_SCHEMA_VERSION, "success": {}}


def load_cache(root: Path) -> dict:
    path = cache_path(root)
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, OSError, json.JSONDecodeError):
        return _empty_cache()
    if (
        not isinstance(data, dict)
        or data.get("schema_version") != CACHE_SCHEMA_VERSION
        or not isinstance(data.get("success"), dict)
    ):
        return _empty_cache()
    return data


def write_cache(root: Path, cache: Mapping) -> None:
    path = cache_path(root)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix="cache.json.tmp-",
            delete=False,
        ) as temporary:
            temporary_name = temporary.name
            json.dump(cache, temporary, ensure_ascii=False, indent=2)
            temporary.write("\n")
            temporary.flush()
            os.fsync(temporary.fileno())
        os.replace(temporary_name, path)
        temporary_name = None
    finally:
        if temporary_name is not None:
            try:
                Path(temporary_name).unlink()
            except FileNotFoundError:
                pass


def build_suite_command(root: Path, suite: str, turn_id: str) -> tuple[list[str], int]:
    if suite == "harness":
        interpreter = root / ".venv" / ("Scripts" if os.name == "nt" else "bin") / (
            "python.exe" if os.name == "nt" else "python"
        )
        if not interpreter.is_file():
            raise RequiredToolMissing(
                f"Missing project Python interpreter: {interpreter}. "
                "Create .venv and install requirements-dev.txt before continuing."
            )
        command = [
            str(interpreter),
            "-m",
            "pytest",
            "scripts/test_stop_hook.py",
            "scripts/test_execute.py",
            "scripts/test_phase_manager.py",
            "--basetemp",
            str(root / "build" / "codex-stop-hook" / f"pytest-{turn_id}"),
        ]
        return command, SUITE_TIMEOUTS[suite]

    if suite == "android":
        wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
        if not wrapper.is_file():
            raise RequiredToolMissing(f"Missing Android Gradle wrapper: {wrapper}")
        command = [
            str(wrapper),
            "test",
            "lint",
            "assembleDebug",
            "--offline",
            "--console=plain",
        ]
        return command, SUITE_TIMEOUTS[suite]

    raise ValueError(f"Unknown validation suite: {suite}")


def _write_suite_log(root: Path, suite: str, output: str) -> Path:
    log_path = root / "build" / "codex-stop-hook" / f"latest-{suite}.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.write_text(output, encoding="utf-8")
    return log_path


def _limited_failure_reason(prefix: str, output: str) -> str:
    if not output:
        return prefix[-STOP_REASON_TAIL_LIMIT:]
    available = max(0, STOP_REASON_TAIL_LIMIT - len(prefix) - 1)
    return f"{prefix}\n{output[-available:]}" if available else prefix[-STOP_REASON_TAIL_LIMIT:]


def _failure_reason_with_log(
    root: Path, log_path: Path, prefix: str, output: str
) -> str:
    try:
        displayed_path = log_path.relative_to(root).as_posix()
    except ValueError:
        displayed_path = str(log_path)
    return _limited_failure_reason(
        f"{prefix}\nFull output: {displayed_path}", output
    )


def run_validation(
    root: Path,
    suite: str,
    command: Sequence[str],
    timeout: int,
) -> ValidationResult:
    artifact_dir = root / "build" / "codex-stop-hook"
    try:
        artifact_dir.mkdir(parents=True, exist_ok=True)
    except OSError as exc:
        output = str(exc)
        reason = _limited_failure_reason(
            f"Unable to prepare {suite} validation artifacts.", output
        )
        return ValidationResult(False, output, reason)

    try:
        validation_environment = os.environ.copy()
        validation_environment["PYTHONUTF8"] = "1"
        result = subprocess.run(
            list(command),
            cwd=root,
            env=validation_environment,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout,
            check=False,
        )
        output = _decode(result.stdout)
        log_path = _write_suite_log(root, suite, output)
        if result.returncode == 0:
            return ValidationResult(True, output, "")
        reason = _failure_reason_with_log(
            root,
            log_path,
            f"{suite} validation failed with exit code {result.returncode}.",
            output,
        )
        return ValidationResult(False, output, reason)
    except subprocess.TimeoutExpired as exc:
        output = _decode(exc.output) + _decode(exc.stderr)
        log_path = _write_suite_log(root, suite, output)
        reason = _failure_reason_with_log(
            root,
            log_path,
            f"{suite} validation timed out after {timeout} seconds.",
            output,
        )
        return ValidationResult(False, output, reason)
    except OSError as exc:
        output = str(exc)
        log_path = _write_suite_log(root, suite, output)
        reason = _failure_reason_with_log(
            root,
            log_path,
            f"Unable to start {suite} validation.",
            output,
        )
        return ValidationResult(False, output, reason)


def _emit_continuation_failure(stdout: TextIO, reason: str) -> None:
    json.dump(
        {
            "continue": False,
            "stopReason": reason,
            "systemMessage": SYSTEM_MESSAGE,
        },
        stdout,
        ensure_ascii=False,
        separators=(",", ":"),
    )
    stdout.write("\n")


def main(
    stdin: TextIO | None = None,
    stdout: TextIO | None = None,
    environ: Mapping[str, str] | None = None,
) -> int:
    input_stream = stdin or sys.stdin
    output_stream = stdout or sys.stdout
    environment = os.environ if environ is None else environ

    if environment.get("TODO_QUEST_HARNESS_CHILD") == "1":
        return 0

    try:
        event = json.loads(input_stream.read())
        if not isinstance(event, dict):
            raise HookFailure("Codex Stop event must be a JSON object")
        if event.get("permission_mode") == "plan":
            return 0

        root = discover_repo_root()
        changes = collect_changes(root)
        suites = classify_changes(changes)
        if not suites:
            return 0

        repository_revision = discover_head_revision(root)
        cache = load_cache(root)
        turn_id = f"{time.time_ns()}-{os.getpid()}"
        for suite in suites:
            signature = suite_command_signature(suite)
            fingerprint = fingerprint_suite(
                root, suite, changes, signature, repository_revision
            )
            if cache["success"].get(suite) == fingerprint:
                continue

            command, timeout = build_suite_command(root, suite, turn_id)
            result = run_validation(root, suite, command, timeout)
            if not result.success:
                _emit_continuation_failure(output_stream, result.failure_reason)
                return 0

            cache["success"][suite] = fingerprint
            write_cache(root, cache)
        return 0
    except json.JSONDecodeError as exc:
        _emit_continuation_failure(output_stream, f"Invalid Codex Stop event JSON: {exc.msg}")
        return 0
    except HookFailure as exc:
        _emit_continuation_failure(output_stream, str(exc))
        return 0
    except Exception as exc:  # Stop hooks must never leak a traceback to Codex.
        _emit_continuation_failure(
            output_stream, f"Stop hook failed: {type(exc).__name__}: {exc}"
        )
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
