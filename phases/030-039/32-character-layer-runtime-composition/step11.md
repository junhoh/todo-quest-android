# Step 11: integrate-character-and-battle-rendering

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterUiState.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/ui/character/`
- `/app/src/test/java/com/todoquest/feature/character/CharacterViewModelTest.kt`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/character/CharacterScreenTest.kt`
- `/phases/030-039/32-character-layer-runtime-composition/index.json`

## 작업

테스트를 먼저 수정한다. CharacterUiState에 appearance/equippedItems를 추가하고 CharacterViewModel snapshot mapping 및 `updateAppearance`, `updateEquippedItems` commands를 Repository에 연결한다. command failure가 사용자에게 노출되면 `strings.xml`의 한국어 generic error resource를 사용한다. 선택 UI는 추가하지 않는다.

Character 화면의 `EquippedCharacterSprite`를 shared `LayeredCharacterSprite`로 교체한다. complete 64×64 composite를 Canvas available size에 한 번만 scale하며 가능한 가장 큰 정수 배율을 사용하고, 작은 target은 uniform fit과 `FilterQuality.None`을 사용한다.

Battle sprite model을 single resource monster와 layered character player를 구분하는 sealed 형태로 바꾼다. CalendarViewModel은 하나의 observed CharacterSnapshot을 character summary와 battle player render state에 함께 사용해 loadout 변경이 두 화면에 반영되게 한다. BattleMap의 unit placement와 bottom-center ground anchor `58/64`는 유지하고 player만 shared composer로 그린다. monster resource path/renderer는 유지한다.

production source에서 `R.drawable.todo_quest_character_modular_sheet` 참조를 제거한다. debug/golden tests만 sheet resource를 사용할 수 있다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
rg -n "todo_quest_character_modular_sheet" app\src\main\java; if ($LASTEXITCODE -eq 0) { throw 'production modular sheet reference remains' }
git diff --check
```

## 검증 절차

1. Character screen, Calendar player, monster renderer와 semantics tests를 확인한다.
2. persisted mixed loadout과 weapon on/off가 두 화면에서 같은 render state를 사용하는지 확인한다.
3. task index의 step 11을 `completed`로 바꾸고 교체한 두 runtime 경로와 Android 검증 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 장비 선택 UI를 추가하지 마라. 이유: 사용자 승인 범위는 persistence와 command까지다.
- Calendar 또는 Character UI에서 Repository 외 데이터 변경을 수행하지 마라. 이유: 아키텍처 경계를 지켜야 한다.
- monster renderer를 layered character 모델로 강제 변환하지 마라. 이유: 몬스터는 독립 single sprite 계약을 유지한다.
- Android 도구나 emulator가 없으면 설치하지 마라. 이유: AGENTS.md에 따라 blocked로 기록해야 한다.
- 기존 테스트를 깨뜨리지 마라.
