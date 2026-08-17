# Step 7: 앱 Navigation Compose 연결

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/app/build.gradle.kts`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`
- step 4~6에서 생성·수정한 repository, ViewModel, CalendarScreen, CharacterScreen 파일
- `/phases/020-029/25-character-progression-foundation/index.json`

## 작업

앱 navigation Compose test를 먼저 추가해 실패를 확인한 다음 단일 activity 안에 Calendar와 Character 두 top-level destination을 연결한다.

- `/app/build.gradle.kts`에 `androidx.navigation:navigation-compose:2.9.8`과 동일 버전의 androidTest navigation testing 의존성을 추가한다.
- route 인자가 없으므로 Kotlin serialization plugin은 추가하지 않고 `AppDestination.Calendar`과 `AppDestination.Character`의 안정적인 문자열 route를 사용한다.
- `TodoQuestApp`의 Material 3 `Scaffold`에 하단 `NavigationBar`를 두고 `NavHost` 시작 destination은 Calendar로 한다.
- 탭 이동은 `launchSingleTop = true`, start destination까지 `popUpTo`하면서 `saveState = true`, `restoreState = true`를 사용해 두 top-level 화면 상태를 보존한다.
- database와 clock은 앱 composition root에서 한 번 만들고 `RoomTaskRepository`와 `RoomCharacterRepository`가 공유한다.
- Calendar와 Character ViewModel은 각 destination에서 factory로 생성하되 UI에 repository 구현이나 DAO를 전달하지 않는다.
- 시스템 back은 Navigation Compose back stack 계약을 따르고 Calendar가 root일 때 activity 기본 종료 동작을 유지한다.
- bottom navigation은 선택 상태, `Calendar`, `Character` TalkBack label과 최소 touch target을 제공한다.

`AppNavigationTest`에서 시작 destination, Character 이동, Calendar 복귀, 탭 선택 상태와 캐릭터 화면 핵심 정보를 검증한다. 기존 launch smoke test와 database isolation test가 새 app root에서도 유효하도록 갱신한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lint assembleDebug assembleDebugAndroidTest
git diff --check
```

## 검증 절차

1. navigation Compose test를 구현보다 먼저 추가하고 컴파일 실패 또는 assertion 실패를 확인한다.
2. AC를 실행하고 navigation 의존성 충돌과 lint 오류가 없는지 확인한다.
3. `adb devices`에 실행 가능한 기기가 있으면 `.\gradlew.bat connectedDebugAndroidTest`를 추가 실행한다. 기기가 없으면 mandatory gate로 처리하지 않는다.
4. task index step 7을 `completed`로 변경하고 navigation과 composition root 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- destination composable 안에서 Room database를 생성하지 마라. 이유: 화면 이동마다 인스턴스가 갈리고 transaction source가 분리될 수 있다.
- route 인자가 없는데 serialization plugin을 추가하지 마라. 이유: 불필요한 build 복잡도다.
- 각 탭 전환 때 back stack entry를 무제한 쌓지 마라. 이유: back 동작과 상태 복원이 불안정해진다.
- navigation을 ViewModel의 도메인 state로 소유하지 마라. 이유: top-level 화면 이동은 app UI 책임이다.
- 기존 테스트를 깨뜨리지 마라.

