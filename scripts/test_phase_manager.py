"""Phase registry core and catalog CLI contracts."""

import json
import sys
import tempfile
from pathlib import Path

import pytest


SCRIPTS_DIR = Path(__file__).resolve().parent
ROOT = SCRIPTS_DIR.parent
sys.path.insert(0, str(SCRIPTS_DIR))

import phase_manager as pm  # noqa: E402


def _write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )


def _new_entry(
    phase_id: int = 40,
    slug: str = "nested-phase",
    **overrides: object,
) -> dict:
    entry = {
        "id": phase_id,
        "slug": slug,
        "dir": f"{pm.bucket_name(phase_id)}/{phase_id}-{slug}",
        "status": "pending",
        "areas": ["harness"],
        "kind": "infrastructure",
        "tags": ["phase-registry"],
    }
    entry.update(overrides)
    return entry


@pytest.fixture
def phase_tmp_path() -> Path:
    """Isolate files without depending on a stale repository basetemp ACL."""

    with tempfile.TemporaryDirectory(prefix="todoquest-phase-manager-") as directory:
        yield Path(directory)


@pytest.fixture
def mixed_registry(phase_tmp_path: Path) -> tuple[Path, Path]:
    phases_dir = phase_tmp_path / "phases"
    legacy_dir = phases_dir / "39-legacy-phase"
    nested_dir = phases_dir / "040-049" / "40-nested-phase"
    legacy_dir.mkdir(parents=True)
    nested_dir.mkdir(parents=True)
    _write_json(
        legacy_dir / "index.json",
        {
            "project": "Todo Quest",
            "phase": "39-legacy-phase",
            "steps": [{"step": 0, "name": "done", "status": "completed"}],
            "completed_at": "2026-01-01T00:00:00+0900",
        },
    )
    _write_json(
        nested_dir / "index.json",
        {
            "project": "Todo Quest",
            "phase": "40-nested-phase",
            "steps": [{"step": 0, "name": "work", "status": "pending"}],
        },
    )
    registry_path = phases_dir / "index.json"
    _write_json(
        registry_path,
        {
            "phases": [
                {
                    "dir": "39-legacy-phase",
                    "status": "completed",
                    "summary": "기존 기록을 그대로 보존한다.",
                    "completed_at": "2026-01-01T00:00:00+0900",
                },
                _new_entry(),
            ]
        },
    )
    return phases_dir, registry_path


@pytest.mark.parametrize(
    ("phase_id", "expected"),
    [
        (0, "000-009"),
        (9, "000-009"),
        (10, "010-019"),
        (39, "030-039"),
        (40, "040-049"),
        (99, "090-099"),
        (100, "100-109"),
        (109, "100-109"),
    ],
)
def test_bucket_name_covers_decimal_boundaries(phase_id: int, expected: str) -> None:
    assert pm.bucket_name(phase_id) == expected


@pytest.mark.parametrize(("phase_id", "size"), [(-1, 10), (True, 10), (1, 0)])
def test_bucket_name_rejects_invalid_arguments(phase_id: int, size: int) -> None:
    with pytest.raises(ValueError):
        pm.bucket_name(phase_id, size)


@pytest.mark.parametrize(
    ("basename", "expected"),
    [
        ("0-design-docs", (0, "design-docs")),
        ("100-combat-rewards-v2", (100, "combat-rewards-v2")),
    ],
)
def test_parse_phase_basename_requires_id_and_kebab_slug(
    basename: str,
    expected: tuple[int, str],
) -> None:
    assert pm.parse_phase_basename(basename) == expected


@pytest.mark.parametrize(
    "basename",
    [
        "-1-negative",
        "01-leading-zero",
        "1-CamelCase",
        "1-snake_case",
        "1-double--dash",
        "1-trailing-",
        "missing-id",
    ],
)
def test_parse_phase_basename_rejects_invalid_names(basename: str) -> None:
    with pytest.raises(pm.PhaseRegistryError):
        pm.parse_phase_basename(basename)


