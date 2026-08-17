# Step 0: project-guardrails

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`

## 작업

`AGENTS.md`와 `docs/DEVELOPMENT.md`를 Todo Quest Android 프로젝트 기준으로 작성한다.

`AGENTS.md`에는 다음 결정을 반드시 포함한다.

- 프로젝트명은 `Todo Quest`다.
- 기술 스택은 Kotlin, Jetpack Compose, Material 3, Room, ViewModel, Coroutines, Flow, WorkManager, AlarmManager다.
- UI는 Room DAO, AlarmManager, WorkManager를 직접 호출하지 않는다.
- 일정 완료 보상은 occurrence 단위로 멱등 처리한다.
- 반복 일정은 원본 일정과 날짜별 발생분을 분리한다.
- MVP는 외부 Google Calendar 연동을 포함하지 않는다.
- 새 기능은 테스트를 먼저 작성한다.
- 파괴적 명령을 실행하지 않는다.
- Android 프로젝트 생성 후 검증 명령은 Gradle wrapper 기준이다.

`docs/DEVELOPMENT.md`에는 다음 내용을 포함한다.

- Windows 기준 Android Studio, JDK 17, Android SDK, Platform Tools, emulator 또는 실제 기기 준비 절차
- `java -version`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `adb` 확인 명령
- Android 프로젝트 생성 후 `.\gradlew.bat test`, `.\gradlew.bat lint`, `.\gradlew.bat assembleDebug` 검증 명령
- 기기 테스트는 환경이 있을 때만 `.\gradlew.bat connectedDebugAndroidTest`로 실행한다는 규칙
- 도구가 없으면 자동 설치하지 않고 blocked로 기록한다는 규칙
- 현재 harness Python 테스트 환경과 실행 명령

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. `AGENTS.md`와 `docs/DEVELOPMENT.md`에 Android가 아닌 웹 템플릿 내용이 남아 있지 않은지 확인한다.
3. `/phases/000-009/0-design-docs/index.json`의 step 0 상태와 결과 필드를 업데이트한다.

## 금지사항

- Android SDK나 JDK를 자동 설치하지 마라. 이유: 사용자 시스템 변경과 네트워크 승인이 필요하다.
- 앱 소스 코드를 생성하지 마라. 이유: 이 phase는 설계 문서 작성만 담당한다.
- 기존 harness 테스트를 깨뜨리지 마라.
