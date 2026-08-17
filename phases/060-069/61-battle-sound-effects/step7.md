# Step 7: wire-audio-lifecycle-and-navigation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/androidTest/java/com/todoquest/app/TodoQuestTestRunner.kt`
- `/app/src/test/java/com/todoquest/app/TodoQuestAppContainerTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/app/src/main/java/com/todoquest/audio/BattleSfxPlayer.kt`
- `/app/src/main/java/com/todoquest/feature/settings/SettingsScreen.kt`
- `/app/src/main/java/com/todoquest/feature/settings/SettingsViewModel.kt`
- `/phases/060-069/61-battle-sound-effects/step4.md`
- `/phases/060-069/61-battle-sound-effects/step5.md`
- `/phases/060-069/61-battle-sound-effects/step6.md`
- `/phases/060-069/61-battle-sound-effects/index.json`

## 작업

application composition root와 navigation integration을 테스트 우선으로 완성한다. `TodoQuestAppContainer`는 `BattleSfxSettingsRepository`와 `BattleSfxPlayer`를 injectable dependency로 노출한다. public/test constructor의 기본 player는 no-op이고 production `create(context)`만 SharedPreferences repository, Android SoundPool delegate와 configured bounded wrapper를 application-scope 한 번 생성한다. Calendar factory에 같은 configured player를 전달한다. Settings factory에는 같은 settings repository를 전달해 switch 변경과 다음 전투 재생이 같은 source를 공유하게 한다.

container는 audio release entry point를 제공하고 `TodoQuestApplication.onTerminate()` 또는 명시적 test teardown에서 idempotent release한다. Android player가 application lifecycle callback을 등록했다면 release 때 반드시 unregister한다. database/notification lifecycle은 변경하지 않는다. production/test init 또는 player 생성 실패는 no-op/logging으로 격리해 앱 시작과 전투를 중단하지 않는다.

`AppDestination.Settings`를 route `settings`, label `navigation_settings`, tag `bottom-navigation-settings`인 다섯 번째 TopLevel로 추가한다. icon은 Material Settings를 사용하고 bottom navigation order를 Calendar, Character, Shop, Compendium, Settings로 고정한다. `topLevelDestinationForRoute`, NavHost와 ViewModel factory를 연결하고 Settings에는 top-level back action을 두지 않는다. 기존 nested Shop/Compendium route의 tab mapping과 state restore를 보존한다.

instrumented test container에는 recording `FakeBattleSfxPlayer`와 fake/in-memory settings repository를 주입해 실제 스피커를 사용하지 않는다. 새 app navigation tests는 다섯 label/tag의 순서·선택·bounds, settings toggle 후 Calendar의 새 event만 muted, 다시 켠 뒤 다른 event 재생, Activity recreation 설정 복원, Calendar tab 이탈/재진입과 Activity recreation에서 과거 event 비재생을 검증한다. background gate는 Android adapter unit test를 source로 삼고 connected test가 실제 시스템 음량이나 DND를 변경하지 않게 한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.app.TodoQuestAppContainerTest" --tests "com.todoquest.feature.calendar.CalendarViewModelTest" --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.AppNavigationTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. container dependency identity/no-op/failure tests와 navigation/toggle/recreation fake tests를 먼저 작성한다.
2. AC 명령을 실행한다.
3. 다섯 tab이 320dp/font scale 2.0에서 겹치지 않고 기존 Calendar·Character·Shop·Compendium 및 nested back 동작이 유지되는지 확인한다.
4. task index의 step 7을 `completed`로 바꾸고 application wiring, lifecycle과 navigation 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- test에서 production SoundPool로 실제 소리를 재생하지 마라. 이유: 검증은 fake request와 순서로 결정적이어야 한다.
- Activity context를 application-scope player에 보관하지 마라. 이유: 회전 시 context leak이 발생한다.
- 기존 top-level state restore와 nested destination mapping을 제거하지 마라. 이유: 기존 navigation 호환 계약이다.
- 앱 시작 시 audio 실패를 fatal로 전파하지 마라. 이유: 전투와 일정 기능은 audio와 독립적이어야 한다.
- 기존 테스트를 깨뜨리지 마라.