def test_new_entry_metadata_and_tags_are_normalized(phase_tmp_path: Path) -> None:
    registry_path = phase_tmp_path / "index.json"
    _write_json(
        registry_path,
        {
            "phases": [
                _new_entry(tags=["Phase Registry", "combat_rewards", "phase-registry"])
            ]
        },
    )

    registry = pm.load_registry(registry_path, ROOT / "phases" / "taxonomy.json")

    entry = registry.entries[0]
    assert entry.id == 40
    assert entry.slug == "nested-phase"
    assert entry.basename == "40-nested-phase"
    assert entry.dir == "040-049/40-nested-phase"
    assert entry.areas == ("harness",)
    assert entry.kind == "infrastructure"
    assert entry.tags == ("phase-registry", "combat-rewards")
    assert entry.is_legacy is False


def test_mixed_legacy_and_nested_registry_resolve_to_same_phase(
    mixed_registry: tuple[Path, Path],
) -> None:
    phases_dir, registry_path = mixed_registry
    registry = pm.load_registry(registry_path, ROOT / "phases" / "taxonomy.json")

    legacy_by_id = pm.resolve_phase(39, registry, phases_dir)
    legacy_by_basename = pm.resolve_phase("39-legacy-phase", registry, phases_dir)
    nested_by_id = pm.resolve_phase("40", registry, phases_dir)
    nested_by_basename = pm.resolve_phase("40-nested-phase", registry, phases_dir)
    nested_by_dir = pm.resolve_phase(
        "040-049/40-nested-phase",
        registry,
        phases_dir,
    )

    assert legacy_by_id == legacy_by_basename
    assert legacy_by_id.path == (phases_dir / "39-legacy-phase").resolve()
    assert legacy_by_id.entry.is_legacy is True
    assert nested_by_id == nested_by_basename == nested_by_dir
    assert nested_by_id.path == (
        phases_dir / "040-049" / "40-nested-phase"
    ).resolve()


def test_legacy_registry_lookup_does_not_rewrite_any_file(
    mixed_registry: tuple[Path, Path],
) -> None:
    phases_dir, registry_path = mixed_registry
    phase_index_path = phases_dir / "39-legacy-phase" / "index.json"
    before_registry = registry_path.read_bytes()
    before_phase_index = phase_index_path.read_bytes()

    registry = pm.load_registry(registry_path, ROOT / "phases" / "taxonomy.json")
    resolved = pm.resolve_phase("39-legacy-phase", registry, phases_dir)
    phase_index = pm.read_phase_index(resolved.path / "index.json")
    assert pm.phase_status_from_index(phase_index) == "completed"

    assert registry_path.read_bytes() == before_registry
    assert phase_index_path.read_bytes() == before_phase_index


@pytest.mark.parametrize(
    "selector",
    ["999-missing-phase", "090-099/99-missing-phase"],
)
def test_resolver_rejects_unknown_selector(
    mixed_registry: tuple[Path, Path],
    selector: str,
) -> None:
    phases_dir, registry_path = mixed_registry
    registry = pm.load_registry(registry_path, ROOT / "phases" / "taxonomy.json")

    with pytest.raises(pm.PhaseNotFoundError):
        pm.resolve_phase(selector, registry, phases_dir)


@pytest.mark.parametrize(
    "selector",
    ["../39-legacy-phase", "/phases/39-legacy-phase", r"C:\phases\39-legacy-phase"],
)
def test_resolver_rejects_absolute_and_parent_selectors(
    mixed_registry: tuple[Path, Path],
    selector: str,
) -> None:
    phases_dir, registry_path = mixed_registry
    registry = pm.load_registry(registry_path, ROOT / "phases" / "taxonomy.json")

    with pytest.raises(pm.UnsafePhasePathError):
        pm.resolve_phase(selector, registry, phases_dir)


