# Step 1: define-first-launch-permission-policy

## 읽어야 할 파일

- /AGENTS.md
- /docs/ARCHITECTURE.md
- /docs/ADR.md
- /app/src/main/java/com/todoquest/domain/repository/ReminderScheduler.kt
- /app/src/main/java/com/todoquest/domain/usecase/ReminderUseCases.kt
- /app/src/test/java/com/todoquest/domain/ReminderUseCaseTest.kt
- /phases/050-059/50-notification-and-gameplay-balance/step0.md
- /phases/050-059/50-notification-and-gameplay-balance/index.json

## 작업

순수 Kotlin 테스트를 production보다 먼저 작성한다. domain/repository에 다음 의미의 Android 비의존 interface를 추가한다.

~~~kotlin
interface FirstLaunchNotificationPromptStore {
    fun consumeFirstLaunchCheck(): Boolean
}

class PrepareFirstLaunchNotificationPromptUseCase(
    private val store: FirstLaunchNotificationPromptStore,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(): Boolean
}
~~~

consumeFirstLaunchCheck는 앱 데이터 수명 동안 최초 호출 한 번만 true를 반환하고 그 호출에서 소비된다. UseCase는 최초 호출이 아니면 capability를 조회하지 않고 false를 반환한다. 최초 호출이면 POST_NOTIFICATIONS capability가 REQUIRED일 때만 true를 반환한다. capability 조회 오류는 launch·일정 기능을 실패시키지 않고 false로 격리한다. exact alarm은 이 UseCase에서 조회하지 않는다.

테스트는 최초 missing capability, 이미 available, 두 번째 호출, capability 조회 예외, exact capability 미조회 순서를 포함한다. domain에는 android.*, Compose, Room, SharedPreferences import가 없어야 한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.FirstLaunchNotificationPermissionUseCaseTest" --tests "com.todoquest.domain.ReminderUseCaseTest" --console=plain
git diff --check
~~~

## 검증 절차

1. fake store·scheduler test를 먼저 추가해 새 type 부재 실패를 확인한다.
2. 최소 interface와 UseCase를 구현하고 AC를 실행한다.
3. domain에서 Android import가 없는지 검색한다.
4. step 1을 completed와 한국어 summary로 갱신한다.

## 금지사항

- SharedPreferences나 Context를 domain에 노출하지 마라. 이유: 최초 실행 정책을 순수 Kotlin으로 검증해야 한다.
- exact alarm capability를 조회하지 마라. 이유: 첫 실행 범위는 notification permission뿐이다.
- 조회 실패를 앱 launch 실패로 전파하지 마라. 이유: 권한 onboarding은 핵심 기능과 독립적이다.
- 기존 테스트를 깨뜨리지 마라.
