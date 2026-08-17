# Step 0: approve-notification-and-difficulty-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/game-design/character-stats/todo-combat-rewards.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/index.json`

## 작업

문서만 수정해 Task Difficulty Combat Balance v1과 Reminder Delivery Reliability v2 계약을 먼저 승인한다. `/docs/ADR.md`에는 `ADR-020`을 추가하고, PRD·Architecture·UI Guide·combat reward 문서에는 동일한 승인 범위와 예정 구현을 기록한다.

난이도 배율은 신규 player attack에 대해 쉬움 `100%`, 보통 `150%`, 어려움 `200%`다. 피해 계산에서는 현재 캐릭터 공격과 MOMENTUM을 반영한 뒤 난이도 배율을 적용하고, 그 다음 치명타와 대상 방어력을 적용한다. Combat Reward v2가 계산한 hit XP와 kill bonus XP 각각에 같은 난이도 배율을 정수 내림으로 적용하고 양수 결과는 최소 1을 보장한다. kill gold, `GOLD_GAIN_BONUS`, 몬스터 level·grade 공식은 변경하지 않는다. level 1 NORMAL 처치 XP는 쉬움 `23`, 보통 `34`, 어려움 `46`, 비치명 hit XP는 `3/4/6`으로 고정한다.

난이도 의미는 별도 version과 completion 시점 snapshot으로 저장한다. 업데이트 전에 생성된 PENDING/APPLIED attack은 version `0` 중립 배율로 보존하며 현재 task 난이도를 소급 조회하거나 재계산하지 않는다. 새 attack만 current difficulty version을 사용한다. 같은 `(taskId, occurrenceDate)`의 completion, RewardLedger, player attack과 XP 지급은 계속 한 번만 확정한다.

알림은 runtime permission과 앱 전체 notification switch뿐 아니라 `todo_task_reminders` channel 차단을 독립적으로 판정한다. 채널이 꺼졌으면 일정 저장은 성공시키고 typed status·한국어 안내·channel settings CTA를 제공한다. 새 exact alarm plan은 Room에 먼저 stage한 뒤 AlarmManager에 등록해 바로 발화하는 callback도 유효 key를 확인할 수 있게 한다. scheduler가 실패하면 orphan alarm을 best-effort 취소하고 조건부로 ERROR를 기록하되 이미 receiver가 claim한 DELIVERED 상태를 덮어쓰지 않는다. DND·사용자 채널 선택·force-stop·제조사 정책은 우회하지 않는다.

Calendar task 목록은 알림 설정이 있을 때만 `10분 전 · 전날 23:50`, `직접 설정 · 당일 08:00`처럼 mode와 occurrence 기준 실제 local 발화 시각을 표시한다. capability가 막힌 task에는 복구 CTA를 제공한다. `완료`·`실패`는 content-sized 시각 버튼으로 줄이되 텍스트와 아이콘, 최소 48dp 터치 영역을 유지한다. Battle EXP 영역은 bar 왼쪽에 `EXP`, 오른쪽에 현재/필요 수치를 bar 양 끝에 맞춰 배치한다.

이번 step에서는 Kotlin, XML resource, Room schema, Android manifest와 script를 수정하지 않는다.

## Acceptance Criteria

~~~powershell
rg -n "ADR-020|100%|150%|200%|23|34|46|알림 채널|전날|48dp" docs/PRD.md docs/ARCHITECTURE.md docs/ADR.md docs/UI_GUIDE.md docs/game-design/character-stats/todo-combat-rewards.md
git diff --check
~~~

## 검증 절차

1. 기존 ADR-014·ADR-015·ADR-018·ADR-019와 새 ADR-020을 대조한다.
2. 기존 PENDING/APPLIED 의미, occurrence 멱등성, 권한 실패 독립성과 외부 캘린더 제외가 보존됐는지 확인한다.
3. AC를 실행하고 phase index의 step 0을 `completed`와 한국어 summary로 갱신한다.

## 금지사항

- Kotlin, Room schema, resource 또는 script를 수정하지 마라. 이유: 이 step은 구현 전 제품·호환성 계약 승인에 한정한다.
- 기존 PENDING/APPLIED attack에 현재 task 난이도를 소급 적용하지 마라. 이유: 이미 확정된 outbox와 보상 의미를 바꾸면 멱등성과 업데이트 호환성이 깨진다.
- 알림 채널·DND·force-stop을 우회한다고 문서화하지 마라. 이유: Android 사용자 선택과 플랫폼 정책을 존중해야 한다.
- 48dp 터치 영역을 줄인다고 문서화하지 마라. 이유: 버튼 축소는 시각 크기와 폭에 한정한다.
- 기존 테스트를 깨뜨리지 마라.
