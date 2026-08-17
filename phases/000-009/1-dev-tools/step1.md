# Step 1: install-compatible-jdk-and-android-studio

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/000-009/1-dev-tools/index.json`
- `/phases/000-009/1-dev-tools/step0.md`

## 작업

Step 0 summary와 현재 시스템 상태를 다시 확인한 뒤, 누락되었거나 명확히 비호환인 도구만 설치한다. 기존 환경과 버전 충돌을 피하기 위해 제거, 덮어쓰기, 다운그레이드는 하지 않는다.

설치 전 다음 상태를 재확인한다.

```powershell
java -version
javac -version
where.exe java
where.exe javac
winget --version
winget list --id EclipseAdoptium.Temurin.17.JDK
winget list --id Google.AndroidStudio
$env:JAVA_HOME
```

JDK 처리 규칙은 다음과 같다.

- JDK 17이 이미 있으면 아무것도 설치하지 않는다.
- JDK가 없거나 JDK 17이 없으면 Temurin JDK 17을 side-by-side 설치한다.
- 기존 Java 버전은 제거하거나 PATH에서 삭제하지 않는다.
- 설치 후 전역 `JAVA_HOME`이나 PATH를 자동 변경하지 않는다. 현재 세션에서 검증이 필요하면 해당 세션에만 임시 환경 변수를 설정한다.

JDK 17 설치가 필요한 경우 다음 명령을 사용한다.

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements
```

Android Studio 처리 규칙은 다음과 같다.

- Android Studio가 이미 있으면 아무것도 설치하지 않는다.
- Android Studio가 없을 때만 공식 winget 패키지를 설치한다.
- 기존 Android Studio 설치를 제거, 복구, 다운그레이드하지 않는다.

Android Studio 설치가 필요한 경우 다음 명령을 사용한다.

```powershell
winget install --id Google.AndroidStudio -e --accept-package-agreements --accept-source-agreements
```

winget 실패, 관리자 권한 또는 UAC 필요, 네트워크 실패, 패키지 미발견, 기존 설치와 충돌 가능성이 확인되면 즉시 중단하고 step을 `blocked`로 기록한다. `blocked_reason`에는 실패한 명령, 관찰된 에러, 사용자가 수동으로 해야 할 조치를 포함한다.

## Acceptance Criteria

```powershell
java -version
javac -version
where.exe java
where.exe javac
winget list --id EclipseAdoptium.Temurin.17.JDK
winget list --id Google.AndroidStudio
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. JDK 17과 Android Studio의 최종 상태가 step summary 또는 blocked reason에 기록되었는지 확인한다.
3. 기존 Java/Android Studio 설치를 제거하거나 다운그레이드하지 않았는지 확인한다.
4. `/phases/000-009/1-dev-tools/index.json`의 step 1 상태와 결과 필드를 업데이트한다.

## 금지사항

- 기존 Java 버전을 제거하거나 덮어쓰지 마라. 이유: 다른 프로젝트의 Java 런타임 요구사항과 충돌할 수 있다.
- 전역 `JAVA_HOME` 또는 PATH를 자동 변경하지 마라. 이유: 사용자의 기존 개발 환경을 깨뜨릴 수 있다.
- 비공식 다운로드 URL, 임의 ZIP, 미러 사이트를 사용하지 마라. 이유: 설치 출처와 업데이트 경로가 불명확하다.
- Android Studio가 이미 설치되어 있으면 재설치하지 마라. 이유: 설정과 SDK 경로가 바뀌어 기존 환경과 충돌할 수 있다.
- 기존 harness 테스트를 깨뜨리지 마라.
