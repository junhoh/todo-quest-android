---
name: harness
description: 프로젝트 문서를 바탕으로 구현 작업을 독립 실행 가능한 phase와 step으로 설계하고 phases 메타데이터를 생성한다. 사용자가 구현 계획, 단계별 작업 설계, harness phase 생성 또는 execute.py 실행 준비를 요청할 때 사용한다.
---

# Harness

이 프로젝트의 구현 작업을 다음 워크플로우에 따라 설계한다.

## 1. 탐색

`docs/`의 PRD, ARCHITECTURE, ADR 등 관련 문서와 `AGENTS.md`를 읽어 기획·아키텍처·설계 의도를 파악한다.

## 2. 논의

구현 전에 구체화하거나 기술적으로 결정해야 할 사항이 있으면 사용자에게 제시한다.

## 3. Step 설계

사용자가 구현 계획 작성을 지시하면 여러 step으로 나뉜 초안을 작성해 피드백을 요청한다.

설계 원칙:

1. 하나의 step에서는 하나의 레이어 또는 모듈만 다룬다.
2. 각 step 파일은 독립된 Codex 세션에서 실행 가능해야 한다. 외부 대화 맥락에 의존하지 않는다.
3. 읽어야 할 관련 문서와 이전 step의 생성·수정 파일을 명시한다.
4. 인터페이스는 시그니처 수준으로 지시하고 구현 세부는 Codex에 맡긴다. 멱등성, 보안, 데이터 무결성 같은 핵심 규칙은 명시한다.
5. Acceptance Criteria에는 실제 실행 가능한 검증 명령을 포함한다.
6. 주의사항은 `X를 하지 마라. 이유: Y` 형식으로 구체화한다.
7. step name은 핵심 작업을 표현하는 kebab-case slug로 작성한다.

## 4. 승인 후 manager로 phase 생성

새 phase 생성의 표준 진입점은 `scripts/run_phase_manager.ps1`이다. 먼저 결정론적 taxonomy 제안을 확인하고, 사용자가 초안을 승인하면 manager로 registry와 phase index를 생성한다.

```powershell
.\scripts\run_phase_manager.ps1 -Command Suggest -Slug <phase-slug> -Step <step-name> -Path <intended-path>
.\scripts\run_phase_manager.ps1 -Command Create -Slug <phase-slug> -Step <step-name> -Path <intended-path>
```

`Suggest`가 모호함을 반환하면 임의로 분류하지 않고 `-Area`, `-Kind`, `-Tag` 값을 구체화한다. `Create`는 다음 id를 할당하고 `000-009`, `010-019`처럼 10개 단위 bucket 아래에 timestamp 없는 phase `index.json`을 만든다. 승인 후 `step{N}.md`를 작성하며, phase index의 모든 step은 처음에 `pending`이어야 한다.

manager의 조회·검증·동기화 명령은 다음과 같다.

```powershell
.\scripts\run_phase_manager.ps1 -Command List
.\scripts\run_phase_manager.ps1 -Command Show -Selector <phase-selector>
.\scripts\run_phase_manager.ps1 -Command Validate
.\scripts\run_phase_manager.ps1 -Command Sync
.\scripts\run_phase_manager.ps1 -Command Sync -Check
.\scripts\run_phase_manager.ps1 -Command MigrateLegacy -DryRun
```

`List`는 status·area·kind·tag filter를, `Show`는 숫자 id, 기존 basename, registry 상대 `dir` selector를 지원한다. migration 전 평면 phase와 신규 bucket phase는 같은 방식으로 조회·실행할 수 있다. catalog의 outgoing·incoming은 실제 `step*.md`의 명시적 phase 참조에서 매번 계산하는 읽기 전용 catalog 관계이며 registry나 child prompt에 실행 의존성으로 저장·주입하지 않는다. `Validate`는 이 legacy 혼합 bootstrap 상태를 허용하고 `-Strict`에서 migration 완료 계약을 검사한다. `Sync`는 phase index의 원천 상태를 `phases/index.json` 표시값과 생성된 `phases/README.md`에 반영하고, `-Check`는 파일을 쓰지 않고 drift만 검사한다. 실제 migration은 별도 승인 phase에서만 수행하고 평소에는 `MigrateLegacy -DryRun`으로 manifest를 확인한다.

