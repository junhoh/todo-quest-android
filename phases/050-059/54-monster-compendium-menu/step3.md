# Step 3: add-compendium-navigation-flow

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/feature/compendium/CompendiumScreen.kt`
- `/app/src/main/java/com/todoquest/feature/compendium/MonsterCompendiumScreen.kt`
- `/app/src/main/java/com/todoquest/feature/compendium/MonsterDetailScreen.kt`
- `/app/src/main/java/com/todoquest/feature/compendium/MonsterCompendiumViewModel.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/phases/050-059/54-monster-compendium-menu/step2.md`
- `/phases/050-059/54-monster-compendium-menu/index.json`

## 작업

AppNavigation connected test를 먼저 확장해 실패를 확인한 뒤 도감을 composition root와 Navigation Compose에 연결한다.

`AppDestination`에 다음 route 계약을 추가한다.

```text
compendium
compendium/monsters
compendium/monsters/{species}
```

`Compendium`은 `navigation_compendium` 한국어 문자열과 `bottom-navigation-compendium` test tag, Material `MenuBook` icon을 사용하는 네 번째 top-level destination이다. Monster list와 detail은 bottom item을 만들지 않는 nested destination이다. detail route builder는 `MonsterSpecies.name`을 내부 argument로 사용하고, route parser는 알 수 없는 값에서 예외로 앱을 종료하지 말고 monster list로 복귀시킨다. 외부 deep link는 추가하지 않는다.

`topLevelDestinations` 순서는 Calendar, Character, Shop, Compendium으로 고정한다. current route가 Inventory이면 Shop, Monster list/detail이면 Compendium tab이 선택되게 parent mapping을 일반화한다. 기존 `popUpTo(start) { saveState = true }`, `launchSingleTop`, `restoreState` 계약을 유지해 탭 반복 선택으로 back stack을 늘리지 않는다.

도감 back 순서는 `몬스터 상세 → 몬스터 목록 → 도감 루트`다. 탭을 다른 destination으로 전환했다가 돌아오면 Compendium의 nested state를 기존 top-level save/restore 정책에 따라 복원한다. 미발견 card에는 navigation callback이 없고, 직접 구성된 미발견 detail route에서도 ViewModel의 `Locked` state가 이름 외 정보를 노출하지 않는다.

`AppNavigation`이 이미 주입받는 application-scope `CombatRepository`로 list/detail ViewModel factory를 만들며 Repository 구현이나 DAO를 Compose 화면에 노출하지 않는다.

테스트는 다음을 포함한다.

- 네 top-level route와 하단 항목 순서·선택 상태.
- Compendium root에서 현재 유일한 Monster category 진입.
- 초기 활성 종의 발견 image/detail 진입과 미발견 종의 name-only 비클릭 상태.
- detail/list/root back 순서와 nested route에서 Compendium tab 선택 유지.
- 탭 전환 뒤 navigation state 복원과 같은 탭 반복 선택의 back stack 비증가.
- 기존 Shop→Inventory parent selection/back, notification tap→Calendar, Calendar/Character 화면 회귀.
- 하단 4개 label이 작은 화면에서도 Calendar content와 겹치지 않음.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:lint :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.AppNavigationTest" --console=plain
git diff --check
```

## 검증 절차

1. navigation connected test를 먼저 추가하고 구현 전 실패를 확인한다.
2. 네 탭과 세 Compendium route의 선택·back·restore 동작을 검증한다.
3. notification navigation과 Shop/Inventory nested route 회귀를 함께 실행한다.
4. Android 도구나 connected device가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
5. task index의 step 3을 `completed`로 바꾸고 route와 parent selection 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 펫 또는 맵 placeholder route와 화면을 추가하지 마라. 이유: 이번 범위는 작동하는 Monster category만 노출한다.
- Monster list/detail을 별도 top-level bottom item으로 만들지 마라. 이유: 둘은 Compendium의 nested destination이다.
- notification tap의 Calendar fallback을 변경하지 마라. 이유: 기존 알림 UX와 occurrence navigation 계약을 보존해야 한다.
- UI에서 `RoomCombatRepository`나 `CombatDao`를 직접 참조하지 마라. 이유: Repository abstraction을 유지해야 한다.
- 기존 테스트를 깨뜨리지 마라.
