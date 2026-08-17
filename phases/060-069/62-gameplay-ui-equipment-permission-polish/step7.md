# Step 7: present-notification-permission-state

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/domain/repository/ReminderScheduler.kt`
- `/app/src/main/java/com/todoquest/domain/model/Reminder.kt`
- `/app/src/main/java/com/todoquest/feature/settings/SettingsViewModel.kt`
- `/app/src/test/java/com/todoquest/feature/settings/SettingsViewModelTest.kt`
- `/app/src/test/java/com/todoquest/notification/AndroidReminderSchedulerTest.kt`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step6.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/index.json`

## 작업

ViewModel unit test를 먼저 작성하고 설정 화면이 일반 알림 권한 상태를 lifecycle-safe하게 표시할 typed UI state를 추가한다.

`SettingsViewModel`에는 `ReminderScheduler` 또는 이를 감싼 명확한 use case를 주입하고 오직 `ReminderCapability.POST_NOTIFICATIONS`의 `capabilityStatus`만 조회한다. UI state는 `Loading`, `Available`, `Required`, `ChannelDisabled`, `CheckFailed`를 구분한다. 초기 진입, 명시적 retry, 시스템 권한/설정 화면에서 돌아온 뒤 호출할 `refreshNotificationPermission()`을 제공한다. 이전 refresh가 진행 중일 때는 중복 요청을 직렬화하거나 최신 요청만 반영하고 cancellation은 실패 상태로 바꾸지 않는다.

이 step에서는 ActivityResult launcher나 Android `Intent`를 ViewModel에 넣지 않는다. 알림 권한이 `Required`, `ChannelDisabled`, `CheckFailed`여도 효과음 설정, 일정 생성/완료, 보상, 전투, 구매 기능을 비활성화하지 않는다. exact alarm 상태는 이번 설정 항목에 포함하지 않는다.

테스트는 세 capability 결과 매핑, 예외→`CheckFailed`, retry 성공, 연속 refresh, 기존 효과음 저장 흐름 독립성을 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.settings.SettingsViewModelTest" --tests "com.todoquest.notification.AndroidReminderSchedulerTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. permission state mapping과 retry에 대한 실패 테스트를 먼저 작성한다.
2. ViewModel이 Android platform type 없이 domain capability interface만 참조하는지 확인한다.
3. 권한 조회 실패 중에도 battle SFX switch 저장이 동작하는지 확인한다.
4. 성공 시 task index의 step 7을 `completed`로 바꾸고 알림 권한 UI state 계약을 한국어 `summary` 두 줄로 기록한다.

## 금지사항

- ViewModel에서 `AlarmManager`, `NotificationManager`, `Intent` 또는 Room DAO를 직접 호출하지 마라. 이유: platform/data 접근은 adapter/repository 경계를 거쳐야 한다.
- exact alarm 권한을 이 설정 항목에 섞지 마라. 이유: 확정 범위는 일반 알림 권한만이다.
- 권한 거부를 핵심 기능의 오류 상태로 취급하지 마라. 이유: Android 권한과 일정·보상 기능은 독립적으로 동작해야 한다.
- 표시용 영문 또는 한국어 문장을 ViewModel에 하드코딩하지 마라. 이유: 사용자 노출 문구는 문자열 리소스를 사용한다.
- 기존 테스트를 삭제하거나 완화하지 마라.
