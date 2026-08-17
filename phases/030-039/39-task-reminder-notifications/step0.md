# Step 0: approve-task-reminder-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/phases/030-039/39-task-reminder-notifications/index.json`

## 작업

canonical 문서에 Todo Quest Task Reminder v1 계약을 승인하고 `ADR-015`를 추가한다. 이 step은 문서 계약만 다루며 Kotlin, Room schema, manifest를 수정하지 않는다.

일정 editor의 알림 선택지는 `설정 없음`, `10분 전`, `1시간 전`, `직접 설정` 네 가지다. 기본은 `설정 없음`이다. `10분 전`과 `1시간 전`은 일정 시간이 있을 때만 사용할 수 있고 자정을 넘어 이전 날짜에 발화할 수 있다. `직접 설정`은 occurrence 당일의 독립된 local time이며 일정 시간이 없는 할 일에도 사용할 수 있고, 일정 시간보다 뒤여도 허용한다. 직접 설정은 이전 날짜나 일수 offset을 받지 않는다.

알림은 task 원본 설정과 occurrence별 예약 key를 분리한다. 반복 일정은 현재 활성 task segment에서 다음 유효 occurrence 하나만 예약하며 알람 발화 뒤 다음 occurrence를 예약한다. 완료·실패·삭제된 occurrence는 발화 직전에도 재검증하고 알림을 억제한다. 생성·수정·반복 분할·삭제·완료·실패·각 취소는 해당 task의 예약을 재검토한다. 지나간 일회성 알림은 즉시 울리지 않고 `NO_FUTURE_OCCURRENCE`로 남기며, 반복 일정은 지나간 occurrence를 건너뛰고 다음 미래 occurrence를 예약한다. 기기 재부팅이나 프로세스 부재 뒤 missed notification을 몰아서 표시하지 않는다.

정확한 시각에는 `AlarmManager.setExactAndAllowWhileIdle()`과 manifest `BroadcastReceiver`를 사용한다. Android 12+에서는 `SCHEDULE_EXACT_ALARM`과 `canScheduleExactAlarms()`, Android 13+에서는 `POST_NOTIFICATIONS` runtime permission을 사용자 알림 저장 행동 이후 순차적으로 처리한다. `USE_EXACT_ALARM`은 사용하지 않는다. 권한이 없거나 scheduler가 실패해도 일정 저장·수정·삭제·occurrence 완료·실패·보상·전투 transaction은 성공 상태를 유지하고 표준화된 reminder status와 한국어 안내로 분리한다. exact 권한이 없을 때 부정확한 alarm으로 대체하지 않는다.

알림 채널은 높은 중요도, 기본 소리·진동을 사용해 가능한 기기에서 heads-up으로 표시하되 사용자 채널 설정과 방해 금지 모드를 우회하지 않는다. notification tap은 Calendar의 occurrence 날짜를 연다. 앱 시작, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, 시스템 시각·시간대 변경, exact-alarm 권한 부여 후 예약을 재검토한다. WorkManager는 재등록과 상태 복구만 담당하고 정확한 알림 발화에는 사용하지 않는다. Force Stop이나 Android background restricted 상태처럼 OS가 alarm·PendingIntent를 의도적으로 중단하는 경우는 보장 범위에서 제외하고 문서화한다.

## Acceptance Criteria

```powershell
rg -n "ADR-015|10분 전|1시간 전|직접 설정|SCHEDULE_EXACT_ALARM|POST_NOTIFICATIONS|NO_FUTURE_OCCURRENCE|heads-up|WorkManager" docs/PRD.md docs/ARCHITECTURE.md docs/ADR.md docs/UI_GUIDE.md docs/DEVELOPMENT.md
git diff --check
```

## 검증 절차

1. 문서 변경을 실행하고 현재 미구현 알림 설명과 새 승인 계약을 구분한다.
2. Android 공식 AlarmManager·notification permission·notification channel·force-stop 정책과 문서 결정을 대조한다.
3. ADR-006의 일정 저장·권한 실패 분리, 반복 occurrence 분리, 외부 캘린더 제외 규칙을 보존하는지 확인한다.
4. AC를 실행한 뒤 task index의 step 0을 `completed`로 바꾸고 승인 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Kotlin, Room schema 또는 manifest를 수정하지 마라. 이유: 이 step은 구현 전 제품·아키텍처 계약만 승인한다.
- 알림 권한이나 exact-alarm 권한 거부를 일정 저장 실패로 기록하지 마라. 이유: ADR-006과 핵심 기능 독립성 계약을 위반한다.
- 반복 원본 전체를 occurrence 하나의 알림 상태로 완료 처리하지 마라. 이유: 반복 원본과 날짜별 발생분 분리 규칙을 보존해야 한다.
- WorkManager로 정확한 시각 알림을 보장한다고 기록하지 마라. 이유: WorkManager는 best-effort 재등록 수단이다.
- 기존 테스트를 깨뜨리지 마라.