@pytest.mark.parametrize("unsafe_dir", ["../outside/40-nested-phase", "/tmp/40-nested-phase", r"C:\tmp\40-nested-phase"])
def test_registry_rejects_absolute_and_parent_dirs(
    phase_tmp_path: Path,
    unsafe_dir: str,
) -> None:
    registry_path = phase_tmp_path / "index.json"
    _write_json(registry_path, {"phases": [_new_entry(dir=unsafe_dir)]})

    with pytest.raises(pm.UnsafePhasePathError):
        pm.load_registry(registry_path, ROOT / "phases" / "taxonomy.json")


@pytest.mark.parametrize(
    "entries",
    [
        [_new_entry(), _new_entry(slug="other", dir="040-049/40-other")],
        [_new_entry(), _new_entry(41, "nested-phase")],
    ],
)
def test_registry_rejects_duplicate_id_or_slug(
    phase_tmp_path: Path,
    entries: list[dict],
) -> None:
    registry_path = phase_tmp_path / "index.json"
    _write_json(registry_path, {"phases": entries})

    with pytest.raises(pm.DuplicatePhaseError):
        pm.load_registry(registry_path, ROOT / "phases" / "taxonomy.json")


@pytest.mark.parametrize(
    "overrides",
    [
        {"status": "running"},
        {"areas": []},
        {"areas": ["harness", "harness"]},
        {"areas": ["unknown"]},
        {"kind": ["feature", "fix"]},
        {"kind": "migration"},
    ],
)
def test_registry_rejects_invalid_status_areas_and_kind(
    phase_tmp_path: Path,
    overrides: dict,
) -> None:
    registry_path = phase_tmp_path / "index.json"
    _write_json(registry_path, {"phases": [_new_entry(**overrides)]})

    with pytest.raises(pm.PhaseRegistryError):
        pm.load_registry(registry_path, ROOT / "phases" / "taxonomy.json")


def test_partial_new_metadata_is_not_treated_as_legacy(phase_tmp_path: Path) -> None:
    registry_path = phase_tmp_path / "index.json"
    _write_json(
        registry_path,
        {
            "phases": [
                {
                    "dir": "40-nested-phase",
                    "status": "pending",
                    "areas": ["harness"],
                }
            ]
        },
    )

    with pytest.raises(pm.PhaseRegistryError):
        pm.load_registry(registry_path, ROOT / "phases" / "taxonomy.json")


@pytest.mark.parametrize(
    ("steps", "expected"),
    [
        ([{"step": 0, "status": "pending"}], "pending"),
        ([{"step": 0, "status": "completed"}], "completed"),
        ([{"step": 0, "status": "error"}], "error"),
        ([{"step": 0, "status": "blocked"}], "blocked"),
        (
            [
                {"step": 0, "status": "completed"},
                {"step": 1, "status": "pending"},
            ],
            "pending",
        ),
    ],
)
def test_phase_step_status_is_execution_source(
    steps: list[dict],
    expected: str,
) -> None:
    phase_index = {
        "status": "completed",
        "summary": "상위 필드가 있어도 step 상태를 덮지 않는다.",
        "steps": steps,
    }

    assert pm.phase_status_from_index(phase_index) == expected


def test_registry_status_sync_returns_display_copy_without_rewriting_source(
    mixed_registry: tuple[Path, Path],
) -> None:
    phases_dir, registry_path = mixed_registry
    phase_index_path = phases_dir / "040-049" / "40-nested-phase" / "index.json"
    registry_bytes = registry_path.read_bytes()
    phase_index_bytes = phase_index_path.read_bytes()
    registry = pm.load_registry(registry_path, ROOT / "phases" / "taxonomy.json")
    resolved = pm.resolve_phase(40, registry, phases_dir)
    phase_index = pm.read_phase_index(phase_index_path)

    synced = pm.registry_with_synced_status(registry, resolved.entry, phase_index)

    assert synced["phases"][1]["status"] == "pending"
    assert registry.raw["phases"][1]["status"] == "pending"
    assert synced["phases"][0]["summary"] == "기존 기록을 그대로 보존한다."
    assert registry_path.read_bytes() == registry_bytes
    assert phase_index_path.read_bytes() == phase_index_bytes


