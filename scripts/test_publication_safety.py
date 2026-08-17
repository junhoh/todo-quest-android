"""Regression checks for files that must not enter the public repository."""

from __future__ import annotations

import re
import subprocess
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]

SENSITIVE_FILENAMES = {
    ".env",
    "google-services.json",
    "keystore.properties",
    "local.properties",
}
SENSITIVE_SUFFIXES = {
    ".ab",
    ".backup",
    ".db",
    ".jks",
    ".keystore",
    ".key",
    ".p12",
    ".pem",
    ".pfx",
    ".sqlite",
    ".sqlite3",
}
DATABASE_SIDECAR_SUFFIXES = (".db-shm", ".db-wal")

WINDOWS_USER_HOME = re.compile(
    r"(?i)(?:[a-z]:)?[\\/]+users[\\/]+(?!<|%|\$env:)([^\\/\s`\"']+)"
)
UNIX_USER_HOME = re.compile(r"/(?:Users|home)/(?!<|\$|\{)([^/\s`\"']+)")
WINDOWS_ACCOUNT = re.compile(
    r"\b[A-Z][A-Z0-9-]*_[A-Z0-9_-]*\\{1,2}[A-Za-z][A-Za-z0-9._-]+\b"
)
EMAIL_ADDRESS = re.compile(
    r"[A-Za-z0-9._%+-]+@[A-Za-z][A-Za-z0-9-]*(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}"
)
TOKEN_PATTERNS = (
    re.compile(r"gh[pousr]_[A-Za-z0-9_]{20,}"),
    re.compile(r"github_pat_[A-Za-z0-9_]{20,}"),
    re.compile(r"AKIA[0-9A-Z]{16}"),
    re.compile(r"AIza[0-9A-Za-z_-]{30,}"),
    re.compile(r"xox[baprs]-[A-Za-z0-9-]{10,}"),
    re.compile(
        r"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}"
    ),
)


def _tracked_paths() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=REPO_ROOT,
        check=True,
        capture_output=True,
    )
    return [
        REPO_ROOT / Path(raw.decode("utf-8"))
        for raw in result.stdout.split(b"\0")
        if raw
    ]


def _repository_path(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def _tracked_text_files() -> list[tuple[Path, str]]:
    files: list[tuple[Path, str]] = []
    for path in _tracked_paths():
        if not path.is_file():
            continue
        raw = path.read_bytes()
        if b"\0" in raw:
            continue
        try:
            files.append((path, raw.decode("utf-8")))
        except UnicodeDecodeError:
            continue
    return files


def test_sensitive_local_artifacts_are_not_tracked() -> None:
    violations: list[str] = []
    for path in _tracked_paths():
        name = path.name.lower()
        if (
            name in SENSITIVE_FILENAMES
            or any(name.endswith(suffix) for suffix in SENSITIVE_SUFFIXES)
            or name.endswith(DATABASE_SIDECAR_SUFFIXES)
        ):
            violations.append(_repository_path(path))

    assert not violations, "sensitive local artifacts are tracked:\n" + "\n".join(
        violations
    )


def test_tracked_text_has_no_personal_environment_or_token_values() -> None:
    violations: list[str] = []
    patterns = (
        ("Windows user-home path", WINDOWS_USER_HOME),
        ("Unix user-home path", UNIX_USER_HOME),
        ("Windows machine account", WINDOWS_ACCOUNT),
        ("email address", EMAIL_ADDRESS),
        *(("credential token", pattern) for pattern in TOKEN_PATTERNS),
    )

    for path, text in _tracked_text_files():
        for line_number, line in enumerate(text.splitlines(), start=1):
            for label, pattern in patterns:
                if pattern.search(line):
                    violations.append(
                        f"{_repository_path(path)}:{line_number}: {label}"
                    )

    assert not violations, "publication-sensitive text is tracked:\n" + "\n".join(
        violations
    )
