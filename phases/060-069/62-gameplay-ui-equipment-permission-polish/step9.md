# Step 9: wire-notification-permission-settings

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/java/com/todoquest/feature/settings/SettingsScreen.kt`
- `/app/src/main/java/com/todoquest/feature/settings/SettingsViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/java/com/todoquest/notification/AndroidReminderCapabilityAdapter.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/notification/NotificationPermissionEntryPointsTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/settings/SettingsScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step7.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step8.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/index.json`

## 작업

platform/Compose 테스트를 먼저 작성하고 설정 화면에 일반 알림 권한 상태와 관리 진입점을 연결한다.

`TodoQuestApp`/factory는 container의 `ReminderScheduler`를 `SettingsViewModel`에 주입하고 기존 `AndroidReminderCapabilityAdapter`를 `SettingsScreen`에 전달한다. 화면은 Calendar에서 검증된 `ActivityResultContracts.RequestPermission`과 `StartActivityForResult` 패턴을 재사용한다.

설정 화면에 `알림 권한` row를 추가하고 다음 상태를 한국어 문자열 리소스로 표시한다.

- `Loading`: 확인 중, action disabled.
- `Available`: 허용됨, action은 앱 알림 설정 화면을 연다.
- `Required`: 권한 필요, Android 13+ 미허용이면 runtime `POST_NOTIFICATIONS`를 요청하고 그 외 앱 단위 차단이면 앱 알림 설정을 연다.
- `ChannelDisabled`: 알림 채널 꺼짐, reminder channel 설정을 연다.
- `CheckFailed`: 상태 확인 실패, action은 ViewModel retry를 호출한다.

runtime permission 결과 및 settings launcher 복귀 시 refresh하고, `Lifecycle.Event.ON_RESUME`에서도 refresh해 외부 설정 변경을 반영한다. 동일 resume에서 중복 refresh가 발생해도 step 7의 직렬화 규칙을 지킨다. 화면 진입만으로 permission dialog를 자동 표시하지 않고 사용자가 row/action을 눌렀을 때만 연다. first-launch prompt store와 Calendar reminder recovery flow는 변경하지 않는다.

row 전체는 최소 48dp touch target과 `settings-notification-permission-row`, action은 `settings-notification-permission-action` test tag를 가진다. TalkBack 설명은 항목 이름, 현재 상태, 가능한 action을 하나의 한국어 설명으로 제공한다. exact alarm 문구나 action은 추가하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.notification.NotificationPermissionEntryPointsTest" --tests "com.todoquest.feature.settings.SettingsViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.settings.SettingsScreenTest,com.todoquest.app.AppNavigationTest" --console=plain
git diff --check
```

## 검증 절차

1. 각 permission state의 label/action 및 launcher routing 실패 테스트를 먼저 작성한다.
2. Android 13+ runtime permission, app notification settings, channel settings 세 경로가 adapter를 통해 선택되는지 확인한다.
3. launcher 복귀와 lifecycle resume 후 상태 label이 갱신되는지 확인한다.
4. 권한 거부 상태에서도 navigation, SFX switch, Calendar/Shop action이 비활성화되지 않는지 확인한다.
5. 연결된 Android device가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
6. 성공 시 task index의 step 9를 `completed`로 바꾸고 권한 진입점과 lifecycle refresh를 한국어 `summary` 두 줄로 기록한다.

## 금지사항

- 화면 진입 즉시 권한 dialog를 자동 실행하지 마라. 이유: 설정 화면에서는 사용자의 명시적 action으로 권한 화면을 연다.
- Settings Composable에서 `NotificationManager`, `AlarmManager` 또는 DAO를 직접 호출하지 마라. 이유: platform 판정은 capability adapter, 상태 조회는 domain scheduler 경계를 사용한다.
- first-launch prompt의 once-only 상태를 설정 화면 action으로 소비하지 마라. 이유: Calendar 최초 진입 정책과 수동 설정 진입은 별개다.
- exact alarm 설정을 추가하지 마라. 이유: 확정 범위는 일반 알림 권한뿐이다.
- 권한이 없다는 이유로 일정·완료·보상·전투·구매 UI를 막지 마라. 이유: 핵심 기능은 Android 권한과 독립적이어야 한다.
- 표시용 문장을 Kotlin에 하드코딩하지 마라. 이유: 새 문구는 `strings.xml`의 한국어 리소스를 사용해야 한다.
- 기존 테스트를 삭제하거나 완화하지 마라.