def test_sync_changes_only_registry_display_status_and_preserves_phase_shape(
    phase_tmp_path: Path,
) -> None:
    registry_path = phase_tmp_path / "index.json"
    phase_index_path = phase_tmp_path / "phase-index.json"
    _write_json(registry_path, {"phases": [_new_entry(status="pending")]})
    phase_index = {
        "project": "Todo Quest",
        "phase": "40-nested-phase",
        "custom": {"historical": True},
        "steps": [
            {
                "step": 0,
                "name": "done",
                "status": "completed",
                "summary": "원본 요약",
                "started_at": "2026-01-01T00:00:00+0900",
                "completed_at": "2026-01-01T00:01:00+0900",
            }
        ],
    }
    _write_json(phase_index_path, phase_index)
    before = phase_index_path.read_bytes()
    registry = pm.load_registry(registry_path, ROOT / "phases" / "taxonomy.json")

    synced = pm.registry_with_synced_status(
        registry,
        registry.entries[0],
        pm.read_phase_index(phase_index_path),
    )

    assert synced["phases"][0]["status"] == "completed"
    assert phase_index_path.read_bytes() == before
    assert pm.read_phase_index(phase_index_path) == phase_index


def _catalog_fixture(phase_tmp_path: Path) -> tuple[Path, Path]:
    phases_dir = phase_tmp_path / "phases"
    first_dir = phases_dir / "040-049" / "40-harness-catalog"
    second_dir = phases_dir / "41-schedule-reminder"
    first_dir.mkdir(parents=True)
    second_dir.mkdir(parents=True)
    _write_json(
        first_dir / "index.json",
        {
            "project": "Todo Quest",
            "phase": "40-harness-catalog",
            "steps": [
                {
                    "step": 0,
                    "name": "catalog-core",
                    "status": "completed",
                    "summary": "마지막 완료 step 요약",
                },
                {"step": 1, "name": "catalog-cli", "status": "pending"},
            ],
        },
    )
    _write_json(
        second_dir / "index.json",
        {
            "project": "Todo Quest",
            "phase": "41-schedule-reminder",
            "summary": "일정 알림 최종 요약",
            "steps": [
                {"step": 0, "name": "reminder", "status": "completed"}
            ],
        },
    )
    (first_dir / "step0.md").write_text(
        "\n".join(
            [
                "# Catalog",
                "- `/phases/040-049/40-harness-catalog/index.json`",
                "- `/phases/41-schedule-reminder/step0.md`",
            ]
        ),
        encoding="utf-8",
    )
    (first_dir / "notes.md").write_text(
        "step 문서가 아니므로 /phases/41-schedule-reminder/index.json 은 무시한다.",
        encoding="utf-8",
    )
    (second_dir / "step0.md").write_text(
        "`/phases/040-049/40-harness-catalog/step0.md`를 참고한다.",
        encoding="utf-8",
    )
    registry_path = phases_dir / "index.json"
    _write_json(
        registry_path,
        {
            "phases": [
                _new_entry(
                    phase_id=40,
                    slug="harness-catalog",
                    dir="040-049/40-harness-catalog",
                    status="completed",
                    tags=["phase-catalog"],
                ),
                {
                    "dir": "41-schedule-reminder",
                    "status": "pending",
                },
            ]
        },
    )
    return phases_dir, registry_path


def test_suggest_uses_deterministic_taxonomy_rules_and_reports_ambiguity() -> None:
    suggestion = pm.suggest_metadata(
        slug="harness-phase-catalog",
        step_names=["phase-manager-cli"],
        intended_paths=["scripts/phase_manager.py", "phases/index.json"],
    )

    assert suggestion["status"] == "classified"
    assert suggestion["areas"] == ["harness"]
    assert suggestion["kind"] == "infrastructure"
    assert suggestion["tags"] == ["harness-phase-catalog"]
    assert suggestion["evidence"]["areas"][0]["value"] == "harness"

    ambiguous = pm.suggest_metadata(
        slug="misc-work",
        step_names=["do-work"],
        intended_paths=["unknown/location.txt"],
    )
    assert ambiguous["status"] == "ambiguous"
    assert set(ambiguous["ambiguities"]) == {"areas", "kind"}
    assert ambiguous["areas"] == []
    assert ambiguous["kind"] is None


