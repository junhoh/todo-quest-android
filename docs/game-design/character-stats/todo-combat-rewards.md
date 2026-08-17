# TODO 완료와 자동 전투 보상

[캐릭터 스탯 설계 인덱스로 돌아가기](../character-stats-design.md)

> 문서 지위: Post-MVP Combat Rewards의 canonical occurrence·공격·경제 계약이다. v1 공식은 기존 PENDING attack의 역사적 의미로 보존하고, Combat Reward v2는 새 attack에만 적용한다. ADR-020의 Task Difficulty Combat Balance v1은 별도 difficulty version으로 신규 completion attack에 구현했다. MVP 범위를 소급 변경하지 않으며 과거 direct reward와 legacy/APPLIED attack에는 새 보상이나 난이도 배율을 소급 적용하지 않는다.

## occurrence 결과와 멱등 경계

- 새 occurrence의 최초 완료는 `CompletionLog`, `rewardMode = COMBAT_ATTACK`인 `RewardLedger`, PENDING player attack event를 하나의 Room transaction에서 만든다.
- 새 ledger의 실제 지급량은 `0 XP / 0 골드`다. 캐릭터 성장은 player attack 적용 transaction에서만 확정한다.
- 기존 하루 20회 player attack 상한은 제거한다. 일일·반복 순번과 정시 여부는 MOMENTUM 및 호환 snapshot을 위해 유지하지만 모든 새 완료는 `combatEligible = true`다.
- 완료·ledger·player attack·monster attack은 각각 `(taskId, occurrenceDate)` 멱등 key를 사용한다. 종류가 다른 기록은 같은 occurrence에서도 서로를 막지 않는다.
- 완료 취소는 occurrence 표시만 `TODO`로 되돌리고 ledger와 attack event를 삭제하거나 보상을 회수하지 않는다. 재완료는 보상이나 공격을 다시 만들지 않는다.
- 완료 transaction 뒤 즉시 전투 처리는 best-effort다. 실패해도 완료를 롤백하지 않고 PENDING attack을 reconciliation이 재시도한다.

## Combat Reward v1 전투 보상 공식

대상 `MonsterInstance`에 저장된 level `L`과 grade를 공격 적용 직전에 읽는다. grade multiplier는 NORMAL `1`, ELITE `2`, BOSS `4`다.

```text
hitXp = 1 + floor((L - 1) / 10)
killBonusXp = isKill ? (10 + floor((L - 1) / 5)) × gradeMultiplier : 0
killGold = isKill
    ? floor((5 + floor((L - 1) / 10)) × gradeMultiplier
        × (10,000 + GOLD_GAIN_BONUS_BP) / 10,000)
    : 0
totalXp = hitXp + killBonusXp
```

- 공격이 비치명이어도 hit XP를 지급한다.
- 처치 공격은 hit XP, kill bonus XP, kill gold를 함께 지급한다.
- `GOLD_GAIN_BONUS_BP`는 공격 처리 직전 실제 장착 modifier에서 계산하며 처치 gold에만 적용한다.
- 모든 경제 값과 곱셈 중간값은 `Long` exact arithmetic을 사용하고 정수 나눗셈의 마지막에 내림한다.
- level 1 NORMAL 비치명 공격은 `1 XP`, 처치는 총 `11 XP / 5 골드`다. level 55 BOSS 처치는 총 `86 XP / 40 골드`이며 장비 gold bonus가 있으면 gold만 증가한다.

이 공식은 reward version `1`의 역사적 계약이다. 업데이트 전에 생성된 version `1` PENDING attack은 계속 이 공식으로 처리하며 v2 값으로 바꾸지 않는다.

## Combat Reward v2 전투 보상 공식

Combat Reward v2는 이 정책 승인 뒤 새로 만드는 reward version `2` player attack에만 적용한다. 대상 `MonsterInstance`의 level `L`과 grade multiplier NORMAL `1`, ELITE `2`, BOSS `4`를 사용한다.

