# Step 2: Nested phase execution integration

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/scripts/phase_manager.py`
- `/scripts/test_phase_manager.py`
- `/scripts/execute.py`
- `/scripts/test_execute.py`
- `/scripts/run_harness.ps1`
- `/phases/040-049/43-harness-phase-catalog/index.json`
- `/phases/040-049/43-harness-phase-catalog/step0.md`
- `/phases/040-049/43-harness-phase-catalog/step1.md`

## 작업

테스트를 먼저 작성하고 existing harness runner가 legacy 평면 phase와 신규 bucket phase를 모두 실행하도록 통합한다. constructor의 경로 해석 외에 기존 run lifecycle의 순서와 정책을 바꾸지 않는다.

- 숫자 id, 기존 `{id}-{slug}`, registry relative dir selector를 resolver로 동일 phase에 연결한다.
- phase identity/basename과 실제 relative dir를 별도 필드로 관리한다.
- branch 이름, header, commit scope는 기존 phase basename을 사용한다.
- child prompt의 index 경로, step 파일, output JSON, Git reset pathspec, 상위 registry status 갱신은 실제 nested relative dir를 사용한다.
- 상위 registry entry는 id/basename/dir 중 canonical resolver 결과로 정확히 하나를 갱신하고 다른 entry를 수정하지 않는다.
- 같은 phase의 completed step summary만 child에 누적하고 다른 phase catalog/reference는 주입하지 않는다.
- guardrail 로드, 최대 3회 재시도, 1800초 timeout, child 환경 변수와 bypass flags, blocked/error 처리, timestamps, step 단일 커밋, final metadata commit, 명시적 `-Push` 정책을 그대로 유지한다.
- wrapper의 기존 `-Phase <basename>`와 `-Push` 사용법을 유지하고 숫자 id 입력을 추가한다.

기존 `/scripts/test_execute.py` 테스트를 삭제하거나 완화하지 말고 legacy fixture와 nested registry fixture를 함께 검증한다. nested prompt path, output exclusion, status mirror, branch/commit name, retry/blocked/error, push 회귀 테스트를 추가한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py scripts/test_phase_manager.py --basetemp .\.venv\pytest-tmp
```

```powershell
git diff --check
```

## 검증 절차

1. nested fixture 테스트를 먼저 추가하고 runner를 통합한다.
2. 기존 flat fixture와 신규 nested fixture가 동일 lifecycle 결과를 내는지 확인한다.
3. `/phases/040-049/43-harness-phase-catalog/index.json`의 step 2를 completed로 바꾸고 호환성 보장 범위를 한국어 summary로 기록한다.

## 금지사항

- retry, commit, push, child sandbox 정책을 재설계하지 마라. 이유: 이번 변경은 경로와 탐색 구조 개선이지 실행 정책 변경이 아니다.
- 다른 phase summary를 자동 주입하지 마라. 이유: 현재의 phase 독립성과 step 명시 참조 방식을 보존해야 한다.
- 기존 flat selector 지원을 제거하지 마라. 이유: migration 전 bootstrap과 사용자의 기존 명령을 모두 지원해야 한다.
- 기존 테스트를 깨뜨리지 마라.
