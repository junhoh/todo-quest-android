# Step 8: synchronize-and-validate

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/app/src/main/java/com/todoquest/feature/battle/`
- `/app/src/main/java/com/todoquest/feature/calendar/`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/6.json`
- `/phases/030-039/33-calendar-combat-feedback/index.json`

## 작업

구현과 테스트의 실제 상태를 canonical 문서에 동기화한다. PRD에는 Calendar Combat Feedback v1 구현 상태, 명시적 occurrence 상태와 failure undo 비롤백 의미를 기록한다. ARCHITECTURE/ADR에는 Room v6 failure log, shared monster event key, application-scope replay 0 transition, ViewModel Channel actor와 고정/scroll layout 경계를 기록한다. UI_GUIDE에는 HUD 한 Row, gold group 간격, HP bar 위치·low state, animation 순서, compact-height map과 TalkBack/live region 계약을 기록한다. monster/game validation 문서에는 manual/deadline trigger, damage·reward·spawn 멱등성 test 위치를 기록한다.

전체 검증을 실행한다. connected device가 있으면 모든 instrumentation test를 실행하고 portrait와 landscape에서 고정 Battle Map, scroll Calendar, HUD/HP, TODO complete/fail, FAILED/취소와 bottom navigation을 확인할 수 있는 build artifact screenshot 또는 test evidence를 남긴다. animation state는 deterministic Compose test와 controller virtual-time test를 근거로 삼고, 화면 캡처만으로 state 순서를 대체하지 않는다.

실패가 있으면 이번 phase 변경과 기존 문제를 구분하고 수정 후 AC를 다시 실행한다. Android SDK/JDK/adb가 없거나 connected test 대상이 없으면 임의 설치하지 말고 step과 phase를 `blocked`로 기록한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. 모든 AC를 실행하고 unit·lint·assemble·connected·harness 결과를 문서와 step summary에 기록한다.
2. AGENTS.md CRITICAL 규칙, occurrence reward/attack 멱등성, Room v6 migration, no Google Calendar, 한국어 resource와 권한 비의존 동작을 최종 확인한다.
3. task index의 step 8을 `completed`로 바꾸고 전체 검증 결과를 한국어 `summary` 한 줄로 기록한다.
4. 모든 step이 completed면 task index와 `/phases/index.json`의 phase 33을 `completed`로 바꾸고 완료 시각과 한국어 summary를 기록한다.

## 금지사항

- 실패한 test나 수행하지 못한 connected 검증을 성공으로 기록하지 마라. 이유: phase status는 실제 Acceptance Criteria 근거여야 한다.
- transient animation을 영구 Room 상태로 문서화하지 마라. 이유: 저장 상태와 presentation event 경계를 흐린다.
- 기존 보상·Stage·부활 수식이 변경된 것처럼 문서화하지 마라. 이유: 이번 구현은 기존 정책을 재사용한다.
- 기존 테스트를 깨뜨리지 마라.
