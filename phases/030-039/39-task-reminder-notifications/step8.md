# Step 8: document-and-validate-reminders

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/app/src/main/AndroidManifest.xml`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomReminderRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/ReminderPlanner.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/ReconcileTaskReminderUseCase.kt`
- `/app/src/main/java/com/todoquest/notification/AndroidReminderScheduler.kt`
- `/app/src/main/java/com/todoquest/notification/ReminderAlarmReceiver.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/phases/030-039/39-task-reminder-notifications/step0.md`
- `/phases/030-039/39-task-reminder-notifications/index.json`

## 작업

canonical 문서와 검증표를 실제 구현 결과에 맞게 최종 동기화한다. 파일·symbol 이름이 구현 과정에서 합리적으로 달라졌다면 실제 source를 기준으로 읽어야 할 경로와 문구를 보정하되 step 0의 제품 결정을 바꾸지 않는다.

- PRD·ADR-015에 네 mode, custom 당일 시각, 무시간 task 규칙, occurrence별 다음 alarm과 권한 실패 격리를 기록한다.
- ARCHITECTURE에 Room v10 `task_reminders`, Task Repository transaction, Reminder Repository→UseCase→AlarmManager/Publisher data flow, receiver 발화 재검증과 WorkManager restore 경계를 반영한다.
- UI_GUIDE에 editor selector·time picker·disabled preset·permission rationale·status warning·한국어 접근성·heads-up channel을 기록한다.
- DEVELOPMENT에 v10 migration chain, domain/repository/scheduler/receiver/worker/UI/application test matrix, 실제 connected/background smoke 방법과 Force Stop·background restriction 한계를 기록한다.
- manifest permission과 Android 12/13/15 정책, channel 사용자 제어, reboot/app update/timezone/exact-grant restore를 구현과 일치시킨다.

전체 검증을 실행한다. connected suite는 notification UI·manifest receiver·navigation 통합의 필수 gate다. process-absent smoke는 가능한 연결 기기에서 수행하고 실행 환경 때문에 불가능하면 성공으로 꾸미지 말고 blocked로 기록한다. 모든 step이 `completed`인지 확인한 뒤 task index step 8과 root `phases/index.json` phase를 `completed`, `completed_at`, 한국어 `summary`로 갱신한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
.\.venv\Scripts\python.exe -c "import pathlib,re,urllib.parse; bad=[]; backslashes=[]; absolute=[]; files=list(pathlib.Path('docs').rglob('*.md')); links=[(p,t) for p in files for t in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8'))]; [(bad.append((str(p),t))) for p,t in links if not (t.startswith(('http://','https://','mailto:','#')) or (p.parent / urllib.parse.unquote(t.split('#',1)[0])).exists())]; [(backslashes.append((str(p),t))) for p,t in links if '\\' in t]; [(absolute.append((str(p),t))) for p,t in links if re.match(r'^[A-Za-z]:',t)]; assert not bad,bad; assert not backslashes,backslashes; assert not absolute,absolute; print(f'{len(files)} markdown files and {len(links)} links checked')"
git diff --check
```

## 검증 절차

1. 실제 public signatures, Room schema, manifest, notification channel, Calendar strings와 step 0 계약을 대조해 문서를 갱신한다.
2. AC를 순서대로 실행하고 JVM debug/release, lint, APK·androidTest APK, 전체 connected suite, harness pytest, 문서 링크가 모두 성공하는지 확인한다.
3. AGENTS.md의 UI boundary, occurrence 분리·보상 멱등성, 권한 독립, 한국어 resource와 테스트 우선 규칙을 최종 검토한다.
4. 연결 기기나 필수 도구가 없으면 설치하지 말고 step과 phase를 `blocked`로 기록한다.
5. 성공하면 step과 root phase를 완료 처리하고 Room v10·exact background notification·권한/UI·전체 검증 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 실제 구현보다 강한 heads-up·정확성·background 보장을 문서화하지 마라. 이유: 사용자 채널 설정, DND, Force Stop과 OS restriction은 앱이 우회할 수 없다.
- connected test 또는 process-absent 검증 미실행을 성공으로 기록하지 마라. 이유: 앱이 꺼진 상태의 notification delivery가 핵심 acceptance다.
- `scripts/execute.py`를 직접 호출하지 마라. 이유: Windows 표준 진입점은 `run_harness.ps1`이다.
- Android 도구를 임의 설치하지 마라. 이유: 사용자 환경 변경은 별도 승인 phase에서만 허용된다.
- 기존 테스트를 깨뜨리지 마라.
