# Step 1: splashscreen-regression-audit

## Read First

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/010-019/10-emulator-connected-regression-fix/step1.md`
- `/phases/010-019/11-device-launch-compatibility/index.json`
- `/phases/010-019/11-device-launch-compatibility/step0.md`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/main/AndroidManifest.xml`
- `/app/src/main/res/values/styles.xml`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`

## Work

Audit the app launch path for compatibility with both emulators and physical devices without reintroducing the custom splash exit listener that phase 10 removed.

- Keep `installSplashScreen()` before `super.onCreate(savedInstanceState)`.
- Do not keep `splashScreen.setOnExitAnimationListener { it.remove() }` unless there is a real custom animation.
- Keep `setContent { TodoQuestApp() }` on the production launch path.
- Keep `Theme.TodoQuest.Starting` based on `Theme.SplashScreen` with `postSplashScreenTheme=@style/Theme.TodoQuest`.
- Do not change UI, ViewModel, Repository, Room, completion, or reward logic.
- Use `MainActivityLaunchSmokeTest` and manual diagnostics to decide whether a failure is an app launch issue or a device compositor issue.

## Acceptance Criteria

```powershell
adb devices
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.MainActivityLaunchSmokeTest"
git diff --check
```

## Validation

1. Verify a connected emulator or physical device is in `device` state.
2. Run the acceptance commands where the local environment allows them.
3. If `MainActivityLaunchSmokeTest` still reports a black screenshot, inspect the failure message, screenshot path, `dumpsys window visible`, and `dumpsys SurfaceFlinger --layers`.
4. If `am start -W` and process creation succeed but the app surface is `shown=false` or hidden by parent while stale splash/transition layers remain, record that as a device/AVD compositor failure instead of adding more app UI changes.
5. Mark this step `completed` when the production launch code avoids the known splash listener regression and the remaining failure mode is documented with artifacts.

## Prohibited

- Do not move `installSplashScreen()` after `super.onCreate()`.
- Do not add a custom splash exit listener just to call `remove()`.
- Do not weaken or delete the black-screen screenshot assertion.
- Do not call Room DAO, AlarmManager, or WorkManager directly from UI code.
- Do not add Google Calendar integration.
