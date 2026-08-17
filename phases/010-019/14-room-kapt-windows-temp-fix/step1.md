# Step 1: kapt-build-regression-validation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/app/build.gradle.kts`
- `/phases/010-019/14-room-kapt-windows-temp-fix/index.json`
- `/phases/010-019/14-room-kapt-windows-temp-fix/step0.md`

## 작업

Windows KAPT Gradle JVM 정렬 수정의 운영 문서를 보완하고 Android 앱의 전체 로컬 빌드 회귀를 검증한다.

- `/docs/DEVELOPMENT.md`에 Room KAPT Windows 문제 해결 절을 추가한다.
  - 실패의 직접 원인이 Room compiler의 SQLite JDBC DLL 추출 위치가 쓰기 불가능한 경로로 결정된 것임을 설명한다.
  - 저장소가 Windows에서 KAPT task의 Java toolchain을 현재 Gradle JVM에 맞추므로 전역 `TEMP`, `TMP`, `java.io.tmpdir`을 수정할 필요가 없음을 기록한다.
  - Android Studio의 Gradle JDK는 프로젝트 기준인 JDK 17을 권장하되, JBR 21에서도 대상 회귀 검증을 수행한다.
  - `:app:kaptDebugKotlin --rerun-tasks --no-daemon --console=plain --stacktrace` 진단 명령과 정상 판정 기준을 기록한다.
- Temurin JDK 17에서 unit test, lint, debug APK assembly를 실행한다.
- `adb devices`에 사용 가능한 emulator 또는 device가 있으면 `connectedDebugAndroidTest`도 실행한다.
- Room schema, 도메인 로직, UI, 데이터베이스 runtime 동작이 변경되지 않았음을 최종 diff로 확인한다.

## Acceptance Criteria

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
git diff --check
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```

## 검증 절차

1. Acceptance Criteria를 순서대로 실행한다.
2. `adb devices`에 연결 대상이 있으면 `connectedDebugAndroidTest`를 추가 실행하고 결과를 summary에 기록한다.
3. `git diff`에서 phase 문서, KAPT build 설정, 개발 문서 외의 변경이 없는지 확인한다.
4. AGENTS.md의 아키텍처, 데이터 무결성, 파괴적 명령 금지 규칙이 유지되는지 확인한다.
5. 성공하면 step 1과 phase 14를 `completed`로 갱신하고 한국어 summary를 기록한다.

## 금지사항

- Android SDK, emulator, JDK를 설치하거나 업데이트하지 마라. 이유: 개발 도구 변경은 별도 승인된 준비 phase에서만 수행한다.
- 앱 UI, Room entity/DAO, Repository, occurrence 완료 또는 보상 로직을 수정하지 마라. 이유: 이번 phase는 빌드 도구의 임시 경로 회귀만 다룬다.
- 연결 기기가 없다는 이유만으로 unit test, lint, assembleDebug 검증을 생략하지 마라. 이유: host 빌드 검증은 device 없이 수행 가능하다.
- 기존 테스트를 깨뜨리지 마라.
