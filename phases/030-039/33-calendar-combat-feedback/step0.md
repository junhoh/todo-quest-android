# Step 0: document-calendar-combat-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/docs/game-design/character-stats/combat-calculation.md`
- `/phases/020-029/28-monster-stats-and-combat/index.json`
- `/phases/020-029/29-calendar-battle-map/index.json`
- `/phases/030-039/30-calendar-battle-progress-hud/index.json`
- `/phases/030-039/32-character-layer-runtime-composition/index.json`
- `/phases/030-039/33-calendar-combat-feedback/index.json`

## 작업

Calendar Combat Feedback v1의 승인 계약을 문서에 먼저 확정한다. 기존 Calendar Battle Map의 단일 scroll 계약을 이번 범위에서 명시적으로 교체해, 시스템 inset 다음 고정 Battle Map과 그 아래 독립 scroll Calendar content를 사용한다고 기록한다. 고정 영역은 진행 HUD, player/monster, 두 HP bar와 전투 effect 전체이며, scroll 영역은 월 이동·요일/날짜 grid·선택 날짜·완료/실패 요약·추가 버튼·task 목록·빈 안내다.

occurrence 상태는 `TODO`, `COMPLETED`, `FAILED` 세 값으로 정의한다. 사용자가 선택한 실패 취소를 제공하되 실패 취소는 task 표시 상태만 TODO로 되돌리고 이미 적용된 monster damage와 combat event는 되돌리지 않는다. 실패 취소 후 늦은 완료는 기존 보상·player attack 계약을 따르고, 다시 실패해도 `(taskId, occurrenceDate)` monster event가 이미 있으면 두 번째 damage나 animation을 만들지 않는다.

수동 실패는 즉시 monster attack을 시도하고 실패 영속 성공 뒤 combat 처리만 실패하면 reconciliation이 복구한다. `MANUAL_FAILURE`와 기존 `MISSED_DEADLINE`은 같은 `monster_attack_events` occurrence key를 사용한다. player/monster HP, reward, active monster와 Stage는 Room source state이고, attack/hit/death/spawn/damage text는 replay 없는 transient transition임을 분리한다. 몬스터 처치 추가 XP·gold·loot는 만들지 않고 기존 occurrence reward와 victory HP recovery, lethal damage 후 최대 HP 25% 즉시 부활 규칙을 유지한다.

HUD는 level·gold icon/value·EXP current/required를 한 Row에 두고 progress bar만 아래에 유지한다. 두 HP bar는 actor 상단에 placement geometry로 배치한다. 일반 화면의 190dp..320dp map 계약은 유지하되 저높이 화면은 calendar scroll viewport를 확보하도록 150dp..190dp compact-height 범위를 허용한다. 공격 effect는 Compose Material icon과 translation/shake/flash/alpha를 사용하고 pixel bitmap 확대 보간이나 새 bitmap·sound asset을 요구하지 않는다.

## Acceptance Criteria

```powershell
rg -n "Calendar Combat Feedback|TODO|COMPLETED|FAILED|MANUAL_FAILURE|MISSED_DEADLINE|실패 취소|고정" docs\PRD.md docs\ARCHITECTURE.md docs\ADR.md docs\UI_GUIDE.md docs\game-design\monster-stats-and-growth.md
git diff --check
```

## 검증 절차

1. AC 명령을 실행하고 문서 간 영구 상태·일회성 transition·고정 layout 계약이 충돌하지 않는지 확인한다.
2. PRD의 기존 occurrence 보상, 단일 활성 monster, 25% 부활과 권한 비의존 계약을 확인한다.
3. task index의 step 0을 `completed`로 바꾸고 승인된 범위와 핵심 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Monster Combat v1의 피해·부활·Stage·보상 수식을 바꾸지 마라. 이유: 이번 phase는 기존 전투 결과의 입력과 표현을 확장한다.
- 새 monster 처치 보상이나 audio 범위를 승인하지 마라. 이유: 기존 domain과 asset 구조에 없는 기능이다.
- Google Calendar 연동을 추가하지 마라. 이유: AGENTS.md의 MVP 제외 범위다.
- 기존 테스트를 깨뜨리지 마라.
