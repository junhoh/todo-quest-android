# Step 0: approve-combat-reward-replacement

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/docs/game-design/character-stats/stats-and-progression.md`
- `/docs/game-design/character-stats/todo-combat-rewards.md`
- `/phases/030-039/38-hud-stat-descriptions-combat-rewards/index.json`

## 작업

제품·아키텍처 문서에 Post-MVP Combat Rewards v1을 승인하고 `ADR-014`를 추가한다. 이 step은 문서 계약만 다루며 Kotlin이나 Room schema를 수정하지 않는다.

신규 완료는 기존 난이도별 직접 XP·골드 지급을 사용하지 않고 모든 occurrence에 player attack outbox를 만든다. 기존 하루 20회 `combatEligible` 상한은 신규 완료에 대해 제거한다. completion ledger는 occurrence 멱등성, 정시 여부, 연속일과 MOMENTUM source를 계속 보존하지만 신규 실제 지급량은 `0 XP / 0 골드`, 보상 방식은 `COMBAT_ATTACK`이다. 일정 완료 transaction 성공 뒤 전투 처리는 계속 best-effort이며, 전투 실패가 완료를 롤백하지 않고 PENDING event를 reconciliation이 재시도한다.

전투 보상은 공격 대상 `MonsterInstance`에 저장된 level `L`과 grade multiplier `M`을 사용한다. `M`은 NORMAL `10,000bp`, ELITE `20,000bp`, BOSS `40,000bp`다.

```text
hitXp = 1 + floor((L - 1) / 10)
killBonusXp = isKill ? floor((10 + floor((L - 1) / 5)) × M / 10,000) : 0
killGold = isKill
    ? floor((5 + floor((L - 1) / 10)) × M × (10,000 + GOLD_GAIN_BONUS_BP) / 10,000²)
    : 0
totalXp = hitXp + killBonusXp
```

모든 곱셈은 `Long`과 exact arithmetic을 사용하고 마지막 정수 나눗셈에서 한 번 내린다. 처치 공격은 hit XP와 kill bonus를 함께 받는다. 보상은 player attack event의 `(taskId, occurrenceDate)` key로 피해·Stage·캐릭터 성장과 같은 Room transaction에서 한 번만 확정한다. v8에 이미 존재하는 APPLIED/PENDING player attack은 legacy reward version `0`으로 보존하고 새 보상을 소급 지급하지 않는다. 기존 character XP·gold와 reward ledger snapshot도 수정하지 않는다.

UI 계약도 승인한다. Battle Map EXP bar는 시각적 `EXP` label 왼쪽부터 current/required 값 오른쪽까지만 사용하고 `0/100`에서도 outline과 track이 보여야 한다. 전투 보상은 replay 없는 600ms 배지로 표시하되 기존 Calendar 완료 Snackbar와 합치지 않는다. Character 기본·파생 능력치는 ViewModel 소유 선택 상태로 한국어 설명 dialog를 열고, 기본 stat의 pending 보조 문구는 `- / 값 / +` 위치를 이동시키지 않는다.

## Acceptance Criteria

```powershell
rg -n "ADR-014|COMBAT_ATTACK|hitXp|killBonusXp|20회|소급" docs/PRD.md docs/ARCHITECTURE.md docs/ADR.md docs/UI_GUIDE.md docs/game-design
git diff --check
```

## 검증 절차

1. 문서에서 기존 TODO 직접 보상·20회 전투 상한·처치 보상 제외 문구를 찾아 새 승인 계약과 충돌하지 않게 구분한다.
2. AC를 실행하고 ADR-005 occurrence ledger와 ADR-009 독립 attack event, ADR-012 replay 없는 presentation을 보존하는지 확인한다.
3. task index의 step 0을 `completed`로 바꾸고 승인된 공식과 비소급 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Kotlin이나 Room schema를 수정하지 마라. 이유: 이 step은 구현 전에 제품·경제·영속 경계를 승인하는 문서 step이다.
- 기존 완료·RewardLedger·attack event에 새 보상을 소급한다고 기록하지 마라. 이유: 이미 확정된 경제 snapshot을 바꾸면 데이터 무결성이 깨진다.
- 전투 실패가 일정 완료를 롤백하도록 설계하지 마라. 이유: 핵심 일정 기능과 best-effort 전투 경계를 유지해야 한다.
- 기존 테스트를 깨뜨리지 마라.