기존 파일 형식의 상세 계약은 아래와 같다.

### `phases/index.json`

파일이 있으면 `phases` 배열에 항목을 추가한다.

```json
{
  "phases": [
    {
      "dir": "0-mvp",
      "status": "pending"
    }
  ]
}
```

`status`는 `pending`, `completed`, `error`, `blocked` 중 하나다. 생성 시 타임스탬프를 넣지 않는다.

### `phases/{task-name}/index.json`

```json
{
  "project": "<프로젝트명>",
  "phase": "<task-name>",
  "steps": [
    { "step": 0, "name": "project-setup", "status": "pending" },
    { "step": 1, "name": "core-types", "status": "pending" },
    { "step": 2, "name": "api-layer", "status": "pending" }
  ]
}
```

- `project`: `AGENTS.md`의 프로젝트명
- `phase`: task 디렉터리명과 동일한 이름
- `steps[].step`: 0부터 시작하는 순번
- `steps[].name`: kebab-case slug
- `steps[].status`: 초기값 `pending`

실행 중 상태별 필드를 기록한다.

| 상태 | 필드 |
|---|---|
| `completed` | `completed_at`, `summary` |
| `error` | `failed_at`, `error_message` |
| `blocked` | `blocked_at`, `blocked_reason` |

`summary`에는 다음 step에 유용한 생성 파일과 핵심 결정을 한 줄로 기록한다. `summary`는 반드시 한국어로 작성한다. `created_at`과 `started_at`은 생성 시 넣지 않는다.

### `phases/{task-name}/step{N}.md`

각 step 파일에 다음 섹션을 포함한다.

````markdown
# Step {N}: {이름}

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- {이전 step에서 생성/수정된 파일 경로}

## 작업

{파일 경로, 인터페이스/시그니처, 로직과 핵심 규칙을 포함한 구체적 지시}

## Acceptance Criteria

```bash
npm run build
npm test
```

## 검증 절차

1. AC 명령을 실행한다.
2. ARCHITECTURE, ADR, AGENTS.md의 CRITICAL 규칙을 확인한다.
3. task index의 상태와 결과 필드를 업데이트한다.

## 금지사항

- {X를 하지 마라. 이유: Y}
- 기존 테스트를 깨뜨리지 마라.
````

AC 성공 시 `completed`와 `summary`, 3회 수정 후 실패 시 `error`와 `error_message`, 사용자 개입이 필요하면 `blocked`와 `blocked_reason`을 기록한다.

## 5. 실행 안내

Windows/Codex 표준 진입점은 PowerShell 래퍼다. `scripts/execute.py`를 직접 호출하지 않는다.

```powershell
.\scripts\run_harness.ps1 -Phase {task-name}
.\scripts\run_harness.ps1 -Phase {task-name} -Push
```

### Phase 생성 후 자동 child 실행

phase 파일 생성 후 같은 요청에서 즉시 구현까지 지시받으면 사용자에게 추가 입력을 요구하지 않고 다음 작업으로 PowerShell 래퍼를 호출한다.

```powershell
.\scripts\run_harness.ps1 -Phase {task-name}
```

phase 생성만 요청받으면 래퍼를 실행하지 않는다. `-Push`는 사용자가 명시적으로 요청한 경우에만 사용한다.

`execute.py`는 각 pending step을 별도 `codex exec` child 세션에서 실행하고, 완료 step의 `summary`를 다음 step에 전달한다. 따라서 step별 컨텍스트가 격리되면서 필요한 결과는 이어지므로 부모 세션의 compaction이 필요하지 않다. child step이 `blocked` 또는 `error`로 종료되면 다음 step으로 진행하지 않는다.