def test_default_taxonomy_explicitly_classifies_only_phase_history_zero_to_forty_three() -> None:
    taxonomy = pm.load_taxonomy(ROOT / "phases" / "taxonomy.json")
    registry = pm.load_registry(
        ROOT / "phases" / "index.json",
        ROOT / "phases" / "taxonomy.json",
    )
    expected_basenames = [
        entry.basename for entry in registry.entries if entry.id in range(44)
    ]

    assert list(taxonomy.legacy_classification_overrides) == expected_basenames
    assert {
        pm.parse_phase_basename(basename)[0]
        for basename in taxonomy.legacy_classification_overrides
    } == set(range(44))
    assert "44-phase-history-migration" not in taxonomy.legacy_classification_overrides


def test_create_allocates_next_id_bucket_and_pending_steps_without_timestamp(
    phase_tmp_path: Path,
) -> None:
    phases_dir = phase_tmp_path / "phases"
    legacy_dir = phases_dir / "9-old"
    legacy_dir.mkdir(parents=True)
    _write_json(
        legacy_dir / "index.json",
        {"phase": "9-old", "steps": [{"step": 0, "status": "completed"}]},
    )
    registry_path = phases_dir / "index.json"
    _write_json(
        registry_path,
        {"phases": [{"dir": "9-old", "status": "completed"}]},
    )

    created = pm.create_phase(
        phases_dir=phases_dir,
        registry_path=registry_path,
        slug="harness-catalog",
        step_names=["registry-core", "catalog-cli"],
        areas=["harness"],
        kind="infrastructure",
        tags=["phase-catalog"],
    )

    assert created["id"] == 10
    assert created["dir"] == "010-019/10-harness-catalog"
    phase_index_path = phases_dir / created["dir"] / "index.json"
    phase_index = json.loads(phase_index_path.read_text(encoding="utf-8"))
    assert phase_index == {
        "project": "Todo Quest",
        "phase": "10-harness-catalog",
        "steps": [
            {"step": 0, "name": "registry-core", "status": "pending"},
            {"step": 1, "name": "catalog-cli", "status": "pending"},
        ],
    }
    assert not any("_at" in key for key in phase_index)

    with pytest.raises(pm.PhaseAlreadyExistsError):
        pm.create_phase(
            phases_dir=phases_dir,
            registry_path=registry_path,
            slug="harness-catalog",
            step_names=["duplicate"],
            areas=["harness"],
            kind="infrastructure",
            tags=["phase-catalog"],
        )


def test_catalog_filters_and_show_calculate_only_step_markdown_references(
    phase_tmp_path: Path,
) -> None:
    phases_dir, registry_path = _catalog_fixture(phase_tmp_path)
    registry = pm.load_registry(registry_path, ROOT / "phases" / "taxonomy.json")

    graph = pm.build_reference_graph(registry, phases_dir)
    first = pm.show_phase("40-harness-catalog", registry, phases_dir, graph)
    assert first["outgoing"] == ["41-schedule-reminder"]
    assert first["incoming"] == ["41-schedule-reminder"]
    assert "040-049/40-harness-catalog" not in first["outgoing"]

    listed = pm.list_phases(
        registry,
        phases_dir,
        graph,
        statuses=["pending"],
    )
    assert [item["basename"] for item in listed] == ["40-harness-catalog"]
    assert listed[0]["summary"] == "마지막 완료 step 요약"
    assert pm.list_phases(
        registry,
        phases_dir,
        graph,
        areas=["harness"],
        kinds=["infrastructure"],
        tags=["phase-catalog"],
    ) == listed


