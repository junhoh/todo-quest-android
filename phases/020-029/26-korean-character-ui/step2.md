# Step 2: 공통 navigation 한국어화

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/res/values/strings.xml`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- step 1에서 수정한 Character UI state, ViewModel, Screen과 테스트 파일
- `/phases/020-029/26-korean-character-ui/index.json`

## 작업

`AppNavigationTest`의 한국어 표시 기대값을 구현보다 먼저 수정해 실패를 확인한 다음 공통 하단 navigation을 string resource 기반으로 전환한다.

- `AppDestination.label: String`을 `@StringRes labelResId: Int` 계약으로 바꾸고 Calendar와 Character destination에 각각 `캘린더`, `캐릭터` 기본 리소스를 연결한다.
- `NavigationBarItem`에서 현재 destination의 label을 `stringResource`로 한 번 해석해 표시 text와 TalkBack `contentDescription`에 동일하게 사용한다.
- `calendar`, `character` route와 `bottom-navigation-calendar`, `bottom-navigation-character` test tag는 그대로 유지한다.
- `AppNavigationTest`에서 두 한국어 탭, 선택 상태, Character 이동 후 `레벨 1`과 `누적 경험치 0`, Calendar 복귀 후 선택 날짜 상태 보존을 검증한다.
- Calendar 화면 내부의 기존 영문 본문, 날짜 format과 task editor 문구는 수정하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug assembleDebugAndroidTest
git diff --check
```

## 검증 절차

1. AppNavigationTest를 구현보다 먼저 수정하고 기존 구현에서 한국어 assertion이 실패함을 확인한다.
2. AC를 실행하고 route 및 navigation state 회귀가 없는지 확인한다.
3. `adb devices`에 실행 가능한 기기가 있으면 `.\gradlew.bat connectedDebugAndroidTest`를 추가 실행한다. 기기가 없으면 test APK 조립 성공을 기록하고 mandatory gate로 처리하지 않는다.
4. AGENTS.md의 한국어 기본 CRITICAL 규칙과 ARCHITECTURE, ADR의 UI→ViewModel→Repository 경계를 확인한다.
5. `/phases/020-029/26-korean-character-ui/index.json`의 step 2와 `/phases/index.json`의 phase를 `completed`로 변경하고 navigation 번역과 전체 검증 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- route와 test tag를 한국어로 변경하지 마라. 이유: 사용자 표시명만 번역하고 navigation·테스트 식별자 호환성을 유지해야 한다.
- Calendar 화면 내부의 기존 영문 문구를 함께 번역하지 마라. 이유: 승인된 범위를 넘어 대규모 테스트 변경을 만들 수 있다.
- navigation label을 ViewModel이나 도메인 state에 넣지 마라. 이유: 앱 shell의 문자열 리소스 해석은 UI 책임이다.
- database, Repository와 reward 로직을 수정하지 마라. 이유: 이번 phase는 presentation 현지화만 다룬다.
- 기존 테스트를 깨뜨리지 마라.
