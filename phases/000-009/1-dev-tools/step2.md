# Step 2: configure-android-sdk

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/000-009/1-dev-tools/index.json`
- `/phases/000-009/1-dev-tools/step0.md`
- `/phases/000-009/1-dev-tools/step1.md`

## 작업

기존 Android SDK 경로를 우선 사용하면서 Todo Quest Android 프로젝트에 필요한 SDK 구성 요소를 확인하거나 추가한다. 기존 SDK 패키지는 삭제, 다운그레이드, 경로 변경하지 않는다.

SDK 경로 우선순위는 다음과 같다.

1. `$env:ANDROID_HOME`
2. `$env:ANDROID_SDK_ROOT`
3. `$env:LOCALAPPDATA\Android\Sdk`

다음 명령으로 SDK 후보와 SDK Manager를 확인한다.

```powershell
$sdkCandidates = @(
  "$env:ANDROID_HOME",
  "$env:ANDROID_SDK_ROOT",
  "$env:LOCALAPPDATA\Android\Sdk"
) | Where-Object { $_ }

$sdkCandidates | ForEach-Object {
  Write-Output "SDK candidate: $_"
  Test-Path -LiteralPath $_
}

$sdkmanagerCandidates = $sdkCandidates | ForEach-Object {
  Join-Path $_ "cmdline-tools\latest\bin\sdkmanager.bat"
}
$sdkmanagerCandidates | ForEach-Object {
  Write-Output "sdkmanager candidate: $_"
  Test-Path -LiteralPath $_
}
```

`sdkmanager.bat`가 있으면 기존 SDK 경로에서 다음 패키지를 확인하거나 추가한다.

```powershell
& "<sdkmanager.bat 경로>" --list
& "<sdkmanager.bat 경로>" "platform-tools" "platforms;android-35" "build-tools;35.0.0" "cmdline-tools;latest"
```

설치 대상 버전은 안정 버전만 사용한다. preview, canary, alpha, beta 패키지는 설치하지 않는다.

`sdkmanager.bat`가 없거나 Android Studio 최초 실행, SDK 라이선스 동의, 관리자 권한, 네트워크 접근, 수동 SDK Manager 설정이 필요하면 즉시 중단하고 step을 `blocked`로 기록한다. `blocked_reason`에는 권장 SDK 경로와 사용자가 Android Studio에서 설치해야 할 패키지를 적는다.

환경 변수 처리 규칙은 다음과 같다.

- 기존 `ANDROID_HOME` 또는 `ANDROID_SDK_ROOT`가 유효하면 유지한다.
- 둘 다 없고 기본 SDK 경로가 유효하면 summary에 권장 설정값을 기록한다.
- 사용자/시스템 환경 변수는 자동으로 덮어쓰지 않는다.

## Acceptance Criteria

```powershell
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
2. SDK 경로, `platform-tools`, `adb`, 안정 Android platform, build-tools, cmdline-tools 상태가 summary 또는 blocked reason에 기록되었는지 확인한다.
3. 기존 SDK 패키지를 삭제하거나 다운그레이드하지 않았는지 확인한다.
4. `/phases/000-009/1-dev-tools/index.json`의 step 2 상태와 결과 필드를 업데이트한다.

## 금지사항

- 기존 Android SDK 디렉터리를 삭제하거나 재생성하지 마라. 이유: 다른 Android 프로젝트가 같은 SDK를 사용할 수 있다.
- 기존 SDK 패키지를 다운그레이드하지 마라. 이유: 기존 프로젝트의 빌드 호환성을 깨뜨릴 수 있다.
- preview, canary, alpha, beta SDK 패키지를 설치하지 마라. 이유: MVP 구현 환경은 안정 버전 기준이어야 한다.
- 사용자 또는 시스템 환경 변수를 자동으로 덮어쓰지 마라. 이유: SDK 경로 충돌은 다른 프로젝트 빌드에 영향을 줄 수 있다.
- 기존 harness 테스트를 깨뜨리지 마라.