def test_sync_readme_is_deterministic_uses_summary_fallback_and_check_is_read_only(
    phase_tmp_path: Path,
) -> None:
    phases_dir, registry_path = _catalog_fixture(phase_tmp_path)
    readme_path = phases_dir / "README.md"
    phase_indexes_before = {
        path: path.read_bytes()
        for path in phases_dir.glob("**/index.json")
        if path != registry_path
    }

    assert pm.sync_catalog(registry_path, phases_dir, readme_path, check=True) is True
    assert not readme_path.exists()
    assert pm.sync_catalog(registry_path, phases_dir, readme_path, check=False) is True
    first_registry = registry_path.read_bytes()
    first_readme = readme_path.read_bytes()
    synced = json.loads(first_registry)
    assert synced["phases"][0]["summary"] == "마지막 완료 step 요약"
    assert synced["phases"][1]["summary"] == "일정 알림 최종 요약"
    assert synced["phases"][1]["status"] == "completed"
    assert b"generated" not in first_readme.lower()
    assert "Outgoing" in first_readme.decode("utf-8")
    assert phase_indexes_before == {
        path: path.read_bytes() for path in phases_dir.glob("**/index.json")
        if path != registry_path
    }

    assert pm.sync_catalog(registry_path, phases_dir, readme_path, check=False) is False
    assert pm.sync_catalog(registry_path, phases_dir, readme_path, check=True) is False
    assert registry_path.read_bytes() == first_registry
    assert readme_path.read_bytes() == first_readme


def test_validate_supports_bootstrap_but_strict_requires_metadata_and_buckets(
    phase_tmp_path: Path,
) -> None:
    phases_dir, registry_path = _catalog_fixture(phase_tmp_path)

    assert pm.validate_catalog(registry_path, phases_dir, strict=False) == []
    errors = pm.validate_catalog(registry_path, phases_dir, strict=True)
    assert any("legacy metadata" in error for error in errors)
    assert any("bucket" in error for error in errors)

    (phases_dir / "41-schedule-reminder" / "step0.md").write_text(
        "`/phases/999-missing/step0.md`",
        encoding="utf-8",
    )
    errors = pm.validate_catalog(registry_path, phases_dir, strict=False)
    assert any("999-missing/step0.md" in error for error in errors)


def _taxonomy_with_legacy_overrides(
    phase_tmp_path: Path,
    overrides: dict[str, dict],
) -> Path:
    taxonomy = json.loads(
        (ROOT / "phases" / "taxonomy.json").read_text(encoding="utf-8")
    )
    taxonomy["legacy_classification_overrides"] = overrides
    taxonomy_path = phase_tmp_path / "taxonomy.json"
    _write_json(taxonomy_path, taxonomy)
    return taxonomy_path


def _migration_fixture(phase_tmp_path: Path) -> tuple[Path, Path, list[str], Path]:
    phases_dir = phase_tmp_path / "phases"
    source_dir = phases_dir / "9-old-phase"
    nested_dir = phases_dir / "010-019" / "10-current-phase"
    source_dir.mkdir(parents=True)
    nested_dir.mkdir(parents=True)
    phase_index = {
        "project": "Todo Quest",
        "phase": "9-old-phase",
        "status": "completed",
        "summary": "과거 /phases/9-old-phase/index.json 상태와 요약",
        "completed_at": "2026-01-01T00:00:00+0900",
        "steps": [{"step": 0, "status": "completed", "summary": "완료"}],
    }
    _write_json(source_dir / "index.json", phase_index)
    (source_dir / "step0.md").write_text(
        "self `/phases/9-old-phase/index.json`\n본문 9-old-phase 보존",
        encoding="utf-8",
    )
    docs_dir = phase_tmp_path / "docs"
    docs_dir.mkdir()
    (docs_dir / "refs.md").write_text(
        "exact /phases/9-old-phase/step0.md\n"
        "similar /phases/9-old-phase-extra/step0.md\n",
        encoding="utf-8",
    )
    binary = b"\x00/phases/9-old-phase/step0.md\xff"
    (docs_dir / "asset.bin").write_bytes(binary)
    nested_index = {
        "project": "Todo Quest",
        "phase": "10-current-phase",
        "steps": [{"step": 0, "name": "pending", "status": "pending"}],
    }
    _write_json(nested_dir / "index.json", nested_index)
    registry_path = phases_dir / "index.json"
    historical_entry = {
        "dir": "9-old-phase",
        "status": "completed",
        "summary": "registry summary\r\n둘째 줄",
        "completed_at": "2026-01-01T00:00:00+0900",
        "blocked_at": "legacy-blocked-byte-value",
        "failed_at": "legacy-failed-byte-value",
    }
    nested_entry = _new_entry(
        phase_id=10,
        slug="current-phase",
        dir="010-019/10-current-phase",
        status="pending",
        tags=["current-phase"],
    )
    _write_json(
        registry_path,
        {
            "phases": [
                historical_entry,
                nested_entry,
            ]
        },
    )
    tracked = [
        "phases/index.json",
        "phases/9-old-phase/index.json",
        "phases/9-old-phase/step0.md",
        "phases/010-019/10-current-phase/index.json",
        "docs/refs.md",
        "docs/asset.bin",
    ]
    taxonomy_path = _taxonomy_with_legacy_overrides(
        phase_tmp_path,
        {
            "9-old-phase": {
                "areas": ["harness"],
                "kind": "infrastructure",
                "tags": ["legacy-history", "migration"],
            }
        },
    )
    return phases_dir, registry_path, tracked, taxonomy_path


