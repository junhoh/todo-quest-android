"""
execute.py 리팩터링 안전망 테스트.
리팩터링 전후 동작이 동일한지 검증한다.
"""

import json
import os
import subprocess
import sys
import textwrap
from datetime import datetime, timezone, timedelta
from pathlib import Path
from unittest.mock import patch, MagicMock

import pytest

sys.path.insert(0, str(Path(__file__).parent))
import execute as ex


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def tmp_project(tmp_path):
    """phases/, AGENTS.md, docs/ 를 갖춘 임시 프로젝트 구조."""
    phases_dir = tmp_path / "phases"
    phases_dir.mkdir()
    (phases_dir / "index.json").write_text(
        json.dumps(
            {
                "phases": [
                    {"dir": "0-mvp", "status": "pending"},
                ]
            },
            indent=2,
        )
    )

    agents_md = tmp_path / "AGENTS.md"
    agents_md.write_text("# Rules\n- rule one\n- rule two")

    docs_dir = tmp_path / "docs"
    docs_dir.mkdir()
    (docs_dir / "arch.md").write_text("# Architecture\nSome content")
    (docs_dir / "guide.md").write_text("# Guide\nAnother doc")

    return tmp_path


@pytest.fixture
def phase_dir(tmp_project):
    """step 3개를 가진 phase 디렉토리."""
    d = tmp_project / "phases" / "0-mvp"
    d.mkdir()

    index = {
        "project": "TestProject",
        "phase": "mvp",
        "steps": [
            {"step": 0, "name": "setup", "status": "completed", "summary": "프로젝트 초기화 완료"},
            {"step": 1, "name": "core", "status": "completed", "summary": "핵심 로직 구현"},
            {"step": 2, "name": "ui", "status": "pending"},
        ],
    }
    (d / "index.json").write_text(json.dumps(index, indent=2, ensure_ascii=False))
    (d / "step2.md").write_text("# Step 2: UI\n\nUI를 구현하세요.")

    return d


@pytest.fixture
def top_index(tmp_project):
    """phases/index.json (top-level)."""
    top = {
        "phases": [
            {"dir": "0-mvp", "status": "pending"},
            {"dir": "1-polish", "status": "pending"},
        ]
    }
    p = tmp_project / "phases" / "index.json"
    p.write_text(json.dumps(top, indent=2))
    return p


