# Step 2: 캘린더 회귀 검증

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/app/src/main/res/values/strings.xml`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarDayIndicatorTest.kt`
- step 0에서 수정한 문서와 step 1에서 수정한 Calendar feature·test 파일
- `/phases/020-029/27-calendar-navigation-korean-ui/index.json`

## 작업

Phase 전체를 검증하고 누락된 회귀만 수정한다.

- 전체 JVM unit test, lint, debug APK와 instrumentation APK 조립을 실행한다.
- 연결된 emulator에서 전체 `connectedDebugAndroidTest`를 실행해 Calendar뿐 아니라 navigation과 Character 화면 회귀도 확인한다.
- Calendar UI에서 이전·다음 월, 연도 경계, 한국어 월·요일, `추가`→`할 일 추가` dialog, 저장·수정·삭제·완료·완료 취소, 보상 snackbar가 모두 통과하는지 확인한다.
- 기본 `values/strings.xml`이 한국어이고 Calendar의 앱 소유 사용자 문구와 TalkBack 설명에 영문 하드코딩이 남지 않았는지 정적 확인한다.
- UI→ViewModel→UseCase→Repository 경계, occurrence 단위 완료·보상 멱등성, 반복 원본 분리, 외부 Google Calendar 제외 규칙을 확인한다.
- harness 스크립트 test와 whitespace 검사를 통과시킨다.

## Acceptance Criteria

```powershell
.\gradlew.bat test lint assembleDebug assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. AC 명령을 모두 실행한다. `adb devices`에 실행 가능한 기기가 없으면 임의 설치·AVD 생성을 하지 말고 step을 `blocked`로 기록한다.
2. 실패가 있으면 phase 범위 안의 원인을 최대 3회 수정하고 AC 전체를 다시 실행한다.
3. ARCHITECTURE, ADR, UI_GUIDE와 AGENTS.md의 CRITICAL 규칙 준수를 확인한다.
4. 성공 시 `/phases/020-029/27-calendar-navigation-korean-ui/index.json`의 step 2와 `/phases/index.json`의 phase를 `completed`로 바꾸고 검증 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 실패한 테스트를 삭제·skip·완화하지 마라. 이유: 기존 기능과 이번 회귀를 실제로 보장해야 한다.
- Android 도구나 emulator가 없을 때 임의 설치하지 마라. 이유: 구현 phase의 환경 변경은 사용자 승인 범위 밖이다.
- `rm -rf`, `git reset --hard`, `git push --force`, `DROP TABLE`을 실행하지 마라. 이유: 저장소와 사용자 데이터를 파괴할 수 있다.
- phase 범위를 넘어 데이터 스키마나 게임 밸런스를 변경하지 마라. 이유: Calendar presentation 회귀 검증과 무관하다.
- 기존 테스트를 깨뜨리지 마라.
