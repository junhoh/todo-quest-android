# Step 6: synchronize-corrupted-tree-spirit-docs-and-validate

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/art/monster/README.md`
- `/docs/art/monster/todo-quest-corrupted-tree-spirit-front-idle-spec.json`
- `/docs/game-design/monster-stats-and-growth.md`
- `/app/src/main/java/com/todoquest/domain/model/Monster.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MonsterSpeciesPolicy.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleAnimationController.kt`
- `/phases/040-049/41-corrupted-tree-spirit-enemy/index.json`

## 작업

구현 결과를 canonical 문서와 동기화하고 phase 전체 acceptance를 실행한다.

- `docs/art/monster/README.md`에 세 번째 canonical PNG·spec·validator 명령과 byte-identical runtime 사본을 등록한다.
- `UI_GUIDE.md`에 나무 정령의 `[14,11,50,58]`, 48px 높이, 공통 외곽선·ground anchor·최근접 보간, 한국어 접근성 표시와 세 종족 정책을 기록한다. 기존 고블린·해골이 공유하는 `[16,13,48,58]` 계약과 혼동하지 않는다.
- `ARCHITECTURE.md`에 `definitionId + grade → definition type → MonsterSpeciesPolicy → CombatSnapshot.activeMonsterSpecies → BattleMonsterVisualCatalog` data flow와 세 종족 매핑을 기록한다.
- `ADR.md`의 Monster Combat/Battle Map 구현 상태에 `BALANCED + NORMAL`을 나무 정령으로 파생하는 결정과 무 migration·무 balance change를 반영한다. 새 상성 ADR은 만들지 않는다.
- `docs/game-design/monster-stats-and-growth.md`에 정확한 type/grade 매핑 행렬, Stage 1 encounter 예시와 능력치·보상 불변을 기록한다.
- 필요한 경우 PRD와 docs index의 현재 canonical 에셋 목록을 실제 구현과 일치하도록 최소 수정한다.
- `forest`, `dark`, `corruption`은 이번 phase에서 시각 콘셉트와 종족 식별 metadata이며 biome, 상성, 상태 효과, 스킬, 전리품, 새 맵 배경을 추가하지 않는다고 명시한다.

전체 검증이 성공하면 task index의 step 6과 phase를 `completed`로 갱신하고 `/phases/index.json`의 `41-corrupted-tree-spirit-enemy`도 `completed`로 갱신한다. phase summary는 canonical/runtime PNG, 종족 정책, presentation과 검증 결과를 한국어 한 줄로 기록한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-goblin-scout-front-idle.png --spec docs\art\monster\todo-quest-goblin-scout-front-idle-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-skeleton-soldier-front-idle.png --spec docs\art\monster\todo-quest-skeleton-soldier-front-idle-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-corrupted-tree-spirit-front-idle.png --spec docs\art\monster\todo-quest-corrupted-tree-spirit-front-idle-spec.json
$canonical = (Get-FileHash -Algorithm SHA256 docs\art\monster\todo-quest-corrupted-tree-spirit-front-idle.png).Hash
$runtime = (Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_corrupted_tree_spirit_front_idle.png).Hash
if ($canonical -ne $runtime) { throw 'Runtime corrupted tree spirit sprite differs from canonical art' }
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_validate_battle_map.py scripts\test_execute.py --basetemp build\pytest-41-tree-spirit-final
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
git diff --check
```

## 검증 절차

1. 모든 AC 명령을 실행한다.
2. 세 monster canonical PNG, 나무 정령 runtime byte equality, 1배율·8배율 나무 정령과 portrait/landscape Battle Map을 확인한다.
3. 테스트 결과와 실제 코드·경로·Room version·종족 mapping을 문서 내용과 대조한다.
4. ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 최종 확인한다.
5. AC 성공 시 phase와 상위 index를 `completed`로 갱신한다. 필수 Android 도구·기기 부재 시 설치하지 않고 `blocked`, 3회 수정 후에도 코드·테스트 실패가 남으면 `error`로 기록한다.

## 금지사항

- 실제 구현과 다른 완료 수치나 검증 결과를 문서에 추정해 쓰지 마라. 이유: canonical 문서는 실행 증거와 일치해야 한다.
- Room migration, biome, 새 배경, 속성 상성, 상태 효과, 스킬 또는 전리품을 추가하지 마라. 이유: 승인된 종족 표시 범위를 넘어선다.
- phase 완료 뒤 부모 Stop을 두 번째 전체 acceptance gate로 사용하지 마라. 이유: 각 child step의 AC와 status가 harness 완료 판정이다.
- 기존 테스트를 깨뜨리지 마라.