```text
hitXp = 3 + floor((L - 1) / 10)
killBonusXp = isKill ? (20 + floor((L - 1) / 5)) × gradeMultiplier : 0
killGold = isKill
    ? floor((15 + floor((L - 1) / 10)) × gradeMultiplier
        × (10,000 + GOLD_GAIN_BONUS_BP) / 10,000)
    : 0
totalXp = hitXp + killBonusXp
```

- v1의 level band 분모 `10/5/10`, grade 배율과 정수 내림 순서를 유지한다.
- `GOLD_GAIN_BONUS_BP`는 계속 실제 장착 modifier에서 읽어 처치 gold에만 적용한다.
- level 1 NORMAL 처치는 `23 XP / 15골드`다.
- level 55 BOSS 처치는 장비 gold bonus가 없을 때 `128 XP / 80골드`다.
- 모든 중간값과 결과는 `Long` exact arithmetic을 사용한다.

## Task Difficulty Combat Balance v1 구현 계약

Task Difficulty Combat Balance v1은 Combat Reward v2의 base 공식 위에 completion 시점에 snapshot한 일정 난이도를 적용한다. difficulty balance version은 combat reward version과 별개이며 current version `1`은 다음 basis points를 사용한다.

| 일정 난이도 | 배율 | basis points |
|---|---:|---:|
| 쉬움 (`EASY`) | `100%` | `10,000` |
| 보통 (`MEDIUM`) | `150%` | `15,000` |
| 어려움 (`HARD`) | `200%` | `20,000` |

피해 적용 순서는 다음으로 고정한다.

1. completion attack에 저장한 `sourceAttack`에 저장한 `sourceMomentumBp`를 기존 정수 공식으로 적용한다.
2. 그 결과에 저장된 난이도와 difficulty version의 배율을 정수 내림으로 적용한다.
3. critical이면 난이도가 반영된 공격력에 기존 critical damage basis points를 적용한다.
4. 대상 몬스터의 기존 방어력 공식을 적용해 최종 피해를 계산한다.

XP는 Combat Reward v2가 계산한 `hitXp`와 `killBonusXp` 각각에 같은 배율을 따로 적용한다.

```text
scaledHitXp = hitXp > 0
    ? max(1, floor(hitXp × difficultyMultiplierBp / 10,000))
    : 0
scaledKillBonusXp = killBonusXp > 0
    ? max(1, floor(killBonusXp × difficultyMultiplierBp / 10,000))
    : 0
totalXp = scaledHitXp + scaledKillBonusXp
```

level 1 NORMAL 비치명 hit XP는 쉬움/보통/어려움 `3/4/6`, 처치 총 XP는 `23/34/46`이다. kill gold와 `GOLD_GAIN_BONUS_BP`, 몬스터 level band 분모 `10/5/10`, grade `1×/2×/4×`는 난이도 배율의 입력이나 결과가 아니며 변경하지 않는다.

difficulty balance version `0`은 nullable source difficulty와 관계없이 항상 중립 `100%`다. 업데이트 전에 존재한 PENDING/APPLIED attack은 null/version `0`으로 보존하며 현재 task 난이도를 조회해 backfill하거나 재계산하지 않는다. current version `1`은 non-null `EASY`·`MEDIUM`·`HARD`를 요구하고, 알 수 없는 version 또는 잘못된 source는 중립으로 추측하지 않고 transaction을 rollback해 PENDING source를 보존한다. APPLIED attack은 저장된 raw/final damage와 scaled XP·gold snapshot만 반환한다.

같은 `(taskId, occurrenceDate)`의 completion, RewardLedger와 player attack은 계속 한 번만 생성되고 XP도 attack transaction에서 한 번만 지급한다. 완료 취소·재완료, task 난이도 편집, process retry와 반복 분할은 기존 attack의 난이도/version을 바꾸거나 새 attack을 만들지 않는다.

