# 프로젝트: Todo Quest

## 기술 스택

- Android 네이티브 앱
- Kotlin
- Jetpack Compose와 Material 3
- Room 기반 로컬 데이터베이스
- ViewModel, Kotlin Coroutines, Flow
- WorkManager와 AlarmManager 기반 백그라운드 작업 및 알림

## 아키텍처 규칙

- CRITICAL: UI 레이어는 Room DAO, AlarmManager, WorkManager를 직접 호출하지 않는다. 모든 데이터 변경과 알림 예약은 Repository 또는 명확한 UseCase를 통해 수행한다.
- CRITICAL: 일정 완료 보상은 occurrence 단위로 멱등하게 처리한다. 같은 일정 발생분에 XP나 골드가 두 번 지급되면 안 된다.
- CRITICAL: 반복 일정은 원본 일정과 날짜별 발생분을 분리해 다룬다. 특정 날짜를 완료해도 반복 원본 전체를 완료 처리하지 않는다.
- CRITICAL: MVP는 외부 Google Calendar 읽기/쓰기 연동을 포함하지 않는다. 앱 내부 캘린더와 로컬 저장만 구현한다.
- CRITICAL: 새 화면과 새 사용자 노출 문구는 `app/src/main/res/values/strings.xml`의 한국어 문자열 리소스를 기본으로 사용하고, Compose 및 ViewModel에 표시용 영문 문장을 하드코딩하지 않는다.
- Compose 화면은 상태를 직접 소유하기보다 ViewModel이 노출하는 UI state를 렌더링한다.
- 도메인 모델은 `domain/`에, Room entity와 DAO는 `data/local/`에, Repository 구현은 `data/repository/`에 둔다.
- Android 권한이 거부되어도 핵심 일정 생성, 완료, 보상 기능은 정상 동작해야 한다.

## 개발 프로세스

- CRITICAL: 새 기능 구현 시 반드시 테스트를 먼저 작성하고, 테스트가 통과하는 구현을 작성할 것. 특히 반복 일정 계산, 보상 지급, 레벨업, 구매 검증은 순수 Kotlin unit test를 우선한다.
- CRITICAL: `rm -rf`, `git push --force`, `git reset --hard`, `DROP TABLE` 같은 파괴적 명령을 실행하지 말 것.
- 커밋 메시지는 conventional commits 형식을 따른다. 예: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`.
- 구현 phase에서 Android 도구가 설치되어 있지 않으면 임의 설치를 시도하지 말고 blocked 상태로 기록한다. 단, 별도 개발 도구 준비 phase에서 사용자가 명시 승인한 경우에만 설치와 사용자 환경 변수 변경을 수행한다.

## 명령어

현재 로컬 개발 도구 기준은 `docs/DEVELOPMENT.md`를 따른다. 새 터미널에서 다음 명령으로 기본 도구 노출 여부를 확인한다.

```powershell
java -version
javac -version
$env:ANDROID_HOME
$env:ANDROID_SDK_ROOT
adb version
```

Android 프로젝트가 생성된 뒤에는 Gradle wrapper를 기준으로 검증한다.

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

현재 저장소의 harness 스크립트 검증은 다음 명령을 사용한다.

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```