`run_harness.ps1`는 저장소 루트를 script 위치 기준으로 계산하고, `.venv\Scripts\python.exe` 존재 여부를 확인한 뒤 `PYTHONUTF8=1`을 설정해서 `scripts\execute.py`를 호출한다.

`.codex/rules/harness.rules`는 이 래퍼만 `prompt` 대상으로 등록한다. rule을 새로 추가하거나 수정한 뒤에는 Codex를 재시작해야 하며, project-local `.codex` layer가 trusted 상태여야 rule이 로드된다.

`execute.py`는 브랜치 생성, `AGENTS.md`와 `docs/*.md` 가드레일 주입, 완료 step 컨텍스트 누적, 최대 3회 재시도, step 단위 단일 커밋, 상태 타임스탬프 기록을 담당한다.

`execute.py`가 각 step을 위해 띄우는 child Codex는 `--dangerously-bypass-approvals-and-sandbox`와 `--dangerously-bypass-hook-trust`로 실행한다. 이 정책은 사용자가 harness 래퍼 실행을 승인한 뒤 Gradle wrapper 배포 zip 다운로드나 Gradle 의존성 접근이 child sandbox 네트워크 제한에 반복 차단되지 않게 하기 위한 것이다.

### Stop hook 정책

Stop hook은 turn 단위로 실행된다. matcher로 phase 실행만 선별할 수 없으므로 repository runner인 `scripts/stop_hook.py`가 hook event context와 Git changed path를 판별한다.

Harness child Codex에는 `TODO_QUEST_HARNESS_CHILD=1`이 전달되며, runner는 `TODO_QUEST_HARNESS_CHILD=1`이면 Stop 검증을 건너뛴다. 각 child step은 step 파일의 Acceptance Criteria를 직접 실행하고 status를 갱신해야 하며, Acceptance Criteria 실행과 step status가 harness 완료 판정의 기준이다. Phase 완료 후 부모 Stop은 이미 커밋된 phase 전체를 다시 검증하지 않는다. 부모 Stop을 두 번째 phase acceptance gate로 사용하지 않는다.

일반 Codex turn에서도 runner는 Plan, 관련 변경 없음, 동일 fingerprint 성공 상태에서는 즉시 종료한다. 관련 미커밋 변경이 있을 때만 harness 또는 Android 검증군을 선택하며, 두 검증군에 모두 관련된 변경이면 각각 실행한다.

성공 cache는 `build/codex-stop-hook/cache.json`에 검증군별 fingerprint로 저장한다. 검증 출력은 `build/codex-stop-hook/latest-harness.log`와 `build/codex-stop-hook/latest-android.log`에 저장하고, harness pytest 임시 산출물도 `build/codex-stop-hook/pytest-<turn>` 아래에 둔다. Android 검증에서 Gradle은 `test`, `lint`, `assembleDebug`를 한 번의 offline invocation으로 실행한다.

검증 실패 또는 필수 도구 부재 시 runner는 `continue: false`, `stopReason`, `systemMessage`가 있는 continuation JSON을 반환하여 종료를 중단한다. 검증 명령이 실행된 경우 `stopReason`에는 전체 출력 로그 경로와 크기가 제한된 출력 끝부분을 포함한다. Codex는 실패를 수정하거나 필요한 도구가 없으면 blocked로 보고해야 한다.

`.codex/hooks.json` 또는 runner처럼 hook 정의에 영향을 주는 변경 후에는 `/hooks`에서 새 hash를 검토하고 신뢰한 다음 Codex를 재시작해야 한다.

`error`나 `blocked`를 재실행할 때는 원인을 해결한 뒤 해당 status를 `pending`으로 되돌리고 오류 또는 차단 사유 필드를 삭제한다.
