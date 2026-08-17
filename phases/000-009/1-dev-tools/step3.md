# Step 3: verify-development-toolchain

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/000-009/1-dev-tools/index.json`
- `/phases/000-009/1-dev-tools/step0.md`
- `/phases/000-009/1-dev-tools/step1.md`
- `/phases/000-009/1-dev-tools/step2.md`

## 작업

Todo Quest Android 프로젝트 구현을 시작할 수 있는 개발 도구 상태를 최종 검증한다. 검증 결과는 다음 구현 phase가 의존할 수 있도록 `/phases/000-009/1-dev-tools/index.json`의 step 3 summary에 한 줄로 기록한다.

다음 명령을 실행한다.

```powershell
java -version
javac -version
where.exe java
where.exe javac
$env:JAVA_HOME
$env:ANDROID_HOME
$env:ANDROID_SDK_ROOT
Get-Command adb -ErrorAction SilentlyContinue
where.exe adb
adb version
```

검증 규칙은 다음과 같다.

- JDK 17이 사용 가능해야 한다.
- Android SDK 경로는 하나 이상 유효해야 한다.
- `adb`가 사용 가능해야 한다.
- `where.exe java`, `where.exe javac`, `where.exe adb` 결과가 의도한 경로와 맞지 않으면 버전 충돌 가능성으로 보고 `blocked`로 기록한다.
- Android 앱 프로젝트가 아직 생성되지 않았으면 `gradlew.bat` 부재는 실패로 보지 않는다. summary에 “Android 프로젝트 생성 후 Gradle wrapper 검증 필요”라고 기록한다.

Android 프로젝트가 이미 생성되어 `gradlew.bat`가 있으면 가능한 범위에서 다음 명령도 실행한다.

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

기기 또는 emulator가 준비된 경우에만 다음 명령을 실행한다.

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## Acceptance Criteria

```powershell
java -version
javac -version
where.exe java
where.exe javac
$env:JAVA_HOME
$env:ANDROID_HOME
$env:ANDROID_SDK_ROOT
Get-Command adb -ErrorAction SilentlyContinue
adb version
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. JDK 17, Android SDK, adb, Gradle wrapper 적용 가능 여부가 summary 또는 blocked reason에 기록되었는지 확인한다.
3. `AGENTS.md`와 `docs/DEVELOPMENT.md`의 CRITICAL 규칙을 확인한다.
4. `/phases/000-009/1-dev-tools/index.json`의 step 3 상태와 결과 필드를 업데이트한다.

## 금지사항

- `gradlew.bat`가 없다는 이유만으로 Android 프로젝트 파일을 생성하지 마라. 이유: 이 phase는 개발 도구 준비만 담당한다.
- emulator나 실제 기기가 없는데 `connectedDebugAndroidTest`를 필수 실패로 처리하지 마라. 이유: 기기 테스트는 환경이 준비된 경우에만 실행한다.
- 버전 충돌 가능성을 무시하고 PATH나 환경 변수를 자동 수정하지 마라. 이유: 다른 개발 환경을 깨뜨릴 수 있다.
- 외부 Google Calendar 연동 관련 설정을 추가하지 마라. 이유: MVP 범위에서 제외되어 있다.
- 기존 harness 테스트를 깨뜨리지 마라.
