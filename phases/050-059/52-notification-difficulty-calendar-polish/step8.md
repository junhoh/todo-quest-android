# Step 8: document-and-validate-notification-balance

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/game-design/character-stats/todo-combat-rewards.md`
- `/scripts/run_phase_manager.ps1`
- `/scripts/run_harness.ps1`
- `/app/src/androidTest/java/com/todoquest/notification/ReminderIntegrationTest.kt`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step0.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step1.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step2.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step3.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step4.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step5.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step6.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step7.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/index.json`

## 작업

전체 구현을 검토하고 canonical 문서, Android end-to-end 검증 tooling과 phase catalog만 수정한다. production Kotlin/Room/Compose 로직은 이 step에서 새 기능을 추가하지 않고 발견된 문서·검증 회귀만 최소 수정한다.

PRD, Architecture, ADR-020, UI Guide, Development와 combat reward 문서를 실제 구현과 동기화한다. 다음을 명시한다.

- 새 difficulty balance version 1과 EASY/MEDIUM/HARD `100/150/200%`, damage 적용 순서, hit/kill XP와 gold 불변.
- Room v14 nullable difficulty/version 0 migration, 신규 completion snapshot, legacy PENDING/APPLIED 비소급.
- notification app permission·channel disabled 분리, staged plan race 방지, channel settings CTA, Android 23~25 default alert.
- Calendar mode·당일/전날 실제 발화 시각, capability recovery, compact 시각 버튼과 48dp target.
- EXP label/value의 progress bar 양끝 정렬과 기존 104dp·TalkBack 계약.

disposable emulator에서 실제 exact alarm→process-absent receiver→system notification 게시를 자동 검증하는 `scripts/verify_task_reminder_delivery.ps1`를 추가하거나 기존 smoke fixture를 감싸는 동등한 표준 진입점을 제공한다. 인터페이스는 다음을 만족한다.

~~~powershell
.\scripts\verify_task_reminder_delivery.ps1 -Serial emulator-5554
~~~

script는 explicit serial을 필수로 받고 기본적으로 `emulator-` serial만 허용한다. 물리 기기는 별도 명시 flag 없이는 install, permission 변경, app data reset을 수행하지 않는다. emulator에서 debug/test APK와 기존 `ReminderBackgroundSmokeFixtureTest`를 사용해 notification permission·exact capability를 확인/준비하고, 15~60초 미래 custom reminder를 seed한 뒤 `force-stop`이 아닌 `cmd activity stop-app com.todoquest`로 process만 종료한다. 최대 90초 bounded polling으로 다음을 모두 확인한다.

- `dumpsys alarm` delivery history에 occurrence alarm이 지정 시각에 존재한다.
- `dumpsys notification --noredact` 또는 동등한 system API에 `com.todoquest` occurrence tag의 active notification이 존재한다.
- TodoQuestReminder error log나 target crash가 없다.

진단 artifact는 `app/build/reminder-diagnostics/<serial>/` 아래에 저장하고 git에 추가하지 않는다. capability를 준비할 수 없거나 emulator/adb/Android 도구가 없으면 임의 설치하지 말고 blocked로 기록한다. 사용자 channel 설정·DND를 강제로 우회하지 않는다.

전체 debug/release unit, lint, APK, connected suite, reminder process-absent smoke, harness pytest, Room migration/schema, phase manager validation과 diff check를 실행한다. 모든 AC가 성공한 뒤 step 8을 completed와 한국어 summary로 갱신하고 manager `Sync`로 catalog status/README를 동기화한 뒤 `Validate`와 `Sync -Check`를 다시 실행한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat test lint assembleDebug --console=plain
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest --console=plain
.\scripts\verify_task_reminder_delivery.ps1 -Serial emulator-5554
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
.\scripts\run_phase_manager.ps1 -Command Validate
git diff --check
~~~

## 검증 절차

1. 문서의 version·gold·legacy·channel·UI 설명을 실제 코드와 테스트에 대조한다.
2. 전체 AC를 실행하고 실패하면 원인을 수정한 뒤 최대 3회 재검증한다.
3. Android 도구나 emulator가 없으면 설치하지 말고 step/phase를 blocked로 기록한다.
4. 성공 시 step 8을 `completed`와 한국어 summary로 갱신한다.
5. `.\scripts\run_phase_manager.ps1 -Command Sync`, `Validate`, `Sync -Check`를 차례로 실행해 registry와 catalog drift가 없는지 확인한다.

## 금지사항

- 실제 사용자 물리 기기의 app data나 permission을 기본 동작으로 변경하지 마라. 이유: 자동 검증은 disposable emulator에 격리해야 한다.
- process-absent 검증에 `force-stop`을 사용하지 마라. 이유: Android가 PendingIntent를 의도적으로 취소해 잘못된 실패를 만든다.
- 전체 AC 실패를 개별 test 성공으로 대체하지 마라. 이유: Room, UI, notification과 harness 회귀를 함께 확인해야 한다.
- 문서에 구현되지 않은 guarantee를 기록하지 마라. 이유: notification visibility는 사용자·DND·제조사 정책의 영향을 받는다.
- 기존 테스트를 깨뜨리지 마라.
