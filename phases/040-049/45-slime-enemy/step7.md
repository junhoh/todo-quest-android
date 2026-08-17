# Step 7: synchronize-slime-docs-and-validate

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/art/monster/README.md`
- `/docs/art/monster/todo-quest-slime-front-idle-spec.json`
- `/docs/game-design/monster-stats-and-growth.md`
- `/app/src/main/java/com/todoquest/domain/model/Monster.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MonsterSpeciesPolicy.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleAnimationController.kt`
- `/phases/040-049/45-slime-enemy/index.json`

## 작업

구현 결과를 canonical 문서와 동기화하고 phase 전체 acceptance를 실행한다.

- `docs/art/monster/README.md`에 다섯 번째 canonical PNG·spec·validator 명령과 byte-identical runtime 사본을 등록한다.
- `UI_GUIDE.md`에 슬라임의 `[17,35,47,58]`, 24px 높이, 12색, 공통 외곽선·ground anchor·최근접 보간과 한국어 접근성 표시를 기록한다. 기존 네 종족 bounds와 혼동하지 않는다.
- `ARCHITECTURE.md`에 `stored stageNumber + encounterNumber + grade + balanceVersion → MonsterSpeciesPolicy → CombatSnapshot.activeMonsterSpecies → BattleMonsterVisualCatalog` data flow를 기록한다. `MonsterType`은 능력치용이고 종족 스케줄 입력이 아님을 명시한다.
- `ADR.md`의 Monster Combat/Battle Map 구현 상태에 결정적 다섯 종족 셔플, NORMAL Stage의 최소 1회·1~2회 균형, ELITE/BOSS 단일 선택, 무 migration·무 balance change를 반영한다.
- `docs/game-design/monster-stats-and-growth.md`에 명시적 종족 목록, seed 입력·셔플 규칙, NORMAL/특수 Stage 계약과 능력치·보상 불변을 기록한다.
- `DEVELOPMENT.md`에는 실제로 실행한 validator, hash, Python/JVM/lint/APK/connected 결과만 기록한다. 필요한 경우 PRD와 docs index의 canonical 에셋 목록을 실제 구현과 일치하도록 최소 수정한다.
- `water`, `slime`은 이번 phase에서 시각 콘셉트와 종족 식별 metadata이며 상성, 상태 효과, 스킬, 전리품, biome, 새 맵 배경을 추가하지 않는다고 명시한다.

전체 검증이 성공하면 task index의 step 7과 phase를 `completed`로 갱신하고 manager `Sync`로 `/phases/index.json`과 `/phases/README.md`를 동기화한다. phase summary는 canonical/runtime PNG, 결정적 종족 스케줄, presentation과 검증 결과를 한국어 한 줄로 기록한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-goblin-scout-front-idle.png --spec docs\art\monster\todo-quest-goblin-scout-front-idle-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-skeleton-soldier-front-idle.png --spec docs\art\monster\todo-quest-skeleton-soldier-front-idle-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-corrupted-tree-spirit-front-idle.png --spec docs\art\monster\todo-quest-corrupted-tree-spirit-front-idle-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-harpy-front-idle.png --spec docs\art\monster\todo-quest-harpy-front-idle-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-slime-front-idle.png --spec docs\art\monster\todo-quest-slime-front-idle-spec.json
$canonical = (Get-FileHash -Algorithm SHA256 docs\art\monster\todo-quest-slime-front-idle.png).Hash
$runtime = (Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_slime_front_idle.png).Hash
if ($canonical -ne $runtime) { throw 'Runtime slime sprite differs from canonical art' }
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_validate_battle_map.py scripts\test_execute.py scripts\test_phase_manager.py --basetemp build\pytest-45-slime-final
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
# Gradle UTP 전체 결과는 app/build/verification/45-slime-enemy/connected-physical-utp.xml을 검사한다.
# 반드시 tests=92, failures=2, errors=0, skipped=0이고 실패 이름이 아래 두 개뿐이어야 한다.
# - CalendarScreenTest.customReminderNullValidationAndCompactEditorScrollKeepActionsReachable
# - ReminderBackgroundSmokeFixtureTest.seedCustomReminderFifteenToSixtySecondsAhead
adb -s RF9R700HNNJ install -r -g .\app\build\outputs\apk\debug\app-debug.apk
adb -s RF9R700HNNJ install -r -g .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s RF9R700HNNJ shell appops set com.todoquest SCHEDULE_EXACT_ALARM allow
adb -s RF9R700HNNJ shell pm grant com.todoquest android.permission.POST_NOTIFICATIONS
adb -s RF9R700HNNJ shell svc power stayon usb
adb -s RF9R700HNNJ shell input keyevent KEYCODE_WAKEUP
adb -s RF9R700HNNJ shell wm dismiss-keyguard
adb -s RF9R700HNNJ shell am instrument -w -r -e class 'com.todoquest.feature.calendar.CalendarScreenTest#customReminderNullValidationAndCompactEditorScrollKeepActionsReachable,com.todoquest.notification.ReminderBackgroundSmokeFixtureTest#seedCustomReminderFifteenToSixtySecondsAhead' com.todoquest.test/com.todoquest.app.TodoQuestTestRunner
adb -s RF9R700HNNJ shell svc power stayon false
.\scripts\run_phase_manager.ps1 -Command Validate
.\scripts\run_phase_manager.ps1 -Command Sync
.\scripts\run_phase_manager.ps1 -Command Sync -Check
git diff --check
```

