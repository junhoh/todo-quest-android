# Step 0: Phase registry core

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/scripts/execute.py`
- `/scripts/test_execute.py`
- `/phases/index.json`
- `/phases/040-049/42-harpy-enemy/index.json`
- `/phases/040-049/43-harness-phase-catalog/index.json`

## 작업

테스트를 먼저 작성하고 `/scripts/phase_manager.py`, `/scripts/test_phase_manager.py`, `/phases/taxonomy.json`에 phase registry의 순수 Python core를 구현한다. Android 소스는 수정하지 않는다.

다음 계약을 구현한다.

- `bucket_name(phase_id: int, size: int = 10) -> str`: 0~9는 `000-009`, 10~19는 `010-019`, 100~109는 `100-109`를 반환한다.
- phase basename은 `{id}-{slug}`이며 id는 음수가 아닌 정수, slug는 kebab-case다.
- registry entry는 신규 형식의 `id`, `slug`, `dir`, `status`, `areas`, `kind`, `tags`를 지원하되 migration 전 기존 `dir`/`status` entry도 읽을 수 있어야 한다.
- resolver는 숫자 id, 기존 basename, registry의 상대 `dir`을 받아 동일한 phase를 찾고 실제 경로가 반드시 `/phases` 아래인지 검증한다.
- 상태 허용값은 기존 `pending`, `completed`, `error`, `blocked`를 그대로 사용한다.
- areas 허용값은 `project`, `schedule`, `character`, `combat`, `art`, `android-platform`, `harness`; kind 허용값은 `feature`, `fix`, `design`, `asset`, `infrastructure`, `documentation`으로 고정한다.
- tag는 kebab-case로 정규화하고 areas는 중복 없는 1개 이상, kind는 정확히 1개다.
- phase 내부 `index.json`과 step status가 실행 상태의 원본이며 상위 registry status는 동기화되는 표시값이라는 책임을 함수와 테스트에서 분명히 한다.
- 기존 phase 내부 index의 서로 다른 필드 shape, status·summary·timestamp를 정규화하거나 재작성하지 않는다.

테스트는 구간 경계 9→10, 39→40, 99→100, 중복 id/slug, 존재하지 않는 selector, 절대 경로와 `..`, legacy와 nested entry 혼합, 허용하지 않는 taxonomy 값을 포함한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_phase_manager.py --basetemp .\.venv\pytest-tmp
```

```powershell
git diff --check
```

## 검증 절차

1. 테스트를 먼저 실패시키고 core 구현 후 AC를 통과시킨다.
2. legacy registry fixture가 파일을 변경하지 않고 조회되는지 확인한다.
3. `/phases/040-049/43-harness-phase-catalog/index.json`의 step 0을 completed로 바꾸고 한국어 summary와 completed_at을 기록한다.

## 금지사항

- 기존 phase 디렉터리를 이동하지 마라. 이유: 실행 중인 bootstrap phase에서 migration을 시작하면 현재 runner가 경로를 잃을 수 있다.
- 기존 phase index를 일괄 정규화하지 마라. 이유: 완료 기록과 역사 메타데이터를 보존해야 한다.
- runner를 이 step에서 수정하지 마라. 이유: 이 step은 registry core 모듈만 다룬다.
- 기존 테스트를 깨뜨리지 마라.
