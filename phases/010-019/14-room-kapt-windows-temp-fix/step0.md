# Step 0: kapt-gradle-jvm-alignment

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/build.gradle.kts`
- `/app/build.gradle.kts`

## 작업

Windows의 Android Studio JBR 21에서 Gradle daemon을 실행하고 Kotlin `jvmToolchain(17)`이 KAPT worker를 분리할 때, Room compiler가 사용하는 `sqlite-jdbc` 네이티브 DLL이 쓰기 불가능한 `C:\WINDOWS`에 추출되는 회귀를 수정한다.

- 구현 전에 Android Studio JBR 21을 `JAVA_HOME`으로 둔 `:app:kaptDebugKotlin --rerun-tasks --no-daemon` 실행으로 `C:\WINDOWS\sqlite-*.dll.lck`의 `AccessDeniedException`을 재현하고 테스트 우선 기준으로 기록한다.
- `/app/build.gradle.kts`에서 `org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask` 유형의 모든 task를 `configureEach`로 설정한다.
- KAPT task의 Java toolchain을 현재 Gradle JVM의 `java.home`과 `JavaVersion.current()`에 맞춘다. Android Studio JBR 21에서는 별도 JDK 17 process worker를 만들지 않고 Gradle worker의 `NONE` 격리 모드로 annotation processing을 실행한다.
- 별도 KAPT process에 `org.sqlite.tmpdir`을 전달하는 시도는 사용하지 않는다. 이유: SQLite DLL 오류 다음에 Gradle 8.13의 Kotlin serialization 1.6.2와 Room compiler의 1.8.1이 충돌해 `NoSuchMethodError`가 발생한다.
- Kotlin 컴파일 결과물의 Java 17 toolchain/target과 Room, SQLite JDBC, Kotlin, Gradle 의존성 버전은 변경하지 않는다.
- `java.io.tmpdir`, 사용자 `TEMP`/`TMP`, Android Studio 설정은 변경하지 않는다.

## Acceptance Criteria

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:kaptDebugKotlin --rerun-tasks --no-daemon --console=plain
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
.\gradlew.bat :app:kaptDebugKotlin --rerun-tasks --no-daemon --console=plain
git diff --check
```

## 검증 절차

1. 수정 전 재현 로그와 수정 후 JBR 21 실행 결과를 비교해 `C:\WINDOWS` 접근과 `No native library found` 오류가 사라졌는지 확인한다.
2. `--info` 로그에서 `Using workers NONE isolation mode to run kapt`를 확인하고 별도 JDK 17 KAPT process worker가 생성되지 않는지 확인한다.
3. Temurin JDK 17에서도 강제 KAPT 재실행이 통과하는지 확인한다.
4. Room schema JSON과 앱 소스에 의도하지 않은 변경이 없는지 확인한다.
5. 성공하면 phase index의 step 0을 `completed`로 업데이트하고 한국어 summary를 기록한다.

## 금지사항

- 사용자 또는 시스템 `TEMP`, `TMP`, `JAVA_HOME`을 영구 변경하지 마라. 이유: 다른 프로젝트와 Android Studio 실행 환경에 영향을 줄 수 있다.
- `C:\WINDOWS`에 쓰기 권한을 부여하거나 관리자 권한 빌드를 해결책으로 사용하지 마라. 이유: 최소 권한 원칙을 위반하고 실제 경로 오류를 숨긴다.
- Room, SQLite JDBC, Kotlin, Gradle 버전을 변경하지 마라. 이유: 이번 회귀는 의존성 업그레이드가 아니라 KAPT 실행 JVM 정렬로 해결한다.
- Gradle 또는 Android Studio 캐시를 삭제하지 마라. 이유: 캐시 삭제 없이 재현 가능한 저장소 수정이어야 한다.
- 기존 테스트를 깨뜨리지 마라.
