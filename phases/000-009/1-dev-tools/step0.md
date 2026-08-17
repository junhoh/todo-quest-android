# Step 0: audit-local-tooling

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/000-009/1-dev-tools/index.json`

## 작업

Todo Quest Android 개발에 필요한 로컬 도구 상태를 점검하고, 기존 환경과 충돌하지 않는 설치 전략을 결정한다.

다음 명령을 실행해 현재 상태를 확인한다.

```powershell
java -version
javac -version
where.exe java
where.exe javac
winget --version
winget list --id EclipseAdoptium.Temurin.17.JDK
winget list --id Google.AndroidStudio
$env:JAVA_HOME
$env:ANDROID_HOME
$env:ANDROID_SDK_ROOT
Get-Command adb -ErrorAction SilentlyContinue
where.exe adb
```

다음 SDK 후보 경로를 확인한다.

```powershell
$sdkCandidates = @(
  "$env:ANDROID_HOME",
  "$env:ANDROID_SDK_ROOT",
  "$env:LOCALAPPDATA\Android\Sdk"
) | Where-Object { $_ }

$sdkCandidates | ForEach-Object {
  if (Test-Path -LiteralPath $_) {
    Get-ChildItem -Force -LiteralPath $_ | Select-Object Name,Mode,Length
  } else {
    Write-Output "Missing SDK candidate: $_"
  }
}
```

기존 JDK, Android Studio, Android SDK가 있으면 버전과 경로를 기록하고, 호환 가능한 기존 설치를 우선 사용한다. 설치, 삭제, 다운그레이드, 환경 변수 변경은 이 step에서 수행하지 않는다.

JDK 17이 이미 있으면 다음 step에서 새 JDK를 설치하지 않도록 summary에 기록한다. 다른 Java 버전만 있으면 제거하지 말고 JDK 17 side-by-side 설치가 필요하다고 기록한다. Android Studio가 이미 있으면 다음 step에서 재설치하지 않도록 기록한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. 점검 결과가 `/phases/000-009/1-dev-tools/index.json`의 step 0 `summary`에 한 줄로 기록되었는지 확인한다.
3. summary에는 JDK 17, Android Studio, Android SDK, adb, winget의 사용 가능 여부와 다음 step에서 설치해야 할 항목을 포함한다.
4. `/phases/000-009/1-dev-tools/index.json`의 step 0 상태와 결과 필드를 업데이트한다.

## 금지사항

- 이 step에서 JDK, Android Studio, Android SDK를 설치하지 마라. 이유: 기존 환경 점검과 설치 판단을 분리해 버전 충돌을 방지해야 한다.
- 기존 Java, Android Studio, Android SDK를 삭제하거나 다운그레이드하지 마라. 이유: 다른 프로젝트가 기존 버전에 의존할 수 있다.
- 사용자 또는 시스템 환경 변수를 자동 변경하지 마라. 이유: PATH/JAVA_HOME 충돌은 사용자의 다른 개발 환경에 영향을 줄 수 있다.
- 기존 harness 테스트를 깨뜨리지 마라.
