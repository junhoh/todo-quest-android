# Step 0: android-project-scaffold

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/docs/UI_GUIDE.md`
- `/phases/000-009/1-dev-tools/index.json`

## 작업

Todo Quest의 첫 Android 구현 기반을 생성한다. 이 step은 빌드 가능한 Android 프로젝트 골격만 담당한다.

프로젝트 루트에 다음 파일과 구조를 만든다.

- `/settings.gradle.kts`
- `/build.gradle.kts`
- `/gradle.properties`
- `/gradlew`
- `/gradlew.bat`
- `/gradle/wrapper/gradle-wrapper.properties`
- `/app/build.gradle.kts`
- `/app/src/main/AndroidManifest.xml`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/test/java/com/todoquest/ExampleUnitTest.kt`

구성 규칙은 다음과 같다.

- Application id와 Kotlin package는 `com.todoquest`를 사용한다.
- JDK 17 기준으로 빌드되도록 설정한다.
- compile SDK와 target SDK는 현재 설치 문서 기준인 `35`를 사용한다.
- min SDK는 MVP에서 과도하게 낮추지 말고 일반적인 Compose 앱 기준으로 설정한다.
- Android Gradle Plugin, Kotlin, Compose BOM, Material 3 버전은 안정 버전으로 고정한다.
- Android Gradle Plugin은 JDK 17, Gradle wrapper, SDK 35와 호환되어야 한다.
- Gradle wrapper는 프로젝트 검증의 기준이 되므로 반드시 포함한다.
- `MainActivity.kt`는 다음 step에서 Compose app shell을 채울 수 있는 최소 컴파일 구조만 작성한다.
- `ExampleUnitTest.kt`는 Gradle `test` 태스크가 동작함을 확인하는 단순 JVM 테스트만 둔다.

권장 시작점은 다음 공식 문서를 기준으로 한다.

- Android Gradle Plugin: https://developer.android.com/build/releases/gradle-plugin
- Android Gradle Plugin 8.10 compatibility: https://developer.android.com/build/releases/agp-8-10-0-release-notes
- Kotlin releases: https://kotlinlang.org/docs/releases.html
- Compose BOM: https://developer.android.com/jetpack/compose/bom

## Acceptance Criteria

```powershell
java -version
javac -version
$env:ANDROID_HOME
$env:ANDROID_SDK_ROOT
.\gradlew.bat test
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. `.\gradlew.bat test`가 통과하는지 확인한다.
3. Android SDK, JDK 17, Gradle wrapper가 프로젝트 문서와 맞는지 확인한다.
4. `/phases/000-009/2-android-skeleton/index.json`의 step 0 상태와 결과 필드를 업데이트한다.

## 금지사항

- Room, WorkManager, AlarmManager 의존성을 추가하지 마라. 이유: 이 step은 Android 프로젝트 골격만 담당한다.
- 도메인 모델, Repository, UseCase를 만들지 마라. 이유: MVP 도메인 규칙은 후속 phase에서 test-first로 구현한다.
- 외부 Google Calendar 연동 설정을 추가하지 마라. 이유: MVP 범위에서 제외되어 있다.
- Android Studio, JDK, Android SDK를 임의 설치하거나 업그레이드하지 마라. 이유: 개발 도구 준비는 이미 별도 phase에서 다뤘고 사용자 승인 없이 환경을 바꾸면 안 된다.
- `connectedDebugAndroidTest`를 필수 실패 조건으로 삼지 마라. 이유: 이 step은 기기 연결 없는 JVM 빌드 골격 검증만 담당한다.
- 기존 테스트를 깨뜨리지 마라.
