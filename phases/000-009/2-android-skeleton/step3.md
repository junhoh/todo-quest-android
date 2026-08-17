# Step 3: skeleton-verification

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/docs/UI_GUIDE.md`
- `/phases/000-009/2-android-skeleton/index.json`
- `/phases/000-009/2-android-skeleton/step0.md`
- `/phases/000-009/2-android-skeleton/step1.md`
- `/phases/000-009/2-android-skeleton/step2.md`
- `/settings.gradle.kts`
- `/build.gradle.kts`
- `/app/build.gradle.kts`
- `/app/src/main/AndroidManifest.xml`

## 작업

Android skeleton phase의 산출물이 다음 구현 phase의 기반으로 충분한지 최종 검증한다. 이 step은 새 기능을 추가하지 않고 검증, 필요한 최소 수정, 상태 기록만 담당한다.

필수 검증 명령은 다음과 같다.

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
git diff --check
```

기기 또는 emulator가 준비된 경우에만 다음 선택 검증도 실행한다.

```powershell
adb devices
.\gradlew.bat connectedDebugAndroidTest
```

Emulator 준비 가이드는 다음과 같다.

1. Android Studio의 Device Manager에서 Android Virtual Device를 생성한다.
2. 가능하면 API 35 system image를 사용한다.
3. emulator를 실행한다.
4. `adb devices`에서 `emulator-... device` 상태가 보이면 `connectedDebugAndroidTest`를 실행한다.

실제 기기 준비 가이드는 다음과 같다.

1. Android 휴대폰에서 개발자 옵션을 활성화한다.
2. USB 디버깅을 켠다.
3. USB로 연결한 뒤 휴대폰의 디버깅 허용 prompt를 승인한다.
4. Windows에서 인식되지 않으면 제조사 USB driver 또는 Google USB driver가 필요한지 확인한다.
5. `adb devices`에서 `<serial> device` 상태가 보이면 `connectedDebugAndroidTest`를 실행한다.

검증 판단 규칙은 다음과 같다.

- `adb devices`에 연결된 대상이 없으면 선택 검증을 건너뛰고 summary에 “기기 연결 검증은 대상 없음으로 생략”이라고 기록한다.
- `adb devices`에 `unauthorized`가 보이면 사용자 기기 승인 필요로 보고 선택 검증을 건너뛰며 summary에 기록한다.
- 필수 검증이 실패하면 원인을 수정하고 다시 실행한다.
- Gradle dependency 다운로드, Android SDK 부재, JDK 충돌처럼 사용자 환경 개입이 필요한 경우 임의 설치하지 말고 `blocked`로 기록한다.

공식 참고 문서는 다음과 같다.

- Emulator 실행: https://developer.android.com/studio/run/emulator
- 실제 기기 실행: https://developer.android.com/studio/run/device
- ADB 확인: https://developer.android.com/tools/adb

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. `adb devices`로 connected test 실행 가능 여부를 확인한다.
3. emulator 또는 실제 기기가 `device` 상태면 `.\gradlew.bat connectedDebugAndroidTest`를 실행한다.
4. 기기가 없거나 `unauthorized` 상태면 connected test를 생략하고 이유를 step summary에 기록한다.
5. `ARCHITECTURE.md`, `ADR.md`, `AGENTS.md`의 CRITICAL 규칙을 확인한다.
6. `/phases/000-009/2-android-skeleton/index.json`의 step 3 상태와 결과 필드를 업데이트한다.

## 금지사항

- 기기 또는 emulator가 없다는 이유로 필수 검증을 실패 처리하지 마라. 이유: connected test는 이 phase에서 선택 검증이다.
- `unauthorized` 기기를 우회하려고 설정을 임의 변경하지 마라. 이유: 사용자가 기기에서 디버깅 허용을 승인해야 한다.
- Android Studio, SDK, JDK, driver를 임의 설치하지 마라. 이유: 사용자 환경 변경은 별도 승인된 phase에서만 수행한다.
- 테스트를 통과시키기 위해 lint를 비활성화하지 마라. 이유: skeleton 품질 검증 자체가 이 step의 목적이다.
- 도메인, Room, 알림, 보상 기능을 추가하지 마라. 이유: 이 phase 범위를 넘어선다.
- 기존 테스트를 깨뜨리지 마라.