**구현 상태**: Android·Room 비의존 `TaskDifficultyCombatBalanceCatalog`와 `TaskDifficultyCombatPolicy`를 테스트 우선으로 추가했다. Room v14 `player_attack_events`는 nullable `sourceTaskDifficulty`와 기본값 `0`인 `taskDifficultyBalanceVersion`을 저장한다. `MIGRATION_13_14`는 기존 v13 PENDING/APPLIED row를 null/version `0`으로 보존하고 task table에서 난이도를 backfill하지 않는다. 신규 completion transaction만 current 난이도와 difficulty version `1`을 snapshot하고 legacy repair는 null/version `0`을 만든다. `RoomCombatRepository`는 PENDING attack의 저장 version/source를 strict하게 검증해 피해와 hit/kill XP를 적용하며 kill gold를 바꾸지 않고, APPLIED snapshot은 재평가하지 않는다.

ADR-020의 Reminder Delivery Reliability v2는 runtime permission·앱 전체 switch와 `todo_task_reminders` 알림 채널 차단을 typed 상태로 분리하고 exact plan을 Room에 먼저 stage하는 별도 delivery 계약이다. 알림 채널 차단·scheduler 실패·`ERROR` 정리는 completion·RewardLedger·player attack과 XP transaction을 롤백하지 않으며 DND, 사용자 채널 선택, Force Stop과 제조사 정책을 우회하지 않는다.

## 공격 적용 transaction

지원하는 reward version의 PENDING player attack은 다음을 하나의 Room transaction에서 확정한다.

1. 저장된 공격력·치명타·MOMENTUM source로 피해를 계산하고 몬스터 HP를 갱신한다.
2. hit/kill 보상을 계산해 character total XP·gold와 level-up 미배분 포인트를 갱신한다.
3. XP로 `MAX_HP`가 변하면 공격 직전 old/new max HP로 현재 HP 비율을 한 번 보존한다.
4. 처치라면 갱신된 파생 능력치의 `HP_RECOVERY`를 적용하고 다음 몬스터와 Stage progress를 확정한다.
5. event를 APPLIED로 바꾸며 seed·roll·피해, reward version, hit/kill 지급량, grade multiplier와 gold bonus operand를 저장한다.

같은 APPLIED attack 조회는 저장된 결과만 반환하고 캐릭터·몬스터·Stage를 다시 변경하거나 transient transition을 다시 방출하지 않는다. SQLite transaction 중 어느 쓰기라도 실패하면 event, HP, 캐릭터, Stage, 보상 전체를 롤백한다.

## legacy와 Room v9

- `reward_ledger.rewardMode`는 `TODO_COMPLETION`과 `COMBAT_ATTACK`을 구분한다.
- `player_attack_events.combatRewardVersion = 0`은 무보상 legacy attack이고 Room v9에서 도입한 Combat Reward v1 version은 `1`이다.
- `MIGRATION_8_9`은 v8 ledger에 `TODO_COMPLETION`, 기존 PENDING/APPLIED attack에 reward version `0`과 지급량 `0`만 추가한다. 기존 XP·gold·HP·Stage·attack 결과를 변경하지 않는다.
- Room v9의 복구 계약은 legacy ledger에서 누락된 player attack을 version `0`, 당시 current ledger에서 누락된 attack을 version `1`로 만든다.
- 알 수 없는 reward mode나 version은 최신 공식으로 추측하지 않고 명시적으로 실패시켜 PENDING source를 보존한다.

Combat Reward v2 승인 뒤 version routing은 다음과 같다.

- version `0`은 계속 무보상 legacy로 처리하고 소급 지급하지 않는다.
- version `1` PENDING은 위의 Combat Reward v1 공식으로 처리한다.
- version `2`는 승인 뒤 새 attack에만 부여하고 v2 공식으로 처리한다.
- version과 관계없이 APPLIED attack은 저장된 reward operand/result snapshot을 반환하며 다시 계산하지 않는다.
- 이 정책은 v12에서 존재한 reward column만 사용했으며 현재 Room v14에서도 같은 reward snapshot을 보존한다. v14의 difficulty source/version은 combat reward version과 별도 column이다.

