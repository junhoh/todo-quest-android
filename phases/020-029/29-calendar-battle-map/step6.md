# Step 6: 문서 동기화와 전체 검증

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/art/battle/README.md`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapLayout.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/phases/020-029/29-calendar-battle-map/index.json`

## 작업

실제 구현 결과를 문서와 동기화하고 전체 Android·harness·아트 검증을 실행한다.

- 기존 화면 구조, 추가/수정한 핵심 파일, BattleMap layer tree, normalized→pixel 식과 clamp, 0~4 monster slot 규칙을 문서에 정확히 반영한다.
- 기본 배경을 다른 stage map으로 바꾸는 방법을 `BattleMapTheme`/`backgroundResId`와 `drawable-nodpi/battle_map_*` 예시로 기록한다.
- 운영 화면은 실제 `CombatRepository`의 한 active monster만 표시하고 다중 monster backend는 미구현임을 유지한다.
- loading, no monster sample, 1 monster, 2~4 component support, missing background fallback, small phone, tablet/landscape, dark mode 상태가 구현·test·Preview 중 어디서 검증되는지 기록한다.
- health bar/name/status는 unit anchor 위, attack/damage는 `BattleOverlayLayer`, stage HUD는 overlay 상단에 추가한다고 후속 위치를 기록한다.
- 배경과 sprite가 독립 resource/layer이며 background PNG에 unit·HP·damage·text가 없다는 visual QA 결과를 기록한다.
- Android 도구가 없다면 설치하지 말고 blocked 처리한다. 연결 emulator가 없으면 unit/lint/assemble 결과를 보존하되 `connectedDebugAndroidTest` 요구 때문에 blocked_reason에 사용자 개입 필요 사항을 기록한다.
- 전체 성공 시 step 6과 phase top-level, root `phases/index.json` 항목을 `completed`로 갱신하고 생성 파일·핵심 결정·실행한 검증을 한국어 `summary`로 기록한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
.\.venv\Scripts\python.exe scripts\validate_battle_map.py --image docs\art\battle\todo-quest-battle-map-grassland.png --spec docs\art\battle\battle-map-grassland-spec.json --runtime app\src\main\res\drawable-nodpi\battle_map_grassland.png
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-goblin-scout-front-idle.png --spec docs\art\monster\todo-quest-goblin-scout-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_battle_map.py scripts\test_validate_character_sheet.py scripts\test_validate_monster_sprite.py scripts\test_execute.py --basetemp .\.venv\pytest-29-battle-final
git diff --check
```

## 검증 절차

1. 모든 AC를 실행하고 unit, lint, assemble, connected UI, art validator와 harness pytest 성공을 확인한다.
2. AGENTS의 UI/Repository 경계, occurrence 보상 멱등성, 반복 원본/발생분 분리, 한국어 resource와 권한 비의존성을 최종 확인한다.
3. 실제 앱에서 map→summary→calendar→task 순서, 발 접지, monster 수별 overlap, scroll과 bottom navigation 충돌 부재를 확인한다.
4. step 6, phase index와 root phase status를 완료 처리한다.

## 금지사항

- 검증 실패를 무시하고 phase를 completed 처리하지 마라. 이유: 완료 상태는 모든 AC 성공을 의미한다.
- phase 완료 후 부모 Stop을 두 번째 acceptance gate로 사용하지 마라. 이유: child step AC가 harness 완료 판정 기준이다.
- Android SDK/JDK를 임의 설치하거나 사용자 환경 변수를 영구 변경하지 마라. 이유: AGENTS 개발 프로세스 규칙이다.
- 구현되지 않은 health bar·animation·HUD·다중 monster backend를 완료로 문서화하지 마라. 이유: 후속 범위를 정확히 유지해야 한다.
- 기존 테스트를 깨뜨리지 마라.