@pytest.fixture
def nested_phase_dir(tmp_project):
    """신규 registry bucket 아래의 phase와 무관한 catalog entry."""
    nested = tmp_project / "phases" / "040-049" / "40-nested-phase"
    nested.mkdir(parents=True)
    index = {
        "project": "TestProject",
        "phase": "40-nested-phase",
        "steps": [
            {
                "step": 0,
                "name": "registry-core",
                "status": "completed",
                "summary": "현재 phase resolver 구현",
            },
            {"step": 1, "name": "runner-integration", "status": "pending"},
        ],
    }
    (nested / "index.json").write_text(
        json.dumps(index, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    (nested / "step1.md").write_text(
        "# Step 1: Runner integration\n\nnested runner를 구현하세요.",
        encoding="utf-8",
    )

    other = tmp_project / "phases" / "41-reference-phase"
    other.mkdir()
    (other / "index.json").write_text(
        json.dumps(
            {
                "project": "TestProject",
                "phase": "41-reference-phase",
                "steps": [
                    {
                        "step": 0,
                        "name": "reference",
                        "status": "completed",
                        "summary": "다른 phase summary는 주입하면 안 됨",
                    }
                ],
            },
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    (tmp_project / "phases" / "index.json").write_text(
        json.dumps(
            {
                "phases": [
                    {"dir": "0-mvp", "status": "pending"},
                    {
                        "id": 40,
                        "slug": "nested-phase",
                        "dir": "040-049/40-nested-phase",
                        "status": "pending",
                        "areas": ["harness"],
                        "kind": "infrastructure",
                        "tags": ["nested-runner"],
                    },
                    {"dir": "41-reference-phase", "status": "completed"},
                ]
            },
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    return nested


def make_nested_executor(tmp_project, selector):
    with patch.object(ex, "ROOT", tmp_project):
        return ex.StepExecutor(selector)


@pytest.fixture
def executor(tmp_project, phase_dir):
    """테스트용 StepExecutor 인스턴스. git 호출은 별도 mock 필요."""
    with patch.object(ex, "ROOT", tmp_project):
        inst = ex.StepExecutor("0-mvp")
    # 내부 경로를 tmp_project 기준으로 재설정
    inst._root = str(tmp_project)
    inst._phases_dir = tmp_project / "phases"
    inst._phase_dir = phase_dir
    inst._phase_dir_name = "0-mvp"
    inst._index_file = phase_dir / "index.json"
    inst._top_index_file = tmp_project / "phases" / "index.json"
    return inst


class TestNestedPhaseResolution:
    @pytest.mark.parametrize(
        "selector",
        [40, "40", "40-nested-phase", "040-049/40-nested-phase"],
    )
    def test_id_basename_and_registry_dir_resolve_the_same_phase(
        self,
        tmp_project,
        nested_phase_dir,
        selector,
    ):
        inst = make_nested_executor(tmp_project, selector)

        assert inst._phase_basename == "40-nested-phase"
        assert inst._phase_relative_dir == "040-049/40-nested-phase"
        assert inst._phase_dir == nested_phase_dir.resolve()
        assert inst._index_file == nested_phase_dir.resolve() / "index.json"

    def test_nested_paths_are_used_for_prompt_step_output_and_git_exclusion(
        self,
        tmp_project,
        nested_phase_dir,
    ):
        inst = make_nested_executor(tmp_project, "40")
        preamble = inst._build_preamble(
            "",
            inst._build_step_context(inst._read_json(inst._index_file)),
        )
        mock_result = MagicMock(returncode=0, stdout='{"result": "ok"}', stderr="")

        with patch("subprocess.run", return_value=mock_result) as mock_run:
            inst._invoke_codex(
                {"step": 1, "name": "runner-integration"},
                preamble,
            )

        prompt = mock_run.call_args.kwargs["input"]
        assert "/phases/040-049/40-nested-phase/index.json" in prompt
        assert "nested runner를 구현하세요" in prompt
        assert "현재 phase resolver 구현" in prompt
        assert "다른 phase summary는 주입하면 안 됨" not in prompt
        assert (nested_phase_dir / "step1-output.json").is_file()
        assert not (tmp_project / "phases" / "step1-output.json").exists()

        calls = []
        inst._run_git = lambda *args: calls.append(args) or MagicMock(returncode=0)
        inst._stage_step_changes()
        reset_paths = [args[-1] for args in calls if args[0] == "reset"]
        assert "phases/040-049/40-nested-phase/step*-output.json" in reset_paths
        assert "phases/040-049/40-nested-phase/phase*-output.json" in reset_paths

    @pytest.mark.parametrize(
        ("status", "timestamp_key"),
        [
            ("completed", "completed_at"),
            ("blocked", "blocked_at"),
            ("error", "failed_at"),
        ],
    )
    def test_status_mirror_updates_only_the_canonical_registry_entry(
        self,
        tmp_project,
        nested_phase_dir,
        status,
        timestamp_key,
    ):
        inst = make_nested_executor(tmp_project, "40-nested-phase")
        registry_path = tmp_project / "phases" / "index.json"
        before = json.loads(registry_path.read_text(encoding="utf-8"))

        assert inst._update_top_index(status, "2026-01-02T00:00:00+0900") is True

        after = json.loads(registry_path.read_text(encoding="utf-8"))
        assert after["phases"][0] == before["phases"][0]
        assert after["phases"][2] == before["phases"][2]
        assert after["phases"][1]["status"] == status
        assert after["phases"][1][timestamp_key] == "2026-01-02T00:00:00+0900"

    def test_branch_header_and_commit_scope_keep_the_phase_basename(
        self,
        tmp_project,
        nested_phase_dir,
        capsys,
    ):
        inst = make_nested_executor(tmp_project, "040-049/40-nested-phase")
        calls = []

        def fake_git(*args):
            calls.append(args)
            if args[:2] == ("rev-parse", "--abbrev-ref"):
                return MagicMock(returncode=0, stdout="feat-40-nested-phase\n", stderr="")
            if args[:2] == ("diff", "--cached"):
                return MagicMock(returncode=1, stdout="", stderr="")
            return MagicMock(returncode=0, stdout="", stderr="")

        inst._run_git = fake_git
        inst._checkout_branch()
        inst._print_header()
        inst._commit_step(1, "runner-integration")

        assert "Phase: 40-nested-phase" in capsys.readouterr().out
        commit = next(args for args in calls if args[0] == "commit")
        assert commit[2].startswith("feat(40-nested-phase):")

    def test_numeric_selector_preserves_retry_error_and_explicit_push_policy(
        self,
        tmp_project,
        nested_phase_dir,
    ):
        inst = make_nested_executor(tmp_project, 40)
        attempts = []

        def complete_on_third_attempt(step, preamble):
            attempts.append(preamble)
            if len(attempts) == inst.MAX_RETRIES:
                index = inst._read_json(inst._index_file)
                target = next(item for item in index["steps"] if item["step"] == step["step"])
                target.update(status="completed", summary="nested retry 완료")
                inst._write_json(inst._index_file, index)
            return {}

        inst._invoke_codex = complete_on_third_attempt
        inst._commit_step = MagicMock()

        assert inst._execute_single_step(
            {"step": 1, "name": "runner-integration"},
            "",
        ) is True
        assert len(attempts) == inst.MAX_RETRIES
        assert "이전 시도 실패" not in attempts[0]
        assert "이전 시도 실패" in attempts[1]

        index = inst._read_json(inst._index_file)
        assert index["steps"][1]["status"] == "completed"
        assert index["steps"][1]["summary"] == "nested retry 완료"

        inst._auto_push = True
        calls = []
        inst._run_git = lambda *args: calls.append(args) or MagicMock(
            returncode=0,
            stdout="",
            stderr="",
        )
        inst._finalize()
        assert ("push", "-u", "origin", "feat-40-nested-phase") in calls

    def test_nested_blocked_step_stops_and_mirrors_blocked_status(
        self,
        tmp_project,
        nested_phase_dir,
    ):
        inst = make_nested_executor(tmp_project, "40")

        def block(step, preamble):
            index = inst._read_json(inst._index_file)
            target = next(item for item in index["steps"] if item["step"] == step["step"])
            target.update(status="blocked", blocked_reason="사용자 설정 필요")
            inst._write_json(inst._index_file, index)
            return {}

        inst._invoke_codex = block
        inst._commit_step = MagicMock()

        with pytest.raises(SystemExit) as exc_info:
            inst._execute_single_step(
                {"step": 1, "name": "runner-integration"},
                "",
            )

        assert exc_info.value.code == 2
        inst._commit_step.assert_not_called()
        registry = json.loads(
            (tmp_project / "phases" / "index.json").read_text(encoding="utf-8")
        )
        assert registry["phases"][1]["status"] == "blocked"
        assert registry["phases"][2]["status"] == "completed"

    def test_nested_error_uses_three_attempts_and_mirrors_error_status(
        self,
        tmp_project,
        nested_phase_dir,
    ):
        inst = make_nested_executor(tmp_project, "040-049/40-nested-phase")
        attempts = []
        inst._invoke_codex = lambda step, preamble: attempts.append(preamble) or {}
        inst._commit_step = MagicMock()

        with pytest.raises(SystemExit) as exc_info:
            inst._execute_single_step(
                {"step": 1, "name": "runner-integration"},
                "",
            )

        assert exc_info.value.code == 1
        assert len(attempts) == inst.MAX_RETRIES == 3
        index = inst._read_json(inst._index_file)
        assert index["steps"][1]["status"] == "error"
        assert index["steps"][1]["error_message"].startswith("[3회 시도 후 실패]")
        inst._commit_step.assert_called_once_with(1, "runner-integration")
        registry = json.loads(
            (tmp_project / "phases" / "index.json").read_text(encoding="utf-8")
        )
        assert registry["phases"][1]["status"] == "error"
        assert registry["phases"][0]["status"] == "pending"
        assert registry["phases"][2]["status"] == "completed"


# ---------------------------------------------------------------------------
# _stamp (= 이전 now_iso)
# ---------------------------------------------------------------------------

class TestStamp:
    def test_returns_kst_timestamp(self, executor):
        result = executor._stamp()
        assert "+0900" in result

    def test_format_is_iso(self, executor):
        result = executor._stamp()
        dt = datetime.strptime(result, "%Y-%m-%dT%H:%M:%S%z")
        assert dt.tzinfo is not None

    def test_is_current_time(self, executor):
        before = datetime.now(ex.StepExecutor.TZ).replace(microsecond=0)
        result = executor._stamp()
        after = datetime.now(ex.StepExecutor.TZ).replace(microsecond=0) + timedelta(seconds=1)
        parsed = datetime.strptime(result, "%Y-%m-%dT%H:%M:%S%z")
        assert before <= parsed <= after


# ---------------------------------------------------------------------------
# _read_json / _write_json
# ---------------------------------------------------------------------------

class TestJsonHelpers:
    def test_roundtrip(self, tmp_path):
        data = {"key": "값", "nested": [1, 2, 3]}
        p = tmp_path / "test.json"
        ex.StepExecutor._write_json(p, data)
        loaded = ex.StepExecutor._read_json(p)
        assert loaded == data

    def test_save_ensures_ascii_false(self, tmp_path):
        p = tmp_path / "test.json"
        ex.StepExecutor._write_json(p, {"한글": "테스트"})
        raw = p.read_text()
        assert "한글" in raw
        assert "\\u" not in raw

    def test_save_indented(self, tmp_path):
        p = tmp_path / "test.json"
        ex.StepExecutor._write_json(p, {"a": 1})
        raw = p.read_text()
        assert "\n" in raw

    def test_save_ends_with_newline_for_catalog_stability(self, tmp_path):
        p = tmp_path / "test.json"
        ex.StepExecutor._write_json(p, {"a": 1})
        raw = p.read_bytes()
        assert raw.endswith(b"\n")
        assert b"\r\n" not in raw

    def test_load_nonexistent_raises(self, tmp_path):
        with pytest.raises(FileNotFoundError):
            ex.StepExecutor._read_json(tmp_path / "nope.json")


# ---------------------------------------------------------------------------
# _load_guardrails
# ---------------------------------------------------------------------------

class TestLoadGuardrails:
    def test_loads_agents_md_and_docs(self, executor, tmp_project):
        with patch.object(ex, "ROOT", tmp_project):
            result = executor._load_guardrails()
        assert "# Rules" in result
        assert "rule one" in result
        assert "# Architecture" in result
        assert "# Guide" in result

    def test_sections_separated_by_divider(self, executor, tmp_project):
        with patch.object(ex, "ROOT", tmp_project):
            result = executor._load_guardrails()
        assert "---" in result

    def test_docs_sorted_alphabetically(self, executor, tmp_project):
        with patch.object(ex, "ROOT", tmp_project):
            result = executor._load_guardrails()
        arch_pos = result.index("arch")
        guide_pos = result.index("guide")
        assert arch_pos < guide_pos

    def test_no_agents_md(self, executor, tmp_project):
        (tmp_project / "AGENTS.md").unlink()
        with patch.object(ex, "ROOT", tmp_project):
            result = executor._load_guardrails()
        assert "AGENTS.md" not in result
        assert "Architecture" in result

    def test_no_docs_dir(self, executor, tmp_project):
        import shutil
        shutil.rmtree(tmp_project / "docs")
        with patch.object(ex, "ROOT", tmp_project):
            result = executor._load_guardrails()
        assert "Rules" in result
        assert "Architecture" not in result

    def test_empty_project(self, tmp_path):
        with patch.object(ex, "ROOT", tmp_path):
            # executor가 필요 없는 static-like 동작이므로 임시 인스턴스
            phases_dir = tmp_path / "phases" / "dummy"
            phases_dir.mkdir(parents=True)
            idx = {"project": "T", "phase": "t", "steps": []}
            (phases_dir / "index.json").write_text(json.dumps(idx))
            inst = ex.StepExecutor.__new__(ex.StepExecutor)
            result = inst._load_guardrails()
        assert result == ""


# ---------------------------------------------------------------------------
# _build_step_context
# ---------------------------------------------------------------------------

class TestBuildStepContext:
    def test_includes_completed_with_summary(self, phase_dir):
        index = json.loads((phase_dir / "index.json").read_text())
        result = ex.StepExecutor._build_step_context(index)
        assert "Step 0 (setup): 프로젝트 초기화 완료" in result
        assert "Step 1 (core): 핵심 로직 구현" in result

    def test_excludes_pending(self, phase_dir):
        index = json.loads((phase_dir / "index.json").read_text())
        result = ex.StepExecutor._build_step_context(index)
        assert "ui" not in result

    def test_excludes_completed_without_summary(self, phase_dir):
        index = json.loads((phase_dir / "index.json").read_text())
        del index["steps"][0]["summary"]
        result = ex.StepExecutor._build_step_context(index)
        assert "setup" not in result
        assert "core" in result

    def test_empty_when_no_completed(self):
        index = {"steps": [{"step": 0, "name": "a", "status": "pending"}]}
        result = ex.StepExecutor._build_step_context(index)
        assert result == ""

    def test_has_header(self, phase_dir):
        index = json.loads((phase_dir / "index.json").read_text())
        result = ex.StepExecutor._build_step_context(index)
        assert result.startswith("## 이전 Step 산출물")


# ---------------------------------------------------------------------------
# _build_preamble
# ---------------------------------------------------------------------------

class TestBuildPreamble:
    def test_includes_project_name(self, executor):
        result = executor._build_preamble("", "")
        assert "TestProject" in result

    def test_includes_guardrails(self, executor):
        result = executor._build_preamble("GUARD_CONTENT", "")
        assert "GUARD_CONTENT" in result

    def test_includes_step_context(self, executor):
        ctx = "## 이전 Step 산출물\n\n- Step 0: done"
        result = executor._build_preamble("", ctx)
        assert "이전 Step 산출물" in result

    def test_forbids_direct_commit(self, executor):
        result = executor._build_preamble("", "")
        assert "직접 커밋하지 마라" in result
        assert "harness가" in result
        assert "feat(mvp):" not in result

    def test_includes_rules(self, executor):
        result = executor._build_preamble("", "")
        assert "작업 규칙" in result
        assert "AC" in result

    def test_no_retry_section_by_default(self, executor):
        result = executor._build_preamble("", "")
        assert "이전 시도 실패" not in result

    def test_retry_section_with_prev_error(self, executor):
        result = executor._build_preamble("", "", prev_error="타입 에러 발생")
        assert "이전 시도 실패" in result
        assert "타입 에러 발생" in result

    def test_includes_max_retries(self, executor):
        result = executor._build_preamble("", "")
        assert str(ex.StepExecutor.MAX_RETRIES) in result

    def test_includes_index_path(self, executor):
        result = executor._build_preamble("", "")
        assert "/phases/0-mvp/index.json" in result


# ---------------------------------------------------------------------------
# _update_top_index
# ---------------------------------------------------------------------------

class TestUpdateTopIndex:
    def test_completed(self, executor, top_index):
        executor._top_index_file = top_index
        executor._update_top_index("completed")
        data = json.loads(top_index.read_text())
        mvp = next(p for p in data["phases"] if p["dir"] == "0-mvp")
        assert mvp["status"] == "completed"
        assert "completed_at" in mvp

    def test_error(self, executor, top_index):
        executor._top_index_file = top_index
        executor._update_top_index("error")
        data = json.loads(top_index.read_text())
        mvp = next(p for p in data["phases"] if p["dir"] == "0-mvp")
        assert mvp["status"] == "error"
        assert "failed_at" in mvp

    def test_blocked(self, executor, top_index):
        executor._top_index_file = top_index
        executor._update_top_index("blocked")
        data = json.loads(top_index.read_text())
        mvp = next(p for p in data["phases"] if p["dir"] == "0-mvp")
        assert mvp["status"] == "blocked"
        assert "blocked_at" in mvp

    def test_other_phases_unchanged(self, executor, top_index):
        executor._top_index_file = top_index
        executor._update_top_index("completed")
        data = json.loads(top_index.read_text())
        polish = next(p for p in data["phases"] if p["dir"] == "1-polish")
        assert polish["status"] == "pending"

    def test_nonexistent_dir_is_noop(self, executor, top_index):
        executor._top_index_file = top_index
        executor._phase_relative_dir = "no-such-dir"
        original = json.loads(top_index.read_text())
        executor._update_top_index("completed")
        after = json.loads(top_index.read_text())
        for p_before, p_after in zip(original["phases"], after["phases"]):
            assert p_before["status"] == p_after["status"]

    def test_no_top_index_file(self, executor, tmp_path):
        executor._top_index_file = tmp_path / "nonexistent.json"
        executor._update_top_index("completed")  # should not raise

    def test_completed_timestamp_is_not_replaced(self, executor, top_index):
        executor._top_index_file = top_index
        top_index.write_text(json.dumps({
            "phases": [
                {
                    "dir": "0-mvp",
                    "status": "completed",
                    "completed_at": "2026-01-01T00:00:00+0900",
                },
            ],
        }, indent=2))
        executor._stamp = lambda: "2026-01-02T00:00:00+0900"

        changed = executor._update_top_index("completed")

        data = json.loads(top_index.read_text())
        mvp = data["phases"][0]
        assert changed is False
        assert mvp["completed_at"] == "2026-01-01T00:00:00+0900"


# ---------------------------------------------------------------------------
# _mark_phase_completed_if_ready
# ---------------------------------------------------------------------------

class TestMarkPhaseCompletedIfReady:
    def test_marks_phase_when_all_steps_completed(self, executor, top_index):
        executor._top_index_file = top_index
        index = json.loads(executor._index_file.read_text())
        for step in index["steps"]:
            step["status"] = "completed"

        changed = executor._mark_phase_completed_if_ready(index, "2026-01-02T00:00:00+0900")

        top = json.loads(top_index.read_text())
        mvp = next(p for p in top["phases"] if p["dir"] == "0-mvp")
        assert changed is True
        assert index["completed_at"] == "2026-01-02T00:00:00+0900"
        assert mvp["status"] == "completed"
        assert mvp["completed_at"] == "2026-01-02T00:00:00+0900"

    def test_noop_when_phase_already_completed(self, executor, top_index):
        executor._top_index_file = top_index
        index = json.loads(executor._index_file.read_text())
        for step in index["steps"]:
            step["status"] = "completed"
        index["completed_at"] = "2026-01-01T00:00:00+0900"
        top_index.write_text(json.dumps({
            "phases": [
                {
                    "dir": "0-mvp",
                    "status": "completed",
                    "completed_at": "2026-01-01T00:00:00+0900",
                },
            ],
        }, indent=2))

        changed = executor._mark_phase_completed_if_ready(index, "2026-01-02T00:00:00+0900")

        top = json.loads(top_index.read_text())
        assert changed is False
        assert index["completed_at"] == "2026-01-01T00:00:00+0900"
        assert top["phases"][0]["completed_at"] == "2026-01-01T00:00:00+0900"

    def test_pending_step_does_not_mark_phase(self, executor, top_index):
        executor._top_index_file = top_index
        index = json.loads(executor._index_file.read_text())

        changed = executor._mark_phase_completed_if_ready(index, "2026-01-02T00:00:00+0900")

        assert changed is False
        assert "completed_at" not in index


# ---------------------------------------------------------------------------
# _checkout_branch (mocked)
# ---------------------------------------------------------------------------

class TestCheckoutBranch:
    def _mock_git(self, executor, responses):
        call_idx = {"i": 0}
        def fake_git(*args):
            idx = call_idx["i"]
            call_idx["i"] += 1
            if idx < len(responses):
                return responses[idx]
            return MagicMock(returncode=0, stdout="", stderr="")
        executor._run_git = fake_git

    def test_already_on_branch(self, executor):
        self._mock_git(executor, [
            MagicMock(returncode=0, stdout="feat-0-mvp\n", stderr=""),
        ])
        executor._checkout_branch()  # should return without checkout

    def test_branch_exists_checkout(self, executor):
        self._mock_git(executor, [
            MagicMock(returncode=0, stdout="main\n", stderr=""),
            MagicMock(returncode=0, stdout="", stderr=""),
            MagicMock(returncode=0, stdout="", stderr=""),
        ])
        executor._checkout_branch()

    def test_branch_not_exists_create(self, executor):
        self._mock_git(executor, [
            MagicMock(returncode=0, stdout="main\n", stderr=""),
            MagicMock(returncode=1, stdout="", stderr="not found"),
            MagicMock(returncode=0, stdout="", stderr=""),
        ])
        executor._checkout_branch()

    def test_checkout_fails_exits(self, executor):
        self._mock_git(executor, [
            MagicMock(returncode=0, stdout="main\n", stderr=""),
            MagicMock(returncode=1, stdout="", stderr=""),
            MagicMock(returncode=1, stdout="", stderr="dirty tree"),
        ])
        with pytest.raises(SystemExit) as exc_info:
            executor._checkout_branch()
        assert exc_info.value.code == 1

    def test_no_git_exits(self, executor):
        self._mock_git(executor, [
            MagicMock(returncode=1, stdout="", stderr="not a git repo"),
        ])
        with pytest.raises(SystemExit) as exc_info:
            executor._checkout_branch()
        assert exc_info.value.code == 1


# ---------------------------------------------------------------------------
# _commit_step (mocked)
# ---------------------------------------------------------------------------

class TestCommitStep:
    def test_single_step_commit(self, executor):
        calls = []
        def fake_git(*args):
            calls.append(args)
            if args[:2] == ("diff", "--cached"):
                return MagicMock(returncode=1)
            return MagicMock(returncode=0, stdout="", stderr="")
        executor._run_git = fake_git

        executor._commit_step(2, "ui")

        commit_calls = [c for c in calls if c[0] == "commit"]
        reset_calls = [c for c in calls if c[0] == "reset"]
        assert len(commit_calls) == 1
        assert "feat(0-mvp):" in commit_calls[0][2]
        assert any("step*-output.json" in c[-1] for c in reset_calls)
        assert not any(c[-1] == "phases/0-mvp/index.json" for c in reset_calls)

    def test_no_changes_skips_commit(self, executor):
        calls = []
        def fake_git(*args):
            calls.append(args)
            if args[:2] == ("diff", "--cached"):
                return MagicMock(returncode=0)
            return MagicMock(returncode=0, stdout="", stderr="")
        executor._run_git = fake_git

        executor._commit_step(2, "ui")

        commit_msgs = [c[2] for c in calls if c[0] == "commit"]
        assert commit_msgs == []


# ---------------------------------------------------------------------------
# _execute_single_step / _finalize metadata commit timing
# ---------------------------------------------------------------------------

class TestCompletionMetadata:
    def test_last_step_marks_phase_before_step_commit(self, executor, top_index):
        executor._top_index_file = top_index
        executor._stamp = lambda: "2026-01-02T00:00:00+0900"
        snapshot = {}

        def fake_invoke(step, preamble):
            index = json.loads(executor._index_file.read_text())
            for item in index["steps"]:
                if item["step"] == step["step"]:
                    item["status"] = "completed"
                    item["summary"] = "마지막 step 완료"
            executor._index_file.write_text(json.dumps(index, indent=2, ensure_ascii=False))
            return {}

        def fake_commit(step_num, step_name):
            snapshot["index"] = json.loads(executor._index_file.read_text())
            snapshot["top"] = json.loads(top_index.read_text())

        executor._invoke_codex = fake_invoke
        executor._commit_step = fake_commit

        executor._execute_single_step({"step": 2, "name": "ui"}, "")

        mvp = next(p for p in snapshot["top"]["phases"] if p["dir"] == "0-mvp")
        assert snapshot["index"]["completed_at"] == "2026-01-02T00:00:00+0900"
        assert snapshot["index"]["steps"][2]["completed_at"] == "2026-01-02T00:00:00+0900"
        assert mvp["status"] == "completed"
        assert mvp["completed_at"] == "2026-01-02T00:00:00+0900"

    def test_finalize_noop_when_phase_already_completed(self, executor, top_index):
        executor._top_index_file = top_index
        index = json.loads(executor._index_file.read_text())
        for step in index["steps"]:
            step["status"] = "completed"
        index["completed_at"] = "2026-01-01T00:00:00+0900"
        executor._index_file.write_text(json.dumps(index, indent=2, ensure_ascii=False))
        top_index.write_text(json.dumps({
            "phases": [
                {
                    "dir": "0-mvp",
                    "status": "completed",
                    "completed_at": "2026-01-01T00:00:00+0900",
                },
            ],
        }, indent=2))
        calls = []
        executor._run_git = lambda *args: calls.append(args) or MagicMock(returncode=0, stdout="", stderr="")

        executor._finalize()

        assert calls == []

    def test_finalize_commits_missing_phase_metadata(self, executor, top_index):
        executor._top_index_file = top_index
        executor._stamp = lambda: "2026-01-02T00:00:00+0900"
        index = json.loads(executor._index_file.read_text())
        for step in index["steps"]:
            step["status"] = "completed"
        executor._index_file.write_text(json.dumps(index, indent=2, ensure_ascii=False))
        calls = []

        def fake_git(*args):
            calls.append(args)
            if args[:2] == ("diff", "--cached"):
                return MagicMock(returncode=1)
            return MagicMock(returncode=0, stdout="", stderr="")

        executor._run_git = fake_git

        executor._finalize()

        updated_index = json.loads(executor._index_file.read_text())
        updated_top = json.loads(top_index.read_text())
        mvp = next(p for p in updated_top["phases"] if p["dir"] == "0-mvp")
        commit_msgs = [c[2] for c in calls if c[0] == "commit"]
        assert updated_index["completed_at"] == "2026-01-02T00:00:00+0900"
        assert mvp["status"] == "completed"
        assert mvp["completed_at"] == "2026-01-02T00:00:00+0900"
        assert commit_msgs == ["chore(0-mvp): mark phase completed"]


# ---------------------------------------------------------------------------
# _invoke_codex (mocked)
# ---------------------------------------------------------------------------

class TestInvokeCodex:
    def test_invokes_codex_with_correct_args(self, executor):
        mock_result = MagicMock(returncode=0, stdout='{"result": "ok"}', stderr="")
        step = {"step": 2, "name": "ui"}
        preamble = "PREAMBLE\n"

        with patch("execute.resolve_codex_command", return_value="codex"), \
             patch("subprocess.run", return_value=mock_result) as mock_run:
            output = executor._invoke_codex(step, preamble)

        cmd = mock_run.call_args[0][0]
        assert cmd[:2] == ["codex", "exec"]
        assert "--dangerously-bypass-approvals-and-sandbox" in cmd
        assert "--dangerously-bypass-hook-trust" in cmd
        assert "--json" in cmd
        assert cmd[-1] == "--json"
        assert "--ask-for-approval" not in cmd
        assert "never" not in cmd
        assert "--sandbox" not in cmd
        assert "danger-full-access" not in cmd
        assert "PREAMBLE" in mock_run.call_args[1]["input"]
        assert "UI를 구현하세요" in mock_run.call_args[1]["input"]

    def test_invokes_codex_with_harness_child_env(self, executor):
        mock_result = MagicMock(returncode=0, stdout='{"result": "ok"}', stderr="")
        step = {"step": 2, "name": "ui"}

        with patch("subprocess.run", return_value=mock_result) as mock_run:
            executor._invoke_codex(step, "preamble")

        env = mock_run.call_args[1]["env"]
        assert env["TODO_QUEST_HARNESS_CHILD"] == "1"
        assert env["PYTHONUTF8"] == "1"

    def test_saves_output_json(self, executor):
        mock_result = MagicMock(returncode=0, stdout='{"ok": true}', stderr="")
        step = {"step": 2, "name": "ui"}

        with patch("subprocess.run", return_value=mock_result):
            executor._invoke_codex(step, "preamble")

        output_file = executor._phase_dir / "step2-output.json"
        assert output_file.exists()
        data = json.loads(output_file.read_text())
        assert data["step"] == 2
        assert data["name"] == "ui"
        assert data["exitCode"] == 0

    def test_nonexistent_step_file_exits(self, executor):
        step = {"step": 99, "name": "nonexistent"}
        with pytest.raises(SystemExit) as exc_info:
            executor._invoke_codex(step, "preamble")
        assert exc_info.value.code == 1

    def test_timeout_is_1800(self, executor):
        mock_result = MagicMock(returncode=0, stdout="{}", stderr="")
        step = {"step": 2, "name": "ui"}

        with patch("subprocess.run", return_value=mock_result) as mock_run:
            executor._invoke_codex(step, "preamble")

        assert mock_run.call_args[1]["timeout"] == 1800


class TestHookConfiguration:
    def test_hooks_delegate_to_repository_runner(self):
        hooks_path = Path(__file__).resolve().parent.parent / ".codex" / "hooks.json"
        data = json.loads(hooks_path.read_text(encoding="utf-8"))
        stop_hook = data["hooks"]["Stop"][0]["hooks"][0]
        command = stop_hook["command"]
        command_windows = stop_hook["commandWindows"]

        assert "git rev-parse --show-toplevel" in command
        assert "PYTHONUTF8" in command
        assert ".venv/bin/python" in command
        assert "python3" in command
        assert "scripts/stop_hook.py" in command
        assert "git rev-parse --show-toplevel" in command_windows
        assert "PYTHONUTF8" in command_windows
        assert ".venv/Scripts/python.exe" in command_windows
        assert "py -3" in command_windows
        assert "scripts/stop_hook.py" in command_windows
        assert stop_hook["timeout"] == 900
        assert stop_hook["statusMessage"] == "Checking relevant project changes"
        assert "pytest" not in command
        assert "pytest" not in command_windows
        assert "gradlew" not in command
        assert "gradlew" not in command_windows

    def test_windows_stop_hook_skips_inside_harness_child(self):
        if os.name != "nt":
            pytest.skip("Windows hook command is only executed on Windows")

        root = Path(__file__).resolve().parent.parent
        hooks_path = root / ".codex" / "hooks.json"
        data = json.loads(hooks_path.read_text(encoding="utf-8"))
        command = data["hooks"]["Stop"][0]["hooks"][0]["commandWindows"]
        env = os.environ.copy()
        env["TODO_QUEST_HARNESS_CHILD"] = "1"

        result = subprocess.run(
            command,
            cwd=root / "scripts",
            input="",
            capture_output=True,
            text=True,
            shell=True,
            env=env,
            timeout=30,
        )

        assert result.returncode == 0
        assert result.stdout == ""
        assert "Downloading https://services.gradle.org" not in result.stdout
        assert "Downloading https://services.gradle.org" not in result.stderr

    def test_windows_stop_hook_forwards_plan_event_to_runner(self):
        if os.name != "nt":
            pytest.skip("Windows hook command is only executed on Windows")

        root = Path(__file__).resolve().parent.parent
        hooks_path = root / ".codex" / "hooks.json"
        data = json.loads(hooks_path.read_text(encoding="utf-8"))
        command = data["hooks"]["Stop"][0]["hooks"][0]["commandWindows"]
        env = os.environ.copy()
        env.pop("TODO_QUEST_HARNESS_CHILD", None)

        result = subprocess.run(
            command,
            cwd=root / "scripts",
            input='{"permission_mode":"plan"}',
            capture_output=True,
            text=True,
            shell=True,
            env=env,
            timeout=30,
        )

        assert result.returncode == 0
        assert result.stdout == ""
        assert result.stderr == ""


class TestHarnessDocumentation:
    STOP_POLICY_CONTRACT = (
        "Stop hook은 turn 단위",
        "matcher로 phase 실행만 선별할 수 없으므로",
        "`TODO_QUEST_HARNESS_CHILD=1`이면 Stop 검증을 건너뛴다",
        "Acceptance Criteria 실행과 step status가 harness 완료 판정의 기준",
        "부모 Stop을 두 번째 phase acceptance gate로 사용하지 않는다",
        "이미 커밋된 phase 전체를 다시 검증하지 않는다",
        "Plan, 관련 변경 없음, 동일 fingerprint 성공 상태에서는 즉시 종료",
        "관련 미커밋 변경이 있을 때만 harness 또는 Android 검증군을 선택",
        "`build/codex-stop-hook/cache.json`",
        "`build/codex-stop-hook/latest-harness.log`",
        "`build/codex-stop-hook/latest-android.log`",
        "검증군별 fingerprint",
        "Gradle은 `test`, `lint`, `assembleDebug`를 한 번의 offline invocation으로 실행",
        "`continue: false`",
        "`systemMessage`",
        "수정하거나 필요한 도구가 없으면 blocked로 보고",
        "`/hooks`에서 새 hash를 검토하고 신뢰",
        "Codex를 재시작",
    )

    def test_skill_runs_harness_child_for_immediate_implementation(self):
        root = Path(__file__).resolve().parent.parent
        skill = (root / ".agents" / "skills" / "harness" / "SKILL.md").read_text(encoding="utf-8")

        assert "phase 파일 생성 후 같은 요청에서 즉시 구현까지 지시받으면" in skill
        assert "추가 입력을 요구하지 않고" in skill
        assert r".\scripts\run_harness.ps1 -Phase {task-name}" in skill
        assert "`/compact`" not in skill

    def test_development_docs_run_harness_child_for_immediate_implementation(self):
        root = Path(__file__).resolve().parent.parent
        docs = (root / "docs" / "DEVELOPMENT.md").read_text(encoding="utf-8")

        assert "phase 파일 생성 후 같은 요청에서 즉시 구현까지 지시받으면" in docs
        assert "추가 입력을 요구하지 않고" in docs
        assert r".\scripts\run_harness.ps1 -Phase <phase-dir>" in docs
        assert "Codex 승인 프롬프트는 래퍼 실행 권한을 확인하는 절차" in docs
        assert "`/compact`" not in docs

    @pytest.mark.parametrize(
        ("relative_path", "phase_only", "push_policy"),
        [
            (
                Path(".agents/skills/harness/SKILL.md"),
                "phase 생성만 요청받으면 래퍼를 실행하지 않는다",
                "`-Push`는 사용자가 명시적으로 요청한 경우에만 사용한다",
            ),
            (
                Path("docs/DEVELOPMENT.md"),
                "phase 생성만 요청받으면 래퍼를 실행하지 않는다",
                "`-Push`는 사용자가 명시적으로 요청한 경우에만 사용한다",
            ),
        ],
    )
    def test_docs_do_not_auto_run_phase_only_or_unrequested_push(
        self, relative_path, phase_only, push_policy
    ):
        root = Path(__file__).resolve().parent.parent
        content = (root / relative_path).read_text(encoding="utf-8")

        assert phase_only in content
        assert push_policy in content

    def test_skill_explains_child_context_isolation_and_stop_policy(self):
        root = Path(__file__).resolve().parent.parent
        skill = (root / ".agents" / "skills" / "harness" / "SKILL.md").read_text(encoding="utf-8")

        assert "각 pending step을 별도 `codex exec` child 세션에서 실행" in skill
        assert "완료 step의 `summary`를 다음 step에 전달" in skill
        assert "부모 세션의 compaction이 필요하지 않다" in skill
        assert "`blocked` 또는 `error`로 종료되면 다음 step으로 진행하지 않는다" in skill

    @pytest.mark.parametrize(
        "relative_path",
        [
            Path(".agents/skills/harness/SKILL.md"),
            Path("docs/DEVELOPMENT.md"),
        ],
    )
    def test_docs_explain_targeted_stop_hook_policy(self, relative_path):
        root = Path(__file__).resolve().parent.parent
        content = (root / relative_path).read_text(encoding="utf-8")

        for contract in self.STOP_POLICY_CONTRACT:
            assert contract in content
        assert "일반 Codex 세션에서는 기존 hook 검증을 그대로 실행" not in content
        assert "일반 Codex 세션에서는 기존 hook 검증을 유지" not in content

    @pytest.mark.parametrize(
        "relative_path",
        [
            Path(".agents/skills/harness/SKILL.md"),
            Path("docs/DEVELOPMENT.md"),
        ],
    )
    def test_docs_define_phase_manager_catalog_workflow(self, relative_path):
        root = Path(__file__).resolve().parent.parent
        content = (root / relative_path).read_text(encoding="utf-8")

        for command in ("Suggest", "Create", "List", "Show", "Validate", "Sync"):
            assert f"-Command {command}" in content
        assert "10개 단위 bucket" in content
        assert "숫자 id, 기존 basename, registry 상대 `dir`" in content
        assert "실제 `step*.md`의 명시적 phase 참조" in content
        assert "읽기 전용 catalog 관계" in content

    @pytest.mark.parametrize(
        "relative_path",
        [
            Path(".agents/skills/harness/SKILL.md"),
            Path("docs/DEVELOPMENT.md"),
        ],
    )
    def test_docs_make_manager_create_the_standard_phase_entrypoint(
        self, relative_path
    ):
        root = Path(__file__).resolve().parent.parent
        content = (root / relative_path).read_text(encoding="utf-8")

        assert r".\scripts\run_phase_manager.ps1 -Command Create" in content
        assert "새 phase 생성의 표준 진입점" in content
        assert "승인 후 `step{N}.md`" in content
        assert "같은 요청에서 즉시 구현까지 지시받으면" in content
        assert "`-Push`는 사용자가 명시적으로 요청한 경우에만 사용한다" in content


# ---------------------------------------------------------------------------
# progress_indicator (= 이전 Spinner)
# ---------------------------------------------------------------------------

class TestProgressIndicator:
    def test_context_manager(self):
        import time
        with ex.progress_indicator("test") as pi:
            time.sleep(0.15)
        assert pi.elapsed >= 0.1

    def test_elapsed_increases(self):
        import time
        with ex.progress_indicator("test") as pi:
            time.sleep(0.2)
        assert pi.elapsed > 0


# ---------------------------------------------------------------------------
# main() CLI 파싱 (mocked)
# ---------------------------------------------------------------------------

class TestMainCli:
    def test_no_args_exits(self):
        with patch("sys.argv", ["execute.py"]):
            with pytest.raises(SystemExit) as exc_info:
                ex.main()
            assert exc_info.value.code == 2  # argparse exits with 2

    def test_invalid_phase_dir_exits(self):
        with patch("sys.argv", ["execute.py", "nonexistent"]):
            with patch.object(ex, "ROOT", Path("/tmp/fake_nonexistent")):
                with pytest.raises(SystemExit) as exc_info:
                    ex.main()
                assert exc_info.value.code == 1

    def test_missing_index_exits(self, tmp_project):
        (tmp_project / "phases" / "empty").mkdir()
        with patch("sys.argv", ["execute.py", "empty"]):
            with patch.object(ex, "ROOT", tmp_project):
                with pytest.raises(SystemExit) as exc_info:
                    ex.main()
                assert exc_info.value.code == 1

    @pytest.mark.parametrize(
        ("argv", "expected_push"),
        [
            (["execute.py", "40"], False),
            (["execute.py", "40", "--push"], True),
        ],
    )
    def test_numeric_selector_and_explicit_push_are_forwarded(self, argv, expected_push):
        runner = MagicMock()
        with patch("sys.argv", argv), patch.object(
            ex,
            "StepExecutor",
            return_value=runner,
        ) as constructor:
            ex.main()

        constructor.assert_called_once_with("40", auto_push=expected_push)
        runner.run.assert_called_once_with()


# ---------------------------------------------------------------------------
# _check_blockers (= 이전 main() error/blocked 체크)
# ---------------------------------------------------------------------------

class TestCheckBlockers:
    def _make_executor_with_steps(self, tmp_project, steps):
        d = tmp_project / "phases" / "test-phase"
        d.mkdir(exist_ok=True)
        index = {"project": "T", "phase": "test", "steps": steps}
        (d / "index.json").write_text(json.dumps(index))

        with patch.object(ex, "ROOT", tmp_project):
            inst = ex.StepExecutor.__new__(ex.StepExecutor)
        inst._root = str(tmp_project)
        inst._phases_dir = tmp_project / "phases"
        inst._phase_dir = d
        inst._phase_dir_name = "test-phase"
        inst._index_file = d / "index.json"
        inst._top_index_file = tmp_project / "phases" / "index.json"
        inst._phase_name = "test"
        inst._total = len(steps)
        return inst

    def test_error_step_exits_1(self, tmp_project):
        steps = [
            {"step": 0, "name": "ok", "status": "completed"},
            {"step": 1, "name": "bad", "status": "error", "error_message": "fail"},
        ]
        inst = self._make_executor_with_steps(tmp_project, steps)
        with pytest.raises(SystemExit) as exc_info:
            inst._check_blockers()
        assert exc_info.value.code == 1

    def test_blocked_step_exits_2(self, tmp_project):
        steps = [
            {"step": 0, "name": "ok", "status": "completed"},
            {"step": 1, "name": "stuck", "status": "blocked", "blocked_reason": "API key"},
        ]
        inst = self._make_executor_with_steps(tmp_project, steps)
        with pytest.raises(SystemExit) as exc_info:
            inst._check_blockers()
        assert exc_info.value.code == 2
