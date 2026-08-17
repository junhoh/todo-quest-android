# Step 2: implement-android-notification-entrypoints

## 읽어야 할 파일

- /AGENTS.md
- /docs/ARCHITECTURE.md
- /docs/ADR.md
- /app/src/main/java/com/todoquest/notification/AndroidReminderCapabilityAdapter.kt
- /app/src/test/java/com/todoquest/notification/AndroidReminderSchedulerTest.kt
- /app/src/main/java/com/todoquest/domain/repository/FirstLaunchNotificationPromptStore.kt
- /phases/050-059/50-notification-and-gameplay-balance/step1.md
- /phases/050-059/50-notification-and-gameplay-balance/index.json

## 작업

Robolectric 테스트를 먼저 작성하고 notification Android 모듈만 구현한다. SharedPreferencesFirstLaunchNotificationPromptStore를 추가해 고정된 private preference/key를 사용하고 process 내 동시 호출을 직렬화하며 commit 성공 뒤에만 최초 호출을 소비한다. 앱 데이터 삭제 전까지 재생성된 store에서도 false를 반환해야 한다.

AndroidReminderCapabilityAdapter에는 첫 실행 CTA가 수행할 typed Android action을 추가한다.

~~~kotlin
sealed interface NotificationPermissionLaunchAction {
    data class RuntimePermission(val permission: String) : NotificationPermissionLaunchAction
    data class AppSettings(val intent: Intent) : NotificationPermissionLaunchAction
    data object None : NotificationPermissionLaunchAction
}

fun firstLaunchNotificationPermissionAction(): NotificationPermissionLaunchAction
fun notificationSettingsIntent(): Intent
~~~

API 33 이상에서 runtime permission이 실제로 missing이면 RuntimePermission을 반환한다. runtime permission은 granted지만 NotificationManager가 disabled이거나 API 23~32에서 알림이 disabled이면 package-scoped ACTION_APP_NOTIFICATION_SETTINGS를 반환하고, 지원 범위가 부족한 경우 ACTION_APPLICATION_DETAILS_SETTINGS로 fallback한다. capability가 available이면 None이다. intent는 현재 package만 가리켜야 한다. UI를 직접 launch하지 않는다. 기존 exactAlarmAccessIntent와 scheduler 동작은 유지한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.notification.NotificationPermissionEntryPointsTest" --tests "com.todoquest.notification.AndroidReminderSchedulerTest" --console=plain
git diff --check
~~~

## 검증 절차

1. API 23/32/33/35와 preference 재생성 test를 먼저 실패시킨다.
2. store와 adapter를 구현하고 AC를 실행한다.
3. adapter가 Activity를 시작하거나 scheduler state를 변경하지 않는지 확인한다.
4. step 2를 completed와 한국어 summary로 갱신한다.

## 금지사항

- notification settings intent에서 다른 package나 broad settings 화면을 기본값으로 열지 마라. 이유: 사용자가 Todo Quest 권한을 바로 승인할 수 있어야 한다.
- runtime permission이 이미 granted인데 다시 요청하지 마라. 이유: 앱 전체 notification switch 비활성은 settings에서 복구해야 한다.
- exact alarm intent 계약을 변경하지 마라. 이유: 기존 Task Reminder 흐름을 보존해야 한다.
- 기존 테스트를 깨뜨리지 마라.
