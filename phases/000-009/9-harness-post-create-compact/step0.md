# Step 0: harness-post-create-compact-docs

## 읽어야 할 파일

- `/AGENTS.md`
- `/.agents/skills/harness/SKILL.md`
- `/docs/DEVELOPMENT.md`
- `/scripts/test_execute.py`

## 작업

`$harness`로 phase 메타데이터와 step 파일을 생성한 뒤 같은 Codex 세션에서 바로 구현 작업을 진행하는 경우, 첫 step 작업을 시작하기 전에 `/compact`를 수행해야 한다는 규칙을 문서화한다.

다음 파일을 수정한다.

- `/.agents/skills/harness/SKILL.md`
  - phase 파일 생성 직후 바로 실행까지 이어갈 때 `/compact`를 먼저 수행하라는 규칙을 추가한다.
  - `/compact` 이후에는 `/AGENTS.md`, `/phases/{task-name}/index.json`, `/phases/{task-name}/step0.md`, 필요한 `docs/*.md`를 다시 읽고 작업을 시작한다고 명시한다.
  - phase 생성만 하고 실행하지 않는 경우에는 `/compact`를 강제하지 않는다고 명시한다.
- `/docs/DEVELOPMENT.md`
  - `Harness 실행` 섹션에 같은 운영 규칙을 추가한다.
- `/scripts/test_execute.py`
  - 스킬 문서와 개발 문서가 phase 생성 직후 `/compact` 규칙을 포함하는지 확인하는 문서 정책 테스트를 먼저 추가한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```

## 검증 절차

1. AC 명령을 실행한다.
2. AGENTS.md의 CRITICAL 규칙을 확인한다.
3. `/phases/000-009/9-harness-post-create-compact/index.json`의 step 0 상태와 결과 필드를 업데이트한다.
4. 모든 step이 완료되면 `/phases/index.json`의 `9-harness-post-create-compact` 상태를 `completed`로 업데이트한다.

## 금지사항

- `scripts/execute.py`나 `scripts/run_harness.ps1`에서 `/compact`를 자동 실행하려고 하지 마라. 이유: `/compact`는 부모 Codex 세션 정리 절차이고 child Codex 실행 컨텍스트와 다르다.
- phase 생성만 하고 실행하지 않는 흐름에 `/compact`를 강제하지 마라. 이유: 사용자가 별도 세션에서 나중에 실행할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
