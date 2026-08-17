# Step 2: document-and-validate-ui-polish

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/app/src/main/java/com/todoquest/feature/battle/`
- `/app/src/main/java/com/todoquest/feature/calendar/`
- `/app/src/test/java/com/todoquest/feature/battle/`
- `/app/src/test/java/com/todoquest/feature/calendar/`
- `/app/src/androidTest/java/com/todoquest/feature/battle/BattleMapTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/phases/030-039/36-calendar-battle-feedback-polish/step0.md`
- `/phases/030-039/36-calendar-battle-feedback-polish/step1.md`
- `/phases/030-039/36-calendar-battle-feedback-polish/index.json`

## 작업

step 0~1의 실제 구현과 테스트 결과를 canonical UI·개발 문서에 동기화한다. `docs/UI_GUIDE.md`에는 EXP label/value와 전용 progress bar 경계, 고대비 HP panel과 player/monster accent, visible attack/hit badge, reward snackbar 최대 600ms와 non-consuming 외부 tap dismiss 계약을 기록한다. `docs/DEVELOPMENT.md`에는 관련 unit·Compose test 위치와 320dp·font scale 2.0, standard·compact, portrait·landscape, 양방향 attack/hit, snackbar 시간·tap 검증 결과를 기록한다. 이번 phase가 Room schema, Repository, 보상·전투 공식이나 transition replay 정책을 변경한 것처럼 PRD·ARCHITECTURE·ADR을 수정하지 않는다.

전체 Android와 harness 검증을 실행한다. 연결된 기기에서 portrait와 landscape의 idle Battle Map을 확인하고, deterministic Compose phase test로 player/monster attack·hit badge의 bounds와 semantics를 판정한다. reward snackbar는 screenshot만으로 시간을 판정하지 않고 Compose clock test와 실제 Calendar tap 동작을 근거로 삼는다. 가능한 경우 최종 화면 증거를 `app/build/verification/calendar-battle-feedback-polish/` 아래 build artifact로 남기되 tracked source asset으로 추가하지 않는다.

Android SDK/JDK/adb 또는 connected device가 없으면 임의 설치하거나 환경 변수를 바꾸지 말고 step과 phase를 `blocked`로 기록한다. 실패한 test는 원인을 수정하고 AC를 다시 실행하며, 수행하지 않은 검증을 성공으로 기록하지 않는다.

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

1. UI_GUIDE와 DEVELOPMENT를 실제 구현·테스트 결과에 맞춰 갱신한다.
2. 모든 AC를 실행하고 unit·lint·assemble·connected·harness 결과를 step summary에 기록한다.
3. AGENTS.md CRITICAL 규칙, occurrence 보상 멱등성, replay 없는 전투 transition, 한국어 resource와 권한 비의존 동작을 최종 확인한다.
4. task index의 step 2를 `completed`로 바꾸고 전체 검증 결과를 한국어 `summary` 한 줄로 기록한다.
5. 모든 step이 completed면 task index와 `/phases/index.json`의 phase 36을 `completed`로 바꾸고 완료 시각과 한국어 summary를 기록한다.

## 금지사항

- UI polish를 Room schema나 보상·전투 정책 변경으로 문서화하지 마라. 이유: 이번 phase는 presentation과 transient feedback만 변경한다.
- screenshot만으로 600ms timeout이나 transient phase 순서를 검증했다고 기록하지 마라. 이유: 시간과 순서는 deterministic test가 근거여야 한다.
- Android 도구나 SDK를 임의 설치하지 마라. 이유: 구현 phase의 도구 설치는 AGENTS.md에서 금지한다.
- 실패하거나 수행하지 않은 AC를 통과로 기록하지 마라. 이유: phase 완료 상태는 실제 검증 근거와 일치해야 한다.
- 기존 테스트를 깨뜨리지 마라.