**구현 상태**: `CombatRewardBalanceCatalog`는 version `1`의 `1/10/5` config와 version `2`의 `3/20/15` config를 함께 제공하고 `CURRENT_VERSION = 2`를 새 completion attack에 snapshot한다. `CombatRewardPolicy.rewardFor(..., combatRewardVersion, ...)`는 저장된 version을 명시적으로 선택한다. `RoomCombatRepository`는 version `0`만 무보상 특례로 처리하고 PENDING v1·v2는 각 config로 한 번 적용하며, 미지원 version은 transaction을 rollback해 PENDING source를 보존한다. difficulty version `1`이면 base hit XP와 kill bonus XP를 각각 scale하고 kill gold는 그대로 둔다. APPLIED row는 seed·roll·피해와 hit/kill XP·gold operand/result snapshot을 그대로 반환하므로 재시작·동시 reconciliation에도 다시 지급하지 않는다. Severe Injury v1의 Room v13 원천은 Room v14에서도 이 reward snapshot과 독립적으로 보존된다.

## 정시 완료, 실패 공격과 MOMENTUM

- 시간이 있는 occurrence의 마감은 예정 시각+15분, 무시간 일정은 해당 로컬 날짜 종료다. 정시 여부와 실제 완료 로컬 날짜는 ledger에 보존한다.
- occurrence 날짜에 정시 완료가 하나 이상이면 연속 완료일을 하루 한 번만 증가시킨다. 연속 `3/7/14일`은 공격 `+3%/+5%/+8%` MOMENTUM 중 가장 높은 한 단계만 적용한다.
- 마감 뒤 미완료 occurrence의 monster attack은 앱 시작과 WorkManager best-effort reconciliation이 처리하며 알림·exact alarm 권한에 의존하지 않는다.
- 한 reconciliation의 실제 monster 피해는 `(occurrenceDate, taskId)` 순서의 처음 3건으로 제한하고 나머지는 영구 skip 결과를 남긴다.
- 실패 공격 뒤 늦게 완료해도 독립 player attack은 한 번 허용한다. 실패 취소는 이미 받은 monster 피해를 역산하지 않는다.

## UI 피드백

- Calendar task는 난이도별 직접 XP·gold 예상치 대신 완료 시 몬스터 공격과 hit/kill 보상 구조를 안내한다.
- `COMBAT_ATTACK` 완료는 Calendar reward snackbar를 만들지 않는다.
- 새 player attack transition은 비치명 `+N EXP`, 처치 `+N EXP · +G 골드` badge를 Battle Map에서 `600ms` 동안 한 번 표시한다.
- badge는 animation phase·input lock·queue 시간을 늘리지 않고 legacy/monster attack, Flow 재구독, process 재시작에서 replay하지 않는다.
- 확정 XP·gold는 Character Flow를 통해 Battle Map HUD와 Character 화면에 반영한다.

## 검증 경계

현재 순수 Kotlin 테스트는 combat reward v1·v2의 level band, grade, gold bonus, level 1 NORMAL `23 XP / 15골드`, level 55 BOSS `128 XP / 80골드`, difficulty version `0` 중립과 version `1`의 `100%/150%/200%`, MOMENTUM→난이도→critical→defense 순서, 비치명 `3/4/6`, level 1 NORMAL 처치 `23/34/46`, kill gold 불변과 overflow·unsupported version/source 거부를 검증한다. Room 테스트는 신규 완료의 direct award `0`, 21번째 이후 attack 생성, legacy difficulty null/version `0`, 신규 completion snapshot, PENDING routing, APPLIED snapshot 보존, 동시·반복 처리 멱등성과 reward/level-up/HP/Stage transaction 및 rollback을 검증한다. Compose 테스트는 최소 `104dp`를 확보하면서 EXP label과 값이 bar 양 끝에 정렬되는 group, 0 track, replay 없는 600ms badge, Calendar no-snackbar 계약을 검증한다.
