# Step 9: 전투 문서 동기화와 전체 검증

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/game-design/README.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/docs/game-design/character-stats-design.md`
- `/docs/game-design/character-stats/todo-combat-rewards.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/app/src/main/java/com/todoquest/domain/model/MonsterStats.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/notification/CombatReconciliationWorker.kt`
- `/phases/020-029/28-monster-stats-and-combat/index.json`

## 작업

실제 생성 파일명을 찾아 구현과 문서를 최종 동기화한다.

- 루트·게임 설계 인덱스에서 monster stats canonical 문서를 연결한다.
- PRD/ARCHITECTURE/ADR와 character combat 하위 문서의 상태 표를 실제 구현 결과로 바꾼다.
- 구현 완료: 세 능력치/Stage 계산, Room v4 원천 상태, occurrence player attack outbox, missed monster attack, 3회 복귀 상한, 25% 즉시 부활, WorkManager reconciliation.
- 남은 후속: 몬스터/전투 UI, 사망 디버프, 장비 source, 몬스터 스킬·치명타, 처치 XP·골드·전리품, 정확한 deadline alarm.
- `MonsterDefinition`의 표시 이름은 backend `nameKey`이며 UI가 추가될 때 `strings.xml` 한국어 resource로 매핑해야 한다고 기록한다.
- WorkManager는 best-effort라 정확한 시각 공격을 보장하지 않으며 event deadline과 처리 시각을 구분한다고 명시한다.
- Room schema와 public 타입 목록, version, 실제 패키지 경로, test 상태가 문서와 일치하도록 고친다.

전체 검증을 실행하고 실패를 수정한다. Android 도구가 없다면 설치하지 말고 blocked로 기록한다. 완료 시 phase top-level status도 `completed`로 바꾸고 한국어 summary를 기록한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
$doc = Get-Content -Raw -Encoding UTF8 -LiteralPath 'docs\game-design\monster-stats-and-growth.md'
@('MAX_HP','DAMAGE','DEFENSE','Room v4','WorkManager','25%','후속') | ForEach-Object { if (-not $doc.Contains($_)) { throw "Final documentation marker missing: $_" } }
git diff --check
```

## 검증 절차

1. 모든 AC를 실행하고 로그에서 test/lint/assemble/harness 성공을 확인한다.
2. AGENTS의 레이어, occurrence 멱등, 파생값 비저장, 권한 독립성과 테스트 우선 규칙을 최종 검토한다.
3. step 9와 phase index를 완료 처리하고 생성 파일·Room v4·전투 정책·검증 결과를 한국어 `summary`로 기록한다.

## 금지사항

- 구현되지 않은 UI·디버프·보상을 완료로 표시하지 마라. 이유: 후속 범위를 정확히 유지해야 한다.
- phase 완료 후 부모 Stop을 별도 두 번째 acceptance gate로 사용하지 마라. 이유: 각 child step AC가 harness 완료 기준이다.
- 검증 실패를 무시하고 status를 completed로 바꾸지 마라. 이유: 완료 상태는 실제 검증 성공을 의미한다.
- 기존 테스트를 깨뜨리지 마라.