def test_migrate_legacy_dry_run_outputs_hash_manifest_without_changes(
    phase_tmp_path: Path,
) -> None:
    phases_dir, registry_path, tracked, taxonomy_path = _migration_fixture(phase_tmp_path)
    before = {
        path: (phase_tmp_path / path).read_bytes() for path in tracked
    }

    manifest = pm.migrate_legacy(
        root=phase_tmp_path,
        phases_dir=phases_dir,
        registry_path=registry_path,
        dry_run=True,
        tracked_files=tracked,
        taxonomy_path=taxonomy_path,
    )

    assert manifest["dry_run"] is True
    assert manifest["moves"] == [
        {
            "id": 9,
            "old": "phases/9-old-phase",
            "new": "phases/000-009/9-old-phase",
        }
    ]
    assert manifest["reference_count"] == 3
    assert manifest["metadata_backfills"] == [
        {
            "id": 9,
            "slug": "old-phase",
            "dir_before": "9-old-phase",
            "dir_after": "000-009/9-old-phase",
            "areas": ["harness"],
            "kind": "infrastructure",
            "tags": ["legacy-history", "migration"],
        }
    ]
    assert manifest["strict_ready"] is True
    assert all("sha256_before" in item for item in manifest["files"])
    phase_index_plan = next(
        item
        for item in manifest["files"]
        if item["path_before"] == "phases/9-old-phase/index.json"
    )
    assert phase_index_plan["binary"] is False
    assert phase_index_plan["sha256_before"] == phase_index_plan["sha256_after"]
    assert before == {path: (phase_tmp_path / path).read_bytes() for path in tracked}
    assert not (phases_dir / "000-009").exists()