## 검증 절차

1. 모든 AC 명령을 실행한다. 연결 gate는 보존된 Gradle UTP 결과의 비권한 90개 통과와, 사용자가 명시 승인한 exact-alarm app-op 상태에서 direct runner 2개 통과를 합쳐 92개 composite coverage로 판정한다. direct runner 종료 후 `stay_on_while_plugged_in=0`을 확인한다.
2. 다섯 canonical PNG, 슬라임 runtime byte equality, 1배율·8배율 슬라임과 portrait/landscape Battle Map을 확인한다.
3. NORMAL Stage의 다섯 종족 보장·1~2회 균형과 ELITE/BOSS 단일 결정적 선택을 실제 코드·테스트·문서와 대조한다.
4. Room version, 능력치·피해·보상 공식, ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 최종 확인한다.
5. AC 성공 시 phase와 상위 index를 `completed`로 갱신한다. 필수 Android 도구·기기 부재 시 설치하지 않고 `blocked`, 3회 수정 후에도 코드·테스트 실패가 남으면 `error`로 기록한다. Gradle UTP가 exact-alarm app-op을 초기화한 사실과 composite gate의 두 부분을 DEVELOPMENT.md에 실제 결과 그대로 기록한다.

## 금지사항

- 실제 구현과 다른 완료 수치, hash 또는 검증 결과를 문서에 추정해 쓰지 마라. 이유: canonical 문서는 실행 증거와 일치해야 한다.
- Room migration, 진짜 난수 저장, biome, 새 배경, 속성 상성, 상태 효과, 스킬 또는 전리품을 추가하지 마라. 이유: 승인된 종족 표시 범위를 넘어선다.
- 기존 활성 몬스터의 HP, Stage, encounter 또는 보상 결과를 소급 변경하지 마라. 이유: 종족은 presentation metadata로만 재해석한다.
- phase 완료 뒤 부모 Stop을 두 번째 전체 acceptance gate로 사용하지 마라. 이유: 각 child step의 AC와 status가 harness 완료 판정이다.
- 기존 테스트를 깨뜨리지 마라.
- UTP의 권한 초기화로 실패한 두 테스트를 코드 실패로 숨기거나 전체 connected gate가 단일 명령으로 통과했다고 기록하지 마라. 이유: 실제 실행 증거와 문서가 일치해야 한다.
