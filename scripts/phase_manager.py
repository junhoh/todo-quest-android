#!/usr/bin/env python3
"""Phase registry core, deterministic catalog, CLI, and legacy migration.

The phase-local ``index.json`` and its step statuses are the execution source of
truth.  The top-level registry status is only a derived display value.  This
Core helpers preserve source objects. Explicit ``Create``, ``Sync``, and
non-dry-run ``MigrateLegacy`` commands own their narrowly scoped writes.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
import subprocess
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path, PurePosixPath, PureWindowsPath
from typing import Any, Mapping, Sequence


ROOT = Path(__file__).resolve().parent.parent
DEFAULT_TAXONOMY_PATH = ROOT / "phases" / "taxonomy.json"

EXPECTED_STATUSES = frozenset({"pending", "completed", "error", "blocked"})
EXPECTED_AREAS = frozenset(
    {
        "project",
        "schedule",
        "character",
        "combat",
        "art",
        "android-platform",
        "harness",
    }
)
EXPECTED_KINDS = frozenset(
    {"feature", "fix", "design", "asset", "infrastructure", "documentation"}
)

_KEBAB_PATTERN = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*\Z")
_BASENAME_PATTERN = re.compile(
    r"(?P<id>0|[1-9][0-9]*)-(?P<slug>[a-z0-9]+(?:-[a-z0-9]+)*)\Z"
)
_NEW_METADATA_KEYS = frozenset({"id", "slug", "areas", "kind", "tags"})
_NEW_REQUIRED_KEYS = frozenset(
    {"id", "slug", "dir", "status", "areas", "kind", "tags"}
)


class PhaseRegistryError(ValueError):
    """Base error for invalid phase registry data."""


class DuplicatePhaseError(PhaseRegistryError):
    """Raised when registry phase identities are not unique."""


class UnsafePhasePathError(PhaseRegistryError):
    """Raised when a registry directory or selector can escape ``phases``."""


class PhaseNotFoundError(PhaseRegistryError):
    """Raised when a selector has no registered, existing phase directory."""


class PhaseAlreadyExistsError(PhaseRegistryError):
    """Raised when Create would overwrite an existing phase identity or path."""


@dataclass(frozen=True)
class SuggestionRule:
    value: str
    keywords: tuple[str, ...]
    path_prefixes: tuple[str, ...]


@dataclass(frozen=True)
class Taxonomy:
    statuses: tuple[str, ...]
    areas: tuple[str, ...]
    kinds: tuple[str, ...]
    tag_pattern: str
    area_rules: tuple[SuggestionRule, ...]
    kind_rules: tuple[SuggestionRule, ...]
    legacy_classification_overrides: Mapping[str, "LegacyClassification"]


@dataclass(frozen=True)
class LegacyClassification:
    areas: tuple[str, ...]
    kind: str
    tags: tuple[str, ...]


@dataclass(frozen=True)
class PhaseEntry:
    """Validated registry projection without rewriting its source object."""

    id: int
    slug: str
    dir: str
    status: str
    areas: tuple[str, ...]
    kind: str | None
    tags: tuple[str, ...]
    is_legacy: bool
    registry_index: int

    @property
    def basename(self) -> str:
        return f"{self.id}-{self.slug}"


@dataclass(frozen=True)
class PhaseRegistry:
    path: Path
    entries: tuple[PhaseEntry, ...]
    raw: dict[str, Any]
    taxonomy: Taxonomy


@dataclass(frozen=True)
class ResolvedPhase:
    entry: PhaseEntry
    path: Path


def bucket_name(phase_id: int, size: int = 10) -> str:
    """Return the zero-padded inclusive bucket containing ``phase_id``."""

    if isinstance(phase_id, bool) or not isinstance(phase_id, int) or phase_id < 0:
        raise ValueError("phase_id must be a non-negative integer")
    if isinstance(size, bool) or not isinstance(size, int) or size <= 0:
        raise ValueError("size must be a positive integer")
    start = (phase_id // size) * size
    end = start + size - 1
    return f"{start:03d}-{end:03d}"


def parse_phase_basename(basename: str) -> tuple[int, str]:
    """Parse the canonical ``{non-negative-id}-{kebab-slug}`` basename."""

    if not isinstance(basename, str):
        raise PhaseRegistryError("phase basename must be a string")
    match = _BASENAME_PATTERN.fullmatch(basename)
    if match is None:
        raise PhaseRegistryError(
            f"invalid phase basename {basename!r}; expected '{{id}}-{{kebab-slug}}'"
        )
    return int(match.group("id")), match.group("slug")


def normalize_tag(tag: str) -> str:
    """Normalize an English catalog tag to lowercase kebab-case."""

    if not isinstance(tag, str):
        raise PhaseRegistryError("each tag must be a string")
    normalized = re.sub(r"[^a-z0-9]+", "-", tag.strip().lower()).strip("-")
    if not normalized or _KEBAB_PATTERN.fullmatch(normalized) is None:
        raise PhaseRegistryError(f"tag {tag!r} cannot be normalized to kebab-case")
    return normalized


def _require_string_list(value: object, field: str) -> list[str]:
    if not isinstance(value, list) or any(not isinstance(item, str) for item in value):
        raise PhaseRegistryError(f"{field} must be a list of strings")
    return value


def _validated_relative_dir(value: object, *, label: str = "phase dir") -> str:
    if not isinstance(value, str) or not value:
        raise UnsafePhasePathError(f"{label} must be a non-empty relative path")
    windows_path = PureWindowsPath(value)
    posix_path = PurePosixPath(value)
    if (
        posix_path.is_absolute()
        or windows_path.is_absolute()
        or bool(windows_path.drive)
        or "\\" in value
    ):
        raise UnsafePhasePathError(f"{label} must be relative to phases: {value!r}")
    parts = value.split("/")
    if any(part in {"", ".", ".."} for part in parts):
        raise UnsafePhasePathError(f"{label} contains an unsafe segment: {value!r}")
    return "/".join(parts)


def _taxonomy_values(data: Mapping[str, Any], field: str) -> tuple[str, ...]:
    values = _require_string_list(data.get(field), field)
    if not values or len(values) != len(set(values)):
        raise PhaseRegistryError(f"taxonomy {field} must contain unique values")
    return tuple(values)


def _suggestion_rules(
    data: Mapping[str, Any],
    field: str,
    allowed_values: tuple[str, ...],
) -> tuple[SuggestionRule, ...]:
    suggestion_rules = data.get("suggestion_rules")
    if not isinstance(suggestion_rules, Mapping):
        raise PhaseRegistryError("taxonomy suggestion_rules must be an object")
    raw_rules = suggestion_rules.get(field)
    if not isinstance(raw_rules, list):
        raise PhaseRegistryError(f"taxonomy suggestion_rules.{field} must be a list")
    rules: list[SuggestionRule] = []
    seen: set[str] = set()
    for index, raw_rule in enumerate(raw_rules):
        if not isinstance(raw_rule, Mapping):
            raise PhaseRegistryError(f"taxonomy {field} rule {index} must be an object")
        value = raw_rule.get("value")
        if value not in allowed_values or value in seen:
            raise PhaseRegistryError(
                f"taxonomy {field} rule {index} has invalid or duplicate value {value!r}"
            )
        keywords = _require_string_list(raw_rule.get("keywords", []), "keywords")
        path_prefixes = _require_string_list(
            raw_rule.get("path_prefixes", []),
            "path_prefixes",
        )
        if not keywords and not path_prefixes:
            raise PhaseRegistryError(f"taxonomy {field} rule {value!r} is empty")
        normalized_keywords = tuple(normalize_tag(keyword) for keyword in keywords)
        normalized_prefixes = tuple(
            prefix.replace("\\", "/").lstrip("/") for prefix in path_prefixes
        )
        rules.append(
            SuggestionRule(
                value=value,
                keywords=normalized_keywords,
                path_prefixes=normalized_prefixes,
            )
        )
        seen.add(value)
    if seen != set(allowed_values):
        missing = sorted(set(allowed_values).difference(seen))
        raise PhaseRegistryError(f"taxonomy {field} rules are missing {missing}")
    return tuple(rules)


def _legacy_classification_overrides(
    data: Mapping[str, Any],
    areas: tuple[str, ...],
    kinds: tuple[str, ...],
) -> dict[str, LegacyClassification]:
    raw_overrides = data.get("legacy_classification_overrides")
    if not isinstance(raw_overrides, Mapping):
        raise PhaseRegistryError(
            "taxonomy legacy_classification_overrides must be an object"
        )
    overrides: dict[str, LegacyClassification] = {}
    seen_ids: set[int] = set()
    seen_slugs: set[str] = set()
    for basename, raw_override in raw_overrides.items():
        label = f"legacy classification override {basename!r}"
        try:
            phase_id, slug = parse_phase_basename(basename)
            if phase_id in seen_ids or slug in seen_slugs:
                raise PhaseRegistryError("id and slug must be unique")
            if not isinstance(raw_override, Mapping):
                raise PhaseRegistryError("must be an object")
            required_keys = {"areas", "kind", "tags"}
            if set(raw_override) != required_keys:
                raise PhaseRegistryError(
                    f"must contain exactly {sorted(required_keys)}"
                )
            override_areas = _require_string_list(raw_override["areas"], "areas")
            if not override_areas:
                raise PhaseRegistryError("areas must contain at least one value")
            if len(override_areas) != len(set(override_areas)):
                raise PhaseRegistryError("areas must not contain duplicates")
            invalid_areas = set(override_areas).difference(areas)
            if invalid_areas:
                raise PhaseRegistryError(
                    f"unsupported areas: {sorted(invalid_areas)}"
                )
            kind = raw_override["kind"]
            if not isinstance(kind, str) or kind not in kinds:
                raise PhaseRegistryError("kind must be exactly one supported value")
            tags = _require_string_list(raw_override["tags"], "tags")
            if not tags:
                raise PhaseRegistryError("tags must contain at least one value")
            if len(tags) != len(set(tags)):
                raise PhaseRegistryError("tags must not contain duplicates")
            invalid_tags = [
                tag for tag in tags if _KEBAB_PATTERN.fullmatch(tag) is None
            ]
            if invalid_tags:
                raise PhaseRegistryError(
                    f"tags must be lowercase kebab-case: {invalid_tags}"
                )
        except PhaseRegistryError as error:
            raise PhaseRegistryError(f"{label}: {error}") from error
        overrides[basename] = LegacyClassification(
            areas=tuple(override_areas),
            kind=kind,
            tags=tuple(tags),
        )
        seen_ids.add(phase_id)
        seen_slugs.add(slug)
    return overrides


def load_taxonomy(path: Path = DEFAULT_TAXONOMY_PATH) -> Taxonomy:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise PhaseRegistryError("taxonomy root must be an object")
    statuses = _taxonomy_values(data, "statuses")
    areas = _taxonomy_values(data, "areas")
    kinds = _taxonomy_values(data, "kinds")
    tag_pattern = data.get("tag_pattern")
    if not isinstance(tag_pattern, str):
        raise PhaseRegistryError("taxonomy tag_pattern must be a string")
    if frozenset(statuses) != EXPECTED_STATUSES:
        raise PhaseRegistryError("taxonomy statuses do not match the fixed contract")
    if frozenset(areas) != EXPECTED_AREAS:
        raise PhaseRegistryError("taxonomy areas do not match the fixed contract")
    if frozenset(kinds) != EXPECTED_KINDS:
        raise PhaseRegistryError("taxonomy kinds do not match the fixed contract")
    if tag_pattern != _KEBAB_PATTERN.pattern:
        raise PhaseRegistryError("taxonomy tag_pattern does not match the fixed contract")
    area_rules = _suggestion_rules(data, "areas", areas)
    kind_rules = _suggestion_rules(data, "kinds", kinds)
    legacy_overrides = _legacy_classification_overrides(data, areas, kinds)
    return Taxonomy(
        statuses=statuses,
        areas=areas,
        kinds=kinds,
        tag_pattern=tag_pattern,
        area_rules=area_rules,
        kind_rules=kind_rules,
        legacy_classification_overrides=legacy_overrides,
    )


def _normalized_tags(value: object) -> tuple[str, ...]:
    tags = _require_string_list(value, "tags")
    result: list[str] = []
    seen: set[str] = set()
    for tag in tags:
        normalized = normalize_tag(tag)
        if normalized not in seen:
            result.append(normalized)
            seen.add(normalized)
    return tuple(result)


def _parse_entry(
    raw: object,
    registry_index: int,
    taxonomy: Taxonomy,
) -> PhaseEntry:
    if not isinstance(raw, dict):
        raise PhaseRegistryError(f"phase entry {registry_index} must be an object")
    relative_dir = _validated_relative_dir(raw.get("dir"))
    status = raw.get("status")
    if status not in taxonomy.statuses:
        raise PhaseRegistryError(
            f"phase entry {registry_index} has unsupported status {status!r}"
        )

    has_new_metadata = bool(_NEW_METADATA_KEYS.intersection(raw))
    if has_new_metadata:
        missing = _NEW_REQUIRED_KEYS.difference(raw)
        if missing:
            raise PhaseRegistryError(
                f"phase entry {registry_index} has partial metadata; missing {sorted(missing)}"
            )
        phase_id = raw["id"]
        slug = raw["slug"]
        if isinstance(phase_id, bool) or not isinstance(phase_id, int) or phase_id < 0:
            raise PhaseRegistryError("phase id must be a non-negative integer")
        if not isinstance(slug, str) or _KEBAB_PATTERN.fullmatch(slug) is None:
            raise PhaseRegistryError("phase slug must be kebab-case")
        basename_id, basename_slug = parse_phase_basename(relative_dir.split("/")[-1])
        if (phase_id, slug) != (basename_id, basename_slug):
            raise PhaseRegistryError("phase id and slug must match the dir basename")

        areas = _require_string_list(raw["areas"], "areas")
        if not areas:
            raise PhaseRegistryError("areas must contain at least one value")
        if len(areas) != len(set(areas)):
            raise PhaseRegistryError("areas must not contain duplicates")
        invalid_areas = set(areas).difference(taxonomy.areas)
        if invalid_areas:
            raise PhaseRegistryError(f"unsupported areas: {sorted(invalid_areas)}")
        kind = raw["kind"]
        if not isinstance(kind, str) or kind not in taxonomy.kinds:
            raise PhaseRegistryError("kind must be exactly one supported value")
        tags = _normalized_tags(raw["tags"])
        return PhaseEntry(
            id=phase_id,
            slug=slug,
            dir=relative_dir,
            status=status,
            areas=tuple(areas),
            kind=kind,
            tags=tags,
            is_legacy=False,
            registry_index=registry_index,
        )

    phase_id, slug = parse_phase_basename(relative_dir.split("/")[-1])
    return PhaseEntry(
        id=phase_id,
        slug=slug,
        dir=relative_dir,
        status=status,
        areas=(),
        kind=None,
        tags=(),
        is_legacy=True,
        registry_index=registry_index,
    )


def load_registry(
    path: Path,
    taxonomy_path: Path = DEFAULT_TAXONOMY_PATH,
) -> PhaseRegistry:
    """Read and validate legacy and metadata-rich entries without writing them."""

    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or not isinstance(data.get("phases"), list):
        raise PhaseRegistryError("registry root must contain a phases list")
    taxonomy = load_taxonomy(taxonomy_path)
    entries = tuple(
        _parse_entry(raw, index, taxonomy)
        for index, raw in enumerate(data["phases"])
    )

    for field, values in (
        ("id", [entry.id for entry in entries]),
        ("slug", [entry.slug for entry in entries]),
        ("dir", [entry.dir for entry in entries]),
    ):
        duplicates = sorted({value for value in values if values.count(value) > 1})
        if duplicates:
            raise DuplicatePhaseError(f"duplicate phase {field}: {duplicates}")
    return PhaseRegistry(path=path, entries=entries, raw=data, taxonomy=taxonomy)


def _validate_selector(selector: object) -> int | str:
    if isinstance(selector, bool):
        raise PhaseRegistryError("phase selector must be an id, basename, or registry dir")
    if isinstance(selector, int):
        if selector < 0:
            raise PhaseRegistryError("phase selector id must be non-negative")
        return selector
    if not isinstance(selector, str) or not selector:
        raise PhaseRegistryError("phase selector must be an id, basename, or registry dir")
    if selector.isdecimal():
        return int(selector)
    return _validated_relative_dir(selector, label="phase selector")


def resolve_phase(
    selector: int | str,
    registry: PhaseRegistry,
    phases_dir: Path,
) -> ResolvedPhase:
    """Resolve an id, basename, or registry dir and contain it below phases."""

    normalized_selector = _validate_selector(selector)
    if isinstance(normalized_selector, int):
        matches = [entry for entry in registry.entries if entry.id == normalized_selector]
    else:
        matches = [
            entry
            for entry in registry.entries
            if normalized_selector in {entry.basename, entry.dir}
        ]
    if not matches:
        raise PhaseNotFoundError(f"phase selector not found: {selector!r}")

    entry = matches[0]
    try:
        phases_root = phases_dir.resolve(strict=True)
        candidate = (phases_root / Path(*entry.dir.split("/"))).resolve(strict=True)
    except FileNotFoundError as error:
        raise PhaseNotFoundError(
            f"registered phase directory does not exist: {entry.dir}"
        ) from error
    try:
        candidate.relative_to(phases_root)
    except ValueError as error:
        raise UnsafePhasePathError(
            f"resolved phase directory escapes phases: {entry.dir}"
        ) from error
    if not candidate.is_dir():
        raise PhaseNotFoundError(f"registered phase path is not a directory: {entry.dir}")
    return ResolvedPhase(entry=entry, path=candidate)


def read_phase_index(path: Path) -> dict[str, Any]:
    """Read a phase-local source index without normalizing or rewriting it."""

    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise PhaseRegistryError("phase index root must be an object")
    return data


def phase_status_from_index(phase_index: Mapping[str, Any]) -> str:
    """Derive execution status from step statuses in the phase-local source."""

    steps = phase_index.get("steps")
    if not isinstance(steps, list):
        raise PhaseRegistryError("phase index steps must be a list")
    if not steps:
        status = phase_index.get("status", "pending")
        if status not in EXPECTED_STATUSES:
            raise PhaseRegistryError(f"unsupported phase status {status!r}")
        return status

    statuses: list[str] = []
    for index, step in enumerate(steps):
        if not isinstance(step, Mapping):
            raise PhaseRegistryError(f"phase step {index} must be an object")
        status = step.get("status")
        if status not in EXPECTED_STATUSES:
            raise PhaseRegistryError(f"phase step {index} has unsupported status {status!r}")
        statuses.append(status)
    if "error" in statuses:
        return "error"
    if "blocked" in statuses:
        return "blocked"
    if all(status == "completed" for status in statuses):
        return "completed"
    return "pending"


def registry_with_synced_status(
    registry: PhaseRegistry,
    entry: PhaseEntry,
    phase_index: Mapping[str, Any],
) -> dict[str, Any]:
    """Return a registry copy with one display status derived from source steps.

    The registry object, registry file, and heterogeneous phase-local index are
    deliberately left untouched.
    """

    if (
        entry.registry_index < 0
        or entry.registry_index >= len(registry.entries)
        or registry.entries[entry.registry_index] != entry
    ):
        raise PhaseRegistryError("phase entry does not belong to this registry")
    result = copy.deepcopy(registry.raw)
    result["phases"][entry.registry_index]["status"] = phase_status_from_index(
        phase_index
    )
    return result


def _taxonomy_for_root(root: Path) -> Path:
    candidate = root / "phases" / "taxonomy.json"
    return candidate if candidate.is_file() else DEFAULT_TAXONOMY_PATH


def _tokenize(values: Sequence[str]) -> set[str]:
    tokens: set[str] = set()
    for value in values:
        normalized = value.replace("\\", "/").lower()
        tokens.update(token for token in re.split(r"[^a-z0-9]+", normalized) if token)
    return tokens


def _score_rules(
    rules: Sequence[SuggestionRule],
    tokens: set[str],
    intended_paths: Sequence[str],
) -> list[dict[str, Any]]:
    normalized_paths = [path.replace("\\", "/").lstrip("/").lower() for path in intended_paths]
    evidence: list[dict[str, Any]] = []
    for rule in rules:
        matched_keywords = [keyword for keyword in rule.keywords if keyword in tokens]
        matched_paths = [
            path
            for path in normalized_paths
            if any(path.startswith(prefix.lower()) for prefix in rule.path_prefixes)
        ]
        score = len(matched_keywords) + len(matched_paths)
        if score:
            evidence.append(
                {
                    "value": rule.value,
                    "score": score,
                    "keywords": matched_keywords,
                    "paths": matched_paths,
                }
            )
    return evidence


def suggest_metadata(
    *,
    slug: str,
    step_names: Sequence[str],
    intended_paths: Sequence[str],
    taxonomy_path: Path = DEFAULT_TAXONOMY_PATH,
) -> dict[str, Any]:
    """Suggest metadata only when deterministic taxonomy evidence is sufficient."""

    if not isinstance(slug, str) or _KEBAB_PATTERN.fullmatch(slug) is None:
        raise PhaseRegistryError("suggest slug must be kebab-case")
    if any(not isinstance(name, str) or not name for name in step_names):
        raise PhaseRegistryError("suggest step names must be non-empty strings")
    if any(not isinstance(path, str) or not path for path in intended_paths):
        raise PhaseRegistryError("suggest paths must be non-empty strings")
    taxonomy = load_taxonomy(taxonomy_path)
    tokens = _tokenize([slug, *step_names, *intended_paths])
    area_evidence = _score_rules(taxonomy.area_rules, tokens, intended_paths)
    kind_evidence = _score_rules(taxonomy.kind_rules, tokens, intended_paths)
    areas = [
        area
        for area in taxonomy.areas
        if any(item["value"] == area for item in area_evidence)
    ]
    kind: str | None = None
    kind_ties: list[str] = []
    if kind_evidence:
        highest_score = max(item["score"] for item in kind_evidence)
        kind_ties = [
            kind
            for kind in taxonomy.kinds
            if any(
                item["value"] == kind and item["score"] == highest_score
                for item in kind_evidence
            )
        ]
        if len(kind_ties) == 1:
            kind = kind_ties[0]
    ambiguities: dict[str, Any] = {}
    if not areas:
        ambiguities["areas"] = {"reason": "no taxonomy rule matched"}
    if kind is None:
        ambiguities["kind"] = {
            "reason": "no unique highest-scoring taxonomy rule",
            "candidates": kind_ties,
        }
    return {
        "status": "ambiguous" if ambiguities else "classified",
        "slug": slug,
        "areas": areas,
        "kind": kind,
        "tags": [slug],
        "evidence": {
            "areas": area_evidence,
            "kinds": kind_evidence,
            "inputs": {
                "steps": list(step_names),
                "paths": list(intended_paths),
            },
        },
        "ambiguities": ambiguities,
    }


def _json_bytes(value: object) -> bytes:
    return (json.dumps(value, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def _write_json(path: Path, value: object) -> None:
    path.write_bytes(_json_bytes(value))


def create_phase(
    *,
    phases_dir: Path,
    registry_path: Path,
    slug: str,
    step_names: Sequence[str],
    areas: Sequence[str],
    kind: str,
    tags: Sequence[str],
    taxonomy_path: Path = DEFAULT_TAXONOMY_PATH,
) -> dict[str, Any]:
    """Create one metadata-rich registry entry and timestamp-free phase index."""

    registry = load_registry(registry_path, taxonomy_path)
    if not isinstance(slug, str) or _KEBAB_PATTERN.fullmatch(slug) is None:
        raise PhaseRegistryError("create slug must be kebab-case")
    if not step_names:
        raise PhaseRegistryError("create requires at least one step")
    for name in step_names:
        if not isinstance(name, str) or _KEBAB_PATTERN.fullmatch(name) is None:
            raise PhaseRegistryError("create step names must be kebab-case")
    if any(entry.slug == slug for entry in registry.entries):
        raise PhaseAlreadyExistsError(f"phase slug is already registered: {slug}")
    next_id = max((entry.id for entry in registry.entries), default=-1) + 1
    basename = f"{next_id}-{slug}"
    relative_dir = f"{bucket_name(next_id)}/{basename}"
    destination = phases_dir / Path(*relative_dir.split("/"))
    if destination.exists():
        raise PhaseAlreadyExistsError(f"phase directory already exists: {relative_dir}")
    raw_entry = {
        "id": next_id,
        "slug": slug,
        "dir": relative_dir,
        "status": "pending",
        "areas": list(areas),
        "kind": kind,
        "tags": list(tags),
    }
    _parse_entry(raw_entry, len(registry.entries), registry.taxonomy)
    phase_index = {
        "project": "Todo Quest",
        "phase": basename,
        "steps": [
            {"step": index, "name": name, "status": "pending"}
            for index, name in enumerate(step_names)
        ],
    }
    updated_registry = copy.deepcopy(registry.raw)
    updated_registry["phases"].append(raw_entry)
    destination.mkdir(parents=True, exist_ok=False)
    try:
        _write_json(destination / "index.json", phase_index)
        _write_json(registry_path, updated_registry)
    except Exception:
        index_path = destination / "index.json"
        if index_path.exists():
            index_path.unlink()
        destination.rmdir()
        raise
    return raw_entry


_REFERENCE_SUFFIX_PATTERN = r"(?:/[A-Za-z0-9._/-]+)?"
_PHASE_DOCUMENT_REFERENCE = re.compile(
    r"(?<![A-Za-z0-9_.-])/?phases/"
    r"(?P<phase>(?:[0-9]{3}-[0-9]{3}/)?(?:0|[1-9][0-9]*)-"
    r"[a-z0-9]+(?:-[a-z0-9]+)*)"
    r"(?P<suffix>/(?:index\.json|step[0-9]+\.md))"
    r"(?![A-Za-z0-9_.-])"
)


def build_reference_graph(
    registry: PhaseRegistry,
    phases_dir: Path,
) -> dict[str, Any]:
    """Build display-only relationships from references written in step Markdown."""

    by_dir = {entry.dir: entry for entry in registry.entries}
    outgoing: dict[str, set[str]] = defaultdict(set)
    incoming: dict[str, set[str]] = defaultdict(set)
    references: list[dict[str, str]] = []
    for source_entry in sorted(registry.entries, key=lambda item: item.id):
        source_dir = phases_dir / Path(*source_entry.dir.split("/"))
        if not source_dir.is_dir():
            continue
        for step_path in sorted(source_dir.glob("step*.md")):
            text = step_path.read_text(encoding="utf-8")
            for match in _PHASE_DOCUMENT_REFERENCE.finditer(text):
                target_dir = match.group("phase")
                target = by_dir.get(target_dir)
                if target is None or target.dir == source_entry.dir:
                    continue
                outgoing[source_entry.dir].add(target.dir)
                incoming[target.dir].add(source_entry.dir)
                references.append(
                    {
                        "source": source_entry.dir,
                        "source_file": step_path.relative_to(phases_dir).as_posix(),
                        "target": target.dir,
                        "path": f"phases/{target.dir}{match.group('suffix')}",
                    }
                )
    return {
        "outgoing": {key: sorted(value) for key, value in outgoing.items()},
        "incoming": {key: sorted(value) for key, value in incoming.items()},
        "references": references,
    }


def display_summary(phase_index: Mapping[str, Any]) -> str | None:
    summary = phase_index.get("summary")
    if isinstance(summary, str) and summary.strip():
        return summary.strip()
    steps = phase_index.get("steps")
    if not isinstance(steps, list):
        return None
    for step in reversed(steps):
        if not isinstance(step, Mapping) or step.get("status") != "completed":
            continue
        step_summary = step.get("summary")
        if isinstance(step_summary, str) and step_summary.strip():
            return step_summary.strip()
    return None


def _catalog_item(
    entry: PhaseEntry,
    phases_dir: Path,
    graph: Mapping[str, Any],
) -> dict[str, Any]:
    phase_index = read_phase_index(
        phases_dir / Path(*entry.dir.split("/")) / "index.json"
    )
    return {
        "id": entry.id,
        "basename": entry.basename,
        "dir": entry.dir,
        "status": phase_status_from_index(phase_index),
        "areas": list(entry.areas),
        "kind": entry.kind,
        "tags": list(entry.tags),
        "summary": display_summary(phase_index),
        "outgoing": list(graph.get("outgoing", {}).get(entry.dir, [])),
        "incoming": list(graph.get("incoming", {}).get(entry.dir, [])),
    }


def list_phases(
    registry: PhaseRegistry,
    phases_dir: Path,
    graph: Mapping[str, Any] | None = None,
    *,
    statuses: Sequence[str] = (),
    areas: Sequence[str] = (),
    kinds: Sequence[str] = (),
    tags: Sequence[str] = (),
) -> list[dict[str, Any]]:
    graph = graph or build_reference_graph(registry, phases_dir)
    result: list[dict[str, Any]] = []
    for entry in sorted(registry.entries, key=lambda item: item.id):
        item = _catalog_item(entry, phases_dir, graph)
        if statuses and item["status"] not in statuses:
            continue
        if areas and not all(area in item["areas"] for area in areas):
            continue
        if kinds and item["kind"] not in kinds:
            continue
        if tags and not all(tag in item["tags"] for tag in tags):
            continue
        result.append(item)
    return result


def show_phase(
    selector: int | str,
    registry: PhaseRegistry,
    phases_dir: Path,
    graph: Mapping[str, Any] | None = None,
) -> dict[str, Any]:
    resolved = resolve_phase(selector, registry, phases_dir)
    return _catalog_item(
        resolved.entry,
        phases_dir,
        graph or build_reference_graph(registry, phases_dir),
    )


def _escape_table(value: object) -> str:
    if value is None or value == "":
        return "-"
    return str(value).replace("|", "\\|").replace("\r", " ").replace("\n", " ")


def render_catalog_readme(items: Sequence[Mapping[str, Any]]) -> str:
    lines = [
        "# Phase 카탈로그",
        "",
        "이 문서는 phase index의 실행 상태와 step 문서의 명시적 참조를 기준으로 동기화한다.",
        "",
        "| ID | Phase | Status | Areas | Kind | Tags | Summary | Outgoing | Incoming |",
        "|---:|---|---|---|---|---|---|---|---|",
    ]
    by_dir = {str(item["dir"]): item for item in items}

    def reference_links(dirs: Sequence[str]) -> str:
        return "<br>".join(
            f"[{_escape_table(by_dir[directory]['basename'])}]({directory}/index.json)"
            for directory in dirs
            if directory in by_dir
        ) or "-"

    for item in items:
        areas = ", ".join(item["areas"]) or "-"
        tags = ", ".join(item["tags"]) or "-"
        lines.append(
            "| "
            + " | ".join(
                [
                    str(item["id"]),
                    f"[{_escape_table(item['basename'])}]({item['dir']}/index.json)",
                    _escape_table(item["status"]),
                    _escape_table(areas),
                    _escape_table(item["kind"]),
                    _escape_table(tags),
                    _escape_table(item["summary"]),
                    reference_links(item["outgoing"]),
                    reference_links(item["incoming"]),
                ]
            )
            + " |"
        )
    return "\n".join(lines) + "\n"


def _synced_registry_and_items(
    registry: PhaseRegistry,
    phases_dir: Path,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    graph = build_reference_graph(registry, phases_dir)
    items = list_phases(registry, phases_dir, graph)
    updated = copy.deepcopy(registry.raw)
    items_by_dir = {item["dir"]: item for item in items}
    for entry in registry.entries:
        raw_entry = updated["phases"][entry.registry_index]
        item = items_by_dir[entry.dir]
        raw_entry["status"] = item["status"]
        if item["summary"] is None:
            raw_entry.pop("summary", None)
        else:
            raw_entry["summary"] = item["summary"]
    return updated, items


def sync_catalog(
    registry_path: Path,
    phases_dir: Path,
    readme_path: Path,
    *,
    check: bool,
    taxonomy_path: Path = DEFAULT_TAXONOMY_PATH,
) -> bool:
    """Return whether drift exists; write registry/README only outside Check mode."""

    registry = load_registry(registry_path, taxonomy_path)
    updated_registry, items = _synced_registry_and_items(registry, phases_dir)
    expected_registry = _json_bytes(updated_registry)
    expected_readme = render_catalog_readme(items).encode("utf-8")
    current_registry = registry_path.read_bytes()
    current_readme = readme_path.read_bytes() if readme_path.is_file() else None
    drift = current_registry != expected_registry or current_readme != expected_readme
    if drift and not check:
        registry_path.write_bytes(expected_registry)
        readme_path.write_bytes(expected_readme)
    return drift


def validate_catalog(
    registry_path: Path,
    phases_dir: Path,
    *,
    strict: bool,
    taxonomy_path: Path = DEFAULT_TAXONOMY_PATH,
) -> list[str]:
    errors: list[str] = []
    try:
        registry = load_registry(registry_path, taxonomy_path)
    except (OSError, json.JSONDecodeError, PhaseRegistryError) as error:
        return [str(error)]
    for entry in registry.entries:
        expected_dir = f"{bucket_name(entry.id)}/{entry.basename}"
        if strict and entry.is_legacy:
            errors.append(f"{entry.basename}: strict validation requires legacy metadata migration")
        if strict and entry.dir != expected_dir:
            errors.append(
                f"{entry.basename}: strict bucket mismatch; expected {expected_dir}, got {entry.dir}"
            )
        phase_dir = phases_dir / Path(*entry.dir.split("/"))
        index_path = phase_dir / "index.json"
        if not phase_dir.is_dir():
            errors.append(f"{entry.dir}: phase directory does not exist")
            continue
        if not index_path.is_file():
            errors.append(f"{entry.dir}: index.json does not exist")
            continue
        try:
            phase_status_from_index(read_phase_index(index_path))
        except (OSError, json.JSONDecodeError, PhaseRegistryError) as error:
            errors.append(f"{entry.dir}/index.json: {error}")

    for source_entry in registry.entries:
        phase_dir = phases_dir / Path(*source_entry.dir.split("/"))
        if not phase_dir.is_dir():
            continue
        for step_path in sorted(phase_dir.glob("step*.md")):
            try:
                text = step_path.read_text(encoding="utf-8")
            except UnicodeDecodeError as error:
                errors.append(f"{step_path.relative_to(phases_dir).as_posix()}: {error}")
                continue
            for match in _PHASE_DOCUMENT_REFERENCE.finditer(text):
                referenced = phases_dir / Path(
                    *f"{match.group('phase')}{match.group('suffix')}".split("/")
                )
                if not referenced.is_file():
                    errors.append(
                        f"{step_path.relative_to(phases_dir).as_posix()}: "
                        f"missing phase reference {match.group('phase')}{match.group('suffix')}"
                    )
    return errors


def _tracked_files(root: Path) -> list[str]:
    completed = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z"],
        check=True,
        capture_output=True,
    )
    return [
        item.decode("utf-8")
        for item in completed.stdout.split(b"\0")
        if item
    ]


def _sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _rewrite_legacy_references(
    text: str,
    moves: Sequence[Mapping[str, Any]],
    *,
    registry_file: bool,
) -> tuple[str, int]:
    rewritten = text
    total = 0
    for move in moves:
        old_phase = str(move["old"])[len("phases/") :]
        new_phase = str(move["new"])[len("phases/") :]
        reference_pattern = re.compile(
            rf"(?<![A-Za-z0-9_.-])(?P<prefix>/?phases/)"
            rf"{re.escape(old_phase)}(?=/|[^A-Za-z0-9-]|$)"
        )
        rewritten, count = reference_pattern.subn(
            lambda match: f"{match.group('prefix')}{new_phase}",
            rewritten,
        )
        total += count
        if registry_file:
            dir_pattern = re.compile(
                rf'(?P<prefix>"dir"\s*:\s*"){re.escape(old_phase)}(?P<suffix>")'
            )
            rewritten, count = dir_pattern.subn(
                lambda match: f"{match.group('prefix')}{new_phase}{match.group('suffix')}",
                rewritten,
            )
            total += count
    return rewritten, total


def _migrated_relative_path(
    relative: str,
    moves: Sequence[Mapping[str, Any]],
) -> str:
    for move in moves:
        old = str(move["old"])
        new = str(move["new"])
        if relative == old:
            return new
        old_prefix = f"{old}/"
        if relative.startswith(old_prefix):
            return f"{new}/{relative[len(old_prefix):]}"
    return relative


def _registry_with_legacy_metadata(
    registry: PhaseRegistry,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    updated = copy.deepcopy(registry.raw)
    backfills: list[dict[str, Any]] = []
    for entry in sorted(registry.entries, key=lambda item: item.id):
        if not entry.is_legacy:
            continue
        classification = registry.taxonomy.legacy_classification_overrides.get(
            entry.basename
        )
        if classification is None:
            raise PhaseRegistryError(
                f"missing legacy classification override for {entry.basename}"
            )
        destination_dir = f"{bucket_name(entry.id)}/{entry.basename}"
        raw_entry = updated["phases"][entry.registry_index]
        raw_entry["dir"] = destination_dir
        raw_entry["id"] = entry.id
        raw_entry["slug"] = entry.slug
        raw_entry["areas"] = list(classification.areas)
        raw_entry["kind"] = classification.kind
        raw_entry["tags"] = list(classification.tags)
        backfills.append(
            {
                "id": entry.id,
                "slug": entry.slug,
                "dir_before": entry.dir,
                "dir_after": destination_dir,
                "areas": list(classification.areas),
                "kind": classification.kind,
                "tags": list(classification.tags),
            }
        )
    return updated, backfills


def _prospective_strict_errors(
    *,
    root: Path,
    phases_dir: Path,
    registry_raw: Mapping[str, Any],
    taxonomy: Taxonomy,
    moves: Sequence[Mapping[str, Any]],
    tracked: Sequence[str],
    file_plans: Sequence[Mapping[str, Any]],
) -> list[str]:
    errors: list[str] = []
    raw_entries = registry_raw.get("phases")
    if not isinstance(raw_entries, list):
        return ["post-migration registry root must contain a phases list"]
    try:
        entries = tuple(
            _parse_entry(raw, index, taxonomy)
            for index, raw in enumerate(raw_entries)
        )
    except PhaseRegistryError as error:
        return [f"post-migration registry: {error}"]

    for field, values in (
        ("id", [entry.id for entry in entries]),
        ("slug", [entry.slug for entry in entries]),
        ("dir", [entry.dir for entry in entries]),
    ):
        duplicates = sorted({value for value in values if values.count(value) > 1})
        if duplicates:
            errors.append(f"post-migration duplicate phase {field}: {duplicates}")

    current_dir_by_destination = {
        str(move["new"])[len("phases/") :]: str(move["old"])[len("phases/") :]
        for move in moves
    }
    for entry in entries:
        expected_dir = f"{bucket_name(entry.id)}/{entry.basename}"
        if entry.is_legacy:
            errors.append(
                f"{entry.basename}: strict validation requires legacy metadata migration"
            )
        if entry.dir != expected_dir:
            errors.append(
                f"{entry.basename}: strict bucket mismatch; expected {expected_dir}, got {entry.dir}"
            )
        current_dir = current_dir_by_destination.get(entry.dir, entry.dir)
        index_path = phases_dir / Path(*current_dir.split("/")) / "index.json"
        if not index_path.is_file():
            errors.append(f"{entry.dir}: post-migration index.json does not exist")
            continue
        try:
            phase_status_from_index(read_phase_index(index_path))
        except (OSError, json.JSONDecodeError, PhaseRegistryError) as error:
            errors.append(f"{entry.dir}/index.json: {error}")

    virtual_files = {
        _migrated_relative_path(path.relative_to(root).as_posix(), moves)
        for path in phases_dir.rglob("*")
        if path.is_file()
    }
    plans_by_before = {str(plan["path_before"]): plan for plan in file_plans}
    for relative in tracked:
        normalized = relative.replace("\\", "/")
        if re.fullmatch(
            r"phases/(?:[0-9]{3}-[0-9]{3}/)?(?:0|[1-9][0-9]*)-"
            r"[a-z0-9]+(?:-[a-z0-9]+)*/step[0-9]+\.md",
            normalized,
        ) is None:
            continue
        plan = plans_by_before.get(normalized)
        if plan is None:
            path = root / Path(*normalized.split("/"))
            if not path.is_file():
                continue
            content = path.read_bytes()
            source_after = _migrated_relative_path(normalized, moves)
        else:
            content = bytes(plan["content"])
            source_after = str(plan["path_after"])
        try:
            text = content.decode("utf-8")
        except UnicodeDecodeError as error:
            errors.append(f"{source_after}: {error}")
            continue
        for match in _PHASE_DOCUMENT_REFERENCE.finditer(text):
            target = f"phases/{match.group('phase')}{match.group('suffix')}"
            if target not in virtual_files:
                errors.append(
                    f"{source_after}: missing post-migration phase reference {target}"
                )
    return errors


def migrate_legacy(
    *,
    root: Path,
    phases_dir: Path,
    registry_path: Path,
    dry_run: bool,
    tracked_files: Sequence[str] | None = None,
    taxonomy_path: Path = DEFAULT_TAXONOMY_PATH,
) -> dict[str, Any]:
    """Plan or apply exact legacy-prefix moves without altering binary contents."""

    root = root.resolve(strict=True)
    phases_dir = phases_dir.resolve(strict=True)
    registry_path = registry_path.resolve(strict=True)
    phases_dir.relative_to(root)
    registry_path.relative_to(root)
    registry = load_registry(registry_path, taxonomy_path)
    migrated_registry, metadata_backfills = _registry_with_legacy_metadata(registry)
    moves = [
        {
            "id": entry.id,
            "old": f"phases/{entry.dir}",
            "new": f"phases/{bucket_name(entry.id)}/{entry.basename}",
        }
        for entry in sorted(registry.entries, key=lambda item: item.id)
        if entry.is_legacy
    ]
    for move in moves:
        source = root / Path(*str(move["old"]).split("/"))
        destination = root / Path(*str(move["new"]).split("/"))
        source.resolve(strict=True).relative_to(root)
        destination.parent.resolve(strict=False).relative_to(root)
        if destination.exists():
            raise PhaseAlreadyExistsError(f"migration destination exists: {move['new']}")

    tracked = list(tracked_files) if tracked_files is not None else _tracked_files(root)
    file_plans: list[dict[str, Any]] = []
    total_references = 0
    registry_relative = registry_path.relative_to(root).as_posix()
    if registry_relative not in {path.replace("\\", "/") for path in tracked}:
        raise PhaseRegistryError("tracked files must include the phase registry")
    for relative in sorted(set(tracked)):
        normalized_relative = relative.replace("\\", "/")
        posix_relative = PurePosixPath(normalized_relative)
        windows_relative = PureWindowsPath(normalized_relative)
        if (
            posix_relative.is_absolute()
            or windows_relative.is_absolute()
            or bool(windows_relative.drive)
            or any(part in {"", ".", ".."} for part in posix_relative.parts)
        ):
            raise UnsafePhasePathError(f"tracked path escapes repository: {relative!r}")
        old_path = root / Path(*normalized_relative.split("/"))
        if not old_path.is_file():
            continue
        old_path.resolve(strict=True).relative_to(root)
        new_relative = normalized_relative
        phase_index_source = False
        for move in moves:
            old_prefix = f"{move['old']}/"
            if new_relative.startswith(old_prefix):
                phase_index_source = new_relative == f"{move['old']}/index.json"
                new_relative = f"{move['new']}/{new_relative[len(old_prefix):]}"
                break
        before = old_path.read_bytes()
        after = before
        reference_count = 0
        binary = b"\0" in before
        if normalized_relative == registry_relative:
            try:
                text = before.decode("utf-8")
            except UnicodeDecodeError as error:
                raise PhaseRegistryError("phase registry must be UTF-8 text") from error
            _, reference_count = _rewrite_legacy_references(
                text,
                moves,
                registry_file=True,
            )
            after = _json_bytes(migrated_registry)
        elif not binary and not phase_index_source:
            try:
                text = before.decode("utf-8")
            except UnicodeDecodeError:
                binary = True
            else:
                rewritten, reference_count = _rewrite_legacy_references(
                    text,
                    moves,
                    registry_file=normalized_relative == registry_relative,
                )
                after = rewritten.encode("utf-8")
        moved = new_relative != normalized_relative
        if moved or after != before:
            file_plans.append(
                {
                    "path_before": normalized_relative,
                    "path_after": new_relative,
                    "sha256_before": _sha256(before),
                    "sha256_after": _sha256(after),
                    "reference_count": reference_count,
                    "binary": binary,
                    "content": after,
                }
            )
        total_references += reference_count

    strict_errors = _prospective_strict_errors(
        root=root,
        phases_dir=phases_dir,
        registry_raw=migrated_registry,
        taxonomy=registry.taxonomy,
        moves=moves,
        tracked=tracked,
        file_plans=file_plans,
    )

    manifest = {
        "dry_run": dry_run,
        "moves": moves,
        "metadata_backfills": metadata_backfills,
        "files": [
            {key: value for key, value in plan.items() if key != "content"}
            for plan in file_plans
        ],
        "reference_count": total_references,
        "strict_ready": not strict_errors,
        "strict_errors": strict_errors,
    }
    if dry_run:
        return manifest
    if strict_errors:
        raise PhaseRegistryError(
            "post-migration strict validation is not ready: " + "; ".join(strict_errors)
        )

    for move in moves:
        source = root / Path(*str(move["old"]).split("/"))
        destination = root / Path(*str(move["new"]).split("/"))
        destination.parent.mkdir(parents=True, exist_ok=True)
        source.rename(destination)
    for plan in file_plans:
        destination = root / Path(*plan["path_after"].split("/"))
        if plan["sha256_before"] != plan["sha256_after"]:
            destination.write_bytes(plan["content"])
    return manifest


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    commands = parser.add_subparsers(dest="command", required=True)

    suggest = commands.add_parser("suggest", aliases=["Suggest"])
    suggest.add_argument("--slug", required=True)
    suggest.add_argument("--step", action="append", default=[])
    suggest.add_argument("--path", action="append", default=[])

    create = commands.add_parser("create", aliases=["Create"])
    create.add_argument("--slug", required=True)
    create.add_argument("--step", action="append", required=True)
    create.add_argument("--path", action="append", default=[])
    create.add_argument("--area", action="append")
    create.add_argument("--kind")
    create.add_argument("--tag", action="append")

    listing = commands.add_parser("list", aliases=["List"])
    listing.add_argument("--status", action="append", default=[])
    listing.add_argument("--area", action="append", default=[])
    listing.add_argument("--kind", action="append", default=[])
    listing.add_argument("--tag", action="append", default=[])

    show = commands.add_parser("show", aliases=["Show"])
    show.add_argument("selector")

    validate = commands.add_parser("validate", aliases=["Validate"])
    validate.add_argument("--strict", action="store_true")

    sync = commands.add_parser("sync", aliases=["Sync"])
    sync.add_argument("--check", action="store_true")

    migrate = commands.add_parser(
        "migrate-legacy",
        aliases=["MigrateLegacy", "migratelegacy"],
    )
    migrate.add_argument("--dry-run", action="store_true")
    return parser


def _print_json(value: object) -> None:
    print(json.dumps(value, indent=2, ensure_ascii=False, sort_keys=True))


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    root = args.root.resolve()
    phases_dir = root / "phases"
    registry_path = phases_dir / "index.json"
    taxonomy_path = _taxonomy_for_root(root)
    try:
        command = args.command.lower().replace("_", "-")
        if command == "suggest":
            suggestion = suggest_metadata(
                slug=args.slug,
                step_names=args.step,
                intended_paths=args.path,
                taxonomy_path=taxonomy_path,
            )
            _print_json(suggestion)
            return 2 if suggestion["status"] == "ambiguous" else 0
        if command == "create":
            suggestion = suggest_metadata(
                slug=args.slug,
                step_names=args.step,
                intended_paths=args.path,
                taxonomy_path=taxonomy_path,
            )
            areas = args.area or suggestion["areas"]
            kind = args.kind or suggestion["kind"]
            tags = args.tag or suggestion["tags"]
            missing = [
                field
                for field, value in (("areas", areas), ("kind", kind), ("tags", tags))
                if not value
            ]
            if missing:
                suggestion["required_overrides"] = missing
                _print_json(suggestion)
                return 2
            created = create_phase(
                phases_dir=phases_dir,
                registry_path=registry_path,
                slug=args.slug,
                step_names=args.step,
                areas=areas,
                kind=kind,
                tags=tags,
                taxonomy_path=taxonomy_path,
            )
            _print_json({"created": created})
            return 0
        registry = load_registry(registry_path, taxonomy_path)
        if command == "list":
            _print_json(
                {
                    "phases": list_phases(
                        registry,
                        phases_dir,
                        statuses=args.status,
                        areas=args.area,
                        kinds=args.kind,
                        tags=args.tag,
                    )
                }
            )
            return 0
        if command == "show":
            _print_json(show_phase(args.selector, registry, phases_dir))
            return 0
        if command == "validate":
            errors = validate_catalog(
                registry_path,
                phases_dir,
                strict=args.strict,
                taxonomy_path=taxonomy_path,
            )
            _print_json(
                {
                    "valid": not errors,
                    "strict": args.strict,
                    "phase_count": len(registry.entries),
                    "errors": errors,
                }
            )
            return 1 if errors else 0
        if command == "sync":
            drift = sync_catalog(
                registry_path,
                phases_dir,
                phases_dir / "README.md",
                check=args.check,
                taxonomy_path=taxonomy_path,
            )
            _print_json({"check": args.check, "drift": drift})
            return 1 if args.check and drift else 0
        if command in {"migratelegacy", "migrate-legacy"}:
            manifest = migrate_legacy(
                root=root,
                phases_dir=phases_dir,
                registry_path=registry_path,
                dry_run=args.dry_run,
                taxonomy_path=taxonomy_path,
            )
            _print_json(manifest)
            return 0
        raise PhaseRegistryError(f"unsupported command: {args.command}")
    except (OSError, subprocess.CalledProcessError, json.JSONDecodeError, PhaseRegistryError) as error:
        _print_json({"error": type(error).__name__, "message": str(error)})
        return 1


if __name__ == "__main__":
    sys.exit(main())