def test_migrate_legacy_apply_rewrites_exact_text_only_and_preserves_binary_and_index(
    phase_tmp_path: Path,
) -> None:
    phases_dir, registry_path, tracked, taxonomy_path = _migration_fixture(phase_tmp_path)
    binary_before = (phase_tmp_path / "docs" / "asset.bin").read_bytes()
    phase_index_before = (phases_dir / "9-old-phase" / "index.json").read_bytes()
    registry_before = json.loads(registry_path.read_text(encoding="utf-8"))
    historical_before = registry_before["phases"][0]
    nested_before = registry_before["phases"][1]

    manifest = pm.migrate_legacy(
        root=phase_tmp_path,
        phases_dir=phases_dir,
        registry_path=registry_path,
        dry_run=False,
        tracked_files=tracked,
        taxonomy_path=taxonomy_path,
    )

    destination = phases_dir / "000-009" / "9-old-phase"
    assert manifest["dry_run"] is False
    assert manifest["strict_ready"] is True
    assert destination.is_dir()
    assert not (phases_dir / "9-old-phase").exists()
    assert (destination / "index.json").read_bytes() == phase_index_before
    refs = (phase_tmp_path / "docs" / "refs.md").read_text(encoding="utf-8")
    assert "/phases/000-009/9-old-phase/step0.md" in refs
    assert "/phases/9-old-phase-extra/step0.md" in refs
    assert (phase_tmp_path / "docs" / "asset.bin").read_bytes() == binary_before
    registry_raw = json.loads(registry_path.read_text(encoding="utf-8"))
    assert registry_raw["phases"][0] == {
        "id": 9,
        "slug": "old-phase",
        "dir": "000-009/9-old-phase",
        "status": "completed",
        "summary": "registry summary\r\n둘째 줄",
        "completed_at": "2026-01-01T00:00:00+0900",
        "blocked_at": "legacy-blocked-byte-value",
        "failed_at": "legacy-failed-byte-value",
        "areas": ["harness"],
        "kind": "infrastructure",
        "tags": ["legacy-history", "migration"],
    }
    for field in ("status", "summary", "completed_at", "blocked_at", "failed_at"):
        assert registry_raw["phases"][0][field] == historical_before[field]
        assert registry_raw["phases"][0][field].encode("utf-8") == historical_before[
            field
        ].encode("utf-8")
    assert registry_raw["phases"][1] == nested_before
    assert [entry["dir"] for entry in registry_raw["phases"]] == [
        "000-009/9-old-phase",
        "010-019/10-current-phase",
    ]
    assert pm.validate_catalog(
        registry_path,
        phases_dir,
        strict=True,
        taxonomy_path=taxonomy_path,
    ) == []


def test_migrate_legacy_rejects_missing_override(phase_tmp_path: Path) -> None:
    phases_dir, registry_path, tracked, _ = _migration_fixture(phase_tmp_path)
    taxonomy_path = _taxonomy_with_legacy_overrides(phase_tmp_path, {})

    with pytest.raises(pm.PhaseRegistryError, match="classification override"):
        pm.migrate_legacy(
            root=phase_tmp_path,
            phases_dir=phases_dir,
            registry_path=registry_path,
            dry_run=True,
            tracked_files=tracked,
            taxonomy_path=taxonomy_path,
        )


@pytest.mark.parametrize(
    "override",
    [
        {"areas": [], "kind": "infrastructure", "tags": ["legacy"]},
        {"areas": ["unknown"], "kind": "infrastructure", "tags": ["legacy"]},
        {"areas": ["harness"], "kind": ["feature"], "tags": ["legacy"]},
        {"areas": ["harness"], "kind": "unknown", "tags": ["legacy"]},
        {"areas": ["harness"], "kind": "infrastructure", "tags": ["Not Kebab"]},
    ],
)
def test_taxonomy_rejects_invalid_legacy_override(
    phase_tmp_path: Path,
    override: dict,
) -> None:
    taxonomy_path = _taxonomy_with_legacy_overrides(
        phase_tmp_path,
        {"9-old-phase": override},
    )

    with pytest.raises(pm.PhaseRegistryError, match="legacy classification override"):
        pm.load_taxonomy(taxonomy_path)


def test_cli_parsing_returns_structured_nonzero_ambiguity_and_filters(
    phase_tmp_path: Path,
    capsys: pytest.CaptureFixture[str],
) -> None:
    phases_dir, _ = _catalog_fixture(phase_tmp_path)

    exit_code = pm.main(
        [
            "--root",
            str(phase_tmp_path),
            "Suggest",
            "--slug",
            "misc-work",
            "--step",
            "do-work",
            "--path",
            "unknown/file.txt",
        ]
    )
    output = json.loads(capsys.readouterr().out)
    assert exit_code != 0
    assert output["status"] == "ambiguous"

    exit_code = pm.main(
        [
            "--root",
            str(phase_tmp_path),
            "List",
            "--status",
            "pending",
        ]
    )
    output = json.loads(capsys.readouterr().out)
    assert exit_code == 0
    assert output["phases"][0]["dir"] == "040-049/40-harness-catalog"
    assert phases_dir.exists()
